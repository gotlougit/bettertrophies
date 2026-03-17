uniffi::include_scaffolding!("stationplayer");

use reqwest::{Client, RequestBuilder};
use serde::de::DeserializeOwned;
use serde_json::Value;
use std::collections::HashMap;
use std::sync::OnceLock;
use tokio::runtime::{Builder, Runtime};

use crate::auth::AccessToken;
use crate::headers::generate_graphql_correlation_id;
pub use crate::playedtitles::{
    CodexSummary, Media, RecentPlayedTitle, RecentPlayedTitlesData, RecentPlayedTitlesResponse,
    Title,
};
pub use crate::profile::{Avatar, MyInfo, PersonalInfo};
pub use crate::trophy::{
    GraphQLError, HintAvailabilityResponse, Trophy, TrophyAppearanceSetting, TrophyDetail,
    TrophyDistributions, TrophyGroup, TrophyGroupsResponse, UserGameTrophyInfo, UserTrophySummary,
};

mod auth;
#[allow(dead_code)]
mod consts;
mod graphql;
mod headers;
pub mod playedtitles;
pub mod profile;
pub mod trophy;

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_gotlou_bettertrophies_StationPlayerLoader_initializeRustlsPlatformVerifier(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    context: jni::objects::JObject,
) {
    rustls_platform_verifier::android::init_with_env(&mut env, context)
        .expect("failed to initialize rustls-platform-verifier");
}

#[derive(Debug, thiserror::Error)]
pub enum StationPlayerError {
    #[error("request failed")]
    Request,
    #[error("resource not found")]
    NotFound,
    #[error("failed to parse response")]
    Parse,
    #[error("could not resolve trophy title service info")]
    MissingTitleServiceInfo,
}

impl From<reqwest::Error> for StationPlayerError {
    fn from(error: reqwest::Error) -> Self {
        if error.status() == Some(reqwest::StatusCode::NOT_FOUND) {
            Self::NotFound
        } else {
            Self::Request
        }
    }
}

type StationPlayerResult<T> = std::result::Result<T, StationPlayerError>;

fn runtime() -> &'static Runtime {
    static RUNTIME: OnceLock<Runtime> = OnceLock::new();
    RUNTIME.get_or_init(|| {
        Builder::new_multi_thread()
            .enable_all()
            .build()
            .expect("failed to build tokio runtime for stationplayer")
    })
}

pub fn generate_sign_in_url(redirect_url: Option<String>) -> String {
    auth::generate_sign_in_url(redirect_url.as_deref())
}

/// Authenticated PlayStation client wrapper.
///
/// `init` populates the bearer token and resolves the current account ID up
/// front so the instance can issue profile, trophy, and GraphQL requests.
pub struct StationPlayer {
    client: Client,
    access_token: Option<AccessToken>,
    account_id: Option<String>,
    graphql_correlation_id: String,
}

/// Creates an authenticated [`StationPlayer`] that is ready for later API calls.
///
/// The returned client already has a bearer token and the current account ID
/// populated, so callers can immediately fetch profile, trophy, and GraphQL
/// data without doing any extra setup.
///
/// This is the entry point for the library: call it once with an NPSSO token,
/// keep the returned [`StationPlayer`], and reuse that client for the rest of
/// the session.
async fn init_with_client(client: Client, npsso: &str) -> StationPlayerResult<StationPlayer> {
    let response = auth::get_auth_token(&client, npsso)
        .send()
        .await
        .map_err(StationPlayerError::from)?;
    let text = response.text().await.map_err(StationPlayerError::from)?;
    let access_token = auth::get_access_token(&text).map_err(|_| StationPlayerError::Parse)?;
    let mut station_player = StationPlayer {
        client,
        access_token: Some(access_token),
        account_id: None,
        graphql_correlation_id: generate_graphql_correlation_id(),
    };
    station_player.get_account_info().await?;
    Ok(station_player)
}

impl StationPlayer {
    pub fn init(npsso: String) -> StationPlayerResult<Self> {
        runtime().block_on(Self::init_async(npsso))
    }

    async fn init_async(npsso: String) -> StationPlayerResult<Self> {
        init_with_client(Client::new(), &npsso).await
    }

    fn access_token(&self) -> AccessToken {
        self.access_token
            .clone()
            .expect("Can't call this API without access token")
    }

