use crate::auth::AccessToken;
use crate::consts;
use crate::graphql::{self, GraphQLSpec};
use crate::headers::{add_graphql_headers, add_mobile_headers, add_trace_headers};
use reqwest::Client;
use reqwest::RequestBuilder;
use serde::de::Deserializer;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;

#[allow(dead_code)]
const MET_GET_USER_IDENTITY_SHA256: &str =
    "28f8ec8a41384b63fb05bc13e3a2a5aa377ade9ba02d0d0900313cc06c3a9a84";
#[allow(dead_code)]
const MET_GET_ACTIVITY_HELP_AVAILABILITY_SHA256: &str =
    "bf448c99b8f4fabfa90d71a858ce28afe7112ec95844aa036da4b7bf07d97aaf";
#[allow(dead_code)]
const MET_GET_CODEX_SUMMARY_SHA256: &str =
    "34f529c96d61dd30693bef9cc3b2012aed6b089fb1f22f0e9e091eed6e4d6788";
const MET_GET_HINT_AVAILABILITY_SHA256: &str =
    "71bf26729f2634f4d8cca32ff73aaf42b3b76ad1d2f63b490a809b66483ea5a7";

#[derive(Deserialize, Serialize)]
pub struct UserGameTrophyInfo {
    #[serde(rename = "definedTrophies")]
    pub defined_trophies: TrophyDistributions,
    #[serde(rename = "earnedTrophies")]
    pub earned_trophies: TrophyDistributions,
    #[serde(rename = "hasTrophyGroups")]
    pub trophy_groups: bool,
    #[serde(rename = "hiddenFlag")]
    pub hidden_flag: bool,
    #[serde(rename = "lastUpdatedDateTime")]
    pub last_updated: String,
    #[serde(rename = "npCommunicationId")]
    pub np_communication_id: String,
    #[serde(rename = "npServiceName")]
    pub np_service_name: String,
    #[serde(rename = "npTitleId")]
    pub np_title_id: Option<String>,
    pub progress: u32,
    #[serde(rename = "trophyGroupCount")]
    pub trophy_group_count: u32,
    #[serde(rename = "trophySetVersion")]
    pub trophy_set_version: String,
    #[serde(rename = "trophyTitleIconUrl")]
    pub trophy_title_icon: String,
    #[serde(rename = "trophyTitleName")]
    pub title: String,
    #[serde(rename = "trophyTitlePlatform")]
    pub platform: String,
}

#[derive(Deserialize, Serialize)]
pub struct UserTrophyByTitle {
    #[serde(rename = "nextOffset")]
    pub next_offset: Option<u32>,
    #[serde(rename = "totalItemCount")]
    pub total_items: u32,
    #[serde(rename = "trophyTitles")]
    pub games: Vec<UserGameTrophyInfo>,
}

#[derive(Deserialize, Serialize)]
pub struct TrophyDistributions {
    pub bronze: u32,
    pub gold: u32,
    pub platinum: u32,
    pub silver: u32,
}

#[derive(Deserialize, Serialize)]
pub struct UserTrophySummary {
    #[serde(rename = "accountId")]
    pub account_id: String,
    #[serde(rename = "earnedTrophies")]
    pub earned_trophies: TrophyDistributions,
    pub progress: u32,
    pub tier: u32,
    #[serde(rename = "trophyLevel")]
    pub trophy_level: u32,
    #[serde(rename = "trophyLevelBasePoint")]
    pub trophy_level_base_point: u32,
    #[serde(rename = "trophyLevelNextPoint")]
    pub trophy_level_next_point: u32,
    #[serde(rename = "trophyPoint")]
    pub trophy_point: u32,
}

#[derive(Deserialize, Serialize)]
pub struct TrophyTitlesByTitleIds {
    #[serde(default)]
    pub titles: Vec<TrophyTitleLookupByTitleId>,
}

