package com.lastpaycheck.domain.repository

class ActiveGameClock(private val monotonicMillis: MonotonicTimeSource) : MonotonicTimeSource {
    private var elapsed = 0L
    private var startedAt: Long? = null

    override fun nowMillis(): Long = elapsed + (
        startedAt?.let {
            monotonicMillis.nowMillis() - it
        } ?: 0L
        )

    fun setRunning(running: Boolean) {
        if (running && startedAt == null) {
            startedAt = monotonicMillis.nowMillis()
        } else if (!running && startedAt != null) {
            elapsed = nowMillis()
            startedAt = null
        }
    }
}
