package com.reco1l.verktex.ui.form

import com.reco1l.verktex.ui.Component
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.container.LinearContainer
import com.reco1l.verktex.ui.control.Control
import com.reco1l.verktex.ui.control.Select

/**
 * A container that collects the values of every control with a [Control.key] inside of it.
 */
open class FormContainer : LinearContainer() {

    /**
     * Called when [submit] is called and the data has been encoded.
     */
    var onSubmit: ((data: Map<String, Any?>) -> Unit)? = null


    /**
     * Encodes the value of a control, a [List] for a [Select]. Controls encoded as `null` are left
     * out of the data.
     */
    open fun onEncodeControlData(control: Control<*>): Any? {
        if (control is Select<*>) {
            return control.value.toList()
        }
        return control.value
    }

    /**
     * Collects the values of every keyed control, by key, and passes them to [onSubmit].
     */
    fun submit(): Map<String, Any?> {
        val data = LinkedHashMap<String, Any?>()

        fun Component.collect() {
            val control = this as? Control<*>
            val key = control?.key

            if (control != null && key != null) {
                val value = onEncodeControlData(control)
                if (value != null) data[key] = value else data.remove(key)
            } else if (this is Container) {
                forEach { it.collect() }
            }
        }

        forEach { it.collect() }
        onSubmit?.invoke(data)
        return data
    }

}
