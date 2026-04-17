package com.jarvis.common

import java.util.UUID

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}
