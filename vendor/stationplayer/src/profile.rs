use crate::auth::AccessToken;
use crate::consts;
use crate::headers::{add_mobile_headers, add_trace_headers};
use reqwest::Client;
use reqwest::RequestBuilder;
use serde::Deserialize;

#[derive(Deserialize)]
pub struct Avatar {
    pub size: String,
    pub url: String,
}

#[derive(Deserialize)]
pub struct PersonalInfo {
    #[serde(rename = "firstName")]
    pub first_name: String,
    #[serde(rename = "lastName")]
    pub last_name: String,
}

#[derive(Deserialize)]
pub struct MyInfo {
    #[serde(rename = "aboutMe")]
    pub about_me: String,
    pub avatars: Vec<Avatar>,
    #[serde(rename = "isMe")]
    pub is_me: bool,
    #[serde(rename = "isOfficiallyVerified")]
    pub is_verified: bool,
    #[serde(rename = "isPlus")]
    pub is_plus: bool,
    pub languages: Vec<String>,
    #[serde(rename = "onlineId")]
    pub online_id: String,
    #[serde(rename = "personalDetail")]
    pub personal_detail: PersonalInfo,
}

pub(crate) fn get_user_info(
    client: &Client,
    account_id: &str,
    access_token: AccessToken,
) -> RequestBuilder {
    let user_info_url = format!(
        "{}/{}/profiles",
        consts::DEFAULT_USER_INFO_BASE_URL,
        account_id
    );
    add_mobile_headers(add_trace_headers(client.get(user_info_url)), access_token)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::auth::get_access_token;
    use crate::headers::with_test_now;
    use chrono::{DateTime, Utc};
    use reqwest::Method;

    fn test_access_token() -> AccessToken {
        get_access_token(r#"{"access_token":"TEST_ACCESS_TOKEN"}"#)
            .expect("test access token should parse")
    }

    fn fixed_now() -> DateTime<Utc> {
        DateTime::parse_from_rfc3339("2026-05-02T12:00:00Z")
            .expect("valid test timestamp")
            .with_timezone(&Utc)
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

    fn assert_uuid(value: &str) {
        uuid::Uuid::parse_str(value).expect("value should be a UUID");
    }

    fn assert_hex_len(value: &str, len: usize) {
        assert_eq!(value.len(), len);
        assert!(value.chars().all(|ch| ch.is_ascii_hexdigit()));
    }

    #[test]
    fn user_info_request_has_expected_url_headers_and_mocked_timestamp() {
        with_test_now(fixed_now(), || {
            let client = Client::new();
            let request = get_user_info(&client, "TEST_ACCOUNT_ID", test_access_token())
                .build()
                .expect("request should build");

            assert_eq!(request.method(), Method::GET);
            assert_eq!(
                request.url().as_str(),
                "https://m.np.playstation.com/api/userProfile/v1/internal/users/TEST_ACCOUNT_ID/profiles"
            );
            assert_eq!(
                header(&request, "authorization"),
                "Bearer TEST_ACCESS_TOKEN"
            );
            assert_eq!(header(&request, "accept-language"), "en-US");
            assert_eq!(header(&request, "accept-encoding"), "gzip");
            assert_eq!(
                header(&request, "user-agent"),
                consts::DEFAULT_OKHTTP_USER_AGENT
            );
            assert_eq!(
                header(&request, "if-modified-since"),
                "Sat, 2 May 2026 11:47:00 +0000"
            );
            assert_eq!(header(&request, "x-psn-sampled"), "0");
            assert_eq!(
                header(&request, "x-psn-app-ver"),
                consts::DEFAULT_APP_VERSION
            );
            assert_uuid(&header(&request, "x-psn-request-id"));
            assert_hex_len(&header(&request, "x-psn-span-id"), 16);
            assert_hex_len(&header(&request, "x-psn-trace-id"), 16);
        });
    }
}
