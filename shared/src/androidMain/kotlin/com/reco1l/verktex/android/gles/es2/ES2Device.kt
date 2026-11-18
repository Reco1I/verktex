package com.reco1l.verktex.android.gles.es2

import com.reco1l.verktex.android.gles.ESDevice
import com.reco1l.verktex.graphics.Shader

/**
 * The [ESDevice] of OpenGL ES 2.0.
 */
class ES2Device : ESDevice() {

    override fun createShader(source: Shader.Source): ES2Shader {
        return ES2Shader(source)
    }
}