#[derive(Deserialize, Serialize)]
pub struct TrophyTitleLookupByTitleId {
    #[serde(rename = "npTitleId")]
    pub np_title_id: String,
    #[serde(rename = "trophyTitles", default)]
    pub trophy_titles: Vec<TrophyTitleLookup>,
}

#[derive(Deserialize, Serialize)]
pub struct TrophyTitleLookup {
    #[serde(rename = "npCommunicationId")]
    pub np_communication_id: Option<String>,
    #[serde(rename = "npServiceName")]
    pub np_service_name: Option<String>,
    #[serde(rename = "notEarnedTrophyIds", default)]
    pub not_earned_trophy_ids: Vec<u32>,
    #[serde(rename = "npTitleId")]
    pub np_title_id: Option<String>,
    pub progress: Option<u32>,
    #[serde(rename = "hasTrophyGroups")]
    pub trophy_groups: Option<bool>,
    #[serde(rename = "lastUpdatedDateTime")]
    pub last_updated: Option<String>,
    #[serde(rename = "trophyGroupCount")]
    pub trophy_group_count: Option<u32>,
    #[serde(rename = "trophyTitleName")]
    pub title: Option<String>,
    #[serde(rename = "trophyTitlePlatform")]
    pub platform: Option<String>,
    #[serde(rename = "trophyTitleIconUrl")]
    pub trophy_title_icon: Option<String>,
    #[serde(rename = "definedTrophies")]
    pub defined_trophies: Option<TrophyDistributions>,
    #[serde(rename = "earnedTrophies")]
    pub earned_trophies: Option<TrophyDistributions>,
    #[serde(rename = "rarestTrophies", default)]
    pub rarest_trophies: Vec<Trophy>,
}

#[derive(Deserialize, Serialize)]
pub struct TrophyGroupsResponse {
    #[serde(rename = "definedTrophies")]
    pub defined_trophies: Option<TrophyDistributions>,
    #[serde(rename = "earnedTrophies")]
    pub earned_trophies: Option<TrophyDistributions>,
    #[serde(rename = "hiddenFlag")]
    pub hidden_flag: Option<bool>,
    #[serde(rename = "lastUpdatedDateTime")]
    pub last_updated: Option<String>,
    pub progress: Option<u32>,
    #[serde(rename = "trophyGroups", default)]
    pub groups: Vec<TrophyGroup>,
    #[serde(rename = "trophySetVersion")]
    pub trophy_set_version: Option<String>,
}

#[derive(Deserialize, Serialize)]
pub struct TrophyGroup {
    #[serde(rename = "definedTrophies")]
    pub defined_trophies: Option<TrophyDistributions>,
    #[serde(rename = "earnedTrophies")]
    pub earned_trophies: Option<TrophyDistributions>,
    #[serde(rename = "groupId", alias = "trophyGroupId")]
    pub group_id: String,
    #[serde(rename = "groupName")]
    pub group_name: Option<String>,
    #[serde(rename = "hiddenFlag")]
    pub hidden_flag: Option<bool>,
    #[serde(rename = "lastUpdatedDateTime")]
    pub last_updated: Option<String>,
    pub progress: Option<u32>,
    #[serde(rename = "trophyGroupIconUrl")]
    pub trophy_group_icon_url: Option<String>,
}

#[derive(Deserialize, Serialize)]
pub struct TrophyGroupTrophiesResponse {
    #[serde(rename = "definedTrophies")]
    pub defined_trophies: Option<TrophyDistributions>,
    #[serde(rename = "earnedTrophies")]
    pub earned_trophies: Option<TrophyDistributions>,
    #[serde(rename = "groupId")]
    pub group_id: Option<String>,
    #[serde(rename = "groupName")]
    pub group_name: Option<String>,
    #[serde(rename = "hiddenFlag")]
    pub hidden_flag: Option<bool>,
    pub progress: Option<u32>,
    #[serde(rename = "hasTrophyGroups")]
    pub has_trophy_groups: Option<bool>,
    #[serde(rename = "lastUpdatedDateTime")]
    pub last_updated: Option<String>,
    #[serde(rename = "totalItemCount")]
    pub total_item_count: Option<u32>,
    #[serde(rename = "rarestTrophies", default)]
    pub rarest_trophies: Vec<Trophy>,
    #[serde(rename = "trophies", default)]
    pub trophies: Vec<Trophy>,
}

