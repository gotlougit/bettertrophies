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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::auth::get_access_token;
    use crate::consts;
    use reqwest::Method;

    const RECENT_PLAYED_TITLES_HASH: &str =
        "2fd023209ae806e5ed59c0dc061c1a49fcd51788226d549b1c8cb310be2da9ba";

    fn test_access_token() -> AccessToken {
        get_access_token(r#"{"access_token":"TEST_ACCESS_TOKEN"}"#)
            .expect("test access token should parse")
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

    fn query_value(request: &reqwest::Request, name: &str) -> String {
        request
            .url()
            .query_pairs()
            .find_map(|(key, value)| (key == name).then(|| value.into_owned()))
            .unwrap_or_else(|| panic!("missing query parameter {name}"))
    }

    #[test]
    fn recent_played_titles_graphql_request_uses_persisted_query_and_count() {
        let request = get_played_titles(
            &Client::new(),
            "TEST_ACCOUNT_ID",
            test_access_token(),
            "TEST_CORRELATION_ID",
            Some(12),
        )
        .build()
        .expect("request should build");

        assert_eq!(request.method(), Method::GET);
        assert_eq!(
            request.url().as_str().split('?').next().unwrap(),
            consts::DEFAULT_GRAPHQL_BASE_URL
        );
        assert_eq!(
            header(&request, "x-psn-correlation-id"),
            "TEST_CORRELATION_ID"
        );
        assert_eq!(
            header(&request, "authorization"),
            "Bearer TEST_ACCESS_TOKEN"
        );
        assert_eq!(
            header(&request, "apollographql-client-name"),
            "PlayStationApp-Android"
        );
        assert_eq!(header(&request, "disable_query_whitelist"), "false");
        assert_eq!(
            query_value(&request, "operationName"),
            "metGetRecentPlayedTitles"
        );

        let variables: serde_json::Value =
            serde_json::from_str(&query_value(&request, "variables"))
                .expect("variables should decode as JSON");
        assert_eq!(variables["accountId"], "TEST_ACCOUNT_ID");
        assert_eq!(variables["count"], 12);
        assert_eq!(
            variables["categories"],
            "ps4_game,ps5_native_game,pspc_game"
        );
        assert_eq!(variables["shouldFetchCodex"], true);

        let extensions: serde_json::Value =
            serde_json::from_str(&query_value(&request, "extensions"))
                .expect("extensions should decode as JSON");
        assert_eq!(extensions["persistedQuery"]["version"], 1);
        assert_eq!(
            extensions["persistedQuery"]["sha256Hash"],
            RECENT_PLAYED_TITLES_HASH
        );
    }

    #[test]
    fn recent_played_titles_graphql_request_defaults_count_to_three() {
        let request = get_played_titles(
            &Client::new(),
            "TEST_ACCOUNT_ID",
            test_access_token(),
            "TEST_CORRELATION_ID",
            None,
        )
        .build()
        .expect("request should build");
        let variables: serde_json::Value =
            serde_json::from_str(&query_value(&request, "variables"))
                .expect("variables should decode as JSON");

        assert_eq!(variables["count"], 3);
    }
}
