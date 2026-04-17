package com.jarvis.embed

/**
 * Minimal tokenizer abstraction. Concrete implementations (WordPiece / SentencePiece)
 * are bundled alongside each downloaded embedding model in the app's files dir.
 */
interface Tokenizer {
    data class Encoded(
        val ids: IntArray,
        val attentionMask: IntArray,
        val typeIds: IntArray?,
    )

    fun encode(text: String, maxLen: Int): Encoded
}
