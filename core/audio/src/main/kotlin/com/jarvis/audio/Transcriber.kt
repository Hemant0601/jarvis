package com.jarvis.audio

interface Transcriber {
    suspend fun transcribe(clip: RecordedClip): String
}
