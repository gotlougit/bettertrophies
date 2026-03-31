use rand::prelude::IndexedRandom;
use serde::Serialize;
use std::env;
use std::error::Error;

fn parse_args() -> Option<String> {
    let mut argv = env::args().skip(1);
    let mut npsso = env::var("PSN_NPSSO").ok();

    while let Some(arg) = argv.next() {
        match arg.as_str() {
            "--npsso" => {
                let value = argv.next().ok_or("missing value for --npsso").unwrap();
                npsso = Some(value);
            }
            "--help" | "-h" => {
                print_usage();
                std::process::exit(0);
            }
            _ => return None,
        }
    }

    npsso
}

fn print_usage() {
    println!("Usage: cargo run -p profile-smoke -- --npsso <token>");
    println!();
    println!("Environment variables:");
    println!("  PSN_NPSSO          NPSSO token used to fetch an access token");
}

fn print_user_info(user_info: &stationplayer::profile::MyInfo) {
    println!("profile:");
    println!("  online id: {}", user_info.online_id);

    let full_name = format!(
        "{} {}",
        user_info.personal_detail.first_name.trim(),
        user_info.personal_detail.last_name.trim()
    )
    .trim()
    .to_string();
    if !full_name.is_empty() {
        println!("  name: {full_name}");
    }
    if !user_info.about_me.trim().is_empty() {
        println!("  about: {}", user_info.about_me.trim());
    }
    if !user_info.languages.is_empty() {
        println!("  languages: {}", user_info.languages.join(", "));
    }
    println!("  plus: {}", yes_no(user_info.is_plus));
    println!("  verified: {}", yes_no(user_info.is_verified));

    if let Some(avatar) = user_info.avatars.first() {
        println!("  avatar: {} ({})", avatar.url, avatar.size);
    }
}

fn print_trophy_summary(summary: &stationplayer::trophy::UserTrophySummary) {
    println!("trophy summary:");
    println!("  level: {}", summary.trophy_level);
    println!("  tier: {}", summary.tier);
    println!("  progress: {}%", summary.progress);
    println!("  points: {}", summary.trophy_point);
    println!(
        "  trophies: P {}  G {}  S {}  B {}",
        summary.earned_trophies.platinum,
        summary.earned_trophies.gold,
        summary.earned_trophies.silver,
        summary.earned_trophies.bronze
    );
}

fn print_trophy_titles(titles: &[stationplayer::trophy::UserGameTrophyInfo]) {
    println!("trophy titles: {}", titles.len());
    for game in titles {
        println!(
            "  {} [{}] {}%  P {} G {} S {} B {}  updated {}",
            game.title,
            game.platform,
            game.progress,
            game.earned_trophies.platinum,
            game.earned_trophies.gold,
            game.earned_trophies.silver,
            game.earned_trophies.bronze,
            game.last_updated
        );
    }
}

fn normalized_title(value: &str) -> String {
    value
        .chars()
        .map(|ch| {
            if ch.is_ascii_alphanumeric() || ch.is_ascii_whitespace() {
                ch.to_ascii_lowercase()
            } else {
                ' '
            }
        })
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
}

fn title_matches_query(title: &str, query: &str) -> bool {
    let normalized_game_title = normalized_title(title);
    let normalized_query = normalized_title(query);

    normalized_game_title.contains(&normalized_query)
        || normalized_game_title.contains("arkham city")
        || normalized_query
            .split_whitespace()
            .all(|term| normalized_game_title.contains(term))
}

fn format_trophy_counts(trophies: &stationplayer::trophy::TrophyDistributions) -> String {
    format!(
        "P {} G {} S {} B {}",
        trophies.platinum, trophies.gold, trophies.silver, trophies.bronze
    )
}

fn print_trophy_title_lookups(lookups: &stationplayer::trophy::TrophyTitlesByTitleIds) {
    println!("trophy titles include not earned:");
    for title in &lookups.titles {
        println!("  np title id: {}", title.np_title_id);
        if title.trophy_titles.is_empty() {
            println!("    no matching trophy title data");
            continue;
        }

        for trophy_title in &title.trophy_titles {
            let name = trophy_title.title.as_deref().unwrap_or("<unknown>");
            let platform = trophy_title.platform.as_deref().unwrap_or("<unknown>");
            let progress = trophy_title
                .progress
                .map(|value| format!("{value}%"))
                .unwrap_or_else(|| "n/a".to_string());
            let not_earned = if trophy_title.not_earned_trophy_ids.is_empty() {
                "none".to_string()
            } else {
                trophy_title
                    .not_earned_trophy_ids
                    .iter()
                    .map(u32::to_string)
                    .collect::<Vec<_>>()
                    .join(", ")
            };

            println!("    {name} [{platform}] {progress}");
            if let Some(earned) = &trophy_title.earned_trophies {
                println!("      earned: {}", format_trophy_counts(earned));
            }
            if let Some(defined) = &trophy_title.defined_trophies {
                println!("      total: {}", format_trophy_counts(defined));
            }
            println!(
                "      not earned ids: {}",
                truncate_with_ellipsis(&not_earned, 80)
            );
        }
    }
}

