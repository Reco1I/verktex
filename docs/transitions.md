# Transitions

The Transitions API lets a component animate as the user interacts with it. A component declares
transforms for each interaction state: idle, hovered, pressed and disabled. The engine runs the
matching block when the state changes. It's built on [transforms](transforms.md)
(`scaleTo`, `fadeTo`, `rotateTo`, `moveTo`, `colorTo`…) and on hover, which is described below.

Sources:

- `ui/InteractionState.kt` defines `InteractionState`, `StateTransitions` and the `transitions { }` builder.
- `ui/UIComponent.kt`, in its Input region, has hover and the interaction state.
- `ui/HoverTracker.kt` and `input/InputEvent.kt` (`HoverEvent`) resolve which components are hovered.
- `HoverTest` (`shared/src/desktopTest/`) covers the behavior described here. The "Verktex/Hover and
  transitions" UI test shows it on screen.

## Quick start

```kotlin
textButton {
    text = "Play"
    transitions {
        idle { scaleTo(1f, 0.15f, Easing.OutQuad) }
        hovered { scaleTo(1.08f, 0.15f, Easing.OutQuad) }
        pressed { scaleTo(0.94f, 0.08f, Easing.OutQuad) }
        disabled { fadeTo(0.3f, 0.2f) }
    }
}
```

Any component can have transitions, not only buttons:

```kotlin
container {
    background = Background.Color(Color4.Pink400)
    transitions {
        idle { rotateTo(0f, 0.3f, Easing.OutBack); colorTo(Color4.White, 0.2f) }
        hovered { rotateTo(8f, 0.3f, Easing.OutBack); colorTo(Color4(0.7f, 0.7f, 1f), 0.2f) }
    }
}
```

Each block runs with the component as its receiver, so `scaleTo(…)` animates the component itself.
Durations are in seconds, as with any transform.

## Interaction states

`UIComponent.interactionState` gives the current state. When a component reports several states at
once, the higher one in this table wins:

