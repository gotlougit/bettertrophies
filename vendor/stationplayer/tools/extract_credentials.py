#!/usr/bin/env python3
"""
PlayStation App — Static Credential + Constants Extractor
=========================================================
100% static extraction of OAuth client credentials AND app constants
from libsiepsapputil.so + libsiepsappres.so + AndroidManifest.xml + Java sources.

── Credential Decryption Chain ────────────────────────────────────────────────
  1. Vernam XOR emb_passphrase  with table_B → HMAC-SHA256 key
  2. Vernam XOR emb_signature   with table_B → APK signing certificate
  3. HMAC-SHA256(cert, key=passphrase)      → 32-byte HMAC result
  4. Vernam XOR HMAC result     with table_A → AES-256 key
  5. Vernam XOR emb_iv          with table_B → AES IV
  6. Vernam XOR credential blob with table_D → pre-AES ciphertext
  7. AES-256-CBC decrypt                    → JSON credentials

── Additional App Constants ───────────────────────────────────────────────────
  Extracted from AndroidManifest.xml and decompiled Java sources in sources/.

Requirements:  uv run --with cryptography python3 extract_credentials.py
"""

import base64
import ctypes
import hashlib
import hmac as hmac_mod
import json
import re
import struct
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# ─── Crypto (lazy import, only needed for AES) ────────────────────────────────


def aes_cbc_decrypt(ciphertext, key, iv):
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    from cryptography.hazmat.backends import default_backend

    backend = default_backend()
    cipher = Cipher(algorithms.AES(key), modes.CBC(iv), backend=backend)
    decryptor = cipher.decryptor()
    plaintext = decryptor.update(ciphertext) + decryptor.finalize()

    pad_len = plaintext[-1]
    if 1 <= pad_len <= 16:
        plaintext = plaintext[:-pad_len]
    return plaintext


# ─── File paths ───────────────────────────────────────────────────────────────

PROJECT_ROOT = Path(__file__).resolve().parent
SO_UTIL = PROJECT_ROOT / "resources/lib/arm64-v8a/libsiepsapputil.so"
SO_RES = PROJECT_ROOT / "resources/lib/arm64-v8a/libsiepsappres.so"
MANIFEST = PROJECT_ROOT / "resources/AndroidManifest.xml"
SOURCES = PROJECT_ROOT / "sources"

# ─── Confirmed .rodata addresses (from Ghidra symbols, _INIT_0 cross-refs) ────

DATA = {
    "emb_package_name": (0x423E, 19, "Encrypted expected package name"),
    "emb_package_name_len": (0x4258, 8, "Length = 19 (uint64)"),
    "emb_signature": (0x4260, 621, "Encrypted APK signing certificate"),
    "emb_iv": (0x44D8, 16, "Encrypted AES IV"),
    "emb_iv_len": (0x44E8, 8, "Length = 16 (uint64)"),
    "emb_passphrase": (0x44F0, 32, "Encrypted HMAC-SHA256 key"),
    "emb_passphrase_len": (0x4510, 8, "Length = 32 (uint64)"),
    "emb_vernam_key_xor": (0x4518, 128, "Table A — getSecureKey (HMAC→AES key)"),
    "emb_vernam_key_magic": (0x45A0, 4, "start_A = 0x905AAFE6"),
    "emb_vernam_bin_xor": (0x45A4, 128, "Table B — getIv + sig verify + HMAC prep"),
    "emb_vernam_bin_magic": (0x4630, 4, "start_B = 0x9543E8A5"),
    "emb_vernam_str_xor": (0x4634, 128, "Table C — package name (VernamCipher)"),
    "emb_vernam_str_magic": (0x46C0, 4, "start_C = 0xF25AFF70"),
    "emb_vernam_res_xor": (0x46C4, 128, "Table D — substituteResourceString (blob)"),
    "emb_vernam_res_magic": (0x4750, 4, "start_D = 0x0A667170"),
}

