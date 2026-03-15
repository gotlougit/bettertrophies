use crate::auth::{AccessToken, access_token_formatted};
use crate::consts;
use chrono::{DateTime, Duration, Utc};
use reqwest::RequestBuilder;
use uuid::Uuid;

pub(crate) fn add_trace_headers(builder: RequestBuilder) -> RequestBuilder {
    let mut span_bytes = [0u8; 8];
    rand::fill(&mut span_bytes);
    let span_id = hex::encode(span_bytes);
    let mut trace_bytes = [0u8; 8];
    rand::fill(&mut trace_bytes);
    let trace_id = hex::encode(trace_bytes);

    builder
        .header("x-psn-request-id", Uuid::new_v4().to_string())
        .header("x-psn-sampled", "0")
        .header("x-psn-span-id", span_id)
        .header("x-psn-trace-id", trace_id)
        .header("x-psn-app-ver", consts::DEFAULT_APP_VERSION)
}

pub(crate) fn get_modified_since() -> String {
    let thirteen_minutes_ago: DateTime<Utc> = Utc::now() - Duration::minutes(13);
    thirteen_minutes_ago.to_rfc2822()
}

pub(crate) fn add_mobile_headers(
    builder: RequestBuilder,
    access_token: AccessToken,
) -> RequestBuilder {
    builder
        .header("authorization", access_token_formatted(access_token))
        .header("accept-language", "en-US")
        .header("accept-encoding", "gzip")
        .header("user-agent", consts::DEFAULT_OKHTTP_USER_AGENT)
        .header("if-modified-since", get_modified_since())
}

pub(crate) fn generate_graphql_correlation_id() -> String {
    Uuid::new_v4().to_string()
}

pub(crate) fn add_graphql_headers(
    builder: RequestBuilder,
    access_token: AccessToken,
    correlation_id: &str,
) -> RequestBuilder {
    builder
        .header("accept", "application/json")
        .header("content-type", "application/json")
        .header("apollographql-client-name", "PlayStationApp-Android")
        .header("apollographql-client-version", consts::DEFAULT_APP_VERSION)
        .header("authorization", access_token_formatted(access_token))
        .header("x-psn-correlation-id", correlation_id)
        .header("x-psn-request-id", Uuid::new_v4().to_string())
        .header("x-psn-app-ver", consts::DEFAULT_APP_VER_HEADER)
        .header("disable_query_whitelist", "false")
        .header("x-psn-store-locale-override", "en-US")
        .header("accept-encoding", "gzip")
        .header("accept-language", "en-US")
        .header("user-agent", consts::DEFAULT_OKHTTP_USER_AGENT)
}
