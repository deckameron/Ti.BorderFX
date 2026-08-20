package ti.borderfx;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.View;

import java.util.Objects;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

@Kroll.proxy
public class BorderFXEffectProxy extends KrollProxy implements Choreographer.FrameCallback
{
	private static final String LCAT = "BorderFXEffect";

	private static final String MODE_BEAM = "beam";
	private static final String MODE_ROTATE = "rotate";
	private static final String MODE_BOTH = "both";

	private final TiViewProxy targetProxy;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	// On Android we don't need the mask/container trick from iOS: we draw the
	// stroke directly with a Paint whose shader is a SweepGradient (the native
	// conic gradient). Rotating the gradient is just rotating the shader's
	// localMatrix — the border geometry itself never moves.
	private View targetView; // Titanium's outer view (TiBorderWrapperView when a borderRadius is set)
	private BorderFXDrawable drawable;

	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path outlinePath = new Path();   // full outline (rounded rect)
	private final Path beamPath = new Path();      // the "beam" segment, cut out of the outline every frame
	private final PathMeasure pathMeasure = new PathMeasure();
	private final Matrix shaderMatrix = new Matrix();
	private SweepGradient shader;

	// Motion is computed manually every frame from elapsed wall-clock time —
	// same design as iOS (no ObjectAnimator), immune to other animations
	// touching the same view hierarchy.
	private boolean running = false;
	private boolean effectVisible = false; // nothing renders before the first start()
	private boolean destroyed = false;
	private long startNanos;
	private float lastProgress = 0f; // 0..1 within the current lap — persists through stop() (freezes the frame)

	private String mode;          // "beam" | "rotate" | "both"
	private Object rawBorderWidth;
	private float borderWidthPx;
	private float beamLength;     // fraction of the perimeter (0..1), used in "beam"/"both"
	private double durationSec;   // seconds per full lap
	private Object rawColors;
	private int[] colors;

	private int lastW = -1;
	private int lastH = -1;
	private Object lastRadiusRaw;
	private float perimeter = 0f;

	public BorderFXEffectProxy(TiViewProxy targetProxy, KrollDict options)
	{
		super();
		this.targetProxy = targetProxy;

		mode = TiConvert.toString(options.get("mode"), MODE_BOTH);
		rawBorderWidth = options.containsKey("borderWidth") ? options.get("borderWidth") : Integer.valueOf(2);
		beamLength = Math.min(Math.max(TiConvert.toFloat(options.get("beamLength"), 0.25f), 0f), 1f);
		durationSec = TiConvert.toFloat(options.get("duration"), 3000f) / 1000.0;
		rawColors = options.get("colors");

		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeJoin(Paint.Join.ROUND);
	}

	// Called by the module right after construction (not in the constructor, to
	// avoid leaking "this" before initialization completes). All view
	// manipulation has to happen on the main thread — Kroll methods arrive on
	// the JS runtime thread, so everything is posted to the Handler.
	void attachToTarget()
	{
		mainHandler.post(this::initOnMainThread);
	}

	// ---------------------------------------------------------------- setup

	private void initOnMainThread()
	{
		if (destroyed) {
			return;
		}
		// Force creation of the native view behind the Titanium proxy
		// (equivalent to [targetProxy view] on iOS).
		TiUIView uiView = targetProxy.getOrCreateView();
		if (uiView == null) {
			Log.w(LCAT, "attach: target view proxy has no native view");
			return;
		}
		View view = resolveNativeView(uiView);
		if (view == null) {
			Log.w(LCAT, "attach: could not resolve a native view for the target proxy");
			return;
		}
		targetView = view;

		borderWidthPx = dimensionToPixels(rawBorderWidth, view);
		colors = parseColors(rawColors);

		// ViewOverlay draws ON TOP of the existing view without wrapping or
		// re-parenting anything — preserves the module's "decorator" contract.
		drawable = new BorderFXDrawable();
		view.getOverlay().add(drawable);

		// Choreographer = the direct analog of CADisplayLink: one callback per vsync.
		Choreographer.getInstance().postFrameCallback(this);
	}

	// The outer view is the TiBorderWrapperView when Titanium wraps the view
	// because of borderRadius/borderWidth — it's the one with the full visual size.
	private View resolveNativeView(TiUIView uiView)
	{
		View outer = uiView.getOuterView();
		return (outer != null) ? outer : uiView.getNativeView();
	}

