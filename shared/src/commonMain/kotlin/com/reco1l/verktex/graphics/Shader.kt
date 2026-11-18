package com.reco1l.verktex.graphics

import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4

/**
 * Represents a shader program that can be used for rendering graphics.
 */
abstract class Shader : GPUResource() {

    /**
     * The source code of the shader program, including the vertex and fragment shader code.
     */
    abstract val source: Source


    /**
     * Applies the given [uniformSet] to the shader program, setting the values of the uniform variables.
     */
    abstract fun applyUniforms(uniformSet: UniformSet)


    /**
     * Represents the source code of a shader program, including the vertex and fragment shader code.
     *
     * The sources are GLSL ES 3.00. A backend that can't compile GLSL at runtime (Vulkan) uses the
     * SPIR-V compiled from them at build time instead, and derives their uniform layout from the GLSL
     * with `GLSL450VulkanTranslator`, which the SPIR-V was translated with.
     *
     * Sources loaded with [com.reco1l.verktex.Provider.createShaderSource] have their `#import`s
     * expanded (see `ShaderImports` and `docs/shaders.md`), sources built from strings are used as they
     * are.
     */
    class Source(
        /**
         * The source code of the vertex shader.
         */
        val vertex: String,

        /**
         * The source code of the fragment shader.
         */
        val fragment: String,

        /**
         * The SPIR-V of [vertex] for Vulkan, null when it wasn't compiled (e.g. built without the NDK).
         */
        val spirvVertex: ByteArray? = null,

        /**
         * The SPIR-V of [fragment] for Vulkan, null when it wasn't compiled.
         */
        val spirvFragment: ByteArray? = null,

        /**
         * The files [vertex] was expanded from, indexed by the source string number of its `#line`
         * directives: the shader, then the files it imports. Empty for sources built from strings.
         */
        val vertexFiles: List<String> = emptyList(),

        /**
         * The files [fragment] was expanded from, see [vertexFiles].
         */
        val fragmentFiles: List<String> = emptyList(),
    ) {

        /**
         * Describes the files of a stage for a compile error, whose lines are prefixed with the source
         * string number, e.g. "0:12" or "1:12" depending on the driver. Empty without imports.
         */
        fun describeFiles(isVertex: Boolean): String {
            val files = if (isVertex) vertexFiles else fragmentFiles
            if (files.size <= 1) return ""
            return "\nSource strings: " + files.withIndex().joinToString { (index, file) -> "$index = $file" }
        }
    }

    /**
     * Represents a set of uniform variables that can be applied to a shader program.
     */
    data class UniformSet(
        /**
         * A map of uniform variable names to their corresponding values. The values can be of any data
         * type that is supported by the using shader program.
         */
        val entries: MutableMap<String, Any> = mutableMapOf()
    ) {

        operator fun set(name: String, value: Int): UniformSet {
            entries[name] = value
            return this
        }

        operator fun set(name: String, value: Float): UniformSet {
            entries[name] = value
            return this
        }

        operator fun set(name: String, value: Vec4): UniformSet {
            entries[name] = value
            return this
        }

        operator fun set(name: String, value: Vec2): UniformSet {
            entries[name] = value
            return this
        }

        operator fun set(name: String, value: Color4): UniformSet {
            entries[name] = value
            return this
        }

        /**
         * Sets a vector (2 to 4 floats) or a column-major 4x4 matrix (16 floats). The array is kept
         * by reference, so its values are read when the draw runs.
         */
        operator fun set(name: String, value: FloatArray): UniformSet {
            entries[name] = value
            return this
        }

    }
}