#[derive(Clone, Deserialize, Serialize)]
pub struct Trophy {
    #[serde(
        rename = "trophyId",
        deserialize_with = "deserialize_stringified_primitive"
    )]
    pub trophy_id: String,
    #[serde(rename = "trophyHidden")]
    pub trophy_hidden: Option<bool>,
    #[serde(rename = "trophyIconUrl")]
    pub trophy_icon_url: Option<String>,
    #[serde(rename = "trophyName")]
    pub trophy_name: Option<String>,
    #[serde(rename = "trophyDetail")]
    pub trophy_detail: Option<String>,
    #[serde(rename = "trophyType")]
    pub trophy_type: Option<String>,
    #[serde(rename = "earned")]
    pub earned: Option<bool>,
    #[serde(rename = "earnedDateTime")]
    pub earned_date_time: Option<String>,
    #[serde(
        rename = "progress",
        default,
        deserialize_with = "deserialize_optional_stringified_primitive"
    )]
    pub progress: Option<String>,
    #[serde(rename = "progressRate")]
    pub progress_rate: Option<u32>,
    #[serde(rename = "progressedDateTime")]
    pub progressed_date_time: Option<String>,
    #[serde(rename = "rare")]
    pub rare: Option<u32>,
    #[serde(rename = "trophyRare")]
    pub trophy_rare: Option<u32>,
    #[serde(rename = "trophyEarnedRate")]
    pub trophy_earned_rate: Option<String>,
}

#[derive(Deserialize, Serialize)]
pub struct TrophyDetail {
    #[serde(flatten)]
    pub trophy: Trophy,
}

#[derive(Deserialize, Serialize)]
pub struct TrophyAppearanceSetting {
    #[serde(rename = "isSet")]
    pub is_set: Option<bool>,
    #[serde(rename = "lastUpdatedDateTime")]
    pub last_updated: Option<String>,
    #[serde(rename = "revealAllTrophies")]
    pub reveal_all_trophies: Option<bool>,
    pub revision: Option<u32>,
    #[serde(rename = "visibility")]
    pub visibility: Option<String>,
    #[serde(rename = "canDisplayAllTrophies")]
    pub can_display_all_trophies: Option<bool>,
}

pub(crate) fn resolve_title_service_info(
    titles: &TrophyTitlesByTitleIds,
    title_id: &str,
) -> Option<(String, String)> {
    titles
        .titles
        .iter()
        .find(|title| title.np_title_id == title_id)
        .and_then(|title| {
            title.trophy_titles.iter().find_map(|trophy_title| {
                Some((
                    trophy_title.np_communication_id.clone()?,
                    trophy_title.np_service_name.clone()?,
                ))
            })
        })
}

pub(crate) fn merge_trophy_group_trophies(
    user_trophies: TrophyGroupTrophiesResponse,
    defined_trophies: TrophyGroupTrophiesResponse,
) -> Vec<Trophy> {
    let defined_by_id = defined_trophies
        .trophies
        .into_iter()
        .map(|trophy| (trophy.trophy_id.clone(), trophy))
        .collect::<HashMap<_, _>>();

    user_trophies
        .trophies
        .into_iter()
        .map(|trophy| {
            let defined = defined_by_id.get(&trophy.trophy_id);
            merge_trophy(trophy, defined)
        })
        .collect()
}

pub(crate) fn merge_trophy_details(
    user_trophy: TrophyDetail,
    defined_trophy: TrophyDetail,
) -> TrophyDetail {
    TrophyDetail {
        trophy: merge_trophy(user_trophy.trophy, Some(&defined_trophy.trophy)),
    }
}