fn print_graphql_status(label: &str, value: &stationplayer::HintAvailabilityResponse) {
    let error_count = value.errors.as_ref().map_or(0, Vec::len);
    println!(
        "{label}: data {}  errors {}",
        yes_no(value.data_json.is_some()),
        error_count
    );

    if let Some(errors) = &value.errors {
        for error in errors {
            println!("  error: {}", error.message);
        }
    }
}

fn print_codex_summary(response: &stationplayer::trophy::CodexSummaryResponse) {
    let error_count = response.errors().map_or(0, |errors| errors.len());
    println!(
        "trophy codex summary: data {}  errors {}",
        yes_no(response.data().is_some()),
        error_count
    );

    if let Some(errors) = response.errors() {
        for error in errors {
            println!("  error: {}", error.message);
        }
    }

    if let Some(data) = response.data() {
        for title in &data.titles {
            println!("  np title id: {}", title.np_title_id);
            if title.trophy_titles.is_empty() {
                println!("    no matching trophy title data");
                continue;
            }

            for trophy_title in &title.trophy_titles {
                let name = trophy_title.title.as_deref().unwrap_or("<unknown>");
                let progress = trophy_title
                    .progress
                    .map(|value| format!("{value}%"))
                    .unwrap_or_else(|| "n/a".to_string());
                let service_name = trophy_title
                    .np_service_name
                    .as_deref()
                    .unwrap_or("<unknown>");
                println!("    {name} [{service_name}] {progress}");
                if let Some(earned) = &trophy_title.earned_trophies {
                    println!("      earned: {}", format_trophy_counts(earned));
                }
                if let Some(defined) = &trophy_title.defined_trophies {
                    println!("      total: {}", format_trophy_counts(defined));
                }
                if !trophy_title.rarest_trophies.is_empty() {
                    println!(
                        "      rarest trophy: {}",
                        format_trophy_line(&trophy_title.rarest_trophies[0], None)
                    );
                }
            }
        }
    }
}

fn print_trophy_groups(response: &stationplayer::trophy::TrophyGroupsResponse) {
    println!("trophy groups: {}", response.groups.len());
    if let Some(progress) = response.progress {
        println!("  overall progress: {progress}%");
    }
    if let Some(earned) = &response.earned_trophies {
        println!("  earned: {}", format_trophy_counts(earned));
    }
    if let Some(defined) = &response.defined_trophies {
        println!("  total: {}", format_trophy_counts(defined));
    }
    for group in &response.groups {
        let name = group.group_name.as_deref().unwrap_or("<default>");
        let progress = group
            .progress
            .map(|value| format!("{value}%"))
            .unwrap_or_else(|| "n/a".to_string());
        println!("  {} ({}) {}", group.group_id, name, progress);
    }
}

fn print_flattened_trophies(trophies: &[stationplayer::trophy::Trophy]) {
    println!("all trophies: {}", trophies.len());
    for trophy in trophies {
        println!("  {}", format_trophy_line(trophy, None));
    }
}

fn print_trophy_detail(
    detail: &stationplayer::trophy::TrophyDetail,
    defined: Option<&stationplayer::trophy::TrophyDetail>,
) {
    println!("trophy detail:");
    println!(
        "  {}",
        format_trophy_line(&detail.trophy, defined.map(|value| &value.trophy))
    );
    let detail_text = detail
        .trophy
        .trophy_detail
        .as_deref()
        .or_else(|| defined.and_then(|value| value.trophy.trophy_detail.as_deref()));
    if let Some(text) = detail_text {
        let text = text.trim();
        if !text.is_empty() {
            println!("  detail: {}", truncate_with_ellipsis(text, 120));
        }
    }
}

fn print_trophy_appearance_setting(setting: &stationplayer::trophy::TrophyAppearanceSetting) {
    println!("trophy appearance setting:");
    println!("  configured: {}", yes_no(setting.is_set.unwrap_or(false)));
    if let Some(visibility) = setting.visibility.as_deref() {
        println!("  visibility: {visibility}");
    }
    if let Some(reveal_all) = setting.reveal_all_trophies {
        println!("  reveal all trophies: {}", yes_no(reveal_all));
    }
    if let Some(can_display_all) = setting.can_display_all_trophies {
        println!("  can display all trophies: {}", yes_no(can_display_all));
    }
}