    fn account_id(&self) -> String {
        self.account_id
            .clone()
            .expect("Can't call this API without account ID")
    }

    async fn send_json<T>(builder: RequestBuilder) -> Result<T, reqwest::Error>
    where
        T: DeserializeOwned,
    {
        builder.send().await?.error_for_status()?.json::<T>().await
    }

    /// Returns the signed-in account as [`auth::AccountInfo`].
    ///
    /// The most important field is `account_id`, because nearly every other API
    /// call in this crate needs it. Optional `account_devices` data can also be
    /// useful if a caller wants to inspect which devices are associated with the
    /// account.
    ///
    /// This method also caches `account_id` inside the client, which is why
    /// [`init`] calls it during construction.
    async fn get_account_info(&mut self) -> Result<auth::AccountInfo, reqwest::Error> {
        let account_info: auth::AccountInfo =
            Self::send_json(auth::get_account_info(&self.client, self.access_token())).await?;
        self.account_id = Some(account_info.account_id.clone());
        Ok(account_info)
    }

    /// Returns the current user's profile as [`profile::MyInfo`].
    ///
    /// This is the account-level identity record for the signed-in user. Callers
    /// typically care about `online_id`, `personal_detail`, `about_me`,
    /// `languages`, subscription and verification flags, and `avatars`.
    ///
    /// That information is useful for showing who the account belongs to,
    /// displaying profile art, or attaching human-readable identity data to
    /// trophy and recently-played results.
    pub fn get_profile(&self) -> StationPlayerResult<profile::MyInfo> {
        runtime().block_on(self.get_profile_async())
    }

    async fn get_profile_async(&self) -> StationPlayerResult<profile::MyInfo> {
        Self::send_json(profile::get_user_info(
            &self.client,
            &self.account_id(),
            self.access_token(),
        ))
        .await
        .map_err(StationPlayerError::from)
    }

    /// Returns the account-wide trophy summary as [`trophy::UserTrophySummary`].
    ///
    /// The returned value answers "how far along is this account overall?" It
    /// includes the trophy level, tier, progress toward the next level, total
    /// trophy points, and the earned bronze/silver/gold/platinum counts.
    ///
    /// This is the high-level trophy overview to use before drilling into
    /// individual games or individual trophies.
    pub fn trophy_summary(&self) -> StationPlayerResult<trophy::UserTrophySummary> {
        runtime().block_on(self.trophy_summary_async())
    }

    async fn trophy_summary_async(&self) -> StationPlayerResult<trophy::UserTrophySummary> {
        Self::send_json(trophy::get_trophy_summary(
            &self.client,
            &self.account_id(),
            self.access_token(),
        ))
        .await
        .map_err(StationPlayerError::from)
    }

    /// Returns one page of trophy-title progress as [`trophy::UserTrophyByTitle`].
    ///
    /// The `games` field contains one [`trophy::UserGameTrophyInfo`] per title,
    /// including the game's name, platform, completion progress, earned versus
    /// defined trophy counts, and the service identifiers needed for deeper
    /// trophy lookups.
    ///
    /// `next_offset` tells you whether there is another page. This method is the
    /// right choice when you want to build a paginated game list or decide which
    /// title to inspect next.
    async fn get_trophy_titles(
        &self,
        offset: u32,
    ) -> Result<trophy::UserTrophyByTitle, reqwest::Error> {
        Self::send_json(trophy::get_trophy_titles(
            &self.client,
            &self.account_id(),
            self.access_token(),
            offset,
        ))
        .await
    }

    /// Returns every trophy title that has trophy history for the current user.
    ///
    /// This method keeps requesting trophy-title pages until there are no more
    /// and returns the merged `Vec<trophy::UserGameTrophyInfo>`.
    ///
    /// Use this when you care about the complete set of games rather than page
    /// management. The returned entries still include the service identifiers
    /// needed to fetch trophy groups and trophy details for a selected title.
    pub fn get_all_user_trophy_games(
        &self,
    ) -> StationPlayerResult<Vec<trophy::UserGameTrophyInfo>> {
        runtime().block_on(self.get_all_user_trophy_games_async())
    }

