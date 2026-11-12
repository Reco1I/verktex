package com.reco1l.verktex.graphics

import com.reco1l.verktex.ui.text.charCountOf
import com.reco1l.verktex.ui.text.codePointAt
import com.reco1l.verktex.time.monotonicNanos
import kotlinx.atomicfu.locks.synchronized
import kotlinx.atomicfu.locks.SynchronizedObject
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Mat2
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.Logger
import com.reco1l.verktex.PerformanceStats
import com.reco1l.verktex.Platform
import com.reco1l.verktex.fonts.Font
import com.reco1l.verktex.math.toRadians
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The main renderer class that handles drawing commands and manages the rendering state.
 */
abstract class GraphicsRenderer {

    /**
     * The device associated with the renderer, which provides access to graphics hardware capabilities
     * and resource management.
     */
    abstract val device: GraphicsDevice

    /**
     * The context in which the renderer operates. This could represent the rendering context or environment.
     */
    abstract val context: GraphicsContext

    /**
     * The window associated with the renderer, which represents the display area for rendering.
     */
    abstract val window: GraphicsWindow


    /**
     * The backend the renderer draws with, null for one that isn't a [GraphicsBackend] (e.g. in tests).
     * It's the one actually used: when Vulkan can't be used, the OpenGL ES one it fell back to.
     */
    open val backend: GraphicsBackend?
        get() = null

    /**
     * The version of the graphics API being used by the renderer.
     */
    abstract var version: String


    private val commandBatch = ArrayDeque<DrawCommand>()

    // Vertex buffers are reused across frames, creating and deleting them per command is costly. Each
    // is used by one command per frame, so it is only rewritten once the frame was executed, see the
    // lifetime in GraphicsBuffer.
    private val bufferPool = ArrayList<GraphicsBuffer>()
    private var buffersUsed = 0

    /**
     * The draw commands recorded for the current frame so far.
     */
    val commandCount
        get() = commandBatch.size
    private val gpuResources = ArrayDeque<GraphicsResource>()
    private val gpuResourcesLock = SynchronizedObject()


    //region Stacks
    private val matrixStack = ArrayDeque<Mat2>()
    private val colorStack = ArrayDeque<Color4>()
    private val tintStack = ArrayDeque<Color4>()
    private val scissorStack = ArrayDeque<Vec4>()
    private val blendModeStack = ArrayDeque<BlendMode>()
    private val depthStateStack = ArrayDeque<DepthState>()
    private val shaderStack = ArrayDeque<GraphicsShader>()
    //endregion

    //region State
    private var activeShader: GraphicsShader? = null
    private var activeBuffer: GraphicsBuffer? = null
    private var activeTexture: GraphicsTexture? = null
    private var activeScissor: Vec4? = null
    private var activeUniformSet: GraphicsShader.UniformSet = EMPTY_UNIFORMS

    // The uniforms of the last drawn external texture, reused so consecutive draws of it merge.
    private var externalUniformsTexture: GraphicsTexture? = null
    private var externalUniforms: GraphicsShader.UniformSet = EMPTY_UNIFORMS

    // Transformed positions are written here instead of allocating a Vec2 per vertex. It is only
    // used within one draw helper call at a time, the renderer is driven by a single thread.
    private val points = FloatArray(8)

    // Counts the commands merging across the traversal order would leave, see isSimulatingBatches.
    private var batchSimulator: BatchSimulator? = null

    /**
     * Whether every draw is also given to a [BatchSimulator], which counts the commands the frame
     * would need if draws could join earlier commands they don't overlap ([simulatedCommandCount]).
     * It doesn't change what is drawn, it only costs some time while recording.
     */
    var isSimulatingBatches: Boolean
        get() = batchSimulator != null
        set(value) {
            if (value != isSimulatingBatches) {
                batchSimulator = if (value) BatchSimulator() else null
            }
        }

    /**
     * The commands recorded so far in this frame would be this many with merging across the
     * traversal order, or -1 when [isSimulatingBatches] is off.
     */
    val simulatedCommandCount: Int
        get() = batchSimulator?.commandCount ?: -1


    /**
     * Sets the current rendering state, including shader, texture, scissor rectangle, blend mode,
     * depth state, and uniform set.
     *
     * If the last command in the batch can be reused with the new state, it will not create a new
     * command.
     */
    fun setState(
        shader: GraphicsShader? = activeShader,
        texture: GraphicsTexture? = activeTexture,
        scissor: Vec4? = scissorStack.lastOrNull(),
        blendMode: BlendMode = blendModeStack.lastOrNull() ?: BlendMode.Alpha,
        depthState: DepthState = depthStateStack.lastOrNull() ?: DepthState.Default,
        uniformSet: GraphicsShader.UniformSet = activeUniformSet,
        vertexLayout: VertexLayout = VertexLayout.Default,
        isQuads: Boolean = false
    ) {
        // A pushed shader replaces the one the draw call asked for, see pushShader.
        val usedShader = shaderStack.lastOrNull() ?: shader

        val lastCommand = commandBatch.lastOrNull()
        if (lastCommand != null &&
            lastCommand.canReuse(
                usedShader,
                texture,
                scissor,
                blendMode,
                depthState,
                uniformSet,
                vertexLayout,
                isQuads,
            )
        ) return

        val vertexBuffer = bufferPool.getOrNull(buffersUsed) ?: createVertexBuffer().also(bufferPool::add)
        buffersUsed++
        vertexBuffer.clear()
        vertexBuffer.isRecorded = true

        activeTexture = texture
        activeShader = usedShader
        activeBuffer = vertexBuffer
        activeScissor = scissor
        activeUniformSet = uniformSet

        val command = DrawCommand(
            buffer = vertexBuffer,
            shader = usedShader,
            texture = texture,
            scissor = scissor,
            blendMode = blendMode,
            depthState = depthState,
            uniformSet = uniformSet,
            vertexLayout = vertexLayout,
            isQuads = isQuads
        )

        commandBatch.addLast(command)
    }