fn merge_trophy(mut trophy: Trophy, defined: Option<&Trophy>) -> Trophy {
    let Some(defined) = defined else {
        return trophy;
    };

    trophy.trophy_hidden = trophy.trophy_hidden.or(defined.trophy_hidden);
    trophy.trophy_icon_url = trophy
        .trophy_icon_url
        .or_else(|| defined.trophy_icon_url.clone());
    trophy.trophy_name = trophy.trophy_name.or_else(|| defined.trophy_name.clone());
    trophy.trophy_detail = trophy
        .trophy_detail
        .or_else(|| defined.trophy_detail.clone());
    trophy.trophy_type = trophy.trophy_type.or_else(|| defined.trophy_type.clone());
    trophy.earned = trophy.earned.or(defined.earned);
    trophy.earned_date_time = trophy
        .earned_date_time
        .or_else(|| defined.earned_date_time.clone());
    trophy.progress = trophy.progress.or_else(|| defined.progress.clone());
    trophy.progress_rate = trophy.progress_rate.or(defined.progress_rate);
    trophy.progressed_date_time = trophy
        .progressed_date_time
        .or_else(|| defined.progressed_date_time.clone());
    trophy.rare = trophy.rare.or(defined.rare);
    trophy.trophy_rare = trophy.trophy_rare.or(defined.trophy_rare);
    trophy.trophy_earned_rate = trophy
        .trophy_earned_rate
        .or_else(|| defined.trophy_earned_rate.clone());
    trophy
}

#[derive(Deserialize, Serialize)]
pub struct GraphQLError {
    pub message: String,
}

#[derive(Deserialize, Serialize)]
pub struct GraphQLResponse<T> {
    pub data: Option<T>,
    pub errors: Option<Vec<GraphQLError>>,
}

pub type UserIdentityResponse = GraphQLResponse<serde_json::Value>;
pub type ActivityHelpAvailabilityResponse = GraphQLResponse<serde_json::Value>;

#[derive(Deserialize, Serialize)]
pub struct HintAvailabilityResponse {
    pub data_json: Option<String>,
    pub errors: Option<Vec<GraphQLError>>,
}

#[derive(Deserialize, Serialize)]
pub struct CodexSummaryData {
    #[serde(default)]
    pub titles: Vec<TrophyTitleLookupByTitleId>,
}

#[derive(Deserialize, Serialize)]
#[serde(untagged)]
pub enum CodexSummaryResponse {
    Direct(CodexSummaryData),
    GraphQL(GraphQLResponse<CodexSummaryData>),
}

impl CodexSummaryResponse {
    pub fn data(&self) -> Option<&CodexSummaryData> {
        match self {
            Self::Direct(data) => Some(data),
            Self::GraphQL(response) => response.data.as_ref(),
        }
    }

    pub fn errors(&self) -> Option<&[GraphQLError]> {
        match self {
            Self::Direct(_) => None,
            Self::GraphQL(response) => response.errors.as_deref(),
        }
    }
}

fn normalize_np_service_name(np_service_name: Option<&str>) -> &str {
    match np_service_name {
        Some(service_name) if !service_name.is_empty() => service_name,
        _ => "trophy",
    }
}

fn join_csv(values: &[String]) -> String {
    values.join(",")
}

fn add_trophy_service_query(url: &str, np_service_name: Option<&str>) -> String {
    let service_name = normalize_np_service_name(np_service_name);
    format!("{url}?npServiceName={service_name}")
}

fn trophy_graphql_request(
    client: &Client,
    access_token: AccessToken,
    correlation_id: &str,
    operation_name: &str,
    variables: Value,
    sha256_hash: &str,
) -> RequestBuilder {
    let spec = GraphQLSpec {
        operation_name: operation_name.to_string(),
        variables: Some(variables),
        sha256_hash: sha256_hash.to_string(),
    };
    let url = graphql::generate_graphql_url(&spec);
    add_graphql_headers(client.get(url), access_token, correlation_id)
}

