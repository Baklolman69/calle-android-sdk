# Terms of Use — CALL-E Android SDK (`calle-android-sdk`)

**Effective Date:** September 19, 2026  
**Last Updated:** September 19, 2026  
**Maintainer:** [@Baklolman69](https://github.com/Baklolman69)

---

## 1. Acceptance of Terms

By downloading, installing, importing, or using the CALL-E Android SDK (`calle-android-sdk`) in any form — whether via JitPack dependency, source integration, or forked repository — you ("Developer", "You") agree to be bound by these Terms of Use. If you do not agree, do not use this SDK.

---

## 2. License Grant

This SDK is released under the **[MIT License](LICENSE)** and is **free to use** for personal, educational, commercial, and open-source projects without restriction, subject to the terms of the MIT License.

You are free to:
- ✅ Use the SDK in commercial and non-commercial applications.
- ✅ Modify, fork, and distribute the source code.
- ✅ Integrate the SDK into Android, Wear OS, and any compatible platform.
- ✅ Use the SDK for hackathons, prototypes, and production applications.

---

## 3. Zero Data Collection

The CALL-E Android SDK operates with **zero telemetry and zero data collection**. The SDK:

- Does **not** collect, store, or transmit any user data, analytics, or device information.
- Does **not** contain any tracking mechanisms, advertising SDKs, or background data transmissions.
- Stores all API keys and configuration **locally on-device only**.
- Communicates **directly** between the developer's application and third-party API endpoints (CALL-E, SerpApi, Groq) — with no intermediary servers.

For full details, see the [Privacy Policy](PRIVACY.md).

---

## 4. Third-Party Services & API Keys

This SDK integrates with the following third-party services. **Each service requires the developer to obtain their own API keys** and agree to that service's respective terms:

| Service | Purpose | Terms & Privacy |
|---|---|---|
| **CALL-E** | AI voice call dispatch, transcripts, call status | [Terms](https://heycall-e.com/terms) · [Privacy](https://heycall-e.com/privacy) |
| **SerpApi** | Optional Google Search business/phone lookup | [Terms](https://serpapi.com/terms-of-service) · [Privacy](https://serpapi.com/privacy-policy) |
| **Groq Cloud** | Optional LLM prompt refinement | [Terms](https://groq.com/terms-of-use) · [Privacy](https://groq.com/privacy-policy) |

> **Note:** The SDK does not provide, bundle, or share any API keys. Developers must supply their own credentials and are fully responsible for their API key usage, billing, and compliance with each provider's terms.

---

## 5. Disclaimer of Warranties

THIS SDK IS PROVIDED **"AS IS"** AND **"AS AVAILABLE"**, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT.

The maintainers and contributors of this SDK make **no guarantees** regarding:

- The availability, uptime, or reliability of any third-party API (CALL-E, SerpApi, Groq).
- The accuracy of AI-generated call transcripts, prompt refinements, or phone number lookups.
- The successful completion of any voice call dispatched through the CALL-E platform.
- Compatibility with all Android devices, OS versions, or network configurations.

---

## 6. Limitation of Liability

**IN NO EVENT** shall the maintainers, contributors, or copyright holders of this SDK be liable for any:

- **Data loss or data leakage** resulting from the use, misuse, or misconfiguration of this SDK or any integrated third-party service.
- **Financial loss, damages, or claims** arising from API usage, billing, call failures, or incorrect phone number resolution.
- **Security breaches** resulting from improper credential storage, API key exposure, or failure to implement recommended security practices (such as `EncryptedSharedPreferences`).
- **Direct, indirect, incidental, special, consequential, or punitive damages** of any kind, regardless of the cause of action.

Developers assume **full responsibility** for securing their applications, API credentials, user data, and compliance with applicable laws and regulations.

---

## 7. Developer Obligations

By using this SDK, you agree to:

1. **Secure Your API Keys:** Store all API credentials securely. Use `EncryptedSharedPreferences` or equivalent secure storage. Never hardcode API keys in source code or commit them to version control.
2. **Provide End-User Privacy Policies:** If your application collects user data or initiates voice calls, you must provide your own privacy policy to your end users disclosing the use of CALL-E, SerpApi, and Groq services.
3. **Obtain User Consent:** Obtain any required consent from end users before initiating voice calls, transmitting data to third-party APIs, or processing personal information.
4. **Comply with Laws:** Comply with all applicable local, national, and international laws and regulations, including but not limited to GDPR, CCPA, COPPA, TCPA, and telecommunications regulations.
5. **Respect Rate Limits & Fair Use:** Adhere to the rate limits and fair use policies of all integrated third-party APIs.

---

## 8. Indemnification

You agree to indemnify, defend, and hold harmless the maintainers and contributors of this SDK from and against any claims, liabilities, damages, losses, and expenses (including reasonable legal fees) arising out of or in connection with your use of this SDK, your violation of these Terms, or your violation of any applicable law or regulation.

---

## 9. Modifications to Terms

These Terms of Use may be updated at any time. Changes will be reflected by updating the "Last Updated" date at the top of this document. Continued use of the SDK after any modifications constitutes acceptance of the updated terms.

---

## 10. Governing Law

These Terms shall be governed by and construed in accordance with applicable open-source software licensing conventions and the laws of the jurisdiction in which the maintainer resides, without regard to conflict of law provisions.

---

## 11. Contact

For questions regarding these Terms of Use, please open an issue on the [GitHub repository](https://github.com/Baklolman69/calle-android-sdk/issues) or contact the maintainer directly via GitHub.

---

*These terms apply exclusively to the `calle-android-sdk` open-source library and do not govern any third-party services, APIs, or end-user applications built using this SDK.*
