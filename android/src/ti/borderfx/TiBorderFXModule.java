package ti.borderfx;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.proxy.TiViewProxy;

@Kroll.module(name = "TiBorderFX", id = "ti.borderfx")
public class TiBorderFXModule extends KrollModule
{
	public TiBorderFXModule()
	{
		super();
	}

	@Kroll.method
	public BorderFXEffectProxy attach(TiViewProxy view, @Kroll.argument(optional = true) KrollDict options)
	{
		// The proxy takes care of creating the native view and posting all UI
		// manipulation to the main thread — here we just validate and pass through.
		BorderFXEffectProxy effect = new BorderFXEffectProxy(view, (options != null) ? options : new KrollDict());
		effect.attachToTarget();
		return effect;
	}
}