fn format_trophy_line(
    trophy: &stationplayer::trophy::Trophy,
    defined: Option<&stationplayer::trophy::Trophy>,
) -> String {
    let id = &trophy.trophy_id;
    let name = trophy
        .trophy_name
        .as_deref()
        .or_else(|| defined.and_then(|value| value.trophy_name.as_deref()))
        .unwrap_or("<unnamed trophy>");
    let trophy_type = trophy
        .trophy_type
        .as_deref()
        .or_else(|| defined.and_then(|value| value.trophy_type.as_deref()))
        .unwrap_or("<unknown>");
    let earned = trophy.earned.map(yes_no).unwrap_or("unknown");
    let rarity = trophy
        .trophy_earned_rate
        .as_deref()
        .or_else(|| trophy.progress.as_deref())
        .unwrap_or("n/a");
    let detail = trophy
        .trophy_detail
        .as_deref()
        .or_else(|| defined.and_then(|value| value.trophy_detail.as_deref()))
        .map(str::trim)
        .filter(|text| !text.is_empty())
        .map(|text| truncate_with_ellipsis(text, 120))
        .unwrap_or_else(|| "n/a".to_string());
    format!("#{id} {name} [{trophy_type}] earned {earned} rate {rarity} detail {detail}")
}

fn truncate_with_ellipsis(value: &str, max_chars: usize) -> String {
    let truncated = value.chars().take(max_chars).collect::<String>();
    if value.chars().count() > max_chars {
        format!("{truncated}...")
    } else {
        truncated
    }
}

fn print_recent_played_titles(
    recent_played_titles: &stationplayer::playedtitles::RecentPlayedTitlesResponse,
) {
    let titles = &recent_played_titles.data.recent_played_titles;
    println!("recent played titles: {}", titles.len());
    for game in titles {
        println!(
            "  {} [{}]  hours {}  story {}  codex {}  help {}",
            game.title.name,
            game.title.platform,
            game.play_time_hours,
            game.story_progress
                .map(|progress| format!("{progress}%"))
                .unwrap_or_else(|| "n/a".to_string()),
            yes_no(game.codex_summary.has_codex),
            yes_no(game.has_help_content),
        );
    }
}

fn print_cloud_media_capture_groups(groups: &[stationplayer::CloudMediaCaptureGroup]) {
    let capture_count = groups
        .iter()
        .map(|group| group.captures.len())
        .sum::<usize>();
    println!(
        "cloud media gallery: {} titles, {} captures",
        groups.len(),
        capture_count
    );

    for group in groups {
        println!(
            "  {} [{}] {} captures",
            group.title_name,
            group.title_id,
            group.captures.len()
        );

        for capture in &group.captures {
            println!("    {}", format_cloud_media_capture_line(capture));
        }
    }
}

fn format_cloud_media_capture_line(capture: &stationplayer::CloudMediaCapture) -> String {
    let mut details = Vec::new();
    details.push(format!("id {}", capture.id));

    if let Some(upload_date) = capture.upload_date.as_deref() {
        details.push(format!("captured {}", friendly_timestamp(upload_date)));
    }
    if let Some(capture_type) = capture.capture_type.as_deref() {
        details.push(capture_type.to_string());
    }
    if let Some(file_type) = capture.file_type.as_deref() {
        details.push(file_type.to_string());
    }
    if let Some(resolution) = capture.resolution.as_deref() {
        details.push(resolution.to_string());
    }
    if let Some(file_size) = capture.file_size {
        details.push(format_size(file_size));
    }
    if let Some(video_duration) = capture.video_duration {
        details.push(format!(
            "duration {}",
            format_duration_seconds(video_duration)
        ));
    }
    if let Some(platform) = capture.sce_platform.as_deref() {
        details.push(platform.to_string());
    }
    if let Some(is_spoiler) = capture.is_spoiler {
        details.push(format!("spoiler {}", yes_no(is_spoiler)));
    }
    if let Some(expire_at) = capture.expire_at.as_deref() {
        details.push(format!("expires {}", friendly_timestamp(expire_at)));
    }
    if let Some(transcode_status) = capture.transcode_status.as_deref() {
        details.push(format!("transcode {}", transcode_status));
    }

    details.join("  ")
}

fn friendly_timestamp(value: &str) -> String {
    value
        .strip_suffix('Z')
        .map(|timestamp| format!("{} UTC", timestamp.replace('T', " ")))
        .unwrap_or_else(|| value.replace('T', " "))
}

fn format_size(bytes: u64) -> String {
    const KIB: f64 = 1024.0;
    const MIB: f64 = 1024.0 * 1024.0;
    const GIB: f64 = 1024.0 * 1024.0 * 1024.0;
    let bytes_f64 = bytes as f64;

    if bytes_f64 >= GIB {
        format!("{:.1} GiB", bytes_f64 / GIB)
    } else if bytes_f64 >= MIB {
        format!("{:.1} MiB", bytes_f64 / MIB)
    } else if bytes_f64 >= KIB {
        format!("{:.1} KiB", bytes_f64 / KIB)
    } else {
        format!("{bytes} B")
    }
}

