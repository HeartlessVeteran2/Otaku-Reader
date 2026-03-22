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
- API keys stored in Android Keystore-backed `EncryptedSharedPreferences` (AES-256-GCM)
- All network traffic over HTTPS
- Regular dependency updates

> **Note (R-7):** A previous version of this policy stated "No cleartext credential storage".
> A one-time migration from a legacy plaintext DataStore to `EncryptedSharedPreferences`
> was completed in v0.1.0. The migration path is guarded so that the plaintext entry is
> deleted only after a confirmed write to the encrypted store. Tracker OAuth credentials
> (Kitsu, MAL, Shikimori) are empty placeholders in source code and must be injected via
> `BuildConfig` fields populated from encrypted CI/CD secrets before release.

## Scope

This security policy covers:
- Otaku Reader Android app
- Extension loading system
- Cloud sync features
- Network communication
