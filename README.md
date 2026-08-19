# Ti.BorderFX

> Native iOS module that adds animated, GPU-accelerated gradient borders — "border beam" effects — to any existing Titanium view.

Ti.BorderFX decorates any `Ti.UI.View` (or subclass — buttons, image views, whatever) with an animated conic-gradient border: a short glowing segment that chases around the edge, a full ring of color that spins, or both combined. It's a **decorator**, not a new view type — you attach it to a view you already created, nothing gets wrapped or replaced, and it tracks size and corner-radius changes automatically, including during animated resizes.

![Titanium](https://img.shields.io/badge/Titanium-13.2.0+-red.svg) ![Platform](https://img.shields.io/badge/platform-iOS-lightgrey.svg) ![License](https://img.shields.io/badge/license-MIT-blue.svg) ![Maintained](https://img.shields.io/badge/Maintained-Yes-green.svg)


<p align="center">
  <img src="https://github.com/deckameron/Ti.BorderFX/blob/main/assets/video.gif?raw=true"
       width="300"
       alt="video" />
</p>

---

### Roadmap

- [x] Core `attach` / `start` / `stop` / `destroy` API
- [x] Beam mode (traveling segment)
- [x] Rotate mode (full spinning ring)
- [x] Both modes combined
- [x] Dynamic resize tracking (`Ti.UI.SIZE`, autolayout, `animate()`)
- [x] Background/foreground resilience
- [ ] Android support

## Features

1. **Beam mode** — a short gradient segment chases around the view's border, like a loading "comet"
2. **Rotate mode** — the entire border is a solid ring with colors spinning continuously through it
3. **Both, combined** — a traveling segment whose own colors shift as it moves
4. **Live color updates** — swap the palette on a running effect without restarting it
5. **Tracks the view automatically** — resizes, corner radius, and animated layout changes are picked up on their own, no manual re-sync needed

---

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Features](#features-1)
  - [Feature 1: Beam Mode](#feature-1-beam-mode)
  - [Feature 2: Rotate Mode](#feature-2-rotate-mode)
  - [Feature 3: Both Modes Combined](#feature-3-both-modes-combined)
  - [Feature 4: Live Color Updates](#feature-4-live-color-updates)
  - [Feature 5: Automatic View Tracking](#feature-5-automatic-view-tracking)
- [API Reference](#api-reference)
- [How It Works](#how-it-works)
- [Tips & Gotchas](#tips--gotchas)
- [Requirements](#requirements)
- [Contributing](#contributing)
- [License](#license)

---

## Installation

### 1. Download the module

Grab the latest build from the [releases page](https://github.com/YOUR_USERNAME/Ti.BorderFX/releases).

### 2. Install it in your Titanium project

```bash
{YOUR_PROJECT}/modules/iphone/
```

### 3. Configure `tiapp.xml`

```xml
<modules>
    <module platform="iphone">ti.borderfx</module>
</modules>

<ios>
    <min-ios-ver>12.0</min-ios-ver>
</ios>
```

> **Why iOS 12.0?** The rotating/color-cycling effect is built on `CAGradientLayer`'s conic gradient type (`kCAGradientLayerConic`), introduced in iOS 12.

---

## Quick Start

```javascript
const BorderFX = require('ti.borderfx');

const card = Ti.UI.createView({
    width: 300,
    height: 160,
    borderRadius: 20,
    backgroundColor: '#1c1c1e'
});
win.add(card);

const effect = BorderFX.attach(card, {
    mode: 'both',
    colors: ['#FF2D55', '#5AC8FA', '#FFD60A', '#FF2D55'],
    borderWidth: 3,
    beamLength: 0.25,
    duration: 2400
});
effect.start();
```

---

## Features

### Feature 1: Beam Mode

**A short gradient segment travels continuously around the view's border**, like a comet tail chasing itself.

```javascript
BorderFX.attach(card, {
    mode: 'beam',
    colors: ['#FF2D55', '#FF9500', '#FFD60A'],
    borderWidth: 3,
    beamLength: 0.2,   // segment covers 20% of the perimeter
    duration: 2000     // ms per full lap
}).start();
```

---

### Feature 2: Rotate Mode

**The whole border is a solid, unbroken ring**, with the gradient's colors spinning through it continuously — no gaps, ever.

```javascript
BorderFX.attach(card, {
    mode: 'rotate',
    colors: ['#5AC8FA', '#5856D6', '#AF52DE', '#5AC8FA'],
    borderWidth: 3,
    duration: 3000
}).start();
```

---

### Feature 3: Both Modes Combined

**A traveling segment whose own colors shift as it moves** — the effect most people picture when they say "animated gradient border."

```javascript
BorderFX.attach(card, {
    mode: 'both',
    colors: ['#34C759', '#30D158', '#00C7BE', '#34C759'],
    borderWidth: 3,
    beamLength: 0.22,
    duration: 2400
}).start();
```

---

### Feature 4: Live Color Updates

**Swap the palette on a running effect** without tearing it down and re-attaching.

```javascript
const effect = BorderFX.attach(card, { mode: 'rotate', colors: ['#FF2D55', '#5AC8FA'] });
effect.start();

// later, e.g. in response to a theme change or a state transition:
effect.updateColors(['#34C759', '#FFD60A', '#FF9500']);
```

---

### Feature 5: Automatic View Tracking

**No manual re-sync required.** The effect polls the target view's real geometry every frame and only recomputes when something actually changed, so it stays correct through:

- `Ti.UI.SIZE` views whose final size depends on children added after `attach()`
- `view.animate({ width, height, ... })` resizes — synced via the view's live presentation layer, not just the final value
- Corner radius read directly from whatever you set on `view.borderRadius`, automatically — nothing to pass manually
- The app being backgrounded and returning to foreground

```javascript
const card = Ti.UI.createView({ width: 220, height: 100, borderRadius: 18, backgroundColor: '#1c1c1e' });
win.add(card);
BorderFX.attach(card, { mode: 'both', colors: ['#FFD60A', '#FF9500', '#FF2D55'] }).start();

// resize whenever — the border keeps up on its own
card.animate({ width: 320, height: 160, duration: 350 });
```

---

## API Reference

### Module Methods

#### `attach(view, options)`

Attaches the effect to an existing Titanium view. Returns an effect proxy — nothing renders until you call `.start()` on it.

**Parameters:**
- `view` (`Ti.UI.View`, required) — the view to decorate
- `options` (Object, optional):

| Option | Type | Default | Description |
|---|---|---|---|
| `mode` | String | `'both'` | `'beam'`, `'rotate'`, or `'both'` |
| `colors` | Array\<String\> | rainbow fallback | Titanium color strings (hex or named). Repeat the first color at the end for a seamless loop |
| `borderWidth` | Number | `2` | Stroke width in points |
| `beamLength` | Number | `0.25` | Fraction (0–1) of the perimeter the beam segment covers. Ignored in `'rotate'` mode |
| `duration` | Number | `3000` | Milliseconds per full lap |

**Returns:** an effect proxy (see below)

---

### Effect Proxy

The object returned by `attach()`.

#### `start()`

Begins (or resumes) the animation.

#### `stop()`

Freezes the effect at its current frame — does not reset to the beginning.

#### `updateColors(colors)`

Swaps the color palette on a running or stopped effect.

**Parameters:**
- `colors` (Array\<String\>, required)

#### `destroy()`

Removes the effect and stops all internal timers. Call this if the target view is being removed but the window/controller stays alive — otherwise the effect keeps polling a view that's no longer visible.

---

## How It Works

A quick technical overview, mostly for anyone extending the module:

- **Geometry**: a `CAShapeLayer` strokes a rounded-rect path matching the target view's bounds and corner radius; its stroke is used as a `.mask` on a static container layer.
- **Color**: a `CAGradientLayer` with `type = .conic`, sized to the view's diagonal and centered, sits as a *sublayer inside* the masked container — never masked directly itself, so it can rotate freely without dragging the mask's shape along with it (masking a layer you also rotate moves the mask too, which was an early bug here).
- **Motion**: no `CABasicAnimation`. Both the beam's dash-phase and the ring's rotation angle are computed manually every frame from elapsed wall-clock time via a single `CADisplayLink`, and written directly to the layers. This makes the effect immune to being interrupted by unrelated animations elsewhere in the same view hierarchy (a real problem with animation-block-based approaches when a screen has its own entrance transitions running).
- **Resize tracking**: the same `CADisplayLink` tick re-reads the view's bounds and corner radius every frame, cheaply no-oping when nothing changed. During an active Core Animation transition (like `view.animate()`), it reads `view.layer.presentationLayer` instead of the model layer, so the border tracks the *currently displayed* size, not the animation's already-updated final value.
- **Corner radius**: read from the view's proxy (`[proxy valueForKey:@"borderRadius"]`), not from `view.layer.cornerRadius` — Titanium doesn't always apply `borderRadius` to that property directly, so reading the proxy's own stored value is the reliable path.

---

## Tips & Gotchas

- **Don't loop a color back to your view's exact `backgroundColor`.** A conic gradient interpolates smoothly between stops, so a chunk of the ring near that stop will visually blend into the card behind it and look like a broken/missing segment. Pick a loop-closing color that's distinct from the background.
- **`beamLength` gaps are expected in `'beam'`/`'both'` mode.** A short segment covering, say, 25% of the perimeter is naturally out of view for the other 75% of its lap. Use `'rotate'` if you want the border always fully visible.
- **`borderWidth` and corner radius interact.** A very thick border relative to a small corner radius can visually pinch at the corner — increase the radius or reduce the width if you see that.
- **iOS 12+ only.** Attaching on an older target silently skips the conic gradient type (falls back to whatever `CAGradientLayer` renders by default for an unset type), so the color-cycling won't look right below iOS 12.

---

## Requirements

- Titanium SDK 13.2.0.GA or later
- iOS 15.0+ deployment target

---

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request