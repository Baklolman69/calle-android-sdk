# Privacy Policy — CALL-E Android SDK (`calle-android-sdk`)

**Effective Date:** September 19, 2026  
**Last Updated:** September 19, 2026  
**Maintainer:** [@Baklolman69](https://github.com/Baklolman69)

---

## 1. Overview

The CALL-E Android SDK (`calle-android-sdk`) is an open-source, client-side Kotlin library that enables Android and Wear OS applications to integrate with the [CALL-E](https://heycall-e.com) AI voice agent platform.

**This SDK operates with zero telemetry and zero data collection.** We do not collect, transmit, store, or process any user data, analytics, device identifiers, or usage metrics on our end. All network communication occurs directly between the developer's application and the respective third-party API endpoints.

---

## 2. Data Collection — We Collect Nothing

| Data Category | Collected by SDK? | Details |
|---|---|---|
| Personal Information | **No** | No names, emails, or identifiers are collected. |
| Device Information | **No** | No device IDs, OS version, or hardware info is transmitted. |
| Usage Analytics | **No** | No telemetry, crash reports, or usage statistics are gathered. |
| Location Data | **No** | No GPS, IP-based, or network location data is accessed. |
| API Keys & Credentials | **No** | All credentials are stored locally on-device only. |
| Call Transcripts | **No** | Transcripts are fetched directly from the CALL-E API to the app. |

**The SDK contains no tracking pixels, analytics SDKs, advertising identifiers, or background data transmissions of any kind.**

---

## 3. Local Credential Storage

The SDK provides a local persistence helper (`CallEPreferences`) that stores API keys and configuration on-device using Android `SharedPreferences` (with optional `EncryptedSharedPreferences` via AndroidX Security Crypto).

- All credentials remain **exclusively on the user's device**.
- The SDK never transmits stored credentials to any server other than the intended API endpoint during authenticated requests.
- Developers are responsible for securing their own API keys and following platform-specific security best practices.

---

## 4. Third-Party Services

The SDK communicates directly with the following third-party services **only when explicitly invoked by the developer's application code**. No background or automatic requests are made.

### 4.1 CALL-E API

- **Purpose:** Dispatching AI voice calls, retrieving call status, and fetching transcripts.
- **Endpoint:** `https://api.heycall-e.com/v1`
- **Data Sent:** Task instruction string, phone number (as provided by the developer).
- **Privacy Policy:** [https://heycall-e.com/privacy](https://heycall-e.com/privacy)
- **Terms of Service:** [https://heycall-e.com/terms](https://heycall-e.com/terms)

### 4.2 SerpApi (Google Search)

- **Purpose:** Optional business phone number lookup via Google Search when no phone number is provided.
- **Endpoint:** `https://serpapi.com/search`
- **Data Sent:** Search query string derived from user prompt.
- **Privacy Policy:** [https://serpapi.com/privacy-policy](https://serpapi.com/privacy-policy)
- **Terms of Service:** [https://serpapi.com/terms-of-service](https://serpapi.com/terms-of-service)

### 4.3 Groq Cloud (LLM Inference)

- **Purpose:** Optional AI-powered prompt refinement and business intent synthesis.
- **Endpoint:** `https://api.groq.com/openai/v1/chat/completions`
- **Data Sent:** User prompt, discovered phone number, and business context for task refinement.
- **Privacy Policy:** [https://groq.com/privacy-policy](https://groq.com/privacy-policy)
- **Terms of Service:** [https://groq.com/terms-of-use](https://groq.com/terms-of-use)

> **Important:** Developers are responsible for reviewing and complying with the privacy policies and terms of service of each third-party provider listed above. The SDK acts solely as a client-side integration layer and does not intermediate, cache, or proxy any data between these services.

---

## 5. Developer Responsibility

This SDK is a **developer tool** — it provides the building blocks for integrating CALL-E voice agents into Android applications. As such:

- **Developers** are solely responsible for how they collect, process, and handle end-user data within their own applications.
- **Developers** must provide their own privacy policies to their end users that disclose the use of CALL-E, SerpApi, Groq, and any other integrated services.
- **Developers** are responsible for obtaining any required user consent before initiating voice calls or transmitting data to third-party APIs.
- **Developers** must comply with all applicable local, national, and international privacy regulations (including but not limited to GDPR, CCPA, and COPPA) when using this SDK in production applications.

---

## 6. Open Source Transparency

This SDK is fully open-source under the [MIT License](LICENSE). The complete source code is publicly available for audit and review at:

**[https://github.com/Baklolman69/calle-android-sdk](https://github.com/Baklolman69/calle-android-sdk)**

Any developer or security researcher can inspect, verify, and confirm that zero data collection or telemetry exists within the codebase.

---

## 7. Contact

For privacy-related questions or concerns regarding this SDK, please open an issue on the [GitHub repository](https://github.com/Baklolman69/calle-android-sdk/issues) or contact the maintainer directly via GitHub.

---

*This privacy policy applies exclusively to the `calle-android-sdk` open-source library and does not cover any third-party services, APIs, or applications built using this SDK.*
