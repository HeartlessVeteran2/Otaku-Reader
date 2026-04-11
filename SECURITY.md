# 🔒 Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| Latest  | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take security seriously. If you discover a security vulnerability:

1. **DO NOT** open a public issue
2. Email maintainers directly with details
3. Include:
   - Description of vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

## Response Timeline

- **Acknowledgment**: Within 48 hours
- **Assessment**: Within 1 week
- **Fix Released**: As soon as possible
- **Public Disclosure**: After fix is released

## Security Measures

- Extension signature verification
- Sandboxed extension loading
- API keys stored in Android Keystore-backed `EncryptedSharedPreferences` (AES-256-GCM values, AES-256-SIV keys)
- All network traffic over HTTPS; cleartext HTTP is blocked via `network_security_config`
- Regular dependency updates

> **Note (R-7):** A previous version of this policy stated "No cleartext credential storage".
> A one-time migration from a legacy plaintext DataStore to `EncryptedSharedPreferences`
> was completed in v0.1.0. The migration path is guarded so that the plaintext entry is
> deleted only after a confirmed write to the encrypted store. Tracker OAuth credentials
> (Kitsu, MAL, Shikimori) are empty placeholders in source code and must be injected via
> `BuildConfig` fields populated from encrypted CI/CD secrets before release.

## API Key Storage

All user-supplied API keys (e.g. Gemini) are stored in
[`EncryptedSharedPreferences`](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
backed by the Android Keystore.

| Property | Value |
|---|---|
| Value encryption | AES-256-GCM |
| Key encryption | AES-256-SIV |
| Key material location | Android Keystore (never leaves the Keystore) |
| Backup exclusion | `android:allowBackup="false"` on the preference file |
| In-memory caching | Disabled — keys are not held in JVM memory between reads |

### Key Rotation

Key rotation is supported and recommended every **90 days**:

- `SecureApiKeyDataStore.isKeyRotationRecommended(provider)` returns `true` when the stored
  key for a given provider is older than 90 days (configurable via `maxAgeDays`).
- `SecureApiKeyDataStore.rotateApiKey(provider, newApiKey)` replaces the current key and
  resets the rotation clock.
- `EncryptedApiKeyStore.isGeminiKeyRotationRecommended()` provides the same check for the
  Gemini-specific store used by `AiPreferences`.

The epoch-millisecond timestamps used for rotation age checks are stored in the **plaintext**
DataStore file (timestamps are not sensitive).

To rotate a key in-app:
1. Navigate to **Settings → AI Settings**.
2. Enter the new API key and tap **Save**.
3. The old key is securely overwritten and the age clock resets.

### Biometric Authentication

For future hardening, biometric (fingerprint / face unlock) authentication before revealing
or changing API keys is recommended.  The Android `BiometricPrompt` API provides the
scaffolding for this.  A future release may gate key-management operations behind a
biometric challenge — see [BiometricPrompt documentation](https://developer.android.com/training/sign-in/biometric-auth).

## Scope

This security policy covers:
- Otaku Reader Android app
- Extension loading system
- Cloud sync features
- Network communication

For a comprehensive technical audit, see [`docs/security/api-key-security.md`](docs/security/api-key-security.md).

