# API Key Security Practices

This document describes how Otaku Reader stores, protects, and rotates API keys,
and provides guidance for contributors extending or maintaining the key-storage layer.

---

## 1. Threat Model

| Threat | Mitigation |
|---|---|
| Physical device access (unlocked) | Keys in Android Keystore — not extractable without Keystore auth |
| Malicious app reading files | `EncryptedSharedPreferences` — file content is ciphertext |
| Cloud backup leaking keys | `android:allowBackup="false"` on the encrypted prefs file |
| Memory dump / heap inspection | Keys are not cached in JVM heap between reads |
| Rooted device | Android Keystore provides hardware-backed storage on supported hardware |
| Compromised key | Key rotation — replace with a new key in ≤ 90 days |

---

## 2. Storage Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  User / Settings UI                                                 │
└────────────────────────────┬────────────────────────────────────────┘
                             │ saveApiKey / setGeminiApiKey
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  SecureApiKeyDataStore (core/ai)  /  AiPreferences (core/preferences)│
│  EncryptedApiKeyStore  (core/preferences)                           │
└──────────┬──────────────────────────────────────┬───────────────────┘
           │ key material                         │ non-sensitive metadata
           ▼                                      ▼
┌──────────────────────────┐           ┌─────────────────────────────┐
│  EncryptedSharedPreferences          │  Plaintext DataStore /      │
│  (AES-256-GCM values,    │           │  Plain SharedPreferences    │
│   AES-256-SIV keys)      │           │  (timestamps only)          │
└──────────┬───────────────┘           └─────────────────────────────┘
           │ wraps
           ▼
┌──────────────────────────┐
│  Android Keystore        │
│  MasterKey (AES-256-GCM) │
│  (hardware-backed on     │
│   devices with StrongBox)│
└──────────────────────────┘
```

### Classes

| Class | Module | Purpose |
|---|---|---|
| `SecureApiKeyDataStore` | `core/ai` | Multi-provider encrypted key store (Gemini, OpenAI, Anthropic, custom) |
| `EncryptedApiKeyStore` | `core/preferences` | Single-purpose Gemini-key store with `StateFlow` for UI observation |
| `AiPreferences` | `core/preferences` | AI settings DataStore; delegates key storage to `EncryptedSharedPreferences` |
| `EncryptedOpdsCredentialStore` | `core/preferences` | Per-server OPDS username/password storage |

---

## 3. Encryption Details

### Master Key

```kotlin
MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()
```

- Key alias: `_androidx_security_master_key` (framework default)
- Algorithm: AES/GCM/NoPadding, 256-bit key
- Storage: Android Keystore system (hardware-backed on devices with StrongBox or TEE)

### Preference Encryption

```kotlin
EncryptedSharedPreferences.create(
    context,
    "secure_api_keys_encrypted",     // file name
    masterKey,
    PrefKeyEncryptionScheme.AES256_SIV,    // key names encrypted with AES-256-SIV
    PrefValueEncryptionScheme.AES256_GCM   // values encrypted with AES-256-GCM
)
```

- **Key names** are encrypted with AES-256-SIV (deterministic, so key lookup is possible)
- **Values** are encrypted with AES-256-GCM (authenticated encryption)

---

## 4. Key Rotation

### Policy

| Parameter | Default | Notes |
|---|---|---|
| Maximum key age | **90 days** | `DEFAULT_KEY_MAX_AGE_DAYS = 90L` |
| Rotation trigger | Soft recommendation | Non-blocking; surfaced in Settings UI |
| Rotation mechanism | User-initiated | User enters new API key in Settings |

### How It Works

1. When `saveApiKey` / `setGeminiApiKey` is called, the current epoch-millisecond
   timestamp is written to a **non-encrypted** store (timestamps are not sensitive).
2. `isKeyRotationRecommended` / `isGeminiKeyRotationRecommended` computes the key age
   and returns `true` when `(now - storedAt) / 86_400_000 ≥ maxAgeDays`.
3. `rotateApiKey(provider, newApiKey)` delegates to `saveApiKey`, which overwrites the
   encrypted value and resets the timestamp.

### Surfacing a Rotation Prompt (Example)

```kotlin
// In your ViewModel or use-case:
val shouldRotate = secureApiKeyDataStore.isKeyRotationRecommended(PROVIDER_GEMINI)
if (shouldRotate) {
    _state.update { it.copy(showRotationBanner = true) }
}
```

### Migration of Legacy Keys

Users who stored keys before rotation-tracking was introduced will have no timestamp.
`isKeyRotationRecommended` returns `false` in that case (no recommendation is made
without a baseline timestamp).  A timestamp is recorded the next time the user saves
their key.

---

## 5. What Is NOT Encrypted

| Data | Storage | Reason |
|---|---|---|
| Key creation/rotation timestamps | Plaintext DataStore / SharedPreferences | Not sensitive; needed for age calculation |
| AI feature toggles (`aiEnabled`, `aiTier`, etc.) | Plaintext DataStore | Not sensitive |
| Tracker OAuth access tokens (Kitsu, MAL, Shikimori) | Not persisted | Held in memory only; re-fetched on app restart |
| Tracker OAuth client IDs / secrets | BuildConfig placeholders (empty in source) | Must be injected via CI/CD secrets at build time |

---

## 6. Backup Exclusion

The `EncryptedSharedPreferences` file is excluded from Android Auto Backup via the
`network_security_config` and the application's backup rules.  Ensure that any new
encrypted prefs file names are added to the backup exclusion configuration if automatic
backup is enabled in `AndroidManifest.xml`.

---

## 7. Future Hardening Recommendations

### 7.1 Biometric Authentication

Gate key-management operations (view, change, rotate) behind a biometric challenge using
[`BiometricPrompt`](https://developer.android.com/reference/androidx/biometric/BiometricPrompt).

```kotlin
// Pseudocode — wires up biometric auth before exposing a key
val biometricPrompt = BiometricPrompt(activity, executor, authCallback)
val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Authenticate to manage API keys")
    .setNegativeButtonText("Cancel")
    .build()