    /**
     * Records an instanced draw, in which [vertexBuffer] is drawn [instanceCount] times advancing through
     * [instanceBuffer]. Both buffers are owned by the caller and must keep their data until the frame is
     * executed, i.e. they can't be rewritten for another draw of the same frame. See the lifetime in
     * [GraphicsBuffer].
     */
    fun drawInstanced(
        shader: GraphicsShader,
        texture: GraphicsTexture?,
        vertexBuffer: GraphicsBuffer,
        vertexLayout: VertexLayout,
        vertexCount: Int,
        instanceBuffer: GraphicsBuffer,
        instanceLayout: VertexLayout,
        instanceCount: Int,
        uniformSet: GraphicsShader.UniformSet
    ) {
        require(vertexBuffer.position == vertexCount * vertexLayout.stride) {
            "The vertex buffer doesn't hold $vertexCount vertices of the given layout."
        }

        vertexBuffer.isRecorded = true
        instanceBuffer.isRecorded = true

        commandBatch.addLast(DrawCommand(
            buffer = vertexBuffer,
            shader = shaderStack.lastOrNull() ?: shader,
            texture = texture,
            scissor = scissorStack.lastOrNull(),
            blendMode = blendModeStack.lastOrNull() ?: BlendMode.Alpha,
            depthState = depthStateStack.lastOrNull() ?: DepthState.Default,
            uniformSet = uniformSet,
            vertexLayout = vertexLayout,
            instanceBuffer = instanceBuffer,
            instanceLayout = instanceLayout,
            instanceCount = instanceCount
        ))
        simulateUnbounded(texture)
    }

    /**
     * Records a draw of [vertexCount] vertices of [buffer] from [firstVertex], as triangles. The
     * buffer is owned by the caller, and like for [drawInstanced] it must keep its data until the
     * frame is executed. A buffer that isn't written again between frames isn't uploaded again by
     * the backends that keep vertex data on the GPU.
     *
     * The vertices aren't transformed, the shader applies [currentTransform] if it needs it.
     */
    fun drawVertices(
        shader: GraphicsShader,
        buffer: GraphicsBuffer,
        vertexLayout: VertexLayout,
        firstVertex: Int,
        vertexCount: Int,
        uniformSet: GraphicsShader.UniformSet,
        texture: GraphicsTexture? = null
    ) {
        require(firstVertex >= 0 && vertexCount >= 0) { "Invalid vertex range $firstVertex, $vertexCount." }
        require((firstVertex + vertexCount) * vertexLayout.stride <= buffer.position) {
            "The buffer doesn't hold ${firstVertex + vertexCount} vertices of the given layout."
        }

        if (vertexCount == 0) {
            return
        }

        buffer.isRecorded = true

        commandBatch.addLast(DrawCommand(
            buffer = buffer,
            shader = shaderStack.lastOrNull() ?: shader,
            texture = texture,
            scissor = scissorStack.lastOrNull(),
            blendMode = blendModeStack.lastOrNull() ?: BlendMode.Alpha,
            depthState = depthStateStack.lastOrNull() ?: DepthState.Default,
            uniformSet = uniformSet,
            vertexLayout = vertexLayout,
            firstVertex = firstVertex,
            vertexCount = vertexCount
        ))
        simulateUnbounded(texture)
    }

    /**
     * The transformation currently applied to what gets drawn.
     */
    val currentTransform: Mat2
        get() = matrixStack.lastOrNull() ?: Mat2.Identity

    /**
     * Clears the depth buffer at this point of the frame. Draws recorded before stay before the clear
     * and the next draw starts a new command.
     */
    fun clearDepth() {
        commandBatch.addLast(DrawCommand(clearDepth = true))
        batchSimulator?.barrier()

        // A geometry draw without a new setState() would otherwise write into a buffer already queued before the clear.
        activeBuffer = null
    }

    /**
     * Where the framebuffer's rows start, which is also the orientation of [captureFramebuffer].
     */
    open val framebufferOrigin: FramebufferOrigin
        get() = FramebufferOrigin.TopLeft

    /**
     * Copies what was drawn so far in this frame into the texture, which must be the size of the
     * window. Its orientation follows the backend's [framebufferOrigin].
     */
    fun captureFramebuffer(texture: GraphicsTexture) {
        commandBatch.addLast(DrawCommand(captureTexture = texture))
        batchSimulator?.barrier()

        // A geometry draw without a new setState() would otherwise write into a buffer already queued before the copy.
        activeBuffer = null
    }

    /**
     * Pushes a transformation matrix onto the matrix stack. This matrix will be applied to subsequent drawing operations.
     */
    fun pushMatrix(matrix: Mat2) {
        matrixStack.addLast(matrix)
    }

    /**
     * Pops a transformation matrix from the matrix stack.
     */
    fun popMatrix() {
        matrixStack.removeLastOrNull()
    }

    /**
     * Pushes a color onto the color stack. Its alpha is multiplied into subsequent colors, its RGB
     * is not, see [pushTint] for that.
     */
    /**
     * The alpha the pushed colors multiply everything drawn by.
     */
    val currentAlpha: Float
        get() = colorStack.lastOrNull()?.alpha ?: 1f

    fun pushColor(color: Color4) {
        colorStack.addLast(color.alpha(color.alpha * (colorStack.lastOrNull()?.alpha ?: 1f)))
    }

    /**
     * Pops a color from the color stack and returns it.
     */
    fun popColor(): Color4? {
        return colorStack.removeLastOrNull()
    }

    /**
     * Pushes a tint onto the tint stack: the RGB of [color] (its alpha is ignored) is multiplied
     * into the RGB of everything drawn until the matching [popTint], on top of the tints already pushed.
     */
    fun pushTint(color: Color4) {
        val parent = tintStack.lastOrNull() ?: Color4.White
        tintStack.addLast(Color4(parent.red * color.red, parent.green * color.green, parent.blue * color.blue))
    }

    /**
     * Pops the last tint pushed with [pushTint].
     */
    fun popTint() {
        tintStack.removeLastOrNull()
    }

    /**
     * The area drawing is currently limited to, in scene space as x, y, width and height: the
     * innermost scissor, or the window when nothing is clipped.
     */
    val clipBounds: Vec4
        get() = scissorStack.lastOrNull() ?: Vec4(0f, 0f, window.windowWidth.toFloat(), window.windowHeight.toFloat())

