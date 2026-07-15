use crate::auth::AccessToken;
use crate::consts;
use crate::headers::{add_mobile_headers, add_trace_headers};
use reqwest::{Client, RequestBuilder, Url};
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

const CLOUD_MEDIA_FIELDS: &str = "title,description,broadcastDate,sceTitleName,countOfViewers,sceUserOnlineId,streamingPreviewImage,serviceType,channelId,sceTitleId,isSpoiler,transcodeStatus";

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CloudMediaGalleryResponse {
    pub limit: Option<u32>,
    #[serde(rename = "nextCursorMark")]
    pub next_cursor_mark: Option<String>,
    #[serde(rename = "ugcDocument", default)]
    pub ugc_documents: Vec<CloudMediaCapture>,
    #[serde(rename = "xpsnRequestId")]
    pub xpsn_request_id: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CloudMediaCapture {
    #[serde(default)]
    pub archived: Option<bool>,
    #[serde(rename = "broadcastDate", default)]
    pub broadcast_date: Option<String>,
    #[serde(default)]
    pub category: Option<String>,
    #[serde(rename = "captureType", default)]
    pub capture_type: Option<String>,
    #[serde(rename = "channelId", default)]
    pub channel_id: Option<String>,
    #[serde(rename = "cloudStatus", default)]
    pub cloud_status: Option<String>,
    #[serde(rename = "colorRange", default)]
    pub color_range: Option<String>,
    #[serde(rename = "conceptId", default)]
    pub concept_id: Option<String>,
    #[serde(rename = "countOfViewers", default)]
    pub count_of_viewers: Option<u64>,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(rename = "downloadUrl", default)]
    pub download_url: Option<String>,
    #[serde(default)]
    pub end: Option<i64>,
    #[serde(rename = "expireAt", default)]
    pub expire_at: Option<String>,
    #[serde(rename = "fileSize", default)]
    pub file_size: Option<u64>,
    #[serde(rename = "fileType", default)]
    pub file_type: Option<String>,
    pub id: String,
    #[serde(rename = "isSpoiler", default)]
    pub is_spoiler: Option<bool>,
    #[serde(default)]
    pub language: Option<String>,
    #[serde(rename = "largePreviewImage", default)]
    pub large_preview_image: Option<String>,
    #[serde(default)]
    pub resolution: Option<String>,
    #[serde(rename = "scePlatform", default)]
    pub sce_platform: Option<String>,
    #[serde(rename = "sceTitleAgeRating", default)]
    pub sce_title_age_rating: Option<u32>,
    #[serde(rename = "sceTitleId", default)]
    pub sce_title_id: Option<String>,
    #[serde(rename = "sceTitleName", default)]
    pub sce_title_name: Option<String>,
    #[serde(rename = "sceUserAccountId", default)]
    pub sce_user_account_id: Option<String>,
    #[serde(rename = "sceUserOnlineId", default)]
    pub sce_user_online_id: Option<String>,
    #[serde(rename = "screenshotUrl", default)]
    pub screenshot_url: Option<String>,
    #[serde(rename = "serviceType", default)]
    pub service_type: Option<String>,
    #[serde(rename = "smallPreviewImage", default)]
    pub small_preview_image: Option<String>,
    #[serde(rename = "sourceOfMedia", default)]
    pub source_of_media: Option<String>,
    #[serde(rename = "sourceUgcId", default)]
    pub source_ugc_id: Option<String>,
    #[serde(default)]
    pub start: Option<i64>,
    #[serde(rename = "streamingPreviewImage", default)]
    pub streaming_preview_image: Option<String>,
    pub title: Option<String>,
    #[serde(rename = "titleImageUrl", default)]
    pub title_image_url: Option<String>,
    #[serde(rename = "transcodeError", default)]
    pub transcode_error: Option<String>,
    #[serde(rename = "transcodeJobId", default)]
    pub transcode_job_id: Option<String>,
    #[serde(rename = "transcodeProgress", default)]
    pub transcode_progress: Option<String>,
    #[serde(rename = "transcodeStatus", default)]
    pub transcode_status: Option<String>,
    #[serde(rename = "ugcType", default)]
    pub ugc_type: Option<u32>,
    #[serde(rename = "uploadDate", default)]
    pub upload_date: Option<String>,
    #[serde(rename = "videoDuration", default)]
    pub video_duration: Option<u64>,
    #[serde(rename = "videoUrl", default)]
    pub video_url: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct CloudMediaCaptureUrls {
    #[serde(rename = "downloadUrl", default)]
    pub download_url: Option<String>,
    #[serde(rename = "largePreviewImage", default)]
    pub large_preview_image: Option<String>,
    #[serde(rename = "screenshotUrl", default)]
    pub screenshot_url: Option<String>,
    #[serde(rename = "smallPreviewImage", default)]
    pub small_preview_image: Option<String>,
    #[serde(rename = "videoUrl", default)]
    pub video_url: Option<String>,
}

#[derive(Clone, Debug, Serialize)]
pub struct CloudMediaCaptureGroup {
    pub title_id: String,
    pub title_name: String,
    pub concept_id: Option<String>,
    pub title_image_url: Option<String>,
    pub captures: Vec<CloudMediaCapture>,
}

#[derive(Clone, Debug)]
pub struct DownloadedCloudMediaCapture {
    pub ugc_id: String,
    pub content_type: Option<String>,
    pub file_name: String,
    pub source_url: String,
    pub bytes: Vec<u8>,
}

pub(crate) fn get_cloud_media_gallery(
    client: &Client,
    access_token: AccessToken,
    limit: u32,
    cursor_mark: Option<&str>,
) -> RequestBuilder {
    let mut url = Url::parse(&format!(
        "{}/category/cloudMediaGallery/ugcType/all",
        consts::DEFAULT_GAME_MEDIA_BASE_URL
    ))
    .expect("valid cloud media gallery URL");
    {
        let mut pairs = url.query_pairs_mut();
        pairs.append_pair("includeTokenizedUrls", "true");
        pairs.append_pair("limit", &limit.to_string());
        if let Some(cursor_mark) = cursor_mark {
            pairs.append_pair("cursorMark", cursor_mark);
        }
    }

    let builder = client.get(url);
    add_mobile_headers(add_trace_headers(builder), access_token)
}

pub(crate) fn get_cloud_media_capture_content(
    client: &Client,
    access_token: AccessToken,
    ugc_id: &str,
) -> RequestBuilder {
    let mut url = Url::parse(&format!("{}/content", consts::DEFAULT_GAME_MEDIA_BASE_URL))
        .expect("valid cloud media content URL");
    {
        let mut pairs = url.query_pairs_mut();
        pairs.append_pair("fields", CLOUD_MEDIA_FIELDS);
        pairs.append_pair("ugcIds", ugc_id);
    }

    let builder = client.get(url);
    add_mobile_headers(add_trace_headers(builder), access_token)
}

pub(crate) fn get_cloud_media_capture_urls(
    client: &Client,
    access_token: AccessToken,
    ugc_id: &str,
) -> RequestBuilder {
    let url = format!("{}/ugc/{ugc_id}/url", consts::DEFAULT_GAME_MEDIA_BASE_URL);
    let builder = client.get(url);
    add_mobile_headers(add_trace_headers(builder), access_token)
}

pub(crate) fn group_captures_by_title(
    mut captures: Vec<CloudMediaCapture>,
) -> Vec<CloudMediaCaptureGroup> {
    captures.sort_by(|left, right| right.upload_date.cmp(&left.upload_date));

    let mut groups = BTreeMap::<(String, String), CloudMediaCaptureGroup>::new();
    for capture in captures {
        let title_id = capture
            .sce_title_id
            .clone()
            .unwrap_or_else(|| "unknown-title".to_string());
        let title_name = capture
            .sce_title_name
            .clone()
            .unwrap_or_else(|| "Unknown Title".to_string());
        let key = (title_name.clone(), title_id.clone());

        groups
            .entry(key)
            .and_modify(|group| group.captures.push(capture.clone()))
            .or_insert_with(|| CloudMediaCaptureGroup {
                title_id,
                title_name,
                concept_id: capture.concept_id.clone(),
                title_image_url: capture.title_image_url.clone(),
                captures: vec![capture],
            });
    }

    let mut groups = groups.into_values().collect::<Vec<_>>();
    groups.sort_by(|left, right| {
        let left_latest = left
            .captures
            .first()
            .and_then(|capture| capture.upload_date.as_ref());
        let right_latest = right
            .captures
            .first()
            .and_then(|capture| capture.upload_date.as_ref());
        right_latest.cmp(&left_latest)
    });
    groups
}

pub(crate) fn resolve_primary_download_url(urls: &CloudMediaCaptureUrls) -> Option<&str> {
    urls.screenshot_url
        .as_deref()
        .or(urls.download_url.as_deref())
        .or(urls.video_url.as_deref())
}

pub(crate) fn file_name_for_download(
    ugc_id: &str,
    source_url: &str,
    content_type: Option<&str>,
) -> String {
    let extension = content_type
        .and_then(file_extension_for_content_type)
        .or_else(|| {
            Url::parse(source_url).ok().and_then(|url| {
                let path = url.path();
                path.rsplit('.').next().and_then(|candidate| {
                    if candidate == path {
                        None
                    } else {
                        Some(candidate.to_ascii_lowercase())
                    }
                })
            })
        })
        .unwrap_or_else(|| "bin".to_string());

    format!("{ugc_id}.{extension}")
}

fn file_extension_for_content_type(content_type: &str) -> Option<String> {
    let normalized = content_type
        .split(';')
        .next()
        .unwrap_or(content_type)
        .trim()
        .to_ascii_lowercase();

    match normalized.as_str() {
        "image/jpeg" => Some("jpg".to_string()),
        "image/png" => Some("png".to_string()),
        "image/webp" => Some("webp".to_string()),
        "video/mp4" => Some("mp4".to_string()),
        "application/vnd.apple.mpegurl" | "application/x-mpegurl" => Some("m3u8".to_string()),
        _ => None,
    }
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

    fn query_value(request: &reqwest::Request, name: &str) -> String {
        request
            .url()
            .query_pairs()
            .find_map(|(key, value)| (key == name).then(|| value.into_owned()))
            .unwrap_or_else(|| panic!("missing query parameter {name}"))
    }

    fn assert_mobile_headers(request: &reqwest::Request) {
        assert_eq!(header(request, "authorization"), "Bearer TEST_ACCESS_TOKEN");
        assert_eq!(header(request, "accept-language"), "en-US");
        assert_eq!(header(request, "accept-encoding"), "gzip");
        assert_eq!(
            header(request, "user-agent"),
            consts::DEFAULT_OKHTTP_USER_AGENT
        );
        assert_eq!(
            header(request, "if-modified-since"),
            "Sat, 2 May 2026 11:47:00 +0000"
        );
        assert_eq!(header(request, "x-psn-sampled"), "0");
        assert_eq!(
            header(request, "x-psn-app-ver"),
            consts::DEFAULT_APP_VERSION
        );
    }

    #[test]
    fn gallery_request_includes_limit_and_optional_cursor_without_network() {
        with_test_now(fixed_now(), || {
            let request = get_cloud_media_gallery(
                &Client::new(),
                test_access_token(),
                100,
                Some("TEST_CURSOR"),
            )
            .build()
            .expect("request should build");

            assert_eq!(request.method(), Method::GET);
            assert_eq!(
                request.url().path(),
                "/api/gameMediaService/v2/c2s/category/cloudMediaGallery/ugcType/all"
            );
            assert_eq!(query_value(&request, "includeTokenizedUrls"), "true");
            assert_eq!(query_value(&request, "limit"), "100");
            assert_eq!(query_value(&request, "cursorMark"), "TEST_CURSOR");
            assert_mobile_headers(&request);
        });
    }

    #[test]
    fn gallery_request_omits_cursor_when_absent() {
        with_test_now(fixed_now(), || {
            let request = get_cloud_media_gallery(&Client::new(), test_access_token(), 50, None)
                .build()
                .expect("request should build");

            assert_eq!(query_value(&request, "limit"), "50");
            assert!(
                !request
                    .url()
                    .query_pairs()
                    .any(|(key, _)| key == "cursorMark"),
                "cursorMark should be omitted when not provided"
            );
        });
    }

    #[test]
    fn capture_content_request_includes_fields_and_ugc_id() {
        with_test_now(fixed_now(), || {
            let request =
                get_cloud_media_capture_content(&Client::new(), test_access_token(), "TEST_UGC_ID")
                    .build()
                    .expect("request should build");

            assert_eq!(request.url().path(), "/api/gameMediaService/v2/c2s/content");
            assert_eq!(query_value(&request, "fields"), CLOUD_MEDIA_FIELDS);
            assert_eq!(query_value(&request, "ugcIds"), "TEST_UGC_ID");
            assert_mobile_headers(&request);
        });
    }

    #[test]
    fn capture_urls_request_uses_signed_url_endpoint() {
        with_test_now(fixed_now(), || {
            let request =
                get_cloud_media_capture_urls(&Client::new(), test_access_token(), "TEST_UGC_ID")
                    .build()
                    .expect("request should build");

            assert_eq!(
                request.url().path(),
                "/api/gameMediaService/v2/c2s/ugc/TEST_UGC_ID/url"
            );
            assert_mobile_headers(&request);
        });
    }
}
