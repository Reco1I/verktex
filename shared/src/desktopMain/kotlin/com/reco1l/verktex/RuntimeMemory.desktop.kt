package com.reco1l.verktex

import java.lang.management.ManagementFactory

// The HotSpot extension of the thread bean, which counts the allocated bytes of each thread.
private val threadBean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean

// The threads that ended are no longer counted, so the total can go down.
internal actual fun processAllocatedBytes(): Long {
    val bean = threadBean ?: return -1L
    return bean.getThreadAllocatedBytes(bean.allThreadIds).sumOf { it.coerceAtLeast(0L) }
}

internal actual fun garbageCollectionCount(): Long =
    ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionCount.coerceAtLeast(0L) }

@Suppress("DEPRECATION")
internal actual fun threadAllocatedBytes(): Long =
    threadBean?.getThreadAllocatedBytes(Thread.currentThread().id) ?: -1L
