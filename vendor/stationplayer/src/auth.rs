use crate::consts;
use crate::headers::get_modified_since;
use base64::Engine;
use reqwest::Url;
use reqwest::{Client, RequestBuilder};
use serde::Deserialize;
use serde_json::json;

#[derive(Deserialize, Clone)]
pub struct AccessToken {
    #[serde(rename = "access_token")]
    inner: String,
}

#[derive(Deserialize)]
struct OAuthResponse {
    #[serde(flatten)]
    pub access_token: AccessToken,
}

#[derive(Deserialize)]
pub struct AccountInfo {
    #[serde(rename = "accountId")]
    pub account_id: String,
}

/// Generates the OAuth URL to be used to sign into PSN.
///
/// As it can be done in a variety of ways, we won't make the request ourselves
/// in a headless fashion. Also the page is usually protected by a WAF.
pub fn generate_sign_in_url(redirect_url: Option<&str>) -> String {
    let mut raw_url = consts::DEFAULT_AUTHORIZE_BASE_URL.to_owned() + "?";
    raw_url = raw_url + "client_id=" + consts::DEFAULT_CLIENT_ID;
    raw_url = raw_url + "&redirect_uri=" + redirect_url.unwrap_or(consts::DEFAULT_REDIRECT_URI);
    raw_url += "&response_type=code";
    raw_url = raw_url + "&scope=" + consts::DEFAULT_SCOPE;
    raw_url = raw_url + "&service_entity=" + consts::DEFAULT_SERVICE_ENTITY;
    raw_url += "&prompt=login";
    let url = Url::parse(&raw_url).expect("This shouldn't happen");
    url.as_str().to_owned()
}

/// Format basic auth header
fn basic_auth_header() -> String {
    let raw = format!(
        "{}:{}",
        consts::DEFAULT_CLIENT_ID,
        consts::DEFAULT_CLIENT_SECRET
    );
    let encoded = base64::engine::general_purpose::STANDARD.encode(raw);
    format!("Basic {}", encoded)
}

/// Builds the request to get the tokens for future requests.
pub(crate) fn get_auth_token(client: &Client, npsso: &str) -> RequestBuilder {
    let payload = json!({
         "token_format": "jwt",
         "grant_type": "sso_token",
         "npsso": npsso,
         "scope": consts::DEFAULT_SCOPE,
         "service_entity": consts::DEFAULT_SERVICE_ENTITY
    });
    let encoded_payload = serde_urlencoded::to_string(&payload).unwrap();

    client
        .post(consts::DEFAULT_TOKEN_URL)
        .header("User-Agent", consts::DEFAULT_AUTH_USER_AGENT)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Authorization", basic_auth_header())
        .header("Accept-Encoding", "gzip")
        .header("Connection", "Keep-Alive")
        .body(encoded_payload)
}

pub(crate) fn access_token_formatted(access_token: AccessToken) -> String {
    format!("Bearer {}", access_token.inner)
}

/// Parses response to obtain the access token.
pub(crate) fn get_access_token(body: &str) -> Result<AccessToken, serde_json::Error> {
    let parsed_body: OAuthResponse = serde_json::from_str(body)?;
    Ok(parsed_body.access_token)
}

/// Builds request for getting account info, this is required for Account ID.
pub(crate) fn get_account_info(client: &Client, access_token: AccessToken) -> RequestBuilder {
    client
        .get(consts::DEFAULT_DMS_URL)
        .header("authorization", access_token_formatted(access_token))
        .header("User-Agent", consts::DEFAULT_OKHTTP_USER_AGENT)
        .header("Accept-Encoding", "gzip")
        .header("Connection", "Keep-Alive")
        .header("If-Modified-Since", get_modified_since())
}