    /**
     * Pushes a shader onto the shader stack. It replaces the shader of subsequent drawing operations,
     * so it must use the built-in vertex layout: position at location 0, UV at 1 and color at 2.
     * Uniforms it does not declare are skipped.
     *
     * It also replaces `BuiltIn.Shaders.Video` for external textures, so a shader with a 2D sampler
     * must not be pushed while an external texture is drawn.
     */
    fun pushShader(shader: GraphicsShader) {
        shaderStack.addLast(shader)
    }

    /**
     * Pops a shader from the shader stack and returns it.
     */
    fun popShader(): GraphicsShader? {
        return shaderStack.removeLastOrNull()
    }

    /**
     * Pushes a blend mode onto the blend mode stack. It will be used for subsequent drawing operations.
     */
    fun pushBlendMode(blendMode: BlendMode) {
        blendModeStack.addLast(blendMode)
    }

    /**
     * Pops a blend mode from the blend mode stack and returns it.
     */
    fun popBlendMode(): BlendMode? {
        return blendModeStack.removeLastOrNull()
    }

    /**
     * Pushes a depth state onto the depth state stack. It will be used for subsequent drawing operations.
     */
    fun pushDepthState(depthState: DepthState) {
        depthStateStack.addLast(depthState)
    }

    /**
     * Pops a depth state from the depth state stack and returns it.
     */
    fun popDepthState(): DepthState? {
        return depthStateStack.removeLastOrNull()
    }

    /**
     * Pushes a scissor rectangle onto the scissor stack. This rectangle will be used to clip subsequent drawing operations.
     */
    fun pushScissor(x: Float, y: Float, width: Float, height: Float) {

        val currentMatrix = matrixStack.lastOrNull() ?: Mat2.Identity

        currentMatrix.transform(x, y, points, 0)
        currentMatrix.transform(x + width, y + height, points, 2)
        val x0 = points[0]
        val y0 = points[1]
        val x1 = points[2]
        val y1 = points[3]

        val x = min(x0, x1)
        val y = min(y0, y1)
        val width = abs(x1 - x0)
        val height = abs(y1 - y0)

        val intersectedX: Float
        val intersectedY: Float
        val intersectedWidth: Float
        val intersectedHeight: Float

        if (scissorStack.isEmpty()) {
            intersectedX = x
            intersectedY = y
            intersectedWidth = width
            intersectedHeight = height
        } else {
            val current = scissorStack.last()

            val minX = max(current.x, x)
            val minY = max(current.y, y)
            val maxX = min(current.x + current.z, x + width)
            val maxY = min(current.y + current.w, y + height)

            intersectedX = minX
            intersectedY = minY
            // Without overlap nothing must be drawn, a negative size would be rejected by GL.
            intersectedWidth = (maxX - minX).coerceAtLeast(0f)
            intersectedHeight = (maxY - minY).coerceAtLeast(0f)
        }

        val rect = Vec4(intersectedX, intersectedY, intersectedWidth, intersectedHeight)
        scissorStack.addLast(rect)
    }

    /**
     * Pops a scissor rectangle from the scissor stack and returns it.
     */
    fun popScissor(): Vec4? {
        return scissorStack.removeLastOrNull()
    }

    /**
     * Transforms the given color by multiplying its alpha value with the alpha of the last color in
     * the color stack. If any tint is pushed ([pushTint]), the RGB is also multiplied by the product of
     * those tints.
     *
     * If the color stack is empty, the original color is returned.
     */
    fun transformColor(color: Color4): Color4 {
        val tint = tintStack.lastOrNull() ?: Color4.White

        return Color4(
            color.red * tint.red,
            color.green * tint.green,
            color.blue * tint.blue,
            color.alpha * (colorStack.lastOrNull()?.alpha ?: 1f)
        )
    }

    //endregion

    //region Resource Management

    /**
     * Registers a GPU resource with the renderer. This allows the renderer to manage the resource's
     * lifecycle and handle context loss events.
     */
    fun registerResource(resource: GraphicsResource) = synchronized(gpuResourcesLock) {
        gpuResources.addLast(resource)
    }

    /**
     * Unregisters a GPU resource from the renderer.
     */
    fun unregisterResource(resource: GraphicsResource) = synchronized(gpuResourcesLock) {
        gpuResources.remove(resource)
    }

    /**
     * Marks all registered GPU resources as lost due to a context loss event and should be recreated.
     *
     * Resources are registered from the update worker while the GL thread marks them, so the list is
     * locked while it is iterated.
     */
    fun markContextLoss() = synchronized(gpuResourcesLock) {
        for (resource in gpuResources) {
            resource.markContextLost()
        }
    }

    /**
     * Creates a new shader instance with the specified source.
     */
    fun createShader(shaderSource: GraphicsShader.Source): GraphicsShader {
        val shader = device.createShader(shaderSource)
        registerResource(shader)
        return shader
    }

    /**
     * Creates every shader of [BuiltIn.Shaders.Programs] and stores it in its property.
     */
    protected fun createBuiltInShaders() {
        for (program in BuiltIn.Shaders.Programs) {
            val source = Platform.provider.createShaderSource(program.vertexPath, program.fragmentPath, isExternal = false)
                ?: throw IllegalStateException("Failed to load the built-in shader ${program.vertexPath} + ${program.fragmentPath}")

            program.store(createShader(source))
        }
    }

    /**
     * Creates a new texture with the specified width and height.
     */
    fun createTexture(width: Float, height: Float): GraphicsTexture {
        val texture = device.createTexture(width, height)
        registerResource(texture)
        return texture
    }

    /**
     * Creates a new external texture (such as one fed with video frames) with the specified width and
     * height, registered so that it is recreated after a context loss.
     */
    fun createExternalTexture(width: Float, height: Float): GraphicsTexture {
        val texture = device.createExternalTexture(width, height)
        registerResource(texture)
        return texture
    }

    /**
     * Creates a new vertex buffer for storing vertex data.
     */
    fun createVertexBuffer(): GraphicsBuffer {
        val buffer = device.createBuffer()
        registerResource(buffer)
        return buffer
    }

    //endregion

    //region Shape

