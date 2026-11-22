use std::env;
use std::path::Path;
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

fn main() {
    println!("cargo::rerun-if-changed=build.rs");
    for key in [
        "PBH_BUILD_GIT_BRANCH",
        "PBH_BUILD_GIT_COMMIT",
        "PBH_BUILD_GIT_ABBREV",
        "PBH_BUILD_UNIX_TIME",
        "SOURCE_DATE_EPOCH",
    ] {
        println!("cargo::rerun-if-env-changed={key}");
    }
    if let Some(head) = git(&["rev-parse", "--path-format=absolute", "--git-path", "HEAD"]) {
        println!("cargo::rerun-if-changed={head}");
    }
    if let Some(reference) = git(&["symbolic-ref", "-q", "HEAD"])
        && let Some(path) = git(&[
            "rev-parse",
            "--path-format=absolute",
            "--git-path",
            &reference,
        ])
        && Path::new(&path).is_file()
    {
        println!("cargo::rerun-if-changed={path}");
    }
    let commit = input("PBH_BUILD_GIT_COMMIT").or_else(|| git(&["rev-parse", "HEAD"]));
    let branch = input("PBH_BUILD_GIT_BRANCH").or_else(|| git(&["branch", "--show-current"]));
    let abbrev = input("PBH_BUILD_GIT_ABBREV").or_else(|| {
        commit
            .as_deref()
            .and_then(|commit| commit.get(..7).map(str::to_owned))
    });
    let time = input("PBH_BUILD_UNIX_TIME")
        .or_else(|| input("SOURCE_DATE_EPOCH"))
        .and_then(|value| value.parse::<u64>().ok())
        .or_else(|| {
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .ok()
                .map(|elapsed| elapsed.as_secs())
        });
    for (key, value) in [
        ("PBH_BUILD_GIT_COMMIT", commit),
        ("PBH_BUILD_GIT_BRANCH", branch),
        ("PBH_BUILD_GIT_ABBREV", abbrev),
        ("PBH_BUILD_UNIX_TIME", time.map(|time| time.to_string())),
    ] {
        if let Some(value) = value {
            println!("cargo::rustc-env={key}={value}");
        }
    }
}

fn input(key: &str) -> Option<String> {
    one_line(env::var(key).ok()?)
}

fn git(arguments: &[&str]) -> Option<String> {
    let result = Command::new("git").args(arguments).output().ok()?;
    result.status.success().then_some(())?;
    one_line(String::from_utf8(result.stdout).ok()?)
}

fn one_line(value: String) -> Option<String> {
    let value = value.trim();
    (!value.is_empty() && !value.contains(['\r', '\n'])).then(|| value.to_owned())
}
