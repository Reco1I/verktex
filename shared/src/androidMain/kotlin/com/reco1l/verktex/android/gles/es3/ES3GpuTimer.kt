package com.reco1l.verktex.android.gles.es3

import android.opengl.GLES30
import android.opengl.GLES20

/**
 * Measures how long the GPU takes to execute each frame with `GL_EXT_disjoint_timer_query`.
 *
 * Results arrive a few frames late, so the CPU never waits for the GPU: each frame measured takes a
 * free query of a pool, and every frame reads the results that became available, oldest first. A
 * frame is left unmeasured only when every query still waits for its result.
 */
internal class ES3GpuTimer {

    /**
     * Whether the device supports timer queries. Checked on the first frame, with a context.
     */
    var isSupported = false
        private set

    /**
     * Milliseconds the GPU took to execute the last measured frame, or -1 if unknown.
     */
    var gpuTime = -1f
        private set

    private var checkedSupport = false

    private val queries = IntArray(QUERY_COUNT)

    // The queries waiting for their result, in the order they were issued: results become
    // available in that order.
    private val pending = ArrayDeque<Int>(QUERY_COUNT)

    // The query of the frame being measured, -1 if it isn't.
    private var running = -1

    private val result = IntArray(1)


    fun begin() {
        if (!checkedSupport) {
            checkedSupport = true
            isSupported = GLES20.glGetString(GLES20.GL_EXTENSIONS)?.contains("GL_EXT_disjoint_timer_query") == true
        }

        if (!isSupported) {
            return
        }

        // The queries go away with the context, they're made again for the new one.
        if (queries[0] == 0 || !GLES30.glIsQuery(queries[0])) {
            GLES30.glGenQueries(QUERY_COUNT, queries, 0)
            pending.clear()
        }

        val free = queries.indexOfFirst { it !in pending }
        if (free < 0) {
            return
        }

        // Clears the disjoint flag, which is set when the timings may be wrong (e.g. the GPU
        // changed its frequency).
        GLES20.glGetIntegerv(GL_GPU_DISJOINT_EXT, result, 0)

        GLES30.glBeginQuery(GL_TIME_ELAPSED_EXT, queries[free])
        running = queries[free]
    }

    fun end() {
        if (!isSupported) {
            return
        }

        if (running != -1) {
            GLES30.glEndQuery(GL_TIME_ELAPSED_EXT)
            pending.addLast(running)
            running = -1
        }

        // Every frame, measured or not, so results keep being read while the queries are all busy.
        while (pending.isNotEmpty()) {
            val query = pending.first()

            GLES30.glGetQueryObjectuiv(query, GLES30.GL_QUERY_RESULT_AVAILABLE, result, 0)
            if (result[0] == 0) {
                break
            }

            GLES30.glGetQueryObjectuiv(query, GLES30.GL_QUERY_RESULT, result, 0)
            pending.removeFirst()

            val nanos = result[0].toLong() and 0xFFFFFFFFL

            // A disjoint operation since the flag was last cleared makes the result meaningless.
            GLES20.glGetIntegerv(GL_GPU_DISJOINT_EXT, result, 0)
            if (result[0] == 0) {
                gpuTime = nanos / 1_000_000f
            }
        }
    }


    private companion object {
        const val QUERY_COUNT = 8

        const val GL_TIME_ELAPSED_EXT = 0x88BF
        const val GL_GPU_DISJOINT_EXT = 0x8FBB
    }
}