| State      | When                                                                                   |
|------------|----------------------------------------------------------------------------------------|
| `Disabled` | `UIButton.isEnabled` or `FormControl.isEnabled` is false.                              |
| `Pressed`  | A `UIClickableContainer` is pressed (`isPressed`), or a `UICheckbox` or `UISlider` is. |
| `Hovered`  | `isHovered`: a mouse is over the component, see [Hover](#hover).                      |
| `Idle`     | None of the above.                                                                     |

Components that don't take presses are only ever idle or hovered.

A custom component can report its own state by overriding the property, and should keep the
priorities of the table:

```kotlin
override val interactionState
    get() = if (isLoading) InteractionState.Disabled else super.interactionState
```

## Declaring transitions

`transitions { }` creates a `StateTransitions` and sets it as the component's `transitions`. It
replaces any transitions set before. Inside it:

- `idle { }`, `hovered { }`, `pressed { }` and `disabled { }` set the block for one state.
- `this[state] = { … }` sets a block when the state is a variable.

A state without a block runs the block of a related state:

- `Pressed` runs `hovered`, or `idle` when there's no `hovered` block. A touch never hovers, but a
  press still shows the hover feedback.
- `Hovered` and `Disabled` run `idle`.

With only an `idle` and a `hovered` block, the component reacts to hover, and keeps the hovered look
while pressed.

Setting `transitions = null` removes them. Transforms already running finish.

### `onStateChange`

`onStateChange: ((InteractionState) -> Unit)?` is called on every state change, after the
transitions ran. Use it for reactions that aren't transforms, such as swapping an icon, playing a
sound or logging. It works with or without `transitions`.

## How they run

The state is checked in `UIComponent.update`, before the transforms advance. It's polled, so every
source of state works without notifying anything: presses, `isEnabled`, hover, and overrides of
`interactionState`. The check is skipped on components with neither `transitions` nor
`onStateChange`.

When the state differs from the last one:

1. The transforms started by the previous state's block that are still running are cancelled. They
   keep the values they reached, and their `after { }` callbacks aren't called.
2. The block of the new state runs. Each transform starts from the property's current value, so an
   interrupted transition continues smoothly from where it was.
3. `onStateChange` is called.

The first update after `transitions` or `onStateChange` is set runs the block of the current state,
even if that state is `Idle`. That way a button that starts disabled looks disabled right away.
Give it durations of 0 to apply the look without animating.

### What is cancelled, and what isn't

Only the transforms the block added to the component itself are tracked, through
`TransformTracker.recordAdded`. Therefore:

- **Other transforms on the component keep running**, e.g. a `fadeIn` when the component is shown.
  They can still conflict with a transition on the same property: transforms apply in the order they
  were added, so the most recent one sets the property each frame.
- **Transforms on other components aren't tracked.** A block that animates a child (`icon.scaleTo(…)`)
  must set that property in every state's block, or the child keeps its last value.
- `transformSequence { }` inside a block is tracked, since it adds to the component's transforms.
  Steps that haven't started yet are cancelled too.
- A block that calls `clearTransforms()` or `clearAnimations()` clears everything on the component,
  including other transforms, and nothing is tracked for that state.

### Built-in feedback

`UIButton` eases its own scale while pressed and fades to half alpha while disabled. **With
`transitions` set, it stops doing both**, so the declared transitions replace them rather than fight
them. A button with `transitions` that should still fade when disabled needs a `disabled` block.

Things that set properties every frame override transitions on the same property. This applies to
`onUpdate` easing like the button's, and to a [choreography](choreography.md), which applies after
transforms. Don't
combine them on the same property.

## Hover

Hover comes from a pointer that isn't pressing, i.e. a mouse cursor. Touches never hover.

### Which components are hovered

Hover follows the browser's `:hover`. The topmost **hoverable** component under the cursor is
hovered, and so is every ancestor that contains it. Siblings underneath aren't hovered.

1. The engine dispatches a `HoverEvent` once per update, at the last cursor position. It goes
   through the same roots as other input, in the same order: the diagnostics, then the overlays,
   then the scene.
2. As with a press, containers pass it only to the children under the cursor, topmost first.
3. The first hoverable component claims it, after its children had their chance (`claimHover`).
4. A component that consumes it without claiming it blocks the hover below and hovers nothing. Such
   components:
   - a shown `UIModal`, within its bounds;
   - the menu of an expanded `UIDropdown`;
   - a `UICaptureContainer` that `isBlockingInput`.

`isHoverable` is true for:

- clickables (`UIClickableContainer` and so `UIButton`) and controls (`UIControl`);
- any component with `onHoverEnter`, `onHoverExit`, `onStateChange` or `transitions`.

Other components, such as a decorative box drawn over a button, don't take the hover. The button
under them is still hovered.

Disabled buttons and form controls are still hovered, so `isHovered` is true, but their
`interactionState` is `Disabled`.

Hover is resolved every frame, not only when the cursor moves. Content that scrolls or animates
under a still cursor updates the hover. A component removed from the tree stops being hovered on the
next frame. Hit testing uses the layout of the previous frame, so a change shows up one frame later.

### Hover API

On every `UIComponent`:

- `isHovered`: whether it's hovered. Set by the engine only.
- `onHoverEnter` and `onHoverExit`: callbacks, called once per enter and per exit.
- `onHoverChanged()`: a protected open function for subclasses, called after the callbacks.
- `isHoverable`: open, to make a custom component hoverable (or not).

On enter, components are hovered from the outermost to the innermost. On exit, from the innermost to
the outermost.

A component that overrides `onInputEvent` and doesn't call `super` for events it doesn't handle
should call `claimHover(event)` once its children didn't consume the event. Otherwise it can't be
hovered. `UIContainer` and the `UIComponent` base already do this.

### Hosts

Hosts give the cursor position in window pixels with `Input.dispatchHover(x, y)`, and call
`Input.dispatchHoverExit()` when there's no cursor anymore:

- **Desktop** (`DesktopGameHost`): every cursor move hovers, with or without a button held. Leaving
  the window (`glfwSetCursorEnterCallback`) exits.
- **Android** (`AndroidGameHost`): `ACTION_HOVER_ENTER` and `ACTION_HOVER_MOVE` hover, and
  `ACTION_HOVER_EXIT` exits. These come through `onGenericMotionEvent`, from a mouse or a stylus.
  Android sends a hover exit when a mouse button goes down, so touch events from
  `InputDevice.SOURCE_MOUSE` keep hovering while it's held.

A single pointer hovers: the engine keeps one position.

## Testing

`HoverTracker` is internal and can be used without a started engine. `tracker.resolve(x, y, listOf(root))`
hovers what is under the point in `root`, and `tracker.clear()` hovers nothing. Update, measure and lay
out the tree with a fixed clock between steps, as `HoverTest` and `MouseInputTest` do. Components
with sizes in density independent pixels (such as `UIButton`'s themed padding) need the platform,
which unit tests don't attach. Give them pixel sizes, e.g. `padding = Padding.Zero`.