    async fn get_all_user_trophy_games_async(
        &self,
    ) -> StationPlayerResult<Vec<trophy::UserGameTrophyInfo>> {
        let mut offset = 0;
        let mut all_games = Vec::new();

        loop {
            let mut page = self
                .get_trophy_titles(offset)
                .await
                .map_err(StationPlayerError::from)?;
            all_games.append(&mut page.games);

            match page.next_offset {
                Some(next_offset) => offset = next_offset,
                None => break,
            }
        }

        Ok(all_games)
    }

    /// Returns trophy-title lookup data for specific NP title IDs as
    /// [`trophy::TrophyTitlesByTitleIds`].
    ///
    /// Each entry in `titles` ties a title ID to the richer trophy-title record
    /// for that game. The useful parts are the title metadata, progress/count
    /// summaries, rarest trophy hints, and especially `np_communication_id` plus
    /// `np_service_name`.
    ///
    /// Those service identifiers are important because callers often start with a
    /// plain title ID but need the communication/service pair before they can ask
    /// for trophy groups or individual trophies.
    async fn trophy_titles_include_not_earned(
        &self,
        np_title_ids: &[String],
    ) -> Result<trophy::TrophyTitlesByTitleIds, reqwest::Error> {
        Self::send_json(trophy::get_trophy_titles_include_not_earned(
            &self.client,
            &self.account_id(),
            self.access_token(),
            np_title_ids,
        ))
        .await
    }

    /// Returns every trophy for one title ID as a flat `Vec<trophy::Trophy>`.
    ///
    /// This is the simplest way to go from "I know the game's NP title ID" to
    /// "give me all of its trophies." The returned list combines user progress
    /// fields such as `earned` and `earned_date_time` with trophy metadata such
    /// as `trophy_name`, `trophy_detail`, `trophy_type`, rarity, and icons when
    /// available.
    ///
    /// That merged shape matters because callers usually want one list they can
    /// render directly, without manually joining user-specific data to the
    /// definition-only trophy records.
    pub fn get_all_trophies_for_title_id(
        &self,
        title_id: String,
    ) -> StationPlayerResult<Vec<trophy::Trophy>> {
        runtime().block_on(self.get_all_trophies_for_title_id_async(title_id))
    }

    async fn get_all_trophies_for_title_id_async(
        &self,
        title_id: String,
    ) -> StationPlayerResult<Vec<trophy::Trophy>> {
        let title_ids = [title_id.clone()];
        let title_lookup = self
            .trophy_titles_include_not_earned(&title_ids)
            .await
            .map_err(StationPlayerError::from)?;
        let (np_communication_id, np_service_name) =
            trophy::resolve_title_service_info(&title_lookup, &title_id)
                .ok_or(StationPlayerError::MissingTitleServiceInfo)?;

        self.trophies_by_np_communication_id(&np_communication_id, &np_service_name)
            .await
    }

    /// Returns every trophy for one communication/service pair as a flat
    /// `Vec<trophy::Trophy>`.
    ///
    /// Use this when a caller already has the service identifiers from a trophy
    /// title listing response and does not need title-ID lookup first.
    pub fn get_all_trophies_for_communication_id(
        &self,
        np_communication_id: String,
        np_service_name: String,
    ) -> StationPlayerResult<Vec<trophy::Trophy>> {
        runtime().block_on(
            self.get_all_trophies_for_communication_id_async(np_communication_id, np_service_name),
        )
    }

    async fn get_all_trophies_for_communication_id_async(
        &self,
        np_communication_id: String,
        np_service_name: String,
    ) -> StationPlayerResult<Vec<trophy::Trophy>> {
        self.trophies_by_np_communication_id(&np_communication_id, &np_service_name)
            .await
    }

    /// Returns every trophy for one title ID grouped by `group_id`.
    ///
    /// The returned [`BTreeMap`] uses each trophy group's `group_id` as the key
    /// and the merged `Vec<trophy::Trophy>` for that group as the value. Each
    /// trophy in the map combines user progress with definition metadata.
    ///
    /// Use this when the caller wants to present trophies grouped by base game,
    /// DLC pack, or other trophy-group boundaries instead of flattening the
    /// whole title into one list.
    pub fn trophy_group_trophies_by_title_id(
        &self,
        title_id: String,
    ) -> StationPlayerResult<HashMap<String, Vec<trophy::Trophy>>> {
        runtime().block_on(self.trophy_group_trophies_by_title_id_async(title_id))
    }

