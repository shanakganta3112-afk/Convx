# liquid-glass

The Liquid Glass rendering engine extracted from Convx, packaged so it can be lifted
into any Android project. Compose only — no Material 3, no theme, no DataStore, no
dependency on the app it came from.

Builds on its own: `:liquid-glass:assembleDebug` runs in CI on every commit.

## What's in it

```
backdrop/
  Backdrop.kt                 the core interface: a captured, blurred backdrop you draw onto
  DrawBackdropModifier.kt     Modifier.drawBackdrop { } / Modifier.drawPlainBackdrop { }
  BackdropEffectScope.kt      the DSL: blur, colourControls, lens, highlights, shadows
  effects/                    Blur, Lens, ColorFilter, RenderEffect
  highlight/                  Highlight, HighlightStyle, HighlightModifier (specular rim)
  shadow/                     Shadow, InnerShadow
  backdrops/                  LayerBackdrop, CombinedBackdrop, CanvasBackdrop,
                              BackdropFreeze, NavTransitionFreeze, static regions
  RuntimeShader(.Cache).kt    API 31+ shader plumbing with graceful fallback
  Platform.kt                 isRenderEffectSupported / isRuntimeShaderSupported
  GlassPool.kt                reuse of expensive shader-backed surfaces
  catalog/components/         ready-made liquid controls: LiquidBottomTab(s), LiquidButton,
                              LiquidSlider, LiquidToggle
  catalog/utils/              DampedDragAnimation, InteractiveHighlight, DragGestureInspector
```

## Adding it to another project

1. Copy the `liquid-glass/` folder next to your modules.
2. In `settings.gradle.kts`: `include(":liquid-glass")`
3. In your app module: `implementation(project(":liquid-glass"))`
4. Requirements: `compileSdk 37`, `minSdk 26`, Kotlin 2.x with the Compose compiler plugin.
   The module enables `buildFeatures { compose = true }` itself.

## The host contract — the only two things you must provide

```kotlin
// 1. Light or dark content on glass.
CompositionLocalProvider(LocalGlassDarkAppearance provides isSystemInDarkTheme()) {
    // your UI
}

// 2. Only if you use navigation transitions that can close a screen.
GlassHost.lastNavWasPop = { navController.currentBackStackEntry?.let { /* ... */ } ?: false }
```

`LocalGlassDarkAppearance` replaces the three-way check the original app used (sampled
chrome, ambient background, surface luminance). `isSystemInDarkTheme()` is a fine
starting point; refine it once you have a backdrop to sample.

## Minimal usage

```kotlin
val backdrop = rememberLayerBackdrop()

Box(Modifier.fillMaxSize()) {
    // 1. Draw your content and let it be captured.
    Content(Modifier.layerBackdrop(backdrop))

    // 2. Draw glass over it.
    Box(
        Modifier
            .size(220.dp, 64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = {
                    blur(24.dp)
                    colorControls(saturation = 1.4f)
                    lens(refractionHeight = 12.dp, refractionAmount = 24.dp)
                },
                highlight = { Highlight.Default.copy(alpha = 0.35f) },
            )
    )
}
```

## Honest scope

This module contains the **engine and the liquid controls** — the part that does the
actual refraction, blur, specular rim and shader work, verified to compile standalone.

Not yet extracted: `GlassEffect.kt` (the `GlassStyle` enum, `GlassEffectConfig` and the
`glassSurface` modifier that wrap this engine), the configuration model, and the
1,223-line advanced customisation settings screen. Those still live in the app and carry
five theme couplings each (`AppTheme.colorScheme`, `isHueless`, `LocalAmbientBackgroundColor`,
`LocalAmbientDarkChrome`, `outerDarkRim`) which need replacing with the same host contract
before they can move.

## Licence

GPL-3.0, inherited from Convx. The engine itself is derived from Haze (Apache-2.0); keep
both notices if you redistribute.
