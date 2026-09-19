# CALL-E Android SDK (`calle-android-sdk`) 📱

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%20%7C%20Wear%20OS-green.svg?logo=android)](https://developer.android.com)
[![JitPack](https://jitpack.io/v/Baklolman69/calle-android-sdk.svg)](https://jitpack.io/#Baklolman69/calle-android-sdk)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

**`calle-android-sdk`** is the native Kotlin SDK for integrating [CALL-E](https://heycall-e.com) AI voice call agents into Android and Wear OS applications.

All clean SDK files are available directly under [`calle/sdk/`](file:///c:/Users/ravis/Downloads/WristCallAI-main/WristCallAI-main/calle-android/calle/sdk) for 1-click integration in any Android Studio project.

---

## 📂 SDK Package Structure (`calle/sdk`)

```
calle/sdk/
├── CallEClient.kt          # Core CALL-E REST API Client & Demo Mode
├── GroqClient.kt           # Groq Cloud LLM Prompt Refinement
├── data/
│   ├── SmartCallResolver.kt # SerpApi Google Search + Groq Intent Resolver
│   ├── SerpApiClient.kt     # Google Business & Phone Lookup
│   └── CallEPreferences.kt  # Secure Persistence Helper
├── models/
│   └── CallEModels.kt       # CallRequest, CallResponse & Transcript Models
└── ui/
    └── CallEStatusBadge.kt  # Animated Jetpack Compose UI Badge
```

---

## ⚡ 2-Minute Quick Integration Guide

### Option 1: Drag & Drop `calle/sdk` (Instant 1-Click Copy)

1. Copy the [`calle/sdk`](file:///c:/Users/ravis/Downloads/WristCallAI-main/WristCallAI-main/calle-android/calle/sdk) folder from this repository.
2. Paste it directly into your Android app's source directory:
   `app/src/main/java/com/calle/sdk`
3. Add Ktor and Serialization dependencies to your app's `build.gradle.kts`:

```kotlin
dependencies {
    // Ktor Client (Android Engine)
    implementation("io.ktor:ktor-client-core:2.3.8")
    implementation("io.ktor:ktor-client-android:2.3.8")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.8")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")
    implementation("io.ktor:ktor-client-logging:2.3.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}
```

---

### Option 2: Gradle / JitPack Dependency

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

Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Baklolman69:calle-android-sdk:1.0.0")
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
        println("Call Dispatched! ID: ${response.id}, Status: ${response.status}")
    }
}
```

---

### 2. Smart Business Resolution (No Phone Number Required!)

If your user only types or speaks a business name, use `SmartCallResolver` to look up the business phone number automatically on Google via SerpApi:

```kotlin
import com.calle.sdk.data.SmartCallResolver

val resolver = SmartCallResolver(
    serpApiKey = "YOUR_SERPAPI_KEY",
    groqApiKey = "YOUR_GROQ_API_KEY"
)

lifecycleScope.launch {
    val resolvedInfo = resolver.resolveCall("Call Cattleack Barbeque in Farmers Branch to check hours")
    
    println("Found Phone: ${resolvedInfo.phone}")          // +19726440167
    println("Refined Task: ${resolvedInfo.refinedPrompt}") // Call +19726440167 and ask...

    val request = CallRequest(resolvedInfo.phone, resolvedInfo.refinedPrompt)
    client.dispatchCall(request)
}
```

---

### 3. Fetch Real-time Audio Transcript

```kotlin
lifecycleScope.launch {
    val transcriptResult = client.getTranscript(callId = "call_123456")
    transcriptResult.onSuccess { transcriptText ->
        println(transcriptText)
        /*
          Output:
          [AGENT]: Hello! Calling to check opening hours.
          [RECIPIENT]: We are open until 10 PM.
        */
    }
}
```

---

### 4. Jetpack Compose Animated Status Badge

```kotlin
import com.calle.sdk.ui.CallEStatusBadge
import com.calle.sdk.models.CallEStatus

@Composable
fun MyScreen(status: CallEStatus) {
    CallEStatusBadge(status = status, compact = false)
}
```

---

## 📑 Full SDK Documentation

For complete class specifications, data models, error handling, and Wear OS guidelines, check out **[CALLE-ANDROID-SDK.md](file:///c:/Users/ravis/Downloads/WristCallAI-main/WristCallAI-main/calle-android/CALLE-ANDROID-SDK.md)**.

---

## ⚠️ Disclaimer & Privacy

> **Zero Telemetry · Zero Data Collection · Full Developer Control**

This SDK operates with **zero telemetry and zero data collection**. All API credentials are stored **locally on-device only** — no user data, analytics, device identifiers, or usage metrics are ever collected, transmitted, or processed by this SDK.

This SDK integrates with the following **third-party services** only when explicitly invoked by your application code:

| Service | Purpose | Privacy & Terms |
|---|---|---|
| **[CALL-E](https://heycall-e.com)** | AI voice call dispatch & transcripts | [Privacy](https://heycall-e.com/privacy) · [Terms](https://heycall-e.com/terms) |
| **[SerpApi](https://serpapi.com)** | Optional Google Search phone lookup | [Privacy](https://serpapi.com/privacy-policy) · [Terms](https://serpapi.com/terms-of-service) |
| **[Groq](https://groq.com)** | Optional LLM prompt refinement | [Privacy](https://groq.com/privacy-policy) · [Terms](https://groq.com/terms-of-use) |

This SDK is provided **"AS IS"** without warranty of any kind. The maintainers and contributors are **not responsible** for any data loss, data leakage, security breaches, or damages arising from the use or misconfiguration of this SDK or any integrated third-party service. Developers are solely responsible for securing their own API credentials, obtaining user consent, and complying with all applicable laws and regulations.

📄 **[Full Privacy Policy](PRIVACY.md)** · 📋 **[Full Terms of Use](TERMS.md)**

---

## 📄 License

Licensed under the [MIT License](LICENSE).
