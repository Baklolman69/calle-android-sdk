# CALL-E Android SDK (`calle-android-sdk`) 📱

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%20%7C%20Wear%20OS-green.svg?logo=android)](https://developer.android.com)
[![API](https://img.shields.io/badge/CALL--E%20API-v2%20Calls-orange.svg)](https://docs.heycall-e.com/calls)
[![JitPack](https://jitpack.io/v/Baklolman69/calle-android-sdk.svg)](https://jitpack.io/#Baklolman69/calle-android-sdk)
[![Discussions](https://img.shields.io/badge/GitHub-Discussions-purple?logo=github)](https://github.com/Baklolman69/calle-android-sdk/discussions)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

**`calle-android-sdk`** is the native Kotlin SDK for integrating [CALL-E](https://heycall-e.com) AI voice call agents into Android and Wear OS applications.

Fully updated and compliant with the **[CALL-E V2 API specification](https://docs.heycall-e.com/calls)** (`/v2/calls`) and **SDK 1.0**.

All SDK sources are located under [`src/main/kotlin/com/calle/sdk`](src/main/kotlin/com/calle/sdk) for seamless Gradle dependency integration or direct copy-paste into any Android project.

---

## 🚀 What's New in v2.0.0

- **CALL-E V2 API Support**: Migrated to `POST /v2/calls` (HTTP 202 Accepted).
- **Mandatory Idempotency**: Built-in automatic `Idempotency-Key` generation and replay protection.
- **Structured Scalar Schemas (`ResultSchema`)**: Closed JSON schema extraction (`string`, `boolean`, `integer`, `number`) with zero-config defaults.
- **Accurate Readiness Rule**: Reactive `pollCallStatus` polling adheres strictly to `result_status != PENDING`.
- **Top-level Transcripts**: Read conversation turns directly from the root `transcript` array.
- **Lifecycle Events Streaming**: Track live progress (`call.ringing`, `call.connected`, `call.speech`, `call.asr`) via `pollCallEvents`.
- **Early Cancellation**: Support for `cancelCall(callId)` prior to provider submission.
- **Security Modernization**: Migrated to modern AndroidX `MasterKey.Builder` with hardware Keystore AES-256 encryption.

---

## 📂 SDK Package Structure

```
src/main/kotlin/com/calle/sdk/
├── CallEClient.kt          # Core CALL-E V2 Client, Reactive Status & Event Flows
├── GroqClient.kt           # Groq Cloud LLM Prompt Refinement
├── data/
│   ├── SmartCallResolver.kt # SerpApi Google Search + Groq Intent Resolver
│   ├── SerpApiClient.kt     # Google Business & Phone Lookup
│   ├── DeviceContactResolver.kt # Local Device Contacts Provider
│   └── CallEPreferences.kt  # AndroidX AES-256 Encrypted Keystore Storage
├── models/
│   └── CallEModels.kt       # V2 ResultSchema, CallResponse, Events & Enums
└── ui/
    └── CallEStatusBadge.kt  # Animated Jetpack Compose UI Status Badge
```

---

## ⚡ 2-Minute Quick Integration Guide

### Option 1: Gradle / JitPack Dependency (Recommended)

Add JitPack to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Baklolman69:calle-android-sdk:2.1.0")
}
```

---

### Option 2: Drag & Drop Source Files

1. Copy the [`src/main/kotlin/com/calle/sdk`](src/main/kotlin/com/calle/sdk) directory from this repository into your project (`app/src/main/java/com/calle/sdk`).
2. Add Ktor and Serialization dependencies to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-client-logging:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

---

## 💻 Code Examples

### 1. Initialize Client & Dispatch Call

```kotlin
import com.calle.sdk.CallEClient
import com.calle.sdk.models.CallRequest

// Initialize with production key or "DEMO_KEY" for offline testing
val client = CallEClient(apiKey = "YOUR_CALLE_API_KEY")

lifecycleScope.launch {
    val request = CallRequest(
        toPhoneNumber = "+15550199000",
        promptInstructions = "Ask opening hours tonight and vegetarian menu items."
    )

    val result = client.dispatchCall(request)
    result.onSuccess { response ->
        println("Call Accepted! ID: ${response.id}, Billing Call ID: ${response.billingCallId}")
    }
}
```

---

### 2. Reactive Live Call Tracking (`Flow`)

Track call lifecycle transitions in real-time until the business result is finalized:

```kotlin
lifecycleScope.launch {
    client.pollCallStatus(callId = "call_123456", pollIntervalMs = 2000L)
        .collect { response ->
            println("Status: ${response.status}, Outcome: ${response.callOutcome}, Result Status: ${response.resultStatus}")
            
            if (response.isResultReady) {
                println("Final Summary: ${response.bestSummary}")
                println("Structured Result: ${response.result}")
            }
        }
}
```

---

### 3. Stream Live Call Events & Callee Speech (`Flow`)

```kotlin
lifecycleScope.launch {
    client.pollCallEvents(callId = "call_123456")
        .collect { event ->
            println("[${event.type}] ${event.message}")
        }
}
```

---

### 4. Smart Business Resolution (No Phone Number Required!)

If your user only types or speaks a business name, use `SmartCallResolver` to look up the business phone number automatically on Google via SerpApi and refine the prompt via Groq:

```kotlin
import com.calle.sdk.data.SmartCallResolver

val resolver = SmartCallResolver(
    serpApiKey = "YOUR_SERPAPI_KEY",
    groqApiKey = "YOUR_GROQ_API_KEY"
)

lifecycleScope.launch {
    val resolvedInfo = resolver.resolveCall("Call Cattleack Barbeque in Farmers Branch to check hours")
    
    println("Found Phone: ${resolvedInfo.phone}")          // +19726440167
    println("Refined Task: ${resolvedInfo.task}")          // Ask Cattleack Barbeque in Farmers Branch...

    val request = CallRequest(
        toPhoneNumber = resolvedInfo.phone,
        promptInstructions = resolvedInfo.task
    )
    client.dispatchCall(request)
}
```

---

### 5. Fetch Full Audio Transcript

```kotlin
lifecycleScope.launch {
    val transcriptResult = client.getTranscript(callId = "call_123456")
    transcriptResult.onSuccess { transcriptText ->
        println(transcriptText)
        /*
          Output:
          [AGENT]: Hello! Calling to check opening hours tonight.
          [RECIPIENT]: We are open until 10 PM.
        */
    }
}
```

---

### 6. Jetpack Compose Animated Status Badge

```kotlin
import com.calle.sdk.ui.CallEStatusBadge

@Composable
fun CallStatusScreen(callResponse: CallResponse) {
    // Pass CallResponse directly: automatically reflects dual telephone & extraction status
    CallEStatusBadge(response = callResponse, compact = false)
}
```

---

## 📑 Full SDK Documentation

For complete class specifications, data models, error handling, and Wear OS guidelines, check out **[CALLE-ANDROID-SDK.md](CALLE-ANDROID-SDK.md)**.

---

## 💬 Community & Discussions

Have questions, ideas for new features, or need help integrating the SDK?  
Join our community on **[GitHub Discussions](https://github.com/Baklolman69/calle-android-sdk/discussions)**!

- 📢 **[SDK v2.1 Announcement](https://github.com/Baklolman69/calle-android-sdk/discussions/10)** — Read about the latest features, schema validation, and lifecycle streaming.
- 💡 **[Feature Ideas & Requests](https://github.com/Baklolman69/calle-android-sdk/discussions/categories/ideas)** — Suggest new integrations or capabilities.
- 🙋 **[Q&A](https://github.com/Baklolman69/calle-android-sdk/discussions/categories/q-a)** — Get help with Android, Wear OS, or CALL-E API setup.

---

## ⚠️ Disclaimer & Privacy

> **Zero Telemetry · Zero Data Collection · Full Developer Control**

This SDK operates with **zero telemetry and zero data collection**. All API credentials are stored **locally on-device only** — no user data, analytics, device identifiers, or usage metrics are ever collected, transmitted, or processed by this SDK.

📄 **[Full Privacy Policy](PRIVACY.md)** · 📋 **[Full Terms of Use](TERMS.md)**

---

## 📄 License

Licensed under the [MIT License](LICENSE).