    async fn trophy_group_trophies_by_title_id_async(
        &self,
        title_id: String,
    ) -> StationPlayerResult<HashMap<String, Vec<trophy::Trophy>>> {
        let title_ids = [title_id.clone()];
        let title_lookup = self
            .trophy_titles_include_not_earned(&title_ids)
            .await
            .map_err(StationPlayerError::from)?;
        let (np_communication_id, np_service_name) =
            trophy::resolve_title_service_info(&title_lookup, &title_id)
                .ok_or(StationPlayerError::MissingTitleServiceInfo)?;

        let trophy_groups = self
            .trophy_groups_async(np_communication_id.clone(), Some(np_service_name.clone()))
            .await?;
        let mut grouped_trophies = HashMap::new();

        for group in trophy_groups.groups {
            let trophies = self
                .trophy_group_trophies_async(
                    np_communication_id.clone(),
                    group.group_id.clone(),
                    Some(np_service_name.clone()),
                )
                .await?;
            grouped_trophies.insert(group.group_id, trophies);
        }

        Ok(grouped_trophies)
    }

    /// Returns every trophy for one `npCommunicationId` as a flat
    /// `Vec<trophy::Trophy>`.
    ///
    /// Unlike the lower-level group APIs, this hides the group-by-group fetches
    /// and returns one merged list. Each [`trophy::Trophy`] can contain both the
    /// user's state for that trophy and the static trophy definition fields.
    ///
    /// Use this when you already know the communication ID and want a single
    /// caller-friendly collection for rendering or filtering.
    async fn trophies_by_np_communication_id(
        &self,
        np_communication_id: &str,
        np_service_name: &str,
    ) -> StationPlayerResult<Vec<trophy::Trophy>> {
        let trophy_groups = self
            .trophy_groups_async(
                np_communication_id.to_string(),
                Some(np_service_name.to_string()),
            )
            .await?;

        let mut trophies = Vec::new();
        for group in trophy_groups.groups {
            let user_trophies = self
                .user_trophy_group_trophies(
                    np_communication_id,
                    &group.group_id,
                    Some(np_service_name),
                )
                .await
                .map_err(StationPlayerError::from)?;
            let defined_trophies = self
                .defined_trophy_group_trophies(
                    np_communication_id,
                    &group.group_id,
                    Some(np_service_name),
                )
                .await
                .map_err(StationPlayerError::from)?;
            trophies.extend(trophy::merge_trophy_group_trophies(
                user_trophies,
                defined_trophies,
            ));
        }

        Ok(trophies)
    }

    /// Returns trophy-group summary data as [`trophy::TrophyGroupsResponse`].
    ///
    /// The response tells you how a title is divided into trophy groups and how
    /// far the user has progressed in each one. The top-level fields summarize
    /// overall counts and progress, while `groups` contains the per-group IDs,
    /// names, icons, completion percentages, and trophy distributions.
    ///
    /// This is useful when a title has DLC sets or multiple trophy packs and you
    /// want to present progress broken down by group before fetching each
    /// group's trophies.
    pub fn trophy_groups(
        &self,
        np_communication_id: String,
        np_service_name: Option<String>,
    ) -> StationPlayerResult<trophy::TrophyGroupsResponse> {
        runtime().block_on(self.trophy_groups_async(np_communication_id, np_service_name))
    }

    async fn trophy_groups_async(
        &self,
        np_communication_id: String,
        np_service_name: Option<String>,
    ) -> StationPlayerResult<trophy::TrophyGroupsResponse> {
        Self::send_json(trophy::get_trophy_groups(
            &self.client,
            &self.account_id(),
            self.access_token(),
            &np_communication_id,
            np_service_name.as_deref(),
        ))
        .await
        .map_err(StationPlayerError::from)
    }

    /// Returns every trophy in one trophy group as a merged `Vec<trophy::Trophy>`.
    ///
    /// Each returned trophy combines the user's state for that trophy with the
    /// static trophy definition data, so the caller gets one directly usable
    /// record per trophy instead of having to join two responses.
    ///
    /// Use this when you want the trophies for a specific group, such as a base
    /// game set or one DLC pack, rather than every trophy in the title.
    pub fn trophy_group_trophies(
        &self,
        np_communication_id: String,
        group_id: String,
        np_service_name: Option<String>,
    ) -> StationPlayerResult<Vec<trophy::Trophy>> {
        runtime().block_on(self.trophy_group_trophies_async(
            np_communication_id,
            group_id,
            np_service_name,
        ))
    }