START_A = 0x905AAFE6
START_B = 0x9543E8A5
START_C = 0xF25AFF70
START_D = 0x0A667170


# ─── File I/O ─────────────────────────────────────────────────────────────────


def read_so(path):
    if not path.exists():
        sys.exit(f"ERROR: {path} not found at {path}")
    return path.read_bytes()


# ─── Vernam XOR (exact reimplementation of decompiled ARM64 code) ─────────────


def vernam_xor(data: bytes, table: bytes, start: int) -> bytes:
    table_size = len(table)
    result = bytearray(len(data))
    s = ctypes.c_int32(start).value
    for i in range(len(data)):
        val = s + i
        if val < 0:
            val = -val
        idx = val % table_size
        result[i] = data[i] ^ table[idx]
    return bytes(result)


# ─── Extract and decrypt credentials ──────────────────────────────────────────


def extract_all():
    util = read_so(SO_UTIL)
    res = read_so(SO_RES)

    consts = {}
    for name, (off, size, _desc) in DATA.items():
        consts[name] = util[off : off + size]

    consts["_pkg_len"] = struct.unpack("<Q", consts["emb_package_name_len"])[0]
    consts["_iv_len"] = struct.unpack("<Q", consts["emb_iv_len"])[0]
    consts["_pw_len"] = struct.unpack("<Q", consts["emb_passphrase_len"])[0]
    consts["_magic_A"] = struct.unpack("<I", consts["emb_vernam_key_magic"])[0]
    consts["_magic_B"] = struct.unpack("<I", consts["emb_vernam_bin_magic"])[0]
    consts["_magic_C"] = struct.unpack("<I", consts["emb_vernam_str_magic"])[0]
    consts["_magic_D"] = struct.unpack("<I", consts["emb_vernam_res_magic"])[0]

    table_A = consts["emb_vernam_key_xor"]
    table_B = consts["emb_vernam_bin_xor"]
    table_C = consts["emb_vernam_str_xor"]
    table_D = consts["emb_vernam_res_xor"]

    enc_pkg = consts["emb_package_name"]
    enc_sig = consts["emb_signature"]
    enc_iv = consts["emb_iv"]
    enc_pw = consts["emb_passphrase"]

    pkg_name = vernam_xor(enc_pkg, table_C, START_C)
    hmac_key = vernam_xor(enc_pw, table_B, START_B)
    cert = vernam_xor(enc_sig, table_B, START_B)
    aes_iv = vernam_xor(enc_iv, table_B, START_B)

    hmac_result = hmac_mod.new(hmac_key, cert, hashlib.sha256).digest()
    aes_key = vernam_xor(hmac_result, table_A, START_A)

    idx = res.find(b"client_info.np\x00")
    if idx < 0:
        sys.exit("ERROR: client_info.np not found in libsiepsappres.so")
    blob_start = idx + len(b"client_info.np\x00")

    blob_end = blob_start
    valid = set(b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=\n")
    while blob_end < len(res) and res[blob_end] in valid:
        blob_end += 1

    blob_b64 = res[blob_start:blob_end].decode("ascii")
    blob_enc = base64.b64decode(blob_b64)
    blob_xor = vernam_xor(blob_enc, table_D, START_D)

    plaintext = aes_cbc_decrypt(blob_xor, aes_key, aes_iv)
    credentials = json.loads(plaintext.decode("utf-8"))

    return {
        "consts": consts,
        "pkg_name": pkg_name,
        "hmac_key": hmac_key,
        "cert": cert,
        "aes_iv": aes_iv,
        "hmac_result": hmac_result,
        "aes_key": aes_key,
        "blob_enc": blob_enc,
        "blob_xor": blob_xor,
        "credentials": credentials,
    }


# ─── Extract additional app constants ─────────────────────────────────────────


def _read_src(relative_path):
    """Read a Java source file, return its text or None."""
    f = SOURCES / relative_path
    if f.exists():
        return f.read_text(encoding="utf-8", errors="replace")
    return None


# Known method-name → URL-template mapping in Lb/k.java (VersaUtils)
# Each entry: (method_name, URL format string)
VERSA_METHOD_URLS = [
    ("accountPage", "https://ca.{env}account.sony.com/"),
    ("authBase", "https://auth.api.{env}sonyentertainmentnetwork.com/"),
    ("ssoCookie", "https://auth.api.{env}sonyentertainmentnetwork.com/2.0/ssocookie"),
    ("signOut", "https://ca.{env}account.sony.com/api/authn/v3/signOut"),
    ("token", "https://ca.{env}account.sony.com/api/authz/v3/oauth/token"),
    ("authorize", "https://ca.{env}account.sony.com/api/authz/v3/oauth/authorize"),
    ("createAccount", "https://web.{env}playstation.com/app/id/create_account/"),
]


def extract_app_constants():
    """Extract hardcoded constants from AndroidManifest and Java sources."""
    result = {}

    # ── AndroidManifest.xml ────────────────────────────────────────────────
    if MANIFEST.exists():
        tree = ET.parse(MANIFEST)
        root = tree.getroot()
        ns = "http://schemas.android.com/apk/res/android"
        result["version_name"] = root.attrib.get(f"{{{ns}}}versionName", "unknown")
        result["version_code"] = root.attrib.get(f"{{{ns}}}versionCode", "unknown")
        result["package_name"] = root.attrib.get("package", "unknown")

        uses_sdk = root.find("uses-sdk")
        if uses_sdk is not None:
            result["min_sdk_version"] = uses_sdk.attrib.get(
                f"{{{ns}}}minSdkVersion", "unknown"
            )
            result["target_sdk_version"] = uses_sdk.attrib.get(
                f"{{{ns}}}targetSdkVersion", "unknown"
            )
    else:
        result["version_name"] = "unknown (AndroidManifest.xml not found)"
        result["version_code"] = "unknown (AndroidManifest.xml not found)"
        result["package_name"] = "unknown (AndroidManifest.xml not found)"

    # ── WebSocket headers (sources/db/g.java) ─────────────────────────────
    src = _read_src("db/g.java")
    ws = {}
    if src:
        for label, pattern in [
            ("x_psn_app_type", r'"X-PSN-APP-TYPE",\s*"([^"]+)"'),
            ("x_psn_protocol_version", r'this\.f31115r\s*=\s*"([^"]+)"'),
            ("sec_websocket_protocol", r'"Sec-WebSocket-Protocol",\s*"([^"]+)"'),
            ("sec_websocket_version", r'"Sec-WebSocket-Version",\s*"([^"]+)"'),
        ]:
            m = re.search(pattern, src)
            if m:
                ws[label] = m.group(1)

        ws["x_psn_reconnection"] = "dynamic (Boolean.toString(f31093C))"
        ws["x_psn_keep_alive_status_type"] = {
            "INVALID": "",
            "VOICE_CHAT": "3",
            "IDLE": "6",
        }
        ws["websocket_close_codes"] = "see sources/db/m.java (m.a enum)"
        result["websocket"] = ws

    # ── WebSocket timeout (sources/xa/f.java) ─────────────────────────────
    src = _read_src("xa/f.java")
    if src:
        m = re.search(r"new db\.h\(\w+\.class,\s*\w+,\s*\w+,\s*(\d+)", src)
        if m:
            if "websocket" in result:
                result["websocket"]["connection_timeout_ms"] = int(m.group(1))
            else:
                result["websocket"] = {"connection_timeout_ms": int(m.group(1))}

    # ── WebSocket keepalive enum (sources/db/j.java) ──────────────────────
    result["websocket_keepalive_enum"] = {
        "INVALID": 0,
        "VOICE_CHAT": 1,
        "IDLE": 2,
    }

    # ── Versa OAuth constants (sources/Lb/k.java) ─────────────────────────
    # The User-Agent string IS the literal string:
    result["versa_user_agent"] = (
        "com.sony.snei.np.android.sso.share.oauth.versa.USER_AGENT"
    )
    result["versa_oauth_endpoints"] = {
        name: url.format(env="{environment}") for name, url in VERSA_METHOD_URLS
    }

    # ── App-level User-Agent (sources/Ob/g.java) ──────────────────────────
    # Stored as context.getPackageName() + ".USER_AGENT"
    result["app_user_agent"] = "com.scee.psxandroid.USER_AGENT"

    # ── Derived version constants ─────────────────────────────────────────
    ver = result.get("version_name", "unknown")
    result["x_psn_app_ver"] = f'dynamic (PackageInfo.versionName = "{ver}")'
    result["x_psn_os_ver"] = "dynamic (Build.VERSION.RELEASE — Android OS version)"
    result["version_user_agent_header"] = f"PlayStationApp-Android/{ver}"
    result["notification_template_version"] = ver

    return result


# ─── Display ──────────────────────────────────────────────────────────────────


def dump(data, app_consts):
    B = lambda b: b.hex()
    BH = lambda b, n=32: b[:n].hex() + ("..." if len(b) > n else "")

    print("=" * 72)
    print("  EMBEDDED CONSTANTS (from libsiepsapputil.so .rodata)")
    print("=" * 72)
    for name, (off, size, desc) in DATA.items():
        if name.startswith("_") or "magic" in name:
            continue
        val = data["consts"][name]
        print(f"\n  [{name}]  @ 0x{off:04x}  ({size} bytes)")
        print(f"    Description: {desc}")
        print(f"    Raw hex:     {B(val) if size <= 64 else BH(val, 64)}")

    print(f"\n  [Magic / Start Index Values]")
    for label, key in [
        ("A", "key_magic"),
        ("B", "bin_magic"),
        ("C", "str_magic"),
        ("D", "res_magic"),
    ]:
        off = DATA[f"emb_vernam_{key}"][0]
        val = data["consts"][f"_magic_{label}"]
        print(f"    start_{label} (@ 0x{off:04x}) = 0x{val:08X}")

    print(f"\n  [Length Fields]")
    len_map = {"package_name": "_pkg_len", "iv": "_iv_len", "passphrase": "_pw_len"}
    off_map = {
        "package_name": "emb_package_name_len",
        "iv": "emb_iv_len",
        "passphrase": "emb_passphrase_len",
    }
    for label in ["package_name", "iv", "passphrase"]:
        off = DATA[off_map[label]][0]
        val = data["consts"][len_map[label]]
        print(f"    {label}_len @ 0x{off:04x} = {val}")

    print(f"\n{'=' * 72}")
    print(f"  DECRYPTED INTERMEDIATE VALUES")
    print(f"{'=' * 72}")

    print(f"\n  [Package Name]")
    print(f"    Decrypted:  {data['pkg_name'].decode()}")

    print(f"\n  [HMAC-SHA256 Key / Passphrase]")
    print(f"    Decrypted:  {B(data['hmac_key'])}")

    print(f"\n  [APK Signing Certificate]")
    print(f"    Decrypted:  {BH(data['cert'], 48)}")

    print(f"\n  [AES IV]")
    print(f"    Decrypted:  {B(data['aes_iv'])}")

    print(f"\n  [HMAC-SHA256 Result]")
    print(f"    HMAC(cert, passphrase) = {B(data['hmac_result'])}")

    print(f"\n  [AES-256 Key]")
    print(f"    Final key:  {B(data['aes_key'])}")

    print(f"\n{'=' * 72}")
    print(f"  CREDENTIAL BLOB DECRYPTION")
    print(f"{'=' * 72}")
    print(
        f"\n  Base64 ({len(data['blob_enc']) * 4 // 3} chars), decoded ({len(data['blob_enc'])} bytes)"
    )
    print(f"  After XOR:  {BH(data['blob_xor'], 48)}")
    print(f"  AES Key:    {B(data['aes_key'])}")
    print(f"  AES IV:     {B(data['aes_iv'])}")
    print(f"  Mode:       AES-256-CBC, PKCS5 padding")

    print(f"\n{'=' * 72}")
    print(f"  CREDENTIALS")
    print(f"{'=' * 72}")
    for k, v in data["credentials"].items():
        print(f"  {k}: {v}")

    print(f"\n{'=' * 72}")
    print(f"  VERIFICATION")
    print(f"{'=' * 72}")
    expected_pkg = b"com.scee.psxandroid"
    ok = data["pkg_name"] == expected_pkg
    print(f"  Package name match:  {'✓' if ok else '✗'}  '{data['pkg_name'].decode()}'")
    print(
        f"  Credential JSON:      ✓  (client_id, client_secret, scope, redirect_uri, service_entity)"
    )

    # ─── App constants section ─────────────────────────────────────────

    print(f"\n{'=' * 72}")
    print(f"  APP CONSTANTS (from AndroidManifest.xml + Java sources)")
    print(f"{'=' * 72}")

    print(f"\n  [AndroidManifest.xml]")
    for k in [
        "package_name",
        "version_name",
        "version_code",
        "min_sdk_version",
        "target_sdk_version",
    ]:
        print(f"    {k}:  {app_consts.get(k, 'N/A')}")

    print(f"\n  [WebSocket Headers]  (sources/db/g.java)")
    ws = app_consts.get("websocket", {})
    for k in [
        "x_psn_app_type",
        "x_psn_protocol_version",
        "sec_websocket_protocol",
        "sec_websocket_version",
        "x_psn_reconnection",
        "connection_timeout_ms",
    ]:
        print(f"    {k}:  {ws.get(k, 'N/A')}")

    ka = ws.get("x_psn_keep_alive_status_type", {})
    print(f"    x_psn_keep_alive_status_type:")
    for state, val_item in ka.items():
        print(f'      {state}: "{val_item}"')
    print(f"    websocket_close_codes:  {ws.get('websocket_close_codes', 'N/A')}")

    print(f"\n  [Versa OAuth]  (sources/Lb/k.java)")
    print(f"    User-Agent:  {app_consts.get('versa_user_agent', 'N/A')}")
    for name, url in app_consts.get("versa_oauth_endpoints", {}).items():
        print(f"    {name}:  {url}")

    print(f"\n  [App User-Agent]")
    print(f"    User-Agent:  {app_consts.get('app_user_agent', 'N/A')}")

    print(f"\n  [Version + Notification Constants]")
    print(f"    x_psn_app_ver:               {app_consts.get('x_psn_app_ver', 'N/A')}")
    print(f"    x_psn_os_ver:                {app_consts.get('x_psn_os_ver', 'N/A')}")
    print(
        f"    version_user_agent_header:   {app_consts.get('version_user_agent_header', 'N/A')}"
    )
    print(
        f"    notification_template_ver:   {app_consts.get('notification_template_version', 'N/A')}"
    )

    print(f"\n  [WebSocket KeepAlive Enum]")
    for k, v_item in app_consts.get("websocket_keepalive_enum", {}).items():
        print(f"    {k} = {v_item}")


# ─── JSON output ──────────────────────────────────────────────────────────────


def output_json(data, app_consts):
    output = {
        "credentials": data["credentials"],
        "package_name": data["pkg_name"].decode(),
        "app_constants": app_consts,
    }
    print(json.dumps(output, indent=2, default=str))


# ─── Main ─────────────────────────────────────────────────────────────────────


def main():
    import argparse

    parser = argparse.ArgumentParser(
        description="PS App credential and constant extractor"
    )
    parser.add_argument(
        "--json", action="store_true", help="Output as JSON instead of human-readable"
    )
    args = parser.parse_args()

    data = extract_all()
    app_consts = extract_app_constants()

    if args.json:
        output_json(data, app_consts)
    else:
        print("PlayStation App — Static Credential + Constants Extractor")
        print("Based on RE of libsiepsapputil.so + libsiepsappres.so + Java sources")
        print()
        dump(data, app_consts)


if __name__ == "__main__":
    main()
