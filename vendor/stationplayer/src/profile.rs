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