    async fn trophy_group_trophies_async(
        &self,
        np_communication_id: String,
        group_id: String,
        np_service_name: Option<String>,
    ) -> StationPlayerResult<Vec<trophy::Trophy>> {
        let user_trophies = self
            .user_trophy_group_trophies(&np_communication_id, &group_id, np_service_name.as_deref())
            .await
            .map_err(StationPlayerError::from)?;
        let defined_trophies = self
            .defined_trophy_group_trophies(
                &np_communication_id,
                &group_id,
                np_service_name.as_deref(),
            )
            .await
            .map_err(StationPlayerError::from)?;
        Ok(trophy::merge_trophy_group_trophies(
            user_trophies,
            defined_trophies,
        ))
    }

    async fn user_trophy_group_trophies(
        &self,
        np_communication_id: &str,
        group_id: &str,
        np_service_name: Option<&str>,
    ) -> Result<trophy::TrophyGroupTrophiesResponse, reqwest::Error> {
        Self::send_json(trophy::get_trophy_group_trophies(
            &self.client,
            &self.account_id(),
            self.access_token(),
            np_communication_id,
            group_id,
            np_service_name,
        ))
        .await
    }

    async fn defined_trophy_group_trophies(
        &self,
        np_communication_id: &str,
        group_id: &str,
        np_service_name: Option<&str>,
    ) -> Result<trophy::TrophyGroupTrophiesResponse, reqwest::Error> {
        Self::send_json(trophy::get_defined_trophy_group_trophies(
            &self.client,
            self.access_token(),
            np_communication_id,
            group_id,
            np_service_name,
        ))
        .await
    }

    /// Returns one trophy as a merged [`trophy::TrophyDetail`].
    ///
    /// The returned value combines the signed-in user's trophy state with the
    /// canonical trophy definition, so fields like `earned`, `earned_date_time`,
    /// `trophy_name`, `trophy_detail`, `trophy_type`, rarity, and icon data are
    /// available in one place whenever the upstream services provide them.
    ///
    /// This is the detailed trophy API most callers should use after selecting a
    /// trophy from a list.
    pub fn trophy_detail(
        &self,
        np_communication_id: String,
        trophy_id: String,
        np_service_name: Option<String>,
    ) -> StationPlayerResult<trophy::TrophyDetail> {
        runtime().block_on(self.trophy_detail_async(
            np_communication_id,
            trophy_id,
            np_service_name,
        ))
    }

    async fn trophy_detail_async(
        &self,
        np_communication_id: String,
        trophy_id: String,
        np_service_name: Option<String>,
    ) -> StationPlayerResult<trophy::TrophyDetail> {
        let user_trophy = self
            .user_trophy_detail(&np_communication_id, &trophy_id, np_service_name.as_deref())
            .await
            .map_err(StationPlayerError::from)?;
        let defined_trophy = self
            .trophy_definition_detail(&np_communication_id, &trophy_id, np_service_name.as_deref())
            .await
            .map_err(StationPlayerError::from)?;
        Ok(trophy::merge_trophy_details(user_trophy, defined_trophy))
    }

    async fn user_trophy_detail(
        &self,
        np_communication_id: &str,
        trophy_id: &str,
        np_service_name: Option<&str>,
    ) -> Result<trophy::TrophyDetail, reqwest::Error> {
        Self::send_json(trophy::get_trophy_detail(
            &self.client,
            &self.account_id(),
            self.access_token(),
            np_communication_id,
            trophy_id,
            np_service_name,
        ))
        .await
    }

    async fn trophy_definition_detail(
        &self,
        np_communication_id: &str,
        trophy_id: &str,
        np_service_name: Option<&str>,
    ) -> Result<trophy::TrophyDetail, reqwest::Error> {
        Self::send_json(trophy::get_defined_trophy_detail(
            &self.client,
            self.access_token(),
            np_communication_id,
            trophy_id,
            np_service_name,
        ))
        .await
    }

