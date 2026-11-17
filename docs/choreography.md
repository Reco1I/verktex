# Choreography

A choreography is a component's timeline of property changes, at absolute times on a clock. Unlike
[transforms](transforms.md), its steps are kept after they end, and the component's state is
computed from the time alone. The result doesn't depend on the frames played before, and seeking or
going back in time just works. It's meant for things with a defined lifetime on a clock that can
jump, such as the hit objects of gameplay, its effects and storyboards.

For UI animations that only move forward, use [transforms](transforms.md).

Sources:

- `shared/src/commonMain/kotlin/com/reco1l/verktex/ui/Choreography.kt`.
- `ChoreographyTest` (`shared/src/desktopTest/.../ui/`) covers the behavior described here.

## Quick start

```kotlin
// Times are in seconds on the clock of the component, e.g. the gameplay clock.
circle.clock = gameplayClock

circle.choreograph {
    at(hitTime - preempt)
    fadeIn(fadeInDuration)
    at(hitTime)
    fadeOut(0.2f)
    scaleTo(1.5f, 0.2f, Easing.OutQuad)
}
```

From the game, a combo burst slides in, waits a second and slides back out:

```kotlin
sprite.choreograph {
    at(sprite.currentClockTime)
    fadeIn(0.5f)
    moveToX(toX, 0.5f, Easing.OutSine)
    then().delay(1f)
    fadeOut(0.5f)
    moveToX(startX, 0.5f, Easing.OutSine)
}
```

## Building a choreography

Each component has at most one choreography (`UIComponent.choreography`). These functions create
it on first use and add steps to it:

| Function                     | Starts the steps at                                         |
|------------------------------|-------------------------------------------------------------|
| `choreograph { … }`          | Wherever the cursor is. Call `at(time)` first.              |
| `choreographAt(time) { … }`  | `time`.                                                     |
| `choreographNow { … }`       | The current time of the component's clock.                  |
| `choreography()`             | Returns the choreography, to add steps by chaining calls.   |

`choreographAt` and `choreographNow` take a `ChoreographyBlock`, which a Java lambda converts to.
The block gets the choreography as a parameter:

```kotlin
container.choreographAt(startTime) { sequence -> sequence
    .fadeIn(SLIDE_DURATION)
    .moveToY(0f, SLIDE_DURATION, Easing.OutQuint) }
```

`choreograph { }` doesn't move the cursor: it keeps the position the previous build left it at,
which is 0 for a new choreography. Start the block with `at(time)`, unless continuing from the last
build is intended.

### Steps

Durations are in seconds. A duration of 0, the default, changes the value at once when the step's
time is reached. Each step takes an optional `Easing`, `Easing.None` by default, see
[transforms](transforms.md#functions).

| Step                              | Changes                                                          |
|-----------------------------------|------------------------------------------------------------------|
| `scaleTo(value)`                  | `scaleX` and `scaleY`.                                           |
| `addScaleY(value)`                | `scaleY` only, despite its name it sets the value.               |
| `fadeTo(value)`                   | `alpha`.                                                         |
| `fadeIn()` / `fadeOut()`          | `alpha` to 1 / 0.                                                |
| `fadeInFromZero()`                | `alpha` from 0 to 1, whatever the previous value.                |
| `rotateTo(degrees)`               | `rotationZ`.                                                     |
| `moveTo(x, y)`                    | `translationX` and `translationY`, in pixels.                    |
| `moveToX(value)` / `moveToY(value)` | One of the two, in pixels.                                     |
| `colorTo(color)`                  | The RGB channels of `color`. Alpha is left to the fades.         |
| `colorTo(red, green, blue)`       | The same, from channels.                                         |

**Each step starts from the value where the previous step on the same property ends.** The first
step on a property starts from the component's value at the moment the step is added. Values are
chained in the order steps are added, not by their times, so add the steps of each property in
chronological order.

### The cursor

Steps are placed at a cursor, and steps added one after another start at the same time:

- `at(time)` moves the cursor to an absolute time and starts a new chain.
- `then()` moves it to the end of the steps added since the last `at`.
- `then(delay)` moves it `delay` seconds after that end.
- `delay(duration)` moves it later by `duration`, from where it is.
- `after { component -> … }` calls back once the time reaches the end of the steps since the last
  `at`.

## How it runs

The choreography is applied in `UIComponent.update`, after the transforms, with the time of the
component's clock (`currentClockTime`). That's its own `clock`, an ancestor's, or else the engine's
time.

On each update, for each property:

- **The latest step that has started sets the value.** It interpolates from its start to its end
  value. Once it ends it holds its end value until a later step starts.
- **Before its first step starts, a property is held at that step's start value.** Adding a
  choreography therefore takes over its properties right away. For example, `fadeInFromZero()` at a
  future time makes the component transparent until then.
- **Properties without steps are left alone**, so other code or transforms can animate them.

Because only the time matters, seeking to any time gives the same state as playing up to it, and
going back restores the earlier state.

### Callbacks and lifetime

- `after { }` callbacks are called once, the first time the time passes them. They aren't called
  again when the time goes back and passes them again.
- `endTime` is when the last step or callback ends.
- `lifetimeEnd` sets when the choreography's lifetime ends, by default `endTime`.
  `onLifetimeEnd` is called once when the time reaches it, e.g. to remove or pool the component.
  `isFinished` tells whether that happened. A choreography with no steps and no `lifetimeEnd` never
  ends.
- `isAnimating` on the component is true while the time is before `endTime`.

### Finishing and reusing

- `finish()` jumps to the end: it applies the final values and calls the callbacks that are left,
  including `onLifetimeEnd`.
- `clear()` removes every step and callback and resets the cursor, the lifetime and the chained
  values. It keeps the step objects for reuse, so a pooled component can get a new choreography
  without allocating: call `choreography?.clear()` and set the properties' starting values before
  building again.
- `clearAnimations()` on the component clears both its transforms and its choreography.

## Interaction with other animations

- Transforms apply before the choreography, so a property with choreography steps ignores transforms.
- `onUpdate` runs after both, so a component setting a property there overrides its choreography.
- [Transitions](transitions.md) are transforms, and have the same conflict.

Give each property one owner. See the comparison table in [transforms](transforms.md#choosing-between-transforms-choreography-and-transitions).

## Testing

Give the component a `ManualClock`, set its time, and update the component with a `FramedClock`
over it, as `ChoreographyTest` does:

```kotlin
val time = ManualClock()
val frames = FramedClock(time, false)
component.clock = time

fun seek(to: Float) {
    time.time = to
    frames.processFrame()
    component.update(frames)
}
```
