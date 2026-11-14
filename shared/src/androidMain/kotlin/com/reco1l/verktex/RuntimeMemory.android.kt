package com.reco1l.verktex

import android.os.Debug

// ART counts the bytes allocated in whole thread-local buffers, so over short spans the count is
// coarse. It has no per-thread count without instrumenting every allocation.

internal actual fun processAllocatedBytes(): Long =
    Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: -1L

internal actual fun garbageCollectionCount(): Long =
    Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: -1L

internal actual fun threadAllocatedBytes(): Long = -1L
