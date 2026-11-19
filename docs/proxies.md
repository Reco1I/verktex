# Proxies

A `Proxy` (`ui/Proxy.kt`) draws another component, its target, at the point of the draw order where
the proxy is, instead of where the target is in its parent. A component can then belong to one
parent, which updates, animates and lays it out, and be drawn in another layer. For example, a hit
object owns its approach circle, which has to draw above every object:

```kotlin
val approachCircles = container { width = fill(); height = fill() }

// In the object, once its pieces exist:
approachProxy = approachCircles.proxy(approachCircle)

// When the object expires:
approachCircles -= approachProxy
```

Sources:

- `shared/src/commonMain/kotlin/com/reco1l/verktex/ui/Proxy.kt`, and `Component.proxy`, which its
  parent's draw checks.
- `ProxyTest` (`shared/src/desktopTest/.../ui/`) covers the behavior described here. The
  "Verktex/Proxies" UI test shows it on screen.

## Behavior

- **While a proxy is in a container, its target is skipped by its parent** and drawn by the proxy
  alone. Removing the proxy, or changing its `target`, gives the target back to its parent. A
  target has one proxy at a time: the last one attached.
- **The target stays where its parent puts it.** Components' matrices are absolute, so the proxy's
  own position doesn't matter. The target's transformations, and its ancestors', are brought up to
  date before it's drawn, whatever was drawn before.
- **The alpha and the tint of the target's ancestors apply**, so fading its parent fades it too.
  The ones of the proxy's ancestors apply as well, on top. The target isn't drawn while it has no
  parent (removing it from its parent hides it, as without a proxy) or one of its ancestors is
  invisible.
- **Only drawing moves.** The target is updated, animated (transforms and choreography) and gets
  input through its parent. The proxy takes no space, no input, and is culled by its target's
  bounds.

A layer of proxies has to be laid out over the area its targets are in, e.g. with `fill()`, as
every container culls what's outside the visible area.
