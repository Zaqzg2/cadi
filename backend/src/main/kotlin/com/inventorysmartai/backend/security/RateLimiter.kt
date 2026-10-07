package com.inventorysmartai.backend.security

import java.util.concurrent.ConcurrentHashMap

/**
 * A sliding-window limiter kept in memory (a free-tier server is one process, so nothing shared is needed). It resets
 * when the service restarts, which is acceptable: it exists to stop a runaway loop or a stranger hammering the URL, not
 * to do billing.
 */
class RateLimiter(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val hits = ConcurrentHashMap<String, ArrayDeque<Long>>()

    /** 0 = allowed (and counted); otherwise how many whole seconds to wait before trying again. */
    fun acquire(key: String, limit: Int, windowMs: Long = 60_000L): Long {
        val now = clock()
        val queue = hits.computeIfAbsent(key) { ArrayDeque() }
        val wait = synchronized(queue) {
            while (queue.isNotEmpty() && now - queue.first() >= windowMs) queue.removeFirst()
            if (queue.size >= limit) {
                val waitMs = windowMs - (now - queue.first())
                maxOf(1L, (waitMs + 999) / 1000)
            } else {
                queue.addLast(now)
                0L
            }
        }
        if (hits.size > MAX_KEYS) prune(now, windowMs)
        return wait
    }

    private fun prune(now: Long, windowMs: Long) {
        hits.entries.removeIf { entry ->
            val queue = entry.value
            synchronized(queue) { queue.isEmpty() || now - queue.last() >= windowMs }
        }
    }

    private companion object {
        const val MAX_KEYS = 5_000
    }
}

/** A global ceiling on AI requests per UTC day, so a leaked app key cannot burn through the free provider quotas. 0 = unlimited. */
class DailyCounter(private val cap: Int, private val clock: () -> Long = { System.currentTimeMillis() }) {
    private var day = -1L
    private var count = 0

    @Synchronized
    fun tryTake(): Boolean {
        if (cap == 0) return true
        val today = clock() / MILLIS_PER_DAY
        if (today != day) {
            day = today
            count = 0
        }
        if (count >= cap) return false
        count++
        return true
    }

    @Synchronized
    fun used(): Int = count

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