	private float dimensionToPixels(Object value, View view)
	{
		if (value == null) {
			return 0f;
		}
		TiDimension dim = TiConvert.toTiDimension(value, TiDimension.TYPE_WIDTH);
		return (dim != null) ? (float) dim.getPixels(view) : 0f;
	}

	private int[] parseColors(Object value)
	{
		Object[] items = (value instanceof Object[]) ? (Object[]) value : null;
		if (items == null || items.length == 0) {
			// fallback: a small default rainbow (same as iOS)
			items = new Object[] { "#FF00FF", "#00FFFF", "#FFFF00", "#FF00FF" };
		}
		Activity activity = TiApplication.getAppRootOrCurrentActivity();
		// SweepGradient requires at least 2 colors — duplicate if only one was given.
		int[] result = new int[Math.max(items.length, 2)];
		for (int i = 0; i < items.length; i++) {
			result[i] = TiConvert.toColor(TiConvert.toString(items[i]), activity);
		}
		if (items.length == 1) {
			result[1] = result[0];
		}
		return result;
	}

	// Read the borderRadius YOU configured on the Titanium proxy (via JS), not
	// whatever ended up on the native view — on Android the rounded corner is
	// drawn by TiBackgroundDrawable/TiBorderWrapperView and never exposed back
	// on the View.
	private float resolveCornerRadiusPx(Object raw, View view)
	{
		if (raw instanceof Object[]) {
			Object[] arr = (Object[]) raw;
			raw = (arr.length > 0) ? arr[0] : null; // per-corner radii: we use the first one
		}
		return dimensionToPixels(raw, view);
	}

	// ---------------------------------------------------------------- frame tick

	@Override
	public void doFrame(long frameTimeNanos)
	{
		if (destroyed) {
			return;
		}
		boolean geometryChanged = syncGeometryIfNeeded();
		updateMotion(frameTimeNanos, geometryChanged);
		Choreographer.getInstance().postFrameCallback(this);
	}

	// Same strategy as iOS: re-read bounds/cornerRadius every frame and no-op
	// cheaply when nothing changed. Unlike iOS we don't need a presentation
	// layer — Titanium's animate() on Android updates the real layout per
	// frame, so getWidth()/getHeight() already reflect the displayed size.
	private boolean syncGeometryIfNeeded()
	{
		TiUIView uiView = targetProxy.peekView();
		if (uiView != null && drawable != null) {
			View current = resolveNativeView(uiView);
			// If borderRadius gets set AFTER attach, Titanium wraps the view in
			// a new TiBorderWrapperView — move the overlay along with it.
			if (current != null && current != targetView) {
				if (targetView != null) {
					targetView.getOverlay().remove(drawable);
				}
				targetView = current;
				current.getOverlay().add(drawable);
				lastW = -1; // force a full geometry rebuild
			}
		}

		View view = targetView;
		if (view == null || drawable == null) {
			return false; // target view is gone; destroy() should have been called
		}
		int w = view.getWidth();
		int h = view.getHeight();
		if (w <= 0 || h <= 0) {
			return false; // hasn't been laid out yet
		}

		Object radiusRaw = targetProxy.getProperty(TiC.PROPERTY_BORDER_RADIUS);
		boolean sizeChanged = (w != lastW) || (h != lastH);
		boolean radiusChanged = !Objects.equals(radiusRaw, lastRadiusRaw);
		if (!sizeChanged && !radiusChanged) {
			return false; // nothing changed — don't rebuild the path 60 times a second for nothing
		}

		lastW = w;
		lastH = h;
		lastRadiusRaw = radiusRaw;

		float cornerRadius = resolveCornerRadiusPx(radiusRaw, view);
		float inset = borderWidthPx / 2f;
		paint.setStrokeWidth(borderWidthPx);

		// Same adjustment as iOS: when the rect shrinks by the inset, the corner
		// radius has to shrink with it (CSS border-box -> padding-box logic),
		// otherwise the corner deforms proportionally to borderWidth.
		RectF pathRect = new RectF(inset, inset, w - inset, h - inset);
		float pathCornerRadius = Math.max(cornerRadius - inset, 0f);
		outlinePath.rewind();
		outlinePath.addRoundRect(pathRect, pathCornerRadius, pathCornerRadius, Path.Direction.CW);

		// PathMeasure gives the exact perimeter for free — no manual math like on iOS.
		pathMeasure.setPath(outlinePath, false);
		perimeter = pathMeasure.getLength();

		// The sweep's center is the view's center (equivalent to the conic
		// startPoint 0.5/0.5 on iOS); the zero angle is at 3 o'clock on both systems.
		shader = new SweepGradient(w / 2f, h / 2f, colors, null);
		shader.setLocalMatrix(shaderMatrix);
		paint.setShader(shader);

		drawable.setBounds(0, 0, w, h);
		return true;
	}

