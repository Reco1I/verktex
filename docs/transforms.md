# Transforms

Transforms are timed changes to a component's properties: scale, alpha, rotation, translation and
color. They're the way to animate UI. A transform runs once from the property's current value to a
target and is then removed.

For animations whose state must depend only on a time that can go back, such as gameplay objects
during seeking, use a [choreography](choreography.md). For animations that follow how the user
interacts with a component (hover, press, disabled), use [transitions](transitions.md), which are
built on transforms.

Source: `shared/src/commonMain/kotlin/com/reco1l/verktex/ui/Transforms.kt`.

## Quick start

```kotlin
// Grow and fade in over 0.3 seconds, at the same time.
box.scaleTo(1.2f, 0.3f, Easing.OutBack)
box.fadeIn(0.3f)

// One after another.
box.transformSequence {
    scaleTo(1.5f, 0.3f, Easing.OutBack)
    then()
    scaleTo(1f, 0.3f, Easing.OutQuad)
}

// Do something when it ends.
toast.fadeOut(0.2f).after { it.removeSelf() }
```

## Functions

Every function is an extension of `Component`. Durations are in seconds. A duration of 0, the
default, sets the value on the next update.

| Function                      | Animates                                                            |
|-------------------------------|---------------------------------------------------------------------|
| `scaleTo(value)`              | `scaleX` and `scaleY`, together.                                    |
| `fadeTo(value)`               | `alpha`.                                                            |
| `fadeIn()` / `fadeOut()`      | `alpha` to 1 / 0.                                                   |
| `moveTo(x, y)`                | `translationX` and `translationY`, in pixels.                       |
| `moveToX(value)`              | `translationX`, in pixels.                                          |
| `rotateTo(degrees)`           | `rotationZ`.                                                        |
| `colorTo(color)`              | The RGB channels of `color`. Its alpha is left to `fadeTo`.         |
| `delay(duration)`             | Nothing. Use it to run code later, with `after`.                    |

All of them take an optional `easing` as their third (or fourth) argument, `Easing.None` (linear) by
default. `Easing` has the usual families, each with `In`, `Out` and `InOut` variants: `Quad`,
`Cubic`, `Quart`, `Quint`, `Sine`, `Expo`, `Circ`, `Elastic`, `Back` and `Bounce`. There are also
`OutElasticHalf` and `OutElasticQuarter`. `Easing` is a functional interface, so
`Easing { x -> x * x }` defines a custom easing.

Each function returns the `Transform`, which can be chained with:

- `after { component -> … }` to call back when it ends;
- `eased(easing)` to change its easing;
- `then()` to start a sequence that begins when it ends, e.g.
  `box.fadeIn(0.2f).then().scaleTo(1.1f, 0.2f)`.

Translation is given in pixels and is set as `translationX = value.px`. A translation the component
had in another unit, such as a percentage of its size, is resolved to pixels when the transform
starts.

## Sequences

`transformSequence(delay = 0f) { … }` groups transforms on one component. Inside the block:

- **Transforms added one after another run at the same time**, starting at the sequence's cursor.
- `then()` moves the cursor to the end of the transforms that end last, so the next ones wait for
  all of them.
- `delay(duration)` moves the cursor later by `duration`, from where it is.
- `after { … }` calls back when the transform that ends last finishes. On an empty sequence, it calls
  back on the next update.

```kotlin
target.transformSequence(delay = 0.5f) {
    moveToX(-100f, 0.3f, Easing.OutQuad)
    then()
    moveToX(100f, 0.6f, Easing.InOutQuad)
    then()
    moveToX(0f, 0.3f, Easing.InQuad)
    after { log("Back in place") }
}
```

The `delay` argument of `transformSequence` starts the whole sequence later. A negative delay starts
it in the past, as if it had already been running for that long.

## How they run

Each component keeps its transforms in a `TransformTracker`, created with the first one. They
advance in `Component.update`, before `onUpdate`:

- **A transform takes its start value when it starts**, not when it's created. A delayed `fadeTo`
  starts from the alpha the component has once its delay ends.
- **Transforms apply in the order they were added.** Two transforms on the same property at the same
  time both set it, and the one added last wins on each frame. Starting a `scaleTo` while another is
  running takes over smoothly from the current value, but the older one keeps running underneath
  until it ends. Call `clearTransforms()` first to replace a running animation outright.
- **A finished transform is removed**, then its `after` callback is called. Callbacks can add or
  clear transforms.
- `clearTransforms()` stops all of them where they are, without calling their callbacks.
  `clearAnimations()` also clears the choreography.
- `isAnimating` is true while a transform (or a step of the choreography) is running.

### Time

By default transforms follow the engine's frame time: each update adds the frame's elapsed time.
That count restarts at 0 whenever the component has no transforms left.

When the component or one of its ancestors has a `clock` (`Component.clock`, see `activeClock`),
transforms use that clock's time instead. They then pause when it pauses and follow its rate. They
don't rewind, though: going back before a transform's start only holds it, and doesn't restore the
earlier value. For animations that must follow a clock both ways, use a
[choreography](choreography.md).

`ignoreUpdate = true` freezes a component and its children, including their transforms.

### Things that override transforms

Transforms set properties during the update, so anything that sets the same property later in the
frame wins:

- the component's `onUpdate`, such as `Button` easing its scale while pressed;
- its `choreography`, which applies after transforms;
- code that sets the property directly every frame.

Give each property one owner.

## Choosing between transforms, choreography and transitions

|                       | Transforms                       | Choreography                            | Transitions                  |
|-----------------------|----------------------------------|-----------------------------------------|------------------------------|
| For                   | UI animations                    | Timelines tied to a clock, e.g. gameplay | Reactions to hover and press |
| Start value           | The current value when it starts | The previous step's end value           | As transforms                |
| Going back in time    | Not supported                    | Supported, the state depends on the time | Not supported               |
| When finished         | Removed                          | Kept until cleared                      | Removed                      |
| Times                 | Relative to now                  | Absolute, on the component's clock      | Relative to the state change |