fn format_duration_seconds(seconds: u64) -> String {
    let minutes = seconds / 60;
    let seconds = seconds % 60;
    if minutes == 0 {
        format!("{seconds}s")
    } else {
        format!("{minutes}m {seconds:02}s")
    }
}

fn print_json<T>(label: &str, value: &T) -> Result<(), Box<dyn Error + Send + Sync>>
where
    T: Serialize,
{
    println!("{label}:");
    println!("{}", serde_json::to_string_pretty(value)?);
    Ok(())
}

fn yes_no(value: bool) -> &'static str {
    if value { "yes" } else { "no" }
}

fn main() -> Result<(), Box<dyn Error + Send + Sync>> {
    let npsso = parse_args().unwrap();
    let station_player = stationplayer::StationPlayer::init(npsso)?;

    let user_info = station_player.get_profile()?;
    print_user_info(&user_info);

    let trophy_summary = station_player.trophy_summary()?;
    print_trophy_summary(&trophy_summary);

    let mut rng = rand::rng();
    let target_title = "Batman Arkham City";
    let trophy_titles = station_player.get_all_user_trophy_games()?;

    print_trophy_titles(&trophy_titles);

    let selected_game = trophy_titles
        .iter()
        .find(|game| title_matches_query(&game.title, target_title))
        .ok_or_else(|| {
            format!("no trophy title matched {target_title:?}; looked for an Arkham City substring")
        })?;

    let np_communication_id = selected_game.np_communication_id.clone();
    let np_service_name = selected_game.np_service_name.clone();

    println!("selected trophy title:");
    println!("  title: {}", selected_game.title);
    println!("  matched from query: {target_title}");
    println!("  platform: {}", selected_game.platform);
    if let Some(np_title_id) = selected_game.np_title_id.as_deref() {
        println!("  np title id: {np_title_id}");
    }
    println!("  np communication id: {}", np_communication_id);
    println!("  np service name: {}", np_service_name);

    let trophy_groups =
        station_player.trophy_groups(np_communication_id.clone(), Some(np_service_name.clone()))?;
    print_trophy_groups(&trophy_groups);

    let all_trophies = if let Some(np_title_id) = selected_game.np_title_id.as_deref() {
        station_player.get_all_trophies_for_title_id(np_title_id.to_string())?
    } else {
        let mut trophies = Vec::new();
        for group in &trophy_groups.groups {
            let mut group_trophies = station_player.trophy_group_trophies(
                np_communication_id.clone(),
                group.group_id.clone(),
                Some(np_service_name.clone()),
            )?;
            trophies.append(&mut group_trophies);
        }
        trophies
    };

    print_flattened_trophies(&all_trophies);

    let selected_trophy = all_trophies
        .choose(&mut rng)
        .ok_or("selected title did not contain any trophies")?;

    println!("selected trophy:");
    println!("  id: {}", selected_trophy.trophy_id);
    println!(
        "  name: {}",
        selected_trophy
            .trophy_name
            .as_deref()
            .unwrap_or("<unnamed trophy>")
    );
    println!(
        "  type: {}",
        selected_trophy
            .trophy_type
            .as_deref()
            .unwrap_or("<unknown>")
    );
    println!(
        "  earned: {}",
        selected_trophy.earned.map(yes_no).unwrap_or("unknown")
    );

    let trophy_detail = station_player.trophy_detail(
        np_communication_id.clone(),
        selected_trophy.trophy_id.clone(),
        Some(np_service_name.clone()),
    )?;
    print_trophy_detail(&trophy_detail, None);

    match station_player
        .trophy_appearance_setting(np_communication_id.clone(), Some(np_service_name.clone()))
    {
        Ok(trophy_appearance_setting) => {
            print_trophy_appearance_setting(&trophy_appearance_setting);
        }
        Err(stationplayer::StationPlayerError::NotFound) => {
            println!("trophy appearance setting: unavailable (404)");
        }
        Err(error) => return Err(error.into()),
    }

    let hint_trophy_ids = vec![selected_trophy.trophy_id.clone()];
    match station_player.trophy_hint_availability(np_communication_id.clone(), hint_trophy_ids) {
        Ok(hint_availability) => {
            print_graphql_status("trophy hint availability", &hint_availability);
        }
        Err(stationplayer::StationPlayerError::NotFound) => {
            println!("trophy hint availability: unavailable (404)");
        }
        Err(error) => return Err(error.into()),
    }

    let cloud_media_capture_groups = station_player.get_all_cloud_media_capture_groups()?;
    print_cloud_media_capture_groups(&cloud_media_capture_groups);

    Ok(())
}