    /// Returns trophy visibility settings as [`trophy::TrophyAppearanceSetting`].
    ///
    /// This tells you how trophy information for a title is configured to be
    /// shown. The key fields are `visibility`, `reveal_all_trophies`, and
    /// `can_display_all_trophies`, which are the values a UI would use to decide
    /// whether hidden trophies can be exposed.
    ///
    /// Callers should expect this to be optional in practice; some titles do not
    /// expose appearance settings at all.
    pub fn trophy_appearance_setting(
        &self,
        np_communication_id: String,
        np_service_name: Option<String>,
    ) -> StationPlayerResult<trophy::TrophyAppearanceSetting> {
        runtime()
            .block_on(self.trophy_appearance_setting_async(np_communication_id, np_service_name))
    }

    async fn trophy_appearance_setting_async(
        &self,
        np_communication_id: String,
        np_service_name: Option<String>,
    ) -> StationPlayerResult<trophy::TrophyAppearanceSetting> {
        Self::send_json(trophy::get_trophy_appearance_setting(
            &self.client,
            self.access_token(),
            &np_communication_id,
            np_service_name.as_deref(),
        ))
        .await
        .map_err(StationPlayerError::from)
    }

    /// Returns raw hint-availability data as [`trophy::HintAvailabilityResponse`].
    ///
    /// The return type is a generic GraphQL wrapper with `data` and `errors`.
    /// `data` tells you whether the service exposed hint information for the
    /// requested trophy IDs; `errors` lets callers distinguish "no hints" from
    /// "the hint query failed".
    ///
    /// The payload stays loosely typed on purpose. This method is mainly useful
    /// when a caller only needs to know whether hint content exists, not to fully
    /// model the GraphQL schema.
    pub fn trophy_hint_availability(
        &self,
        np_communication_id: String,
        trophy_ids: Vec<String>,
    ) -> StationPlayerResult<trophy::HintAvailabilityResponse> {
        runtime().block_on(self.trophy_hint_availability_async(np_communication_id, trophy_ids))
    }

    async fn trophy_hint_availability_async(
        &self,
        np_communication_id: String,
        trophy_ids: Vec<String>,
    ) -> StationPlayerResult<trophy::HintAvailabilityResponse> {
        let response: trophy::GraphQLResponse<Value> =
            Self::send_json(trophy::get_hint_availability(
                &self.client,
                self.access_token(),
                &self.graphql_correlation_id,
                &np_communication_id,
                &trophy_ids,
            ))
            .await
            .map_err(StationPlayerError::from)?;
        Ok(trophy::HintAvailabilityResponse {
            data_json: response.data.map(|data| data.to_string()),
            errors: response.errors,
        })
    }

    /// Returns recently played titles as
    /// [`playedtitles::RecentPlayedTitlesResponse`].
    ///
    /// The useful data lives in `data.recent_played_titles`: a list of
    /// [`playedtitles::RecentPlayedTitle`] values with the game's title metadata,
    /// platform, playtime, optional story progress, help-content availability,
    /// and any attached media or codex summary.
    ///
    /// This is the high-level "what has this account been playing lately?"
    /// query. It is especially useful for building a recent activity view or
    /// choosing a title to inspect further.
    ///
    /// This method uses a tolerant UTF-8-lossy parse path because the upstream
    /// response body is not always cleanly decodable.
    pub fn recent_played_titles(
        &self,
        count: Option<u32>,
    ) -> StationPlayerResult<playedtitles::RecentPlayedTitlesResponse> {
        runtime().block_on(self.recent_played_titles_async(count))
    }

    async fn recent_played_titles_async(
        &self,
        count: Option<u32>,
    ) -> StationPlayerResult<playedtitles::RecentPlayedTitlesResponse> {
        let correlation_id: &str = self.graphql_correlation_id.as_ref();
        match playedtitles::get_played_titles(
            &self.client,
            &self.account_id(),
            self.access_token(),
            correlation_id,
            count,
        )
        .send()
        .await
        {
            Ok(response) => {
                let body = response.bytes().await.map_err(StationPlayerError::from)?;
                let recent_played_titles =
                    graphql::parse_json_utf8_lossy(&body).map_err(|_| StationPlayerError::Parse)?;
                Ok(recent_played_titles)
            }
            Err(e) => Err(StationPlayerError::from(e)),
        }
    }
}
