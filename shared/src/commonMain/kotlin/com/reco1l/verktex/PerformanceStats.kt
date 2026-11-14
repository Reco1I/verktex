package com.reco1l.verktex

import com.reco1l.verktex.time.monotonicNanos
import kotlin.concurrent.Volatile

/**
 * Timings of the engine workers for the last frames, shown by [com.reco1l.verktex.ui.UIPerformanceOverlay].
 *
 * Frame values are written and read on the draw worker, the update time comes from the update worker.
 */
object PerformanceStats {

    /**
     * How many frame times are kept.
     */
    const val HISTORY_SIZE = 120

    /**
     * Milliseconds spent drawing the last frame, from the first component to the last GL call,
     * without waiting for the display.
     */
    var drawTime = 0f
        private set

    /**
     * Milliseconds the last frame spent measuring and laying out the scenes, part of [recordTime].
     */
    var layoutTime = 0f
        private set

    /**
     * Milliseconds the last frame spent going through the component tree, recording draw commands
     * (including [layoutTime]).
     */
    var recordTime = 0f

    /**
     * Milliseconds the last frame spent executing the draw commands through the graphics API.
     */
    var submitTime = 0f

    /**
     * Milliseconds the GPU took to execute a recent frame, measured a few frames late, or -1 when
     * the device can't measure it. A frame time above the draw time and close to this one means
     * the GPU is what limits the frame rate.
     */
    var gpuTime = -1f

    // Accumulated during the frame, as every scene lays itself out.
    private var frameLayoutNanos = 0L

    /**
     * Adds time spent measuring and laying out to the current frame.
     */
    fun addLayoutTime(nanos: Long) {
        frameLayoutNanos += nanos
    }

    /**
     * Milliseconds the last update frame took.
     */
    @Volatile
    var updateTime = 0f

    /**
     * Draw commands the scene and its overlays produced in the last frame, the diagnostics (such
     * as the performance overlay) excluded. While an overlay shows a capture, what it covers is
     * the one command drawing the capture.
     */
    var drawCommands = 0

    /**
     * Megabytes per second the whole process allocated over the last sampling window, or -1 when
     * the platform can't tell.
     */
    var allocationRate = -1f
        private set

    /**
     * Garbage collections per minute over the last sampling window, or -1 when the platform can't
     * tell.
     */
    var collectionRate = -1f
        private set

    /**
     * Kilobytes the draw worker allocated per frame on average over the last sampling window,
     * from the start of a frame to its last graphics call, or -1 when the platform can't tell.
     */
    var drawAllocation = -1f
        private set

    /**
     * Average time between frames in milliseconds over the kept frame times.
     */
    val frameTime: Float
        get() {
            var total = 0f
            var count = 0
            for (time in frameTimes) {
                if (time > 0f) {
                    total += time
                    count++
                }
            }
            return if (count > 0) total / count else 0f
        }

    /**
     * The longest kept frame time in milliseconds.
     */
    val maxFrameTime: Float
        get() = frameTimes.max()

    /**
     * Average frames per second over the kept frame times.
     */
    val fps: Float
        get() {
            val frameTime = frameTime
            return if (frameTime > 0f) 1000f / frameTime else 0f
        }


    private val frameTimes = FloatArray(HISTORY_SIZE)
    private var nextIndex = 0
    private var lastFrameNanos = 0L
    private var frameStartNanos = 0L

    // The counters at the start of the sampling window, see sampleMemory.
    private var windowStartNanos = 0L
    private var windowStartAllocated = 0L
    private var windowStartCollections = 0L
    private var windowDrawAllocated = 0L
    private var windowFrames = 0
    private var frameStartAllocated = 0L
    private var frameDrawAllocated = -1L


    /**
     * Returns the time in milliseconds between the starts of two frames, `0` is the oldest kept one.
     */
    fun frameTimeAt(index: Int) = frameTimes[(nextIndex + index) % HISTORY_SIZE]

    fun onFrameStart() {
        val now = monotonicNanos()

        if (lastFrameNanos != 0L) {
            frameTimes[nextIndex] = (now - lastFrameNanos) / 1_000_000f
            nextIndex = (nextIndex + 1) % HISTORY_SIZE
        }

        lastFrameNanos = now
        frameStartNanos = now

        layoutTime = frameLayoutNanos / 1_000_000f
        frameLayoutNanos = 0L

        if (frameDrawAllocated >= 0L) {
            windowDrawAllocated += frameDrawAllocated
            windowFrames++
            frameDrawAllocated = -1L
        }
        sampleMemory(now)
        frameStartAllocated = threadAllocatedBytes()
    }

    fun onFrameDrawn() {
        drawTime = (monotonicNanos() - frameStartNanos) / 1_000_000f

        // Vulkan calls it again once it submitted, the last call counts.
        val allocated = threadAllocatedBytes()
        frameDrawAllocated = if (allocated >= 0L && frameStartAllocated >= 0L) allocated - frameStartAllocated else -1L
    }

    // The process counters may allocate, so they're read once per window.
    private fun sampleMemory(now: Long) {
        if (windowStartNanos != 0L && now - windowStartNanos < MEMORY_WINDOW_NANOS) {
            return
        }

        val allocated = processAllocatedBytes()
        val collections = garbageCollectionCount()

        if (windowStartNanos != 0L) {
            val seconds = (now - windowStartNanos) / 1_000_000_000f

            allocationRate = if (allocated >= 0L && windowStartAllocated >= 0L) {
                (allocated - windowStartAllocated).coerceAtLeast(0L) / (1024f * 1024f) / seconds
            } else -1f

            collectionRate = if (collections >= 0L && windowStartCollections >= 0L) {
                (collections - windowStartCollections) * 60f / seconds
            } else -1f

            drawAllocation = if (windowFrames > 0) windowDrawAllocated / 1024f / windowFrames else -1f
        }

        windowStartNanos = now
        windowStartAllocated = allocated
        windowStartCollections = collections
        windowDrawAllocated = 0L
        windowFrames = 0
    }

    private const val MEMORY_WINDOW_NANOS = 2_000_000_000L
}
