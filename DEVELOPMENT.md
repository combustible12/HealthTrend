# HealthTrend verification and release

Only this repository is modified. Existing v1 preference keys, application id and historical range fields are retained. Imported images are copied into app files; credentials are excluded from Android backups.

CI runs parser and report-format unit tests, Android 35 and 36 emulators, bundled ML Kit OCR and the gallery import/confirmation flow, report/template/history persistence, symptom UI creation/reopening/activity recreation, and an actual v1-to-current `adb install -r` data-retention check. Update tests exercise encrypted token storage, invalid/foreign/non-newer APK rejection, acceptance of a real newer APK with the installed signer, and rejection of a real newer APK with a different signer. Verification artifacts record the public APK signing certificate fingerprint; no private key or password is exported. The baseline and new test APK are built on the same runner with the same test signing identity. That verifies upgrade behavior; it does not recover signing keys from previous workflow runners.

Production updates must use a persistent signing identity. GitHub Actions configuration:

- `HEALTHTREND_KEYSTORE_BASE64`: base64-encoded signing keystore
- `HEALTHTREND_STORE_PASSWORD`: keystore password
- `HEALTHTREND_KEY_ALIAS`: signing alias
- `HEALTHTREND_KEY_PASSWORD`: key password

The app validates package identity, version and signer before opening the installer. The repository is private; updates require a user-supplied token with read access to its release assets. The token is encrypted with Android Keystore and is never embedded in an APK. Only non-draft, non-prerelease GitHub releases are offered. Release notes must contain `版本代码: N` matching the signed APK versionCode.

No development artifact is a final delivery. A production release requires core tests and visual QA to pass, stable signing, and the signed APK published as a GitHub release asset. No original production signing key is present in the initial repository.
