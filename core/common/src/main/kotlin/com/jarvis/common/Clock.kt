package com.jarvis.common

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

interface AppClock {
    fun now(): Instant
}

object SystemAppClock : AppClock {
    override fun now(): Instant = Clock.System.now()
}
