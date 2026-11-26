package com.reco1l.verktex.ui

import com.reco1l.verktex.math.toRadians
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.graphics.warnReleased
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.graphics.Buffer
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.graphics.Shader
import com.reco1l.verktex.graphics.Texture
import com.reco1l.verktex.texture.TextureReference
import com.reco1l.verktex.texture.reference
import com.reco1l.verktex.graphics.VertexLayout
import com.reco1l.verktex.graphics.VertexLayout.Attribute
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.container.Container

inline fun Container.particleSystem(maxParticles: Int = 256, builder: ParticleSystem.() -> Unit): ParticleSystem {
    return ParticleSystem(maxParticles).apply(builder).also(::plusAssign)
}

/**
 * Draws particles whose movement and fading are computed by the vertex shader as a function of time, so nothing
 * has to be updated for them after they are emitted.
 *
 * Positions are in the local space of this component. When there are more than [maxParticles] the oldest one
 * gets replaced.
 */
open class ParticleSystem(val maxParticles: Int = 256) : Canvas() {

    /**
     * The texture of the particles, a region given directly or one looked up (see
     * [com.reco1l.verktex.texture.TextureReference.Lookup]), resolved whenever they're drawn.
     */
    var textureReference: TextureReference? = null

    /**
     * The region the particles are drawn with. Setting it is a shorthand for setting [textureReference] to the
     * region itself.
     */
    var texture: Texture.Region?
        get() = textureReference?.resolve()
        set(value) {
            textureReference = value?.reference
        }


    private val particles = FloatArray(maxParticles * FLOATS_PER_PARTICLE)
    private val colors = Array(maxParticles) { Color4.White }

    private var nextParticle = 0
    private var particleCount = 0

    private var time = 0f

    private var instanceBuffer: Buffer? = null


    /**
     * Emits a particle at the given position.
     *
     * @param lifetime How long the particle lasts, in seconds.
     * @param rotation The initial rotation, in degrees.
     * @param angularVelocity The rotation speed, in degrees per second.
     */
    fun emit(
        x: Float,
        y: Float,
        lifetime: Float,
        velocityX: Float = 0f,
        velocityY: Float = 0f,
        accelerationX: Float = 0f,
        accelerationY: Float = 0f,
        startScale: Float = 1f,
        endScale: Float = startScale,
        startAlpha: Float = 1f,
        endAlpha: Float = startAlpha,
        rotation: Float = 0f,
        angularVelocity: Float = 0f,
        color: Color4 = Color4.White
    ) {
        var i = nextParticle * FLOATS_PER_PARTICLE

        particles[i++] = x
        particles[i++] = y
        particles[i++] = velocityX
        particles[i++] = velocityY
        particles[i++] = accelerationX
        particles[i++] = accelerationY
        particles[i++] = time
        particles[i++] = lifetime
        particles[i++] = startScale
        particles[i++] = endScale
        particles[i++] = startAlpha
        particles[i++] = endAlpha
        particles[i++] = rotation.toRadians()
        particles[i] = angularVelocity.toRadians()

        colors[nextParticle] = color

        nextParticle = (nextParticle + 1) % maxParticles

        if (particleCount < maxParticles) {
            particleCount++
        }
    }

    /**
     * Removes all the particles.
     */
    fun clearParticles() {
        nextParticle = 0
        particleCount = 0
    }


    override fun onUpdate(clock: FrameClock) {
        time += clock.elapsedFrameTime
        super.onUpdate(clock)
    }