    fun GraphicsBuffer.addVertex(x: Float, y: Float, u: Float, v: Float, color: Color4) {
        putVertex(x, y, u, v, color.packedRGBA)
    }


    /**
     * Allows to draw custom geometry by providing a block that operates on the active vertex buffer.
     */
    fun drawGeometry(block: GraphicsBuffer.(transform: Mat2, baseColor: Color4) -> Unit) {
        activeBuffer?.apply {
            val transform = matrixStack.lastOrNull() ?: Mat2.Identity
            val baseColor = colorStack.lastOrNull() ?: Color4.White

            block(transform, baseColor)
        } ?: throw IllegalStateException("No active vertex buffer to draw geometry. Make sure to call setState() before drawing.")
    }

    //region UI batches

    /**
     * Sets the state for the UI shader, which draws boxes and text in the same commands. Boxes
     * don't sample the texture, so they join the current command whatever its texture is, and
     * [texture] is only needed for text: it joins a command with the same texture or without one
     * yet, which then takes it. Its vertices are quads of four, see [DrawCommand.isQuads].
     */
    private fun setUIState(texture: GraphicsTexture?) {
        val last = commandBatch.lastOrNull()

        if (shaderStack.isEmpty() && last != null &&
            last.shader === BuiltIn.Shaders.UI &&
            last.vertexLayout === VertexLayout.UI &&
            last.isQuads &&
            !last.clearDepth &&
            last.instanceBuffer == null &&
            last.uniformSet === EMPTY_UNIFORMS &&
            last.scissor == scissorStack.lastOrNull() &&
            last.blendMode == (blendModeStack.lastOrNull() ?: BlendMode.Alpha) &&
            last.depthState == (depthStateStack.lastOrNull() ?: DepthState.Default)
        ) {
            if (texture == null || last.texture === texture) {
                activeBuffer = last.buffer
                return
            }

            if (last.texture == null) {
                commandBatch[commandBatch.lastIndex] = last.copy(texture = texture)
                activeTexture = texture
                activeBuffer = last.buffer
                return
            }
        }

        setState(
            shader = BuiltIn.Shaders.UI,
            texture = texture,
            uniformSet = EMPTY_UNIFORMS,
            vertexLayout = VertexLayout.UI,
            isQuads = true
        )
    }

    // A vertex of VertexLayout.UI, with the box parameters of its quad.
    private fun GraphicsBuffer.addUIVertex(x: Float, y: Float, u: Float, v: Float, color: Int, box: FloatArray) {
        requireCapacity(UI_VERTEX_STRIDE)
        putFloat(x)
        putFloat(y)
        putFloat(u)
        putFloat(v)
        putPackedBytes(color)
        for (i in 0 until UI_BOX_FLOATS) {
            putFloat(box[i])
        }
    }

    // The box parameters shared by the vertices of a UI quad, see setUIBox.
    private val uiBox = FloatArray(UI_BOX_FLOATS)

    /**
     * Sets [uiBox]: width, height, border width, mode plus 4 times the fade flags, the four radii,
     * then the packed border colors, or the softness and the inset of a soft box. A null border
     * color sets its side's fade flag, see ui.frag.
     */
    private fun setUIBox(
        width: Float, height: Float, borderWidth: Float, mode: Int,
        radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
        borderTop: Color4?, borderRight: Color4?, borderBottom: Color4?, borderLeft: Color4?,
        softness: Float, inset: Float
    ) {
        val box = uiBox

        var flags = 0
        if (borderTop == null) flags = flags or 1
        if (borderRight == null) flags = flags or 2
        if (borderBottom == null) flags = flags or 4
        if (borderLeft == null) flags = flags or 8

        box[0] = width
        box[1] = height
        box[2] = borderWidth
        box[3] = (mode + 4 * flags).toFloat()
        box[4] = radiusTopLeft
        box[5] = radiusTopRight
        box[6] = radiusBottomRight
        box[7] = radiusBottomLeft

        if (mode == UI_MODE_SOFT_BOX) {
            box[8] = softness
            box[9] = inset
            for (i in 10 until UI_BOX_FLOATS) box[i] = 0f
        } else {
            packUIColor(borderTop, box, 8)
            packUIColor(borderRight, box, 10)
            packUIColor(borderBottom, box, 12)
            packUIColor(borderLeft, box, 14)
        }
    }

    // A color in two floats of 16 bits each, which floats hold exactly, unpacked by ui.vert.
    private fun packUIColor(color: Color4?, box: FloatArray, index: Int) {
        if (color == null) {
            box[index] = 0f
            box[index + 1] = 0f
            return
        }
        val transformed = transformColor(color)
        box[index] = ((transformed.red8 shl 8) or transformed.green8).toFloat()
        box[index + 1] = ((transformed.blue8 shl 8) or transformed.alpha8).toFloat()
    }

    // A quad of the UI shader with the box set by setUIBox and a fill color at each corner.
    private fun addUIQuad(x: Float, y: Float, width: Float, height: Float, c0: Int, c1: Int, c2: Int, c3: Int) {
        val buffer = activeBuffer ?: throw IllegalStateException("No active vertex buffer to draw geometry.")
        val transform = matrixStack.lastOrNull() ?: Mat2.Identity

        val p = points
        transform.transform(x, y, p, 0)
        transform.transform(x + width, y, p, 2)
        transform.transform(x + width, y + height, p, 4)
        transform.transform(x, y + height, p, 6)

        // A quad: top left, top right, bottom right, bottom left.
        val box = uiBox
        buffer.addUIVertex(p[0], p[1], 0f, 0f, c0, box)
        buffer.addUIVertex(p[2], p[3], 1f, 0f, c1, box)
        buffer.addUIVertex(p[4], p[5], 1f, 1f, c2, box)
        buffer.addUIVertex(p[6], p[7], 0f, 1f, c3, box)
    }

    //endregion

    //region Batch simulation

