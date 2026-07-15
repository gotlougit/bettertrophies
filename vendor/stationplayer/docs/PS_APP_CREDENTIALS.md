# PlayStation App — Magic Values & Credential Loading

## 1. Overview

The PlayStation App is a **React Native** Android app bundled with **Hermes bytecode** (v96). It calls Sony's PlayStation Network (PSN) APIs for login, push notifications, game library, messaging, and more. To call these APIs, it needs **OAuth client credentials** (client ID, client secret, scope, redirect URI) — but Sony does not expose these publicly.

### The Challenge

The credentials are **never stored in plaintext** anywhere in the APK. They are:

- 🔒 Encrypted at rest inside a native library (`libsiepsappres.so`)
- 🔑 Decrypted using a **multi-layer key derivation chain** spread across two `.so` files
- 🛡️ Tied to the APK's **signing certificate** via an integrity check at runtime

This document explains how the credentials are stored, derived, and ultimately recovered.

---

## 2. Runtime Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  React Native JS (Hermes bytecode)                              │
│  NativeModules.ClientInfo.getClientInfo("client_id", "...")     │
└──────────────────────────┬──────────────────────────────────────┘
                           │ JS→Java bridge
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  ClientInfoReactModule.java                                     │
│  → com.sony.sie.metropolis.credential.a (ClientInfo)            │
│  → SecureResource.get("client_info.np")                         │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  SecureKey.java          (loads libsiepsapputil.so)             │
│  1. initNative(Context)  → package name + cert check            │
│  2. getKeyNative()       → 32-byte AES-256 key                  │
│  3. getIvNative()        → 16-byte AES IV                       │
│  4. substituteNative()   → Vernam XOR the encrypted blob        │
└──────────────────────────┬──────────────────────────────────────┘
                           │ JNI
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  libsiepsapputil.so      (+ libsiepsappres.so for blob storage) │
│  C++ native layer with embedded .rodata constants                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. The Decryption Chain (End-to-End)

The full derivation uses **4 distinct Vernam XOR operations**, **HMAC-SHA256**, and **AES-256-CBC**:

```
┌────────────────────────────────────────────────────────────────────┐
│  STATIC DATA (in libsiepsapputil.so .rodata)                       │
│                                                                    │
│  emb_passphrase (32B enc) ──┐                                      │
│  emb_signature (621B enc) ──┤                                      │
│  emb_iv (16B enc) ─────────┤                                      │
│  emb_package_name (19B enc)─┘                                      │
│                                                                    │
│  emb_vernam_key_xor (128B) ── table_A                              │
│  emb_vernam_bin_xor (128B) ── table_B                              │
│  emb_vernam_str_xor (128B) ── table_C                              │
│  emb_vernam_res_xor (128B) ── table_D                              │
└────────────────────────────────────────────────────────────────────┘
        │                                    │
        ▼                                    ▼
┌───────────────────┐              ┌───────────────────┐
│  _INIT_0 (static  │              │  Start indices    │
│   initializer)    │              │  (magic values):  │
│                   │              │  A = 0x905AAFE6   │
│  Populates        │              │  B = 0x9543E8A5   │
│  APP_ATTRIBUTE    │              │  C = 0xF25AFF70   │
│  struct with      │              │  D = 0x0A667170   │
│  pointers to      │              └───────────────────┘
│  .rodata.         │
└───────────────────┘

        ▼
┌────────────────────────────────────────────────────────────────────┐
│  STEP 1: Vernam XOR (table_C, start_C)                             │
│  emb_package_name → "com.scee.psxandroid"                          │
│  (verified against caller's package name at runtime)               │
├────────────────────────────────────────────────────────────────────┤
│  STEP 2: Vernam XOR (table_B, start_B)                             │
│  emb_signature → APK signing certificate (X.509 DER, 621 bytes)   │
│  emb_passphrase → HMAC-SHA256 key (32 bytes)                      │
│  emb_iv         → raw IV bytes (16 bytes)                          │
├────────────────────────────────────────────────────────────────────┤
│  STEP 3: Vernam XOR (table_B, start_B)                             │
│  raw_iv → AES IV (16 bytes)                                       │
│  (Same XOR is applied again — effectively xor(n, table_B, start_B) │
│   already done in STEP 2; getIv re-applies the same transform)    │
├────────────────────────────────────────────────────────────────────┤
│  STEP 4: HMAC-SHA256                                               │
│  HMAC-SHA256(key=decrypted_passphrase, message=certificate)        │
│  → 32-byte HMAC result                                             │
├────────────────────────────────────────────────────────────────────┤
│  STEP 5: Vernam XOR (table_A, start_A)                             │
│  HMAC result → AES-256 key (32 bytes)                             │
├────────────────────────────────────────────────────────────────────┤
│  STEP 6: Vernam XOR (table_D, start_D)                             │
│  Encrypted blob from libsiepsappres.so → pre-AES ciphertext        │
├────────────────────────────────────────────────────────────────────┤
│  STEP 7: AES-256-CBC Decrypt                                       │
│  Key = step 5 result    IV = step 3 result                         │
│  pre-AES ciphertext → plaintext JSON with OAuth credentials        │
└────────────────────────────────────────────────────────────────────┘
```