    override fun onDraw(renderer: Renderer) {
        val region = texture ?: return

        if (region.isReleased) {
            warnReleased(region)
            return
        }

        val buffer = instanceBuffer ?: renderer.createVertexBuffer().also { instanceBuffer = it }
        val instanceCount = writeAliveParticles(buffer)

        if (instanceCount == 0) {
            return
        }

        val transform = renderer.currentTransform

        renderer.drawInstanced(
            shader = BuiltIn.Shaders.Particle,
            texture = region.texture,
            vertexBuffer = quadBuffer(renderer),
            vertexLayout = QUAD_LAYOUT,
            vertexCount = 6,
            instanceBuffer = buffer,
            instanceLayout = PARTICLE_LAYOUT,
            instanceCount = instanceCount,
            uniformSet = obtainUniforms(renderer).let { uniforms ->
                uniforms.particleSize[0] = region.displayWidth
                uniforms.particleSize[1] = region.displayHeight
                uniforms.uvRect[0] = region.u0
                uniforms.uvRect[1] = region.v0
                uniforms.uvRect[2] = region.u1
                uniforms.uvRect[3] = region.v1

                val color = contentColor
                renderer.transformColorInto(color.red, color.green, color.blue, color.alpha, uniforms.colorMultiplier)

                // Column-major, with the 2D transformation in the X and Y axes.
                uniforms.transform.let {
                    it[0] = transform.a; it[1] = transform.b
                    it[4] = transform.c; it[5] = transform.d
                    it[12] = transform.tx; it[13] = transform.ty
                }

                uniforms.set.set("uTime", time)
            }
        )
    }

    // The uniforms of the next draw of this frame: a draw's are read when the frame ends, so each draw of a frame
    // has its own, reused in the next frames instead of allocated on every one.
    private fun obtainUniforms(renderer: Renderer): DrawUniforms {
        if (uniformsFrame != renderer.frameIndex) {
            uniformsFrame = renderer.frameIndex
            uniformsUsed = 0
        }

        return drawUniforms.getOrNull(uniformsUsed++) ?: DrawUniforms().also { drawUniforms += it }
    }

    private val drawUniforms = ArrayList<DrawUniforms>(1)
    private var uniformsFrame = -1L
    private var uniformsUsed = 0

    // Written in place: arrays are read by reference when the draw runs, and never compared with the values set before.
    private class DrawUniforms {
        val particleSize = FloatArray(2)
        val uvRect = FloatArray(4)
        val colorMultiplier = FloatArray(4)
        val transform = FloatArray(16).also { it[10] = 1f; it[15] = 1f }

        val set = Shader.UniformSet()
            .set("uParticleSize", particleSize)
            .set("uUVRect", uvRect)
            .set("uColorMultiplier", colorMultiplier)
            .set("uTransform", transform)
    }

    private fun writeAliveParticles(buffer: Buffer): Int {
        buffer.clear()

        var count = 0

        for (particle in 0 until particleCount) {
            val i = particle * FLOATS_PER_PARTICLE

            val birth = particles[i + BIRTH_OFFSET]
            val lifetime = particles[i + BIRTH_OFFSET + 1]

            if (time - birth >= lifetime) {
                continue
            }

            for (j in 0 until FLOATS_PER_PARTICLE) {
                buffer.putFloat(particles[i + j])
            }

            val color = colors[particle]

            buffer.putPackedBytes(color.packedRGBA)

            count++
        }

        return count
    }

    override fun onDetached() {
        super.onDetached()

        val buffer = instanceBuffer ?: return
        instanceBuffer = null

        Verktex.scheduleOnDraw {
            buffer.dispose()
            Verktex.graphics.unregisterResource(buffer)
        }
    }


    private companion object {

        const val FLOATS_PER_PARTICLE = 14
        const val BIRTH_OFFSET = 6

        val QUAD_LAYOUT = VertexLayout(listOf(Attribute(0, 2)))

        val PARTICLE_LAYOUT = VertexLayout(listOf(
            Attribute(1, 2, isPerInstance = true),
            Attribute(2, 2, isPerInstance = true),
            Attribute(3, 2, isPerInstance = true),
            Attribute(4, 2, isPerInstance = true),
            Attribute(5, 2, isPerInstance = true),
            Attribute(6, 2, isPerInstance = true),
            Attribute(7, 2, isPerInstance = true),
            Attribute(8, 4, VertexLayout.Type.NormalizedByte, isPerInstance = true)
        ))

        private var quad: Buffer? = null

        fun quadBuffer(renderer: Renderer): Buffer {
            return quad ?: renderer.createVertexBuffer().also { buffer ->
                for (corner in floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f)) {
                    buffer.putFloat(corner)
                }
                quad = buffer
            }
        }
    }
}