pub(crate) fn get_trophy_summary(
    client: &Client,
    account_id: &str,
    access_token: AccessToken,
) -> RequestBuilder {
    let url = format!(
        "{}/users/{}/trophySummary",
        consts::DEFAULT_TROPHY_BASE_URL,
        account_id
    );
    add_mobile_headers(add_trace_headers(client.get(url)), access_token)
}

pub(crate) fn get_trophy_titles(
    client: &Client,
    account_id: &str,
    access_token: AccessToken,
    offset: u32,
) -> RequestBuilder {
    let url = format!(
        "{}/users/{}/trophyTitles?limit=100&offset={}",
        consts::DEFAULT_TROPHY_BASE_URL,
        account_id,
        offset
    );
    add_mobile_headers(add_trace_headers(client.get(url)), access_token)
}

pub(crate) fn get_trophy_titles_include_not_earned(
    client: &Client,
    account_id: &str,
    access_token: AccessToken,
    np_title_ids: &[String],
) -> RequestBuilder {
    let url = format!(
        "{}/users/{}/titles/trophyTitles?includeNotEarnedTrophyIds=true&npTitleIds={}",
        consts::DEFAULT_TROPHY_BASE_URL,
        account_id,
        join_csv(np_title_ids)
    );
    add_mobile_headers(add_trace_headers(client.get(url)), access_token)
}

pub(crate) fn get_trophy_groups(
    client: &Client,
    account_id: &str,
    access_token: AccessToken,
    np_communication_id: &str,
    np_service_name: Option<&str>,
) -> RequestBuilder {
    let url = add_trophy_service_query(
        &format!(
            "{}/users/{}/npCommunicationIds/{}/trophyGroups",
            consts::DEFAULT_TROPHY_BASE_URL,
            account_id,
            np_communication_id
        ),
        np_service_name,
    );
    add_mobile_headers(add_trace_headers(client.get(url)), access_token)
}

pub(crate) fn get_trophy_group_trophies(
    client: &Client,
    account_id: &str,
    access_token: AccessToken,
    np_communication_id: &str,
    group_id: &str,
    np_service_name: Option<&str>,
) -> RequestBuilder {
    let url = add_trophy_service_query(
        &format!(
            "{}/users/{}/npCommunicationIds/{}/trophyGroups/{}/trophies",
            consts::DEFAULT_TROPHY_BASE_URL,
            account_id,
            np_communication_id,
            group_id
        ),
        np_service_name,
    );
    add_mobile_headers(add_trace_headers(client.get(url)), access_token)
}

pub(crate) fn get_defined_trophy_group_trophies(
    client: &Client,
    access_token: AccessToken,
    np_communication_id: &str,
    group_id: &str,
    np_service_name: Option<&str>,
) -> RequestBuilder {
    let url = add_trophy_service_query(
        &format!(
            "{}/npCommunicationIds/{}/trophyGroups/{}/trophies",
            consts::DEFAULT_TROPHY_BASE_URL,
            np_communication_id,
            group_id
        ),
        np_service_name,
    );
    add_mobile_headers(add_trace_headers(client.get(url)), access_token)
}

pub(crate) fn get_trophy_detail(
    client: &Client,
    account_id: &str,
    access_token: AccessToken,
    np_communication_id: &str,
    trophy_id: &str,
    np_service_name: Option<&str>,
) -> RequestBuilder {
    let url = add_trophy_service_query(
        &format!(
            "{}/users/{}/npCommunicationIds/{}/trophies/{}",
            consts::DEFAULT_TROPHY_BASE_URL,
            account_id,
            np_communication_id,
            trophy_id
        ),
        np_service_name,
    );
    add_mobile_headers(add_trace_headers(client.get(url)), access_token)
}

