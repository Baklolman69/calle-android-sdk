# CALL-E Android SDK (`calle-android-sdk`) 📱

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%20%7C%20Wear%20OS-green.svg?logo=android)](https://developer.android.com)
[![API Level](https://img.shields.io/badge/Min%20SDK-24%2B%20(Android%207.0%2B)-brightgreen.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

**`calle-android-sdk`** is the official Kotlin SDK for integrating [CALL-E](https://heycall-e.com) AI voice agents into native Android and Wear OS applications.

All SDK files are accessible under the [`calle/sdk`](file:///c:/Users/ravis/Downloads/WristCallAI-main/WristCallAI-main/calle-android/calle/sdk) package for drag-and-drop or Gradle module inclusion.

---

## 📂 Package Directory Structure (`calle/sdk`)

```
calle/sdk/
├── CallEClient.kt          # Primary REST API Client & Simulation Bridge
├── GroqClient.kt           # Groq AI 120B Client
├── data/
│   ├── SmartCallResolver.kt # Business Search & Intent Synthesizer
│   ├── SerpApiClient.kt     # Google Business Phone Lookup
│   └── CallEPreferences.kt  # Settings & Key Persistence
├── models/
│   └── CallEModels.kt       # API Data Transfer Objects & Enums
└── ui/
    └── CallEStatusBadge.kt  # Jetpack Compose State Animation Badge
```

---

## 📐 Architecture & Call Dispatch Flow

```mermaid
sequenceDiagram
    autonumber
    participant App as Android / Wear OS App
    participant Resolver as SmartCallResolver
    participant Client as CallEClient
    participant External as SerpApi & Groq AI
    participant Telecom as CALL-E Telecom API

    App->>Resolver: resolveCall("Call Cattleack BBQ to ask hours")
    alt Phone Number Missing
        Resolver->>External: Search Business & Synthesize Intent
        External-->>Resolver: Discovered Phone (+1...) & Refined Task
    end
    Resolver-->>App: ResolvedCallInfo (E.164 Phone + Task)
    
    App->>Client: dispatchCall(CallRequest)
    Client->>Telecom: POST /v1/calls (Authorization: Bearer KEY)
    Telecom-->>Client: CallResponse (Call ID, Status: DISPATCHING)
    Client-->>App: Result.success(CallResponse)

    loop Call Lifecycle Polling
        App->>Client: getCallStatus(callId)
        Client->>Telecom: GET /v1/calls/{callId}
        Telecom-->>Client: CallResponse (Status: CALL_IN_PROGRESS / SUCCESS)
    end

    App->>Client: getTranscript(callId)
    Client-->>App: Formatted Dialogue ("[AGENT]: ... \n [RECIPIENT]: ...")
```

---

## 📦 Integration Options

### Option A: Drag & Drop Package (`calle/sdk`)

1. Copy the [`calle/sdk`](file:///c:/Users/ravis/Downloads/WristCallAI-main/WristCallAI-main/calle-android/calle/sdk) directory.
2. Paste it into your project at `src/main/java/com/calle/sdk`.
3. Add Ktor and Serialization dependencies to `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.ktor:ktor-client-core:2.3.8")
    implementation("io.ktor:ktor-client-android:2.3.8")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.8")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")
    implementation("io.ktor:ktor-client-logging:2.3.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}
```

### Option B: Gradle / JitPack Integration

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.Baklolman69:calle-android-sdk:1.0.0")
}
```

---

## 🚀 Usage Guide & Examples

### 1. Initializing Client & Testing API Keys

```kotlin
import com.calle.sdk.CallEClient

// Production Initialization
val client = CallEClient(apiKey = "sk_live_123456789")

// Sandbox / Offline Simulation Mode
val demoClient = CallEClient(apiKey = "DEMO_KEY")

// Test Key Validity
val isValid = client.validateApiKey()
```

### 2. Dispatching AI Phone Calls

```kotlin
import com.calle.sdk.models.CallRequest

val request = CallRequest(
    toPhoneNumber = "+15550199000",
    promptInstructions = "Check if tables are available for 4 people at 7 PM."
)

val result = client.dispatchCall(request)
result.onSuccess { response ->
    println("Call Dispatched. ID: ${response.id}")
}
```

### 3. Business Phone Discovery (`SmartCallResolver`)

```kotlin
import com.calle.sdk.data.SmartCallResolver

val resolver = SmartCallResolver(
    serpApiKey = "YOUR_SERPAPI_KEY",
    groqApiKey = "YOUR_GROQ_API_KEY"
)

val info = resolver.resolveCall("Call Cattleack Barbeque in Farmers Branch TX")
val request = CallRequest(info.phone, info.refinedPrompt)
client.dispatchCall(request)
```

### 4. Fetching Audio Transcripts

```kotlin
val transcriptResult = client.getTranscript("call_123456")
transcriptResult.onSuccess { transcript ->
    println(transcript)
}
```

---

## 📄 License

Licensed under the [MIT License](LICENSE).
