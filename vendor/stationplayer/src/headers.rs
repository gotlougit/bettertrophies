use crate::auth::{AccessToken, access_token_formatted};
use crate::consts;
use chrono::{DateTime, Duration, Utc};
use reqwest::RequestBuilder;
use uuid::Uuid;

#[cfg(test)]
use std::cell::RefCell;

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

fn modified_since_from(now: DateTime<Utc>) -> String {
    let thirteen_minutes_ago = now - Duration::minutes(13);
    thirteen_minutes_ago.to_rfc2822()
}

#[cfg(not(test))]
fn now_utc() -> DateTime<Utc> {
    Utc::now()
}

#[cfg(test)]
thread_local! {
    static TEST_NOW: RefCell<Option<DateTime<Utc>>> = RefCell::new(None);
}

#[cfg(test)]
fn now_utc() -> DateTime<Utc> {
    TEST_NOW.with(|cell| cell.borrow().as_ref().cloned().unwrap_or_else(Utc::now))
}

#[cfg(test)]
pub(crate) fn with_test_now<T>(now: DateTime<Utc>, f: impl FnOnce() -> T) -> T {
    struct ResetTestNow;

    impl Drop for ResetTestNow {
        fn drop(&mut self) {
            TEST_NOW.with(|cell| {
                *cell.borrow_mut() = None;
            });
        }
    }

    TEST_NOW.with(|cell| {
        *cell.borrow_mut() = Some(now);
    });

    let _reset = ResetTestNow;
    f()
}

pub(crate) fn get_modified_since() -> String {
    modified_since_from(now_utc())
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