---

## 4. Embedded Constants (libsiepsapputil.so .rodata)

All decryption material lives in the `.rodata` segment at known file offsets:

| Symbol | Offset | Size | Purpose |
|--------|--------|------|---------|
| `emb_package_name` | `0x423e` | 19 B | Encrypted expected package name |
| `emb_signature` | `0x4260` | 621 B | Encrypted APK signing certificate (X.509 DER) |
| `emb_iv` | `0x44d8` | 16 B | Encrypted AES IV |
| `emb_passphrase` | `0x44f0` | 32 B | Encrypted HMAC-SHA256 key |
| `emb_vernam_key_xor` | `0x4518` | 128 B | **Table A** — XOR for HMAC→AES key |
| `emb_vernam_bin_xor` | `0x45a4` | 128 B | **Table B** — XOR for IV, signature, passphrase |
| `emb_vernam_str_xor` | `0x4634` | 128 B | **Table C** — XOR for package name |
| `emb_vernam_res_xor` | `0x46c4` | 128 B | **Table D** — XOR for credential blob |

### Start Indices (Magic Values)

Each Vernam XOR table has a corresponding 32-bit unsigned "start index" (labeled as "magic" in the binary). The XOR operation starts at `start_idx` and increments for each subsequent byte:

| Table | Start Index | Value |
|-------|-------------|-------|
| A (`emb_vernam_key_magic`) | `0x45a0` | `0x905AAFE6` |
| B (`emb_vernam_bin_magic`) | `0x4630` | `0x9543E8A5` |
| C (`emb_vernam_str_magic`) | `0x46c0` | `0xF25AFF70` |
| D (`emb_vernam_res_magic`) | `0x4750` | `0x0A667170` |

### Vernam XOR Algorithm

The XOR is NOT a simple repeat of the table. It uses a sliding window starting at `start_idx`:

```python
def vernam_xor(data: bytes, table: bytes, start: int) -> bytes:
    """Exact reimplementation of the decompiled ARM64 loop."""
    table_size = len(table)
    result = bytearray(len(data))
    s = ctypes.c_int32(start).value  # sign-extend to int32
    for i in range(len(data)):
        val = s + i
        if val < 0:
            val = -val              # absolute value
        idx = val % table_size
        result[i] = data[i] ^ table[idx]
    return bytes(result)
```

Key details:
- The index is a `uint32_t` that **increments monotonically** (wraps after ~4 billion)
- The abs() on the signed interpretation prevents negative modulo results
- Each table is **128 bytes** and used as a circular XOR pad

---

## 5. The SecurityAttribute Struct & _INIT_0

The static initializer `_INIT_0` populates a global `APP_ATTRIBUTE` struct (in `.bss`, zero-initialized at load) with pointers into `.rodata`. This struct drives all subsequent decryption:

```
APP_ATTRIBUTE + 0x00 → emb_package_name       (encrypted, 19 bytes)
APP_ATTRIBUTE + 0x08 = 19                     (pkg_len)
APP_ATTRIBUTE + 0x10 → emb_signature           (encrypted, 621 bytes)
APP_ATTRIBUTE + 0x18 = 621                    (sig_len)
APP_ATTRIBUTE + 0x20 → emb_iv                 (encrypted, 16 bytes)
APP_ATTRIBUTE + 0x28 = 16                     (iv_len)
APP_ATTRIBUTE + 0x30 → emb_passphrase          (encrypted, 32 bytes)
APP_ATTRIBUTE + 0x38 = 32                     (passphrase_len)
───────────────────────────────────────────────────
APP_ATTRIBUTE + 0x40 → emb_vernam_key_xor     (table_A, 128 bytes)
APP_ATTRIBUTE + 0x48 = 128                    (table_A size)
APP_ATTRIBUTE + 0x50 = 0x905AAFE6             (start_A)
───────────────────────────────────────────────────
APP_ATTRIBUTE + 0x58 → emb_vernam_bin_xor     (table_B, 128 bytes)
APP_ATTRIBUTE + 0x60 = 128                    (table_B size)
APP_ATTRIBUTE + 0x68 = 0x9543E8A5             (start_B)
───────────────────────────────────────────────────
APP_ATTRIBUTE + 0x70 → emb_vernam_str_xor     (table_C, 128 bytes)
APP_ATTRIBUTE + 0x78 = 128                    (table_C size)
APP_ATTRIBUTE + 0x80 = 0xF25AFF70             (start_C)
───────────────────────────────────────────────────
APP_ATTRIBUTE + 0x88 → emb_vernam_res_xor     (table_D, 128 bytes)
APP_ATTRIBUTE + 0x90 = 128                    (table_D size)
APP_ATTRIBUTE + 0x98 = 0x0A667170             (start_D)
```

