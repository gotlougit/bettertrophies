use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::SystemTime;

const MAX_STALE_DAYS: u64 = 29;
const METADATA_JSON: &str = "metadata/app_constants.json";

fn main() {
    let manifest_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));

    // By default, use the bundled src/consts.rs as-is so downstream builds
    // don't need Python, jadx, or the Google Play APK.  Set
    // STATIONPLAYER_REGENERATE_CONSTS=1 to run the full extraction+generation
    // pipeline.
    println!("cargo:rerun-if-env-changed=STATIONPLAYER_REGENERATE_CONSTS");
    if let Ok(val) = std::env::var("STATIONPLAYER_REGENERATE_CONSTS").as_deref()
        && val == "1"
    {
        println!("cargo:warning=using generated src/consts.rs");
        regenerate_consts(&manifest_dir);
    } else {
        println!("cargo:rerun-if-changed=src/consts.rs");
        println!(
            "cargo:warning=using bundled src/consts.rs (set STATIONPLAYER_REGENERATE_CONSTS=1 to refresh)"
        );
    }

    uniffi::generate_scaffolding("./src/stationplayer.udl").unwrap();
}

fn regenerate_consts(manifest_dir: &Path) {
    let json_path = manifest_dir.join(METADATA_JSON);
    let extract_script = manifest_dir.join("tools/extract.sh");
    let gen_script = manifest_dir.join("tools/generate_consts.py");
    let consts_path = manifest_dir.join("src/consts.rs");

    let need_extract = !json_path.exists() || is_stale(&json_path, MAX_STALE_DAYS);

    if need_extract {
        println!("cargo:warning=metadata is missing or stale, running extract.sh ...");
        let status = Command::new("bash")
            .arg(&extract_script)
            .current_dir(manifest_dir)
            .status()
            .unwrap_or_else(|e| panic!("failed to run extract.sh: {e}"));

        if !status.success() {
            if json_path.exists() {
                println!("cargo:warning=extract.sh failed, falling back to cached metadata");
            } else {
                panic!("extract.sh failed and {METADATA_JSON} does not exist");
            }
        }
    }

    if json_path.exists() {
        println!("cargo:rerun-if-changed={METADATA_JSON}");
        let status = Command::new("python3")
            .arg(&gen_script)
            .arg(&json_path)
            .arg("-o")
            .arg(&consts_path)
            .current_dir(manifest_dir)
            .status()
            .expect("failed to run tools/generate_consts.py");

        if !status.success() {
            panic!("tools/generate_consts.py failed");
        }
    } else {
        panic!("{METADATA_JSON} not found and extraction did not produce it");
    }
}

/// Returns `true` when the file's last git commit (or mtime) is older than
/// `max_days`.  Uses `git log` so the check survives fresh clones; falls back
/// to filesystem mtime when git is unavailable.
fn is_stale(path: &Path, max_days: u64) -> bool {
    let epoch_secs = match git_commit_epoch(path) {
        Some(secs) => secs,
        None => match file_mtime_epoch(path) {
            Some(secs) => secs,
            None => return true, // can't determine age → treat as stale
        },
    };

    let now = SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_secs();

    let age_days = now.saturating_sub(epoch_secs) / 86_400;
    age_days > max_days
}

fn git_commit_epoch(path: &Path) -> Option<u64> {
    let output = Command::new("git")
        .args(["log", "-1", "--format=%ct", "--"])
        .arg(path.file_name()?)
        .current_dir(path.parent()?)
        .output()
        .ok()?;

    if !output.status.success() {
        return None;
    }

    let s = String::from_utf8_lossy(&output.stdout).trim().to_string();
    s.parse().ok()
}

fn file_mtime_epoch(path: &Path) -> Option<u64> {
    let m = std::fs::metadata(path).ok()?;
    let t = m.modified().ok()?;
    Some(t.duration_since(std::time::UNIX_EPOCH).ok()?.as_secs())
}