	private void updateMotion(long frameTimeNanos, boolean geometryChanged)
	{
		if (drawable == null || perimeter <= 0) {
			return;
		}
		// A resize while the effect is stopped still needs the frozen frame
		// redrawn at the new geometry — that's why geometryChanged also
		// triggers the redraw.
		boolean needsRedraw = geometryChanged;
		if (running && durationSec > 0) {
			double elapsed = (frameTimeNanos - startNanos) / 1_000_000_000.0;
			double laps = elapsed / durationSec;
			lastProgress = (float) (laps - Math.floor(laps));
			needsRedraw = true;
		}
		if (!needsRedraw) {
			return;
		}

		if (!MODE_ROTATE.equals(mode)) {
			rebuildBeamPath();
		}
		if (!MODE_BEAM.equals(mode) && shader != null) {
			shaderMatrix.setRotate(lastProgress * 360f, lastW / 2f, lastH / 2f);
			shader.setLocalMatrix(shaderMatrix);
		}
		drawable.invalidateSelf();
	}

	// Instead of DashPathEffect (historically unreliable on hardware-accelerated
	// canvases), we cut the beam segment straight out of the outline with
	// PathMeasure.getSegment() — and get round caps on both ends for free.
	private void rebuildBeamPath()
	{
		beamPath.rewind();
		float dashLength = Math.max(perimeter * beamLength, 1f);
		float start = lastProgress * perimeter;
		float end = start + dashLength;
		if (end <= perimeter) {
			pathMeasure.getSegment(start, end, beamPath, true);
		} else {
			// the segment crosses the path's "end" — split it into two slices
			pathMeasure.getSegment(start, perimeter, beamPath, true);
			pathMeasure.getSegment(0f, end - perimeter, beamPath, true);
		}
	}

	// ---------------------------------------------------------------- JS API

	@Kroll.method
	public void start()
	{
		mainHandler.post(() -> {
			if (destroyed) {
				return;
			}
			effectVisible = true;
			startNanos = System.nanoTime();
			running = true;
		});
	}

	@Kroll.method
	public void stop()
	{
		// freezes at the current frame, without "jumping back" to the start
		mainHandler.post(() -> running = false);
	}

	@Kroll.method
	public void updateColors(Object newColors)
	{
		mainHandler.post(() -> {
			if (destroyed) {
				return;
			}
			colors = parseColors(newColors);
			if (lastW > 0 && lastH > 0) {
				shader = new SweepGradient(lastW / 2f, lastH / 2f, colors, null);
				shader.setLocalMatrix(shaderMatrix);
				paint.setShader(shader);
				if (drawable != null) {
					drawable.invalidateSelf();
				}
			}
		});
	}

	@Kroll.method
	public void destroy()
	{
		mainHandler.post(this::teardown);
	}

	private void teardown()
	{
		if (destroyed) {
			return;
		}
		destroyed = true;
		running = false;
		Choreographer.getInstance().removeFrameCallback(this);
		if (targetView != null && drawable != null) {
			targetView.getOverlay().remove(drawable);
		}
		drawable = null;
		targetView = null;
	}

	@Override
	public void release()
	{
		mainHandler.post(this::teardown);
		super.release();
	}

	// ---------------------------------------------------------------- drawable

	private class BorderFXDrawable extends Drawable
	{
		@Override
		public void draw(Canvas canvas)
		{
			if (!effectVisible || paint.getShader() == null) {
				return;
			}
			if (MODE_ROTATE.equals(mode)) {
				canvas.drawPath(outlinePath, paint); // solid ring — the color "travels" via shader rotation
			} else {
				canvas.drawPath(beamPath, paint);
			}
		}

		@Override
		public void setAlpha(int alpha)
		{
		}

		@Override
		public void setColorFilter(ColorFilter colorFilter)
		{
		}

		// Deprecated but still abstract on Drawable — implementing it is mandatory.
		@SuppressWarnings("deprecation")
		@Override
		public int getOpacity()
		{
			return PixelFormat.TRANSLUCENT;
		}
	}
}
