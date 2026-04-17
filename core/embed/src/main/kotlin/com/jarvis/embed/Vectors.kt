package com.jarvis.embed

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object Vectors {
    fun normalise(v: FloatArray): FloatArray {
        var s = 0f
        for (x in v) s += x * x
        val n = sqrt(s).coerceAtLeast(1e-12f)
        val out = FloatArray(v.size)
        for (i in v.indices) out[i] = v[i] / n
        return out
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        val len = minOf(a.size, b.size)
        var dot = 0f
        for (i in 0 until len) dot += a[i] * b[i]
        return dot
    }

    fun pack(v: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { bb.putFloat(it) }
        return bb.array()
    }

    fun unpack(bytes: ByteArray, dim: Int): FloatArray {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(dim)
        for (i in 0 until dim) out[i] = bb.float
        return out
    }
}
