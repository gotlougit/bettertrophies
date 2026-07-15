#!/usr/bin/env python3
"""Generate src/consts.rs from an extracted metadata JSON file.

Reads a JSON file produced by extract_credentials.py and writes a consts.rs
with the correct values filled in.  Constants that are NOT present in the JSON
(device-derived UA strings, API base URLs, etc.) are kept verbatim as embedded
in the template below.

Usage:
  python3 tools/generate_consts.py metadata/app_constants.json
  python3 tools/generate_consts.py x.json  -o src/consts.rs
"""

import argparse
import json
import sys
from pathlib import Path


def deep_get(d: dict, dotted_path: str):
    """Traverse nested dicts with dot-separated keys."""
    for key in dotted_path.split("."):
        d = d[key]
    return d


def template(json_data: dict) -> str:
    """Return the full contents of consts.rs with values substituted."""

    def v(path: str) -> str:
        return deep_get(json_data, path)

    def notification_template_version() -> str:
        """Convert '26.4.0' -> '26.04' (major + zero-padded minor)."""
        ver = v("app_constants.notification_template_version")
        parts = ver.split(".")
        return f"{parts[0]}.{int(parts[1]):02d}"

    return f"""\
pub(crate) const DEFAULT_CLIENT_ID: &str = "{v('credentials.client_id')}";
pub(crate) const DEFAULT_CLIENT_SECRET: &str = "{v('credentials.client_secret')}";
// TODO: redirect to proper URL where user can copy the tokens required
pub(crate) const DEFAULT_REDIRECT_URI: &str = "{v('credentials.redirect_uri')}";
pub(crate) const DEFAULT_SCOPE: &str = "{v('credentials.scope')}";
pub(crate) const DEFAULT_SERVICE_ENTITY: &str = "{v('credentials.service_entity')}";

pub(crate) const DEFAULT_AUTH_USER_AGENT: &str =
    "{v('app_constants.versa_user_agent')}";
pub(crate) const DEFAULT_OKHTTP_USER_AGENT: &str = "okhttp/4.12.0";
// TODO: use different device names in user agent
pub(crate) const DEFAULT_DALVIK_USER_AGENT: &str =
    "Dalvik/2.1.0 (Linux; U; Android 16; Mi 9T Build/BP2A.250605.031.A2)";
pub(crate) const DEFAULT_APP_VERSION: &str = "{v('app_constants.version_name')}";
pub(crate) const DEFAULT_APP_VER_HEADER: &str = "{v('app_constants.version_user_agent_header')}";
pub(crate) const DEFAULT_APP_TYPE: &str = "{v('app_constants.websocket.x_psn_app_type')}";
pub(crate) const DEFAULT_PUSH_OS_VERSION: &str = "16";
pub(crate) const DEFAULT_LANGUAGE: &str = "en-US";

pub(crate) const DEFAULT_NOTIFICATION_TEMPLATE: &str = "{notification_template_version()}";
pub(crate) const DEFAULT_PUSH_KEEPALIVE_STATUS_TYPE: &str = "{v('app_constants.websocket.x_psn_keep_alive_status_type.IDLE')}";
pub(crate) const DEFAULT_PUSH_PROTOCOL_VERSION: &str = "{v('app_constants.websocket.x_psn_protocol_version')}";
pub(crate) const DEFAULT_WEB_USER_AGENT: &str = "Mozilla/5.0 (Linux; Android 16; Mi 9T) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

pub(crate) const DEFAULT_TOKEN_URL: &str = "https://ca.account.sony.com/api/authz/v3/oauth/token";
pub(crate) const DEFAULT_AUTHORIZE_BASE_URL: &str =
    "https://ca.account.sony.com/api/authz/v3/oauth/authorize";
pub(crate) const DEFAULT_GRAPHQL_BASE_URL: &str = "https://m.np.playstation.com/api/graphql/v1/op";
pub(crate) const DEFAULT_GAME_MEDIA_BASE_URL: &str =
    "https://m.np.playstation.com/api/gameMediaService/v2/c2s";
pub(crate) const DEFAULT_TROPHY_BASE_URL: &str = "https://m.np.playstation.com/api/trophy/v1";
pub(crate) const DEFAULT_USER_INFO_BASE_URL: &str =
    "https://m.np.playstation.com/api/userProfile/v1/internal/users";
pub(crate) const DEFAULT_DMS_URL: &str =
    "https://dms.api.playstation.com/api/v1/devices/accounts/me";
"""


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate src/consts.rs from extracted app metadata."
    )
    parser.add_argument(
        "json_path",
        help="Path to the JSON file produced by extract_credentials.py",
    )
    parser.add_argument(
        "-o",
        "--output",
        default="src/consts.rs",
        help="Output path for the generated Rust source (default: src/consts.rs)",
    )
    args = parser.parse_args()

    with open(args.json_path) as f:
        data = json.load(f)

    output = template(data)

    out_path = Path(args.output)
    out_path.write_text(output)
    print(f"Wrote {out_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
