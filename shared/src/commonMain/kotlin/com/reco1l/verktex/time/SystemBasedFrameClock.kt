package com.reco1l.verktex.time

class SystemBasedFrameClock : FrameClock(), IFrameProcessableClock, IControllableClock {

    //region Clock implementation

    override var time = 0f
        private set

    override var isRunning = true
        private set

    override var elapsedFrameTime = 0f
        private set

    override var lastFrameTime = 0f
        private set

    //endregion

    /**
     * The most a frame advances this clock by, in seconds. A frame that took longer, a stall, advances it by this
     * instead, so what animates on it slows down for that frame rather than skipping ahead. Unlimited by default.
     */
    var maxElapsedFrameTime = Float.POSITIVE_INFINITY

    private var lastRealFrameTime = SystemClock.time


    override fun processFrame() {
        if (!isRunning) return

        val realTime = SystemClock.time

        elapsedFrameTime = (realTime - lastRealFrameTime).coerceAtMost(maxElapsedFrameTime)
        lastRealFrameTime = realTime

        lastFrameTime = time
        time += elapsedFrameTime
    }


    //region Controllable implementation

    override fun seek(to: Float): Boolean {
        time = to
        return true
    }

    override fun reset() {
        time = 0f
    }

    override fun start() {
        isRunning = true

        // Prevent jumps in time when resuming due to real time jumps
        lastRealFrameTime = SystemClock.time
    }

    override fun pause() {
        isRunning = false
    }

    //endregion
}