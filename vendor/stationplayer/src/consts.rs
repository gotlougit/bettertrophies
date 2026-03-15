pub(crate) const DEFAULT_CLIENT_ID: &str = "09515159-7237-4370-9b40-3806e67c0891";
pub(crate) const DEFAULT_CLIENT_SECRET: &str = "ucPjka5tntB2KqsP";
// TODO: redirect to proper URL where user can copy the tokens required
pub(crate) const DEFAULT_REDIRECT_URI: &str = "com.scee.psxandroid.scecompcall://redirect";
pub(crate) const DEFAULT_SCOPE: &str = "psn:mobile.v2.core psn:clientapp";
pub(crate) const DEFAULT_SERVICE_ENTITY: &str = "urn:service-entity:psn";

pub(crate) const DEFAULT_AUTH_USER_AGENT: &str =
    "com.sony.snei.np.android.sso.share.oauth.versa.USER_AGENT";
pub(crate) const DEFAULT_OKHTTP_USER_AGENT: &str = "okhttp/4.12.0";
// TODO: use different device names in user agent
pub(crate) const DEFAULT_DALVIK_USER_AGENT: &str =
    "Dalvik/2.1.0 (Linux; U; Android 16; Mi 9T Build/BP2A.250605.031.A2)";
pub(crate) const DEFAULT_APP_VERSION: &str = "26.2.0";
pub(crate) const DEFAULT_APP_VER_HEADER: &str = "PlayStationApp-Android/26.2.0";
pub(crate) const DEFAULT_APP_TYPE: &str = "MOBILE_APP.PSAPP";
pub(crate) const DEFAULT_PUSH_OS_VERSION: &str = "16";
pub(crate) const DEFAULT_LANGUAGE: &str = "en-US";

pub(crate) const DEFAULT_NOTIFICATION_TEMPLATE: &str = "26.02";
pub(crate) const DEFAULT_PUSH_KEEPALIVE_STATUS_TYPE: &str = "6";
pub(crate) const DEFAULT_PUSH_PROTOCOL_VERSION: &str = "3.0";
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
