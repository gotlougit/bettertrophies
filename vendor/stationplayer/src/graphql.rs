use serde::{Serialize, de::DeserializeOwned};

use crate::consts;

#[derive(Debug, Clone)]
pub(crate) struct GraphQLSpec {
    pub operation_name: String,
    pub variables: Option<serde_json::Value>,
    pub sha256_hash: String,
}

#[derive(Serialize)]
struct PersistedQuery {
    version: u8,
    #[serde(rename = "sha256Hash")]
    sha256_hash: String,
}

#[derive(Serialize)]
struct Extensions {
    #[serde(rename = "persistedQuery")]
    persisted_query: PersistedQuery,
}

#[derive(Serialize)]
struct GraphQLQueryParams {
    #[serde(rename = "operationName")]
    operation_name: String,
    variables: String,
    extensions: String,
}

pub(crate) fn generate_graphql_url(spec: &GraphQLSpec) -> String {
    let variables_json = match &spec.variables {
        Some(value) => serde_json::to_string(value).expect("Can't serialize GraphQL variables"),
        None => "{}".to_string(),
    };

    let extensions_json = serde_json::to_string(&Extensions {
        persisted_query: PersistedQuery {
            version: 1,
            sha256_hash: spec.sha256_hash.clone(),
        },
    })
    .expect("Can't serialize GraphQL extensions");

    let query_params = GraphQLQueryParams {
        operation_name: spec.operation_name.clone(),
        variables: variables_json,
        extensions: extensions_json,
    };

    let encoded =
        serde_urlencoded::to_string(query_params).expect("Can't url-encode GraphQL params");

    format!("{}?{}", consts::DEFAULT_GRAPHQL_BASE_URL, encoded)
}

pub(crate) fn decode_utf8_lossy(body: &[u8]) -> String {
    String::from_utf8_lossy(body).into_owned()
}

pub(crate) fn parse_json_utf8_lossy<T>(body: &[u8]) -> Result<T, serde_json::Error>
where
    T: DeserializeOwned,
{
    let text = decode_utf8_lossy(body);
    serde_json::from_str(&text)
}
