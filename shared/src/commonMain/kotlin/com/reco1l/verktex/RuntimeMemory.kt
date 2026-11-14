package com.reco1l.verktex

// Counters of the runtime's memory management, read by PerformanceStats.

/**
 * Bytes the process allocated since it started, or -1 when the platform can't tell. It's not
 * cheap and may allocate, so it's read a few times per second at most.
 */
internal expect fun processAllocatedBytes(): Long

/**
 * Garbage collections since the process started, or -1 when the platform can't tell. Read as
 * rarely as [processAllocatedBytes].
 */
internal expect fun garbageCollectionCount(): Long

/**
 * Bytes the calling thread allocated since it started, or -1 when the platform can't tell. It
 * doesn't allocate, so it can be read every frame.
 */
internal expect fun threadAllocatedBytes(): Long