    // The draw just recorded covers the first count points of points.
    private fun simulatePoints(texture: GraphicsTexture?, count: Int) {
        val simulator = batchSimulator ?: return
        val p = points
        var minX = p[0]
        var minY = p[1]
        var maxX = minX
        var maxY = minY
        for (i in 1 until count) {
            val x = p[i * 2]
            val y = p[i * 2 + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        simulator.draw(commandBatch.last(), texture, minX, minY, maxX, maxY)
    }

    // The draw just recorded is inside the rectangle, in local space.
    private fun simulateRect(texture: GraphicsTexture?, x: Float, y: Float, width: Float, height: Float) {
        if (batchSimulator == null) return
        val transform = currentTransform
        val p = points
        transform.transform(x, y, p, 0)
        transform.transform(x + width, y, p, 2)
        transform.transform(x + width, y + height, p, 4)
        transform.transform(x, y + height, p, 6)
        simulatePoints(texture, 4)
    }

    // The draw just recorded has vertices the shader transforms, so it may cover anything.
    private fun simulateUnbounded(texture: GraphicsTexture?) {
        batchSimulator?.draw(
            commandBatch.last(), texture,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY
        )
    }

    //endregion

    /**
     * Draws a rectangle with the specified position, size, color, border and corner radii.
     *
     * Each side of the border can have its own color. A side whose color is null has none of its
     * own and fades between the sides next to it, so with no color on the left and the right the
     * border fades from the top color to the bottom one (e.g. a bezel). Sides meet on the diagonals
     * from the corners.
     *
     * @param radius The radius of every corner, unless given one by one.
     * @param softness The width of a soft edge in pixels, which blurs the rectangle (e.g. a shadow).
     * A soft rectangle has no border, a negative [borderWidth] cuts the shape out of its edge.
     * @param inset How far the rectangle is inside the drawn area on each side, so a soft edge can
     * extend past it.
     */
    fun drawRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color4 = Color4.Transparent,
        borderWidth: Float = 0f,
        borderColor: Color4 = Color4.Transparent,
        radius: Float = 0f,
        softness: Float = 0f,
        inset: Float = 0f,
        radiusTopLeft: Float = radius,
        radiusTopRight: Float = radius,
        radiusBottomRight: Float = radius,
        radiusBottomLeft: Float = radius,
        borderTop: Color4? = borderColor,
        borderRight: Color4? = borderColor,
        borderBottom: Color4? = borderColor,
        borderLeft: Color4? = borderColor
    ) {
        setUIState(null)

        setUIBox(
            width, height, borderWidth, if (softness > 0f) UI_MODE_SOFT_BOX else UI_MODE_BOX,
            radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
            borderTop, borderRight, borderBottom, borderLeft,
            softness, inset
        )

        val fill = transformColor(color).packedRGBA
        addUIQuad(x, y, width, height, fill, fill, fill, fill)
        simulatePoints(null, 4)
    }

    /**
     * Draws a rectangle like [drawRect] with a color at each corner, interpolated across it. A
     * linear gradient is linear across the rectangle, so its colors at the corners are exact.
     */
    fun drawGradientRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        topLeft: Color4,
        topRight: Color4,
        bottomRight: Color4,
        bottomLeft: Color4,
        borderWidth: Float = 0f,
        borderColor: Color4 = Color4.Transparent,
        radius: Float = 0f,
        radiusTopLeft: Float = radius,
        radiusTopRight: Float = radius,
        radiusBottomRight: Float = radius,
        radiusBottomLeft: Float = radius
    ) {
        // Without a shape to cut, only the colors are needed. The UI shader costs much more per
        // pixel, which adds up for large gradients (a fullscreen one took ~3 ms on an Adreno 610),
        // even if it breaks the batch.
        if (borderWidth <= 0f && radiusTopLeft <= 0f && radiusTopRight <= 0f && radiusBottomRight <= 0f && radiusBottomLeft <= 0f) {
            setState(shader = BuiltIn.Shaders.Color, texture = null, uniformSet = EMPTY_UNIFORMS)

            drawGeometry { transform, _ ->
                val p = points
                transform.transform(x, y, p, 0)
                transform.transform(x + width, y, p, 2)
                transform.transform(x + width, y + height, p, 4)
                transform.transform(x, y + height, p, 6)

                val c0 = transformColor(topLeft).packedRGBA
                val c1 = transformColor(topRight).packedRGBA
                val c2 = transformColor(bottomRight).packedRGBA
                val c3 = transformColor(bottomLeft).packedRGBA

                putVertex(p[0], p[1], 0f, 0f, c0)
                putVertex(p[2], p[3], 1f, 0f, c1)
                putVertex(p[4], p[5], 1f, 1f, c2)

                putVertex(p[0], p[1], 0f, 0f, c0)
                putVertex(p[4], p[5], 1f, 1f, c2)
                putVertex(p[6], p[7], 0f, 1f, c3)
            }
            simulatePoints(null, 4)
            return
        }

        setUIState(null)

        setUIBox(
            width, height, borderWidth, UI_MODE_BOX,
            radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
            borderColor, borderColor, borderColor, borderColor,
            0f, 0f
        )

        addUIQuad(
            x, y, width, height,
            transformColor(topLeft).packedRGBA,
            transformColor(topRight).packedRGBA,
            transformColor(bottomRight).packedRGBA,
            transformColor(bottomLeft).packedRGBA
        )
        simulatePoints(null, 4)
    }

    /**
     * Draws a triangle with the specified position, size, and color.
     */
    fun drawTriangle(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        middle: Float = 0.5f,
        color: Color4 = Color4.White
    ) {
        setState(
            shader = BuiltIn.Shaders.SolidQuad,
            uniformSet = EMPTY_UNIFORMS
        )

        // The quad shader fades out at UV edges, so every vertex samples its center to keep the
        // shape opaque up to its geometric edges.
        drawGeometry { transform, baseColor ->
            val color = transformColor(color)

            val p = points

            transform.transform(x, y, p, 0)
            transform.transform(x + width, y, p, 2)
            transform.transform(x + width * middle, y + height, p, 4)

            addVertex(p[0], p[1], 0.5f, 0.5f, color)
            addVertex(p[2], p[3], 0.5f, 0.5f, color)
            addVertex(p[4], p[5], 0.5f, 0.5f, color)
        }
        simulatePoints(null, 3)
    }

    /**
     * Draws a triangle mesh, [vertices] holds [vertexCount] positions in local space as x, y pairs
     * and every three of them form a triangle.
     *
     * It draws with the solid shader and every vertex samples the center of the UV space (0.5) and
     * uses [color]. A shader pushed with [pushShader] replaces the solid one.
     */
    fun drawTriangles(vertices: FloatArray, vertexCount: Int, color: Color4) {
        require(vertexCount % 3 == 0) { "The vertex count must be a multiple of 3, got $vertexCount." }
        require(vertices.size >= vertexCount * 2) { "Expected ${vertexCount * 2} floats, got ${vertices.size}." }

        setState(
            shader = BuiltIn.Shaders.SolidQuad,
            uniformSet = EMPTY_UNIFORMS
        )

        drawGeometry { transform, _ ->
            val color = transformColor(color)
            val p = points

            for (i in 0 until vertexCount) {
                transform.transform(vertices[i * 2], vertices[i * 2 + 1], p, 0)
                addVertex(p[0], p[1], 0.5f, 0.5f, color)
            }
        }

        if (batchSimulator != null && vertexCount > 0) {
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (i in 0 until vertexCount) {
                val x = vertices[i * 2]
                val y = vertices[i * 2 + 1]
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            simulateRect(null, minX, minY, maxX - minX, maxY - minY)
        }
    }

    /**
     * Draws a line between two points with the specified width and color.
     */
    fun drawLine(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        lineWidth: Float,
        color: Color4 = Color4.White
    ) {
        val length = hypot(x2 - x1, y2 - y1)
        if (length <= 0f || lineWidth <= 0f) return

        setState(
            shader = BuiltIn.Shaders.SolidQuad,
            uniformSet = EMPTY_UNIFORMS
        )

        drawGeometry { transform, baseColor ->
            val color = transformColor(color)

            // Perpendicular offset of half the line width.
            val nx = -(y2 - y1) / length * lineWidth / 2f
            val ny = (x2 - x1) / length * lineWidth / 2f

            val p = points

            transform.transform(x1 + nx, y1 + ny, p, 0)
            transform.transform(x2 + nx, y2 + ny, p, 2)
            transform.transform(x2 - nx, y2 - ny, p, 4)
            transform.transform(x1 - nx, y1 - ny, p, 6)

            addVertex(p[0], p[1], 0.5f, 0.5f, color)
            addVertex(p[2], p[3], 0.5f, 0.5f, color)
            addVertex(p[4], p[5], 0.5f, 0.5f, color)

            addVertex(p[0], p[1], 0.5f, 0.5f, color)
            addVertex(p[4], p[5], 0.5f, 0.5f, color)
            addVertex(p[6], p[7], 0.5f, 0.5f, color)
        }
        simulatePoints(null, 4)
    }

    // The points of the last arc on a unit circle, cos and sin of each segment boundary. Arcs of
    // the same resolution and angles (e.g. several rings) reuse them instead of computing them.
    private var arcPointsSegments = -1
    private var arcPointsStart = 0f
    private var arcPointsEnd = 0f
    private var arcPointsCache = FloatArray(0)

    // Full circles of every resolution, as rings of different sizes alternate.
    private val fullCirclePoints = HashMap<Int, FloatArray>()

    private fun arcPoints(segments: Int, startAngle: Float, endAngle: Float): FloatArray {
        if (startAngle == 0f && endAngle == 360f) {
            return fullCirclePoints.getOrPut(segments) { computeArcPoints(segments, startAngle, endAngle) }
        }

        if (segments == arcPointsSegments && startAngle == arcPointsStart && endAngle == arcPointsEnd) {
            return arcPointsCache
        }

        arcPointsSegments = segments
        arcPointsStart = startAngle
        arcPointsEnd = endAngle
        arcPointsCache = computeArcPoints(segments, startAngle, endAngle)
        return arcPointsCache
    }

    private fun computeArcPoints(segments: Int, startAngle: Float, endAngle: Float): FloatArray {

        val start = (startAngle - 90f).toRadians()
        val delta = ((endAngle - 90f).toRadians() - start) / segments

        val points = FloatArray((segments + 1) * 2)
        for (i in 0..segments) {
            val angle = start + i * delta
            points[i * 2] = cos(angle)
            points[i * 2 + 1] = sin(angle)
        }

        return points
    }

    // Full circles can be drawn by the rounded box shader. Not while writing depth: the box's
    // corners would write it too, while masks rely on the circle's shape.
    private fun canDrawAsRoundedBox(width: Float, height: Float, startAngle: Float, endAngle: Float): Boolean {
        val depthState = depthStateStack.lastOrNull() ?: DepthState.Default
        return abs(endAngle - startAngle) >= 360f && abs(width - height) < 0.5f && !(depthState.enabled && depthState.writeEnabled)
    }

    private fun calculateArcResolution(width: Float, height: Float, angle: Float = 360f): Int {
        if (angle <= 0f || width <= 0f || height <= 0f) return 0

        val averageRadius = (width + height) / 4f
        val angleBetweenSegments = min(5f, 360f / averageRadius)
        val segments = abs(angle) / angleBetweenSegments

        return max(3, segments.toInt())
    }

    fun drawArc(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        startAngle: Float = 0f,
        endAngle: Float = 360f,
        color: Color4 = Color4.White
    ) {
        // A full circle is a single rounded box instead of a fan of segments.
        if (canDrawAsRoundedBox(width, height, startAngle, endAngle)) {
            drawRect(x, y, width, height, color = color, radius = width / 2f)
            return
        }

        val segments = calculateArcResolution(width, height, abs(endAngle - startAngle)).coerceAtLeast(1)

        setState(
            shader = BuiltIn.Shaders.SolidQuad,
            uniformSet = EMPTY_UNIFORMS
        )

        drawGeometry { transform, baseColor ->
            val color = transformColor(color)

            val cx = x + width / 2f
            val cy = y + height / 2f
            val w = width / 2f
            val h = height / 2f
            val p = points
            transform.transform(cx, cy, p, 0)
            val centerX = p[0]
            val centerY = p[1]

            if (segments <= 0) {
                Logger.w(
                    "GraphicsRenderer",
                    "drawArc: Invalid number of segments ($segments) for arc. Skipping drawing."
                )
                return@drawGeometry
            }

            val start = (startAngle - 90f).toRadians()
            val end = (endAngle - 90f).toRadians()

            val delta = (end - start) / segments

            var previousX = cx + w * cos(start)
            var previousY = cy + h * sin(start)

            for (j in 0..segments) {

                val angle = start + j * delta
                val x = cx + w * cos(angle)
                val y = cy + h * sin(angle)

                if (j > 0) {
                    transform.transform(previousX, previousY, p, 2)
                    transform.transform(x, y, p, 4)

                    addVertex(p[2], p[3], 0.5f, 0.5f, color)
                    addVertex(p[4], p[5], 0.5f, 0.5f, color)
                    addVertex(centerX, centerY, 0.5f, 0.5f, color)
                }

                previousX = x
                previousY = y
            }
        }
        simulateRect(null, x, y, width, height)
    }

    /**
     * Draws the outline of an arc with the specified thickness, angles are in degrees starting
     * from the top and going clockwise, as in [drawArc].
     */
    fun drawArcStroke(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        thickness: Float,
        startAngle: Float = 0f,
        endAngle: Float = 360f,
        color: Color4 = Color4.White
    ) {
        if (thickness <= 0f) return

        // Rings stay segments: as a rounded box the whole square inside them is shaded, which made
        // big rings (the menu's ripples) cost the GPU much more than the segments cost.
        val segments = calculateArcResolution(width, height, abs(endAngle - startAngle))
        if (segments <= 0) return

        setState(
            shader = BuiltIn.Shaders.SolidQuad,
            uniformSet = EMPTY_UNIFORMS
        )

        drawGeometry { transform, _ ->
            val color = transformColor(color)

            val cx = x + width / 2f
            val cy = y + height / 2f
            val outerX = width / 2f
            val outerY = height / 2f
            val innerX = (outerX - thickness).coerceAtLeast(0f)
            val innerY = (outerY - thickness).coerceAtLeast(0f)

            val unitCircle = arcPoints(segments, startAngle, endAngle)
            val p = points

            for (segment in 0 until segments) {
                val cos0 = unitCircle[segment * 2]
                val sin0 = unitCircle[segment * 2 + 1]
                val cos1 = unitCircle[segment * 2 + 2]
                val sin1 = unitCircle[segment * 2 + 3]

                // Outer start and end, then inner start and end.
                transform.transform(cx + outerX * cos0, cy + outerY * sin0, p, 0)
                transform.transform(cx + outerX * cos1, cy + outerY * sin1, p, 2)
                transform.transform(cx + innerX * cos0, cy + innerY * sin0, p, 4)
                transform.transform(cx + innerX * cos1, cy + innerY * sin1, p, 6)

                addVertex(p[0], p[1], 0.5f, 0.5f, color)
                addVertex(p[2], p[3], 0.5f, 0.5f, color)
                addVertex(p[6], p[7], 0.5f, 0.5f, color)

                addVertex(p[0], p[1], 0.5f, 0.5f, color)
                addVertex(p[6], p[7], 0.5f, 0.5f, color)
                addVertex(p[4], p[5], 0.5f, 0.5f, color)
            }
        }
        simulateRect(null, x, y, width, height)
    }

    /**
     * Draws a texture with the specified position, size and color.
     *
     * With a radius, the texture is cut by a rounded rectangle, the mask, which is the drawn
     * rectangle unless given (in the same space), e.g. the bounds of the component a cropped or
     * letterboxed texture belongs to. External textures can't be rounded.
     */
    fun drawTexture(
        texture: GraphicsTexture,
        region: GraphicsTexture.Region,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color4 = Color4.White,
        radius: Float = 0f,
        uv: Vec4 = Vec4(region.u0, region.v0, region.u1, region.v1),
        radiusTopLeft: Float = radius,
        radiusTopRight: Float = radius,
        radiusBottomRight: Float = radius,
        radiusBottomLeft: Float = radius,
        maskX: Float = x,
        maskY: Float = y,
        maskWidth: Float = width,
        maskHeight: Float = height
    ) {
        val externalTransform = texture.transform
        val isRounded = radiusTopLeft > 0f || radiusTopRight > 0f || radiusBottomRight > 0f || radiusBottomLeft > 0f

        // The texture shader only reads the uniforms for rounded corners, so plain textures share
        // an empty set and consecutive ones from the same texture end up in one draw call.
        val uniformSet = if (texture.isExternal && externalTransform != null) {
            // External textures are sampled through their transform, which the draw reads by reference
            // after the bind latched the latest frame. Rounded corners are not supported for them.
            if (externalUniformsTexture !== texture) {
                externalUniformsTexture = texture
                externalUniforms = GraphicsShader.UniformSet().apply { set("uTransform", externalTransform) }
            }
            externalUniforms
        } else if (isRounded && maskWidth > 0f && maskHeight > 0f) {
            GraphicsShader.UniformSet().apply {
                set("uRadii", Vec4(radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft))
                set("uMask", Vec4(maskX, maskY, maskWidth, maskHeight))
                set("uQuad", Vec4(x, y, width, height))
                set("uUVRect", uv)
            }
        } else {
            EMPTY_UNIFORMS
        }

        // A pushed shader replaces the video one too, see pushShader.
        setState(
            shader = if (texture.isExternal) BuiltIn.Shaders.Video else BuiltIn.Shaders.TextureQuad,
            uniformSet = uniformSet,
            texture = texture
        )

        drawGeometry { transform, baseColor ->
            val color = transformColor(color)

            val p = points

            transform.transform(x, y, p, 0)
            transform.transform(x + width, y, p, 2)
            transform.transform(x + width, y + height, p, 4)
            transform.transform(x, y + height, p, 6)

            // Triangle 1
            addVertex(p[0], p[1], uv.x, uv.y, color)
            addVertex(p[2], p[3], uv.z, uv.y, color)
            addVertex(p[4], p[5], uv.z, uv.w, color)

            // Triangle 2
            addVertex(p[0], p[1], uv.x, uv.y, color)
            addVertex(p[4], p[5], uv.z, uv.w, color)
            addVertex(p[6], p[7], uv.x, uv.w, color)
        }
        simulatePoints(texture, 4)
    }

    /**
     * Draws text using the specified font, position, and color. The text is rendered using the
     * font's atlas pages, each run of glyphs on the same page is one draw command.
     */
    fun drawText(
        text: String,
        font: Font,
        x: Float,
        y: Float,
        color: Color4 = Color4.White
    ) {
        val transform = matrixStack.lastOrNull() ?: Mat2.Identity
        val packedColor = transformColor(color).packedRGBA

        // Glyphs are laid out in local space and every corner is transformed, so rotation and
        // scale apply to text as well.
        val baseline = y - font.metrics.ascent

        var charIndex = 0
        var cursor = x
        var previousCodePoint = -1

        // The page the current draw command samples from; a glyph on another page starts a new one.
        var page: GraphicsTexture? = null
        var buffer: GraphicsBuffer? = null

        // The bounds of the glyphs drawn from the current page, for the batch simulation.
        val simulator = batchSimulator
        var runMinX = Float.POSITIVE_INFINITY
        var runMinY = Float.POSITIVE_INFINITY
        var runMaxX = Float.NEGATIVE_INFINITY
        var runMaxY = Float.NEGATIVE_INFINITY

        while (charIndex < text.length) {

            val codePoint = text.codePointAt(charIndex)
            val charCount = charCountOf(codePoint)

            if (previousCodePoint != -1) {
                cursor += font.getKerning(previousCodePoint, codePoint)
            }
            previousCodePoint = codePoint

            val glyph = font[codePoint]

            // Some glyphs may not have a texture region if they not need a visible representation
            // (like a space). In such cases, we skip rendering but still advance the cursor.
            if (glyph.region != null) {
                // Text shares the UI batches with boxes, a glyph on another atlas page needs another one.
                if (glyph.region.texture !== page) {
                    if (simulator != null && page != null) {
                        simulator.draw(commandBatch.last(), page, runMinX, runMinY, runMaxX, runMaxY)
                        runMinX = Float.POSITIVE_INFINITY
                        runMinY = Float.POSITIVE_INFINITY
                        runMaxX = Float.NEGATIVE_INFINITY
                        runMaxY = Float.NEGATIVE_INFINITY
                    }
                    page = glyph.region.texture
                    setUIState(page)
                    buffer = activeBuffer
                }

                val left = cursor + glyph.bearingX
                val top = baseline + glyph.bearingY

                val right = left + glyph.width
                val bottom = top + glyph.height

                val u0 = glyph.region.u0
                val v0 = glyph.region.v0
                val u1 = glyph.region.u1
                val v1 = glyph.region.v1

                val p = points

                transform.transform(left, top, p, 0)
                transform.transform(right, top, p, 2)
                transform.transform(right, bottom, p, 4)
                transform.transform(left, bottom, p, 6)

                // A quad: top left, top right, bottom right, bottom left.
                buffer!!.apply {
                    addUIVertex(p[0], p[1], u0, v0, packedColor, UI_TEXT_BOX)
                    addUIVertex(p[2], p[3], u1, v0, packedColor, UI_TEXT_BOX)
                    addUIVertex(p[4], p[5], u1, v1, packedColor, UI_TEXT_BOX)
                    addUIVertex(p[6], p[7], u0, v1, packedColor, UI_TEXT_BOX)
                }

                if (simulator != null) {
                    for (i in 0 until 4) {
                        val px = p[i * 2]
                        val py = p[i * 2 + 1]
                        if (px < runMinX) runMinX = px
                        if (px > runMaxX) runMaxX = px
                        if (py < runMinY) runMinY = py
                        if (py > runMaxY) runMaxY = py
                    }
                }
            }

            cursor += glyph.advance
            charIndex += charCount
        }

        if (simulator != null && page != null) {
            simulator.draw(commandBatch.last(), page, runMinX, runMinY, runMaxX, runMaxY)
        }
    }

    //endregion

    //region Rendering Lifecycle

    /**
     * Prepares the renderer for a new frame, e.g. by making the graphics context current.
     *
     * @return `false` if the frame cannot be rendered right now and should be skipped.
     */
    open fun prepareFrame(): Boolean = true

    open fun begin() {
        activeBuffer = null
        activeShader = null
        activeTexture = null
        activeScissor = null
        activeUniformSet = EMPTY_UNIFORMS

        // Should not happen but just in case, clear the command batch to avoid rendering stale commands.
        for (command in commandBatch) {
            releaseBuffers(command)
        }
        commandBatch.clear()
        buffersUsed = 0
        batchSimulator?.reset()
    }

    abstract fun drawCommand(command: DrawCommand)

    // The command was executed (or dropped), its buffers can be rewritten.
    private fun releaseBuffers(command: DrawCommand) {
        command.buffer?.isRecorded = false
        command.instanceBuffer?.isRecorded = false
    }

    open fun end() {
        val submitStartNanos = monotonicNanos()
        while (commandBatch.isNotEmpty()) {
            val command = commandBatch.removeFirst()
            drawCommand(command)
            releaseBuffers(command)
        }
        PerformanceStats.submitTime = (monotonicNanos() - submitStartNanos) / 1_000_000f
        buffersUsed = 0
        PerformanceStats.onFrameDrawn()
    }

    //endregion


    companion object {
        const val VERTEX_2D_STRIDE = 20 // 8 bytes for position, 8 bytes for UV, 4 bytes for color
        const val UI_VERTEX_STRIDE = 84 // VertexLayout.UI

        // The floats after the color in a vertex of VertexLayout.UI.
        private const val UI_BOX_FLOATS = 16

        // The modes of the UI shader, see ui.frag.
        private const val UI_MODE_BOX = 0
        private const val UI_MODE_TEXT = 1
        private const val UI_MODE_SOFT_BOX = 2

        // The box of every glyph, of which only the mode is read.
        private val UI_TEXT_BOX = FloatArray(UI_BOX_FLOATS).also { it[3] = UI_MODE_TEXT.toFloat() }

        // Shared by every draw without uniforms of its own. Nothing writes to it, commands and
        // shaders only read their set.
        private val EMPTY_UNIFORMS = GraphicsShader.UniformSet()
    }

}