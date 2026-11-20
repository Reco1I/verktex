package com.reco1l.verktex

import com.reco1l.verktex.fonts.FontFamily
import com.reco1l.verktex.graphics.Shader

/**
 * Contains built-in resources that are automatically loaded by the engine.
 * These resources include shaders and fonts that can be used in the application.
 */
object BuiltIn {

    /**
     * Built-in shaders.
     */
    object Shaders {

        /**
         * A shader for rendering solid-colored quads.
         */
        lateinit var SolidQuad: Shader

        /**
         * A shader drawing only the interpolated vertex color. It's much cheaper per pixel than
         * [UI], which matters for large areas such as fullscreen gradients.
         */
        lateinit var Color: Shader

        /**
         * A shader for rounded boxes and text in the same batches, see [com.reco1l.verktex.graphics.VertexLayout.UI].
         */
        lateinit var UI: Shader

        /**
         * A shader for rendering textured quads.
         */
        lateinit var TextureQuad: Shader

        /**
         * A shader for rendering external (video) textures, sampled through their transform.
         */
        lateinit var Video: Shader

        /**
         * A shader blurring a texture along one axis, see [com.reco1l.verktex.ui.container.CaptureContainer.blurRadius].
         */
        lateinit var Blur: Shader

        /**
         * A shader for rendering particles, see [com.reco1l.verktex.ui.ParticleSystem].
         */
        lateinit var Particle: Shader

        /**
         * A shader for the bands of a path, see [com.reco1l.verktex.ui.shape.PathShape].
         */
        lateinit var Path: Shader


        /**
         * The sources of a built-in shader in the assets and the property it's stored in once created.
         */
        class Program(
            val vertexPath: String,
            val fragmentPath: String,
            internal val store: (Shader) -> Unit,
        )

        /**
         * Every built-in shader, created by the renderer with `Renderer.createBuiltInShaders`.
         */
        val Programs = listOf(
            Program("shaders/main.vert", "shaders/quad.frag") { SolidQuad = it },
            Program("shaders/main.vert", "shaders/color.frag") { Color = it },
            Program("shaders/ui.vert", "shaders/ui.frag") { UI = it },
            Program("shaders/main.vert", "shaders/texture.frag") { TextureQuad = it },
            Program("shaders/main.vert", "shaders/video.frag") { Video = it },
            Program("shaders/main.vert", "shaders/blur.frag") { Blur = it },
            Program("shaders/particle.vert", "shaders/particle.frag") { Particle = it },
            Program("shaders/path.vert", "shaders/path.frag") { Path = it },
        )
    }

    /**
     * Built-in font families.
     */
    object Fonts {

        /**
         * The default font family used by the engine, [Nunito](https://fonts.google.com/specimen/Nunito):
         * a variable font with a `wght` axis, so every [com.reco1l.verktex.fonts.Font.Weight] is drawn
         * where the platform applies it.
         */
        lateinit var Default: FontFamily

        /**
         * [Fredoka](https://fonts.google.com/specimen/Fredoka), a rounded display font: variable, with
         * `wght` and `wdth` axes, of which only the weight is applied.
         */
        lateinit var Fredoka: FontFamily

        /**
         * [Anta](https://fonts.google.com/specimen/Anta), a static font of a single weight.
         */
        lateinit var Anta: FontFamily

        /**
         * The platform's own UI font, e.g. Roboto on Android. It looks different on every platform,
         * and is [Default] when the platform couldn't provide one.
         */
        lateinit var System: FontFamily

        /**
         * [Font Awesome](https://fontawesome.com/) icon font family for regular icons.
         */
        lateinit var FontAwesomeIconRegular: FontFamily

        /**
         * [Font Awesome](https://fontawesome.com/) icon font family for solid icons.
         */
        lateinit var FontAwesomeIconSolid: FontFamily

        /**
         * [Font Awesome](https://fontawesome.com/) icon font family for brand icons.
         */
        lateinit var FontAwesomeIconBrands: FontFamily
    }
}