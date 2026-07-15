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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::headers::with_test_now;
    use chrono::{DateTime, Utc};
    use reqwest::Method;

    const TEST_ACCESS_TOKEN_JSON: &str = r#"{"access_token":"TEST_ACCESS_TOKEN"}"#;

    fn test_access_token() -> AccessToken {
        get_access_token(TEST_ACCESS_TOKEN_JSON).expect("test access token should parse")
    }

    fn header(request: &reqwest::Request, name: &str) -> String {
        request
            .headers()
            .get(name)
            .unwrap_or_else(|| panic!("missing header {name}"))
            .to_str()
            .expect("header should be utf-8")
            .to_string()
    }

    fn fixed_now() -> DateTime<Utc> {
        DateTime::parse_from_rfc3339("2026-05-02T12:00:00Z")
            .expect("valid test timestamp")
            .with_timezone(&Utc)
    }

    #[test]
    fn sign_in_url_contains_expected_oauth_params_without_pii() {
        let url = Url::parse(&generate_sign_in_url(Some("testapp://redirect")))
            .expect("sign-in URL should parse");
        let params = url
            .query_pairs()
            .collect::<std::collections::HashMap<_, _>>();

        assert_eq!(
            url.as_str().split('?').next().unwrap(),
            consts::DEFAULT_AUTHORIZE_BASE_URL
        );
        assert_eq!(
            params.get("client_id").map(|value| value.as_ref()),
            Some(consts::DEFAULT_CLIENT_ID)
        );
        assert_eq!(
            params.get("redirect_uri").map(|value| value.as_ref()),
            Some("testapp://redirect")
        );
        assert_eq!(
            params.get("response_type").map(|value| value.as_ref()),
            Some("code")
        );
        assert_eq!(
            params.get("scope").map(|value| value.as_ref()),
            Some(consts::DEFAULT_SCOPE)
        );
        assert_eq!(
            params.get("service_entity").map(|value| value.as_ref()),
            Some(consts::DEFAULT_SERVICE_ENTITY)
        );
        assert_eq!(
            params.get("prompt").map(|value| value.as_ref()),
            Some("login")
        );
    }

    #[test]
    fn auth_token_request_contains_required_method_headers_and_body() {
        let client = Client::new();
        let request = get_auth_token(&client, "TEST_NPSSO")
            .build()
            .expect("request should build");
        let body = std::str::from_utf8(
            request
                .body()
                .and_then(|body| body.as_bytes())
                .expect("request should have an in-memory body"),
        )
        .expect("body should be utf-8");

        assert_eq!(request.method(), Method::POST);
        assert_eq!(request.url().as_str(), consts::DEFAULT_TOKEN_URL);
        assert_eq!(
            header(&request, "User-Agent"),
            consts::DEFAULT_AUTH_USER_AGENT
        );
        assert_eq!(
            header(&request, "Content-Type"),
            "application/x-www-form-urlencoded"
        );
        assert_eq!(header(&request, "Accept-Encoding"), "gzip");
        assert_eq!(header(&request, "Connection"), "Keep-Alive");
        assert!(header(&request, "Authorization").starts_with("Basic "));
        assert!(body.contains("grant_type=sso_token"));
        assert!(body.contains("npsso=TEST_NPSSO"));
        assert!(body.contains("token_format=jwt"));
        assert!(body.contains("scope=psn%3Amobile.v2.core+psn%3Aclientapp"));
        assert!(body.contains("service_entity=urn%3Aservice-entity%3Apsn"));
    }

    #[test]
    fn account_info_request_uses_bearer_auth_and_mocked_timestamp() {
        with_test_now(fixed_now(), || {
            let client = Client::new();
            let request = get_account_info(&client, test_access_token())
                .build()
                .expect("request should build");

            assert_eq!(request.method(), Method::GET);
            assert_eq!(request.url().as_str(), consts::DEFAULT_DMS_URL);
            assert_eq!(
                header(&request, "authorization"),
                "Bearer TEST_ACCESS_TOKEN"
            );
            assert_eq!(
                header(&request, "User-Agent"),
                consts::DEFAULT_OKHTTP_USER_AGENT
            );
            assert_eq!(header(&request, "Accept-Encoding"), "gzip");
            assert_eq!(header(&request, "Connection"), "Keep-Alive");
            assert_eq!(
                header(&request, "If-Modified-Since"),
                "Sat, 2 May 2026 11:47:00 +0000"
            );
        });
    }
}
