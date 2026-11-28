package com.reco1l.verktex.ui

import com.reco1l.verktex.Verktex

/**
 * The theme of a [Component] ([Component.theme]): the one it overrides for itself and its children, the
 * one it resolves its [themed] values with, and what is applied again when it changes.
 */
class ThemeScope internal constructor(private val owner: Component) {

    /**
     * The theme of the component and its children, or null to use the one of its parent.
     *
     * Changing it resolves the [themed] values of the component and its children again, as a change
     * of [Verktex.theme] does. Values assigned without [themed] stay as they are.
     */
    var override: Theme? = null
        set(value) {
            if (field != value) {
                field = value
                owner.applyTheme()
            }
        }

    /**
     * The theme the [themed] values of the component resolve with: its own [override], the one of the
     * closest parent with one, or [Verktex.theme].
     */
    val resolved: Theme
        get() = override ?: owner.parent?.theme?.resolved ?: Verktex.theme

    // The bindings of the component, see setBinding().
    private var bindings: LinkedHashMap<Any, ThemeBinding>? = null

    // The theme the bindings were last applied with, null for the global theme, which values assigned
    // while constructing resolve with since the component has no parent yet. It's not read here since
    // components are also constructed while Verktex is.
    private var bound: Theme? = null

    /**
     * Whether the bindings were applied with another theme than the one the component resolves with now,
     * e.g. after it was attached under another theme.
     */
    internal val isStale: Boolean
        get() = (bound ?: Verktex.theme) !== resolved


    /**
     * Calls [apply] with the value [resolve] returns for the theme of the component, now and whenever the
     * theme changes, for values without a [themed] form. Binding with the same [key] again replaces the
     * previous binding.
     */
    fun <T> bind(key: Any, resolve: Theme.() -> T, apply: (T) -> Unit) {
        val binding = ThemeBinding { theme -> apply(theme.resolve()) }
        setBinding(key, binding)
        binding.apply(resolved)
    }

    /**
     * Removes the binding added with [bind] with the given [key], keeping its last value.
     */
    fun unbind(key: Any) {
        setBinding(key, null)
    }

    internal fun setBinding(key: Any, binding: ThemeBinding?) {
        if (binding == null) {
            bindings?.remove(key)
            return
        }
        val bindings = bindings ?: LinkedHashMap<Any, ThemeBinding>().also { bindings = it }
        bindings[key] = binding
    }

    /**
     * Applies the bindings again with the current theme.
     */
    internal fun applyBindings() {
        val theme = resolved
        bound = theme
        // Bindings may assign themeable properties, which register or remove bindings.
        bindings?.values?.toList()?.forEach { it.apply(theme) }
    }
}
