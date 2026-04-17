# Jarvis

A private, on-device personal assistant for Android that stores everything you capture (text + voice) as a Graph RAG knowledge brain, reasons over it with an on-device LLM (Gemma 3 via MediaPipe) or OpenRouter, and backs itself up to your Google Drive — encrypted end-to-end.

## Primary UI

Two main views, with a chat drill-in and a settings pane:

1. **Brain** (`GraphScreen`) — a neural-network-style, zoomable, pannable canvas of your memory. Nodes are notes/people/topics/events/places; edges are relationships. Tap a node to open a chat scoped to that memory.
2. **Capture** (`CaptureScreen`) — single input surface with a big one-tap record button and a free-form text field. Both feed the same ingest pipeline.
3. **Chat** — per-node or free-form conversation with the assistant. Cloud/local toggle lives in the top bar.
4. **Settings** — model downloads (Gemma / Whisper / embeddings), OpenRouter key, Google Drive connection, biometric unlock.

A home-screen widget deep-links into Capture for one-tap recording from outside the app.

## Architecture

Multi-module Gradle project. Core modules own their Hilt bindings so the `:app` module stays thin.

```
:app                     Compose UI, navigation, widget, MainActivity, Application.
:core:common             Shared primitives: ids, clock.
:core:data               Room + SQLCipher schema (nodes, edges, chunks, embeddings, captures, messages).
:core:graph              Graph RAG: repository, hybrid retriever (vector + 1-2 hop walk), ingest pipeline, LLM router.
:core:embed              ONNX Runtime embedding service (e.g. all-MiniLM-L6-v2 or EmbeddingGemma).
:core:llm                MediaPipe Gemma client, OpenRouter HTTP client, LLM settings, model catalog/downloads.
:core:audio              AudioRecorder (PCM16 16 kHz), Whisper transcriber, foreground recording service.
:core:backup             Google sign-in, encrypted Drive appDataFolder snapshot, WorkManager scheduling.
```

### Data flow

```
 [Capture view] --> AudioRecorder --> Whisper --+
                 +-> text field ----------------+
                                                 v
                                        IngestPipeline
                                        - chunking
                                        - embeddings (ONNX)
                                        - entity/relation extraction (Gemma)
                                                 |
                                                 v
                                          SQLCipher DB
                                         (nodes, edges,
                                          chunks, embeddings)
                                                 |
                          +----------------------+----------------------+
                          v                      v                      v
                   [Brain view]          GraphRagRetriever       DriveBackupWorker
                   force-directed        vector + graph walk     encrypted DB snapshot
                                                 |
                                                 v
                                            LlmRouter
                                     (Gemma local by default,
                                      OpenRouter on toggle)
                                                 |
                                                 v
                                            [Chat view]
```

### Security

- **SQLCipher** full-DB AES-256 encryption. Passphrase is generated per install and stored in `EncryptedSharedPreferences` (backed by the Android Keystore).
- **Biometric unlock** gates app launch (opt-in in Settings).
- **Drive backup** writes the already-encrypted DB file to `appDataFolder`, visible only to this app's own OAuth client. Keys never leave the device; for cross-device restore the user is asked for a separate passphrase that re-wraps the key.

### Retrieval

Hybrid graph RAG: embed the query, find top-k nearest chunks, expand owning nodes 1-2 hops, pull chunks from the expanded set, re-rank with a hop-distance penalty, then feed cited memories into the prompt.

## Tech stack

- Kotlin 2.0, Gradle 8.9, AGP 8.5.
- Compose (Material 3), Navigation-Compose, Hilt, KSP.
- Room 2.6 + SQLCipher 4.6.
- MediaPipe Tasks GenAI 0.10.21 (default model: Gemma 4 E2B, Apr 2026 Apache-2.0).
- ONNX Runtime Mobile for embeddings.
- whisper.cpp via `whisper-jni`.
- OkHttp + Retrofit + Moshi for OpenRouter.
- WorkManager for backup.
- Google Sign-In + Drive + Gmail APIs.

## Setup

1. Install Android Studio Koala (AGP 8.5+).
2. `cp local.properties.sample local.properties` and fill in `sdk.dir`. Optional: `OPENROUTER_API_KEY`, `GOOGLE_WEB_CLIENT_ID`.
3. Generate the Gradle wrapper: `gradle wrapper --gradle-version 8.9` (the binary `gradle-wrapper.jar` isn't committed — Android Studio can also do this during project sync).
4. Open in Android Studio and sync. First sync will fetch MediaPipe, ONNX and whisper-jni.
5. Run on an Android 13+ device. The app works immediately with a hashing-based embedder and a grounded-from-memory fallback responder — no downloads required. Open **Settings** to download Gemma 4 E2B (~2 GB) for on-device reasoning, Whisper for voice notes, or set an OpenRouter key for cloud access.
6. In Settings, connect a Google account to enable Drive backup.

## Module graph

```
:app           -> :core:data, :core:graph, :core:llm, :core:embed, :core:audio, :core:backup, :core:common
:core:graph    -> :core:data, :core:embed, :core:llm, :core:common
:core:audio    -> :core:llm, :core:common
:core:backup   -> :core:data, :core:common
:core:llm      -> :core:common
:core:embed    -> :core:common
:core:data     -> :core:common
```

## Roadmap / status

| Area                | Status                                                                  |
| ------------------- | ----------------------------------------------------------------------- |
| Gradle + modules    | Full multi-module setup                                                 |
| Compose UI          | Brain / Capture / Chat / Settings screens                               |
| DB + schema         | Room + SQLCipher + DAOs                                                 |
| Graph RAG retriever | Hybrid retrieval scaffold; needs chunk-by-id DAO method for full fidelity |
| Ingest pipeline     | Chunking + embeddings + Gemma extraction                                |
| Gemma client        | MediaPipe GenAI wrapper                                                 |
| OpenRouter client   | Non-streaming generate; streaming TBD                                   |
| Whisper             | JNI wrapper + PCM decoding                                              |
| Embeddings          | ONNX Runtime + pluggable tokenizer; default tokenizer TBD               |
| Drive backup        | appDataFolder snapshot via WorkManager                                  |
| Gmail backup        | Scope reserved; worker TBD                                              |
| Biometric unlock    | Setting exists; gate TBD                                                |
| Widget              | One-tap deep-link                                                       |
| Tests               | Basic deps wired; suites TBD                                            |
