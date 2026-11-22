# Tapmark

**Tapmark** (`com.tapmark.nfc`) is a read-only Android NFC card and tag inspector built with Kotlin and Jetpack Compose in a tactile archival paper and copper-coil design.

---

## Features

- **Contactless EMV Payment Inspection**
  - Implements `SELECT 2PAY.SYS.DDF01` (PPSE) with priority sorting and fallback AIDs, `SELECT AID`, dynamic PDOL construction, `GET PROCESSING OPTIONS` (with non-zero amount & TTQ retry fallbacks), `READ RECORD` over AFL and fallback SFIs, and `GET DATA` (`9F36` ATC, `9F13` Last Online ATC, `9F17` PIN Try Counter, `9F4D`/`DF4D` & `9F4F`/`DF4F`).
  - Parses Track 2 (`57`), PAN (`5A`), masked PAN with 1-tap reveal toggle, expiry/effective dates, Luhn check, cardholder name, and network detection.
- **Issuing Bank Detection (Privacy-Safe)**
  - Resolves issuer bank, card type, tier, and country using a curated offline BIN database (`BankBinDatabase`), bundled compressed asset lookup (`app/src/main/assets/bin_db.tsv.gz`), EMV `5F50` Issuer URL & `50`/`9F12` application label matching, and optional live 6-digit-BIN-only lookup (`BankResolver`, strictly never transmitting more than 6 PAN digits).
- **EMV Chip Transaction History**
  - Discovers chip log records via `9F4D`/`DF4D` or common SFI probing, unwraps `0x70`-wrapped cyclic log records, skips uninitialized slots, validates `9A` dates and `9F21` timestamps, decodes 6-byte BCD and 4-byte binary amounts (`9F02`/`9F03`) with currency symbols, transaction type (`9C`), merchant (`9F4E`), per-log ATC (`9F36`), and cryptogram status (`9F27`).
- **NDEF & MIFARE / Tag Inspection**
  - Safe, read-only inspection via `NfcAdapter.enableReaderMode` across `IsoDep`, `Ndef`, `NfcA`, `NfcB`, `NfcF`, `NfcV`, `MifareClassic`, and `MifareUltralight` / NTAG chips.
  - Decodes Well-Known Text (UTF-8/UTF-16), URI prefixes, Wi-Fi `application/vnd.wfa.wsc` credentials, vCard, JSON, and memory/UID hex dumps (`UID`, reversed UID, decimal UID, ATQA/SAK, historical bytes).
- **Kinetic Copper Coil & 3D Card UI**
  - Warm archival paper (`#F5F2EB`) and copper terracotta (`#C8552D`) aesthetic with an animated launch emblem (`OpeningIntroOverlay`), kinetic `CopperCoilCanvas` with traveling loop pulses and tap-to-ping ripples, interactive 3D touch-tilt `PhysicalCardView`, expandable specification ledger, and local scan history (`CardHistoryRepository`, stripping full PANs before disk persistence).

---

## Requirements

- **JDK**: JDK 17+ (project compiles with Java 17 target; tested with Android Studio JBR / OpenJDK)
- **Android SDK**: `compileSdk = 37`, `minSdk = 26`, `targetSdk = 35`
- **Gradle**: Wrapper included (`./gradlew` / `gradlew.bat`, Gradle 9.9.2, AGP 9.5.2)

---

## Build & Test

Set `JAVA_HOME` and `ANDROID_HOME` (or configure `sdk.dir` in `local.properties`):

```bash
export JAVA_HOME="/path/to/jdk-or-jbr"
export ANDROID_HOME="/path/to/Android/Sdk"

# Run JVM unit tests
./gradlew :app:testDebugUnitTest

# Build release APK
./gradlew :app:assembleRelease
```

On Windows PowerShell:

```powershell
$env:JAVA_HOME = "D:\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleRelease
```

> **Note:** Release signing in `app/build.gradle.kts` reads an optional local `keystore.properties` pointing to `keystore/tapmark-release.jks` (falling back to debug signing for open-source clones). Keystores, `keystore.properties`, and built `.apk` binaries are excluded from version control via `.gitignore`.

---

Made by Jeremiah Temporado