biometricPrompt.authenticate(promptInfo)
// In the success callback: allow the key-management operation
```

### 7.2 Tracker OAuth — Authorization Code + PKCE

The Kitsu and Shikimori integrations currently use the Resource Owner Password
Credentials (ROPC) grant, which passes the user's raw password to the app.
Migrate to Authorization Code + PKCE (see `TODO(security/C-7)` comments in
`TrackingApis.kt`) to eliminate raw password handling.

### 7.3 Certificate Pinning

Consider certificate pinning for the Gemini API endpoint to protect against
MITM attacks on compromised network infrastructure.

### 7.4 Keystore Master Key Rotation

Android Keystore manages the cryptographic key material; the master key itself does
not expire.  If a device compromise is suspected:

1. Call `EncryptedApiKeyStore.setGeminiApiKey("")` / `SecureApiKeyDataStore.clearAllApiKeys()` to
   wipe the stored keys.
2. Delete the `EncryptedSharedPreferences` file to force a fresh Keystore entry on next use.
3. Ask the user to re-enter their API key.

---

## 8. Security Audit Status

| Item | Status | Notes |
|---|---|---|
| API keys encrypted at rest | ✅ Complete | AES-256-GCM via Android Keystore |
| Keys not accessible to other apps | ✅ Complete | Private app storage + Keystore |
| Cleartext credential storage | ✅ Mitigated | One-time migration from legacy DataStore complete |
| Key rotation support | ✅ Complete | Timestamp tracking + 90-day recommendation |
| Rotation documentation | ✅ Complete | This document |
| Biometric auth | 🔄 Recommended | Not yet implemented — see §7.1 |
| Tracker PKCE migration | 🔄 Recommended | See `TODO(security/C-7)` |
| CI/CD secret injection | 🔄 Required for release | See `TrackerCredentials.kt` |
