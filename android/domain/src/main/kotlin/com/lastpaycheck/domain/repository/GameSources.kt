package com.lastpaycheck.domain.repository

fun interface MonotonicTimeSource {
    fun nowMillis(): Long
}

data class RandomDraw(val value: Int, val nextState: Long)

fun interface RandomSource {
    fun next(state: Long, bound: Int): RandomDraw
}