### How initNative Works

At runtime, `SecureKey.initNative(Context)` triggers the C++ function `newNativeClientWithValidation`:

1. **Decrypt package name** using Vernam XOR with table_C + start_C
2. **Check caller's package name** against decrypted name (must match `com.scee.psxandroid`)
3. **Extract calling APK's signing certificate** via JNI (`PackageInfo.signatures[0].toByteArray()`)
4. **Decrypt emb_signature** using Vernam XOR with table_B + start_B
5. **Compare certificates** — must match or init fails (returns 0)
6. **Compute HMAC-SHA256** of the certificate using decrypted emb_passphrase as key
7. **Create NativeClient** struct storing the 32-byte HMAC result
8. **Return handle** to Java as an `int` (index into NativeClientManager)

This means the **original Sony APK signing certificate** is recoverable from the binary: it's `vernam_xor(emb_signature, table_B, start_B)`.

---

## 6. Credential Blob (libsiepsappres.so)

The encrypted credentials are stored as a **base64 string** identified by the ASCII key `client_info.np` at a variable offset in `libsiepsappres.so`.

**After base64 decode**: 240 bytes of ciphertext

**After Vernam XOR (table_D, start_D)**: Pre-AES ciphertext

**After AES-256-CBC decrypt**: JSON with these fields:

| Field | Description |
|-------|-------------|
| `client_id` | OAuth 2.0 client ID (UUIDv4) |
| `client_secret` | OAuth 2.0 client secret |
| `scope` | Space-separated OAuth scope string (includes `psn:accessToken:std:auth` and others) |
| `redirect_uri` | OAuth redirect URI for the app |
| `service_entity` | Service entity identifier |

---

## 7. App-Level Constants

Beyond OAuth credentials, the APK contains several other hardcoded "magic" values required for API calls.

### 7.1 AndroidManifest.xml

| Constant | Value |
|----------|-------|
| Package name | `com.scee.psxandroid` |
| Version name | `26.4.0` |
| Version code | `26040002` |
| Min SDK | 26 |
| Target SDK | 35 |

### 7.2 WebSocket Headers (for Push Notifications)

The WebSocket connection to Sony's push notification server requires these headers:

| Header | Value |
|--------|-------|
| `Sec-WebSocket-Protocol` | `np-pushpacket` |
| `Sec-WebSocket-Version` | `13` |
| `X-PSN-PROTOCOL-VERSION` | `3.0` |
| `X-PSN-APP-TYPE` | `MOBILE_APP.PSAPP` |
| `X-PSN-APP-VER` | Dynamic (from `PackageInfo.versionName`) |
| `X-PSN-OS-VER` | Dynamic (from `Build.VERSION.RELEASE`) |
| `X-PSN-RECONNECTION` | Dynamic (`Boolean.toString(...)`) |
| `X-PSN-KEEP-ALIVE-STATUS-TYPE` | `""` (INVALID), `"3"` (VOICE_CHAT), `"6"` (IDLE) |
| `Authorization` | `Bearer {access_token}` |

### 7.3 WebSocket Close Codes

Close codes used by the WebSocket to signal disconnection reasons:

| Code | Meaning |
|------|---------|
| `0x82000001` | UNSPECIFIED |
| `0x82000003` | PONG_NOT_REACHED_BEFORE_TIMEOUT |
| `0x82000004` | MOBILE_APP_CLOSE_CONNECTION |

### 7.4 WebSocket KeepAlive State Machine

The WebSocket supports a keep-alive state enum (`db.j`):

| State | Value | Header sent |
|-------|-------|--------------|
| `INVALID` | 0 | `""` (empty) |
| `VOICE_CHAT` | 1 | `"3"` |
| `IDLE` | 2 | `"6"` |

Default keep-alive intervals (overridable by server):
- `clientKeepAliveInterval`: 10,000 ms
- `clientKeepAliveTimeout`: 20,000 ms
- `serverPresenceTimeout`: 30,000 ms

### 7.5 Versa OAuth Endpoints (Authentication)

All OAuth flows use endpoints on Sony's `sonyentertainmentnetwork.com` and `account.sony.com` domains, with an environment prefix:

