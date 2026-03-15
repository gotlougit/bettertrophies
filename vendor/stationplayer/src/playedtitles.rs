use crate::auth::AccessToken;
use crate::graphql::{self, GraphQLSpec};
use crate::headers::add_graphql_headers;
use reqwest::Client;
use reqwest::RequestBuilder;
use serde::Deserialize;

#[derive(Deserialize)]
pub struct RecentPlayedTitlesResponse {
    pub data: RecentPlayedTitlesData,
}

#[derive(Deserialize)]
pub struct RecentPlayedTitlesData {
    #[serde(rename = "recentPlayedTitlesRetrieve")]
    pub recent_played_titles: Vec<RecentPlayedTitle>,
}

#[derive(Deserialize)]
pub struct RecentPlayedTitle {
    #[serde(rename = "__typename")]
    pub typename: String,
    #[serde(rename = "codexSummary")]
    pub codex_summary: CodexSummary,
    #[serde(rename = "hasHelpContent")]
    pub has_help_content: bool,
    pub id: String,
    #[serde(rename = "npTitleId")]
    pub np_title_id: String,
    #[serde(rename = "playTimeHours")]
    pub play_time_hours: u32,
    #[serde(rename = "storyProgress")]
    pub story_progress: Option<u32>,
    pub title: Title,
}

#[derive(Deserialize)]
pub struct CodexSummary {
    #[serde(rename = "__typename")]
    pub typename: String,
    #[serde(rename = "hasCodex")]
    pub has_codex: bool,
    #[serde(rename = "hasNewEntries")]
    pub has_new_entries: Option<bool>,
    #[serde(rename = "unlockedCodexCount")]
    pub unlocked_codex_count: Option<u32>,
}

#[derive(Deserialize)]
pub struct Title {
    #[serde(rename = "__typename")]
    pub typename: String,
    #[serde(rename = "conceptId")]
    pub concept_id: String,
    pub media: Vec<Media>,
    pub name: String,
    pub platform: String,
}

#[derive(Deserialize)]
pub struct Media {
    #[serde(rename = "__typename")]
    pub typename: String,
    pub role: String,
    #[serde(rename = "type")]
    pub media_type: String,
    pub url: String,
}

pub(crate) fn get_played_titles(
    client: &Client,
    account_id: &str,
    access_token: AccessToken,
    correlation_id: &str,
    count: Option<u32>,
) -> RequestBuilder {
    let graphql_spec = GraphQLSpec {
        operation_name: "metGetRecentPlayedTitles".to_string(),
        variables: Some(serde_json::json!({
            "accountId": account_id,
            "count": count.unwrap_or(3),
            "categories": "ps4_game,ps5_native_game,pspc_game",
            "shouldFetchCodex": true,
        })),
        sha256_hash: "2fd023209ae806e5ed59c0dc061c1a49fcd51788226d549b1c8cb310be2da9ba".to_string(),
    };
    let url = graphql::generate_graphql_url(&graphql_spec);

    add_graphql_headers(client.get(url), access_token, correlation_id)
}