pub(crate) fn get_defined_trophy_detail(
    client: &Client,
    access_token: AccessToken,
    np_communication_id: &str,
    trophy_id: &str,
    np_service_name: Option<&str>,
) -> RequestBuilder {
    let url = add_trophy_service_query(
        &format!(
            "{}/npCommunicationIds/{}/trophies/{}",
            consts::DEFAULT_TROPHY_BASE_URL,
            np_communication_id,
            trophy_id
        ),
        np_service_name,
    );
    add_mobile_headers(add_trace_headers(client.get(url)), access_token)
}

pub(crate) fn get_trophy_appearance_setting(
    client: &Client,
    access_token: AccessToken,
    np_communication_id: &str,
    np_service_name: Option<&str>,
) -> RequestBuilder {
    let url = add_trophy_service_query(
        &format!(
            "{}/users/me/npCommunicationIds/{}/appearanceSetting",
            consts::DEFAULT_TROPHY_BASE_URL,
            np_communication_id
        ),
        np_service_name,
    );
    add_mobile_headers(add_trace_headers(client.get(url)), access_token)
}

#[allow(dead_code)]
pub(crate) fn get_user_identity(
    client: &Client,
    access_token: AccessToken,
    correlation_id: &str,
) -> RequestBuilder {
    trophy_graphql_request(
        client,
        access_token,
        correlation_id,
        "metGetUserIdentity",
        serde_json::json!({}),
        MET_GET_USER_IDENTITY_SHA256,
    )
}

#[allow(dead_code)]
pub(crate) fn get_activity_help_availability(
    client: &Client,
    access_token: AccessToken,
    correlation_id: &str,
    np_title_ids: &[String],
) -> RequestBuilder {
    trophy_graphql_request(
        client,
        access_token,
        correlation_id,
        "metGetActivityHelpAvailability",
        serde_json::json!({
            "npTitleIds": np_title_ids,
        }),
        MET_GET_ACTIVITY_HELP_AVAILABILITY_SHA256,
    )
}

#[allow(dead_code)]
pub(crate) fn get_codex_summary(
    client: &Client,
    access_token: AccessToken,
    correlation_id: &str,
    np_title_ids: &[String],
) -> RequestBuilder {
    trophy_graphql_request(
        client,
        access_token,
        correlation_id,
        "metGetCodexSummary",
        serde_json::json!({
            "npTitleIds": np_title_ids,
        }),
        MET_GET_CODEX_SUMMARY_SHA256,
    )
}

pub(crate) fn get_hint_availability(
    client: &Client,
    access_token: AccessToken,
    correlation_id: &str,
    np_communication_id: &str,
    trophy_ids: &[String],
) -> RequestBuilder {
    trophy_graphql_request(
        client,
        access_token,
        correlation_id,
        "metGetHintAvailability",
        serde_json::json!({
            "npCommId": np_communication_id,
            "trophyIds": trophy_ids,
        }),
        MET_GET_HINT_AVAILABILITY_SHA256,
    )
}

fn deserialize_stringified_primitive<'de, D>(deserializer: D) -> Result<String, D::Error>
where
    D: Deserializer<'de>,
{
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum Primitive {
        String(String),
        Number(u64),
        Signed(i64),
    }

    match Primitive::deserialize(deserializer)? {
        Primitive::String(value) => Ok(value),
        Primitive::Number(value) => Ok(value.to_string()),
        Primitive::Signed(value) => Ok(value.to_string()),
    }
}

fn deserialize_optional_stringified_primitive<'de, D>(
    deserializer: D,
) -> Result<Option<String>, D::Error>
where
    D: Deserializer<'de>,
{
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum Primitive {
        String(String),
        Number(u64),
        Signed(i64),
    }

    match Option::<Primitive>::deserialize(deserializer)? {
        Some(Primitive::String(value)) => Ok(Some(value)),
        Some(Primitive::Number(value)) => Ok(Some(value.to_string())),
        Some(Primitive::Signed(value)) => Ok(Some(value.to_string())),
        None => Ok(None),
    }
}