| Method | URL Template |
|--------|-------------|
| `accountPage` | `https://ca.{env}account.sony.com/` |
| `authBase` | `https://auth.api.{env}sonyentertainmentnetwork.com/` |
| `ssoCookie` | `https://auth.api.{env}sonyentertainmentnetwork.com/2.0/ssocookie` |
| `signOut` | `https://ca.{env}account.sony.com/api/authn/v3/signOut` |
| `token` | `https://ca.{env}account.sony.com/api/authz/v3/oauth/token` |
| `authorize` | `https://ca.{env}account.sony.com/api/authz/v3/oauth/authorize` |
| `createAccount` | `https://web.{env}playstation.com/app/id/create_account/` |

The `{env}` variable is typically empty for production (`np` results in `""`, producing e.g. `auth.api.sonyentertainmentnetwork.com`).

### 7.6 Push Notification Server Address

The push notification WebSocket address is resolved dynamically from:
```
https://mobile-pushcl.{env}.communication.playstation.net
```
The `/np/serveraddr` endpoint returns a `fqdn` used to construct:
```
wss://{fqdn}/np/pushNotification
```

### 7.7 User-Agent Strings

Two User-Agent values are used:

| Context | Value |
|---------|-------|
| OAuth/Versa API calls | `com.sony.snei.np.android.sso.share.oauth.versa.USER_AGENT` (literal string) |
| App-level HTTP | `com.scee.psxandroid.USER_AGENT` (literal string) |
| HTTP header format | `PlayStationApp-Android/{versionName}` |

These are **identity string constants** — not dynamic. They appear to be recognized by Sony's servers as valid client identifiers.

---

## 8. Static Extraction (No Emulator Needed)

The `extract_credentials.py` script performs **100% static extraction** — no emulator, no Frida, no running APK. It requires only the two `.so` files and the `cryptography` Python package.

### Requirements

```bash
uv run --with cryptography python3 extract_credentials.py
```

### What It Outputs

- **Decrypted OAuth credentials**: `client_id`, `client_secret`, `scope`, `redirect_uri`, `service_entity`
- **All crypto intermediates**: AES key, AES IV, HMAC key, signing certificate
- **All app constants**: WebSocket headers, API endpoints, User-Agent strings, version info
- **Verification**: Confirms decrypted package name matches `com.scee.psxandroid`

### Why This Works Statically

The key insight is that while the **original** runtime design required the live APK's signing certificate, the certificate itself is **stored encrypted** at `emb_signature` in the binary. Since the encryption for the certificate uses the same static Vernam XOR as everything else (table_B with start_B), the certificate can be recovered off-device. Once you have the certificate, the full HMAC→AES chain is statically soluble.

---

## 9. Key Files Reference

| File | Role |
|------|------|
| `libsepsapputil.so` | All embedded constants, Vernam XOR tables, C++ decryption logic |
| `libsepsappres.so` | Stores the encrypted `client_info.np` base64 blob |
| `SecureKey.java` | Java bridge: loads `.so`, exposes `initNative/getKeyNative/getIvNative/substituteNative` |
| `ClientInfoReactModule.java` | React Native bridge: exposes `getClientInfo()` to JS |
| `ClientInfo.java` (`com.sony.sie.metropolis.credential.a`) | Orchestrates credential retrieval per-field |
| `WebSocket.java` (`db/g.java`) | Push notification WebSocket with all header constants |
| `VersaUtils.java` (`Lb/k.java`) | OAuth endpoint URL builders and auth utilities |
| `AndroidManifest.xml` | Package name, version, SDK targets |

---

## 10. Summary Diagram

```
┌───────────────────────────────────────────────────────────────┐
│                     libsiepsapputil.so                        │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  .rodata: 4 Vernam tables (128B each)                   │ │
│  │           4 magic start indices                         │ │
│  │           encrypted: package name, cert, IV, passphrase  │ │
│  │  .bss:    APP_ATTRIBUTE (populated by _INIT_0)          │ │
│  │  .text:   initNative, getKey, getIv, substituteNative   │ │
│  └─────────────────────────────────────────────────────────┘ │
│                              │                                │
│    ┌─────────────────────────┼──────────────────────────┐     │
│    │  _INIT_0 sets up        │                          │     │
│    │  APP_ATTRIBUTE with     │  4 Vernam XOR paths       │     │
│    │  .rodata pointers       │  + 1 HMAC-SHA256          │     │
│    └─────────────────────────┼──────────────────────────┘     │
│                              │                                │
└──────────────────────────────┼────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                    libsiepsappres.so                          │
│  client_info.np → base64 blob → Vernam XOR → AES decrypt     │
│                                                              │
│                 → {"client_id": "...",                        │
│                    "client_secret": "...",                    │
│                    "scope": "psn:accessToken:...",            │
│                    "redirect_uri": "...",                     │
│                    "service_entity": "..."}                   │
└──────────────────────────────────────────────────────────────┘
```

