#[path = "../src/config_migrations.rs"]
mod config_migrations;

use config_migrations::{
    MAIN_VERSION, PROFILE_VERSION, migrate_main, migrate_main_for_installation, migrate_profile,
};
use serde_yaml_ng::Value;

fn yaml(input: &str) -> Value {
    serde_yaml_ng::from_str(input).unwrap()
}

#[test]
fn synology_host_network_upgrade_rewrites_only_eligible_older_endpoints() {
    let gateway = "http://old:credential@172.19.0.1:80/webui?option=1#old";
    let initial = yaml(&format!(
        "config-version: 36\nclient:\n  gateway:\n    endpoint: '{gateway}'\n  remote:\n    endpoint: 'https://192.168.0.2:9001'\n"
    ));
    let mut standard = initial.clone();
    let mut synology = initial;
    migrate_main_for_installation(&mut standard, false).unwrap();
    migrate_main_for_installation(&mut synology, true).unwrap();
    assert_eq!(standard["client"]["gateway"]["endpoint"], gateway);
    assert_eq!(
        synology["client"]["gateway"]["endpoint"],
        "http://127.0.0.1:80/webui"
    );
    assert_eq!(synology["client"]["remote"], standard["client"]["remote"]);
    assert!(
        !migrate_main_for_installation(&mut synology, true)
            .unwrap()
            .changed
    );
}

#[test]
fn released_main_fixture_upgrades_without_changing_existing_configuration() {
    let fixture = include_str!("../../../test/test-db/config/config.yml")
        .replace("@@@@DATABESE_TYPE@@@@", "sqlite");
    let mut main = yaml(&fixture);
    let original = main.clone();
    let report = migrate_main(&mut main).unwrap();

    assert_eq!((report.before, report.after), (46, MAIN_VERSION));
    assert!(report.changed);
    assert!(report.downloader_id_changes.is_empty());
    assert_eq!(main["config-version"].as_i64(), Some(MAIN_VERSION));
    for key in ["client", "database", "persist", "proxy", "server"] {
        assert_eq!(main[key], original[key], "{key}");
    }
    assert_eq!(main["ip-remapping"]["teredo"].as_bool(), Some(false));
    assert!(main["ip-remapping"]["nat64"].is_mapping());
    assert!(
        !main["btn"]
            .as_mapping()
            .unwrap()
            .contains_key("allow-script-execute")
    );
    assert!(uuid::Uuid::parse_str(main["installation-id"].as_str().unwrap()).is_ok());
    assert!(!migrate_main(&mut main).unwrap().changed);
}

#[test]
fn legacy_downloader_name_becomes_stable_id_and_proxy_mode_is_renumbered() {
    let mut main = yaml(
        r#"
config-version: 26
client:
  Home.qBittorrent:
    type: qbittorrent
    endpoint: http://127.0.0.1:9090
    increment-ban: true
  TV:
    type: transmission
    rpc-url: /torrent/rpc
proxy:
  setting: 3
btn:
  config-url: http://btn-dev2.ghostchu-services.top/ping/config
pbh-plus-key: sample-license
custom: keep-me
"#,
    );
    let report = migrate_main(&mut main).unwrap();

    assert_eq!(report.downloader_id_changes.len(), 2);
    let qb = report.downloader_id_changes["Home.qBittorrent"].as_str();
    let tv = report.downloader_id_changes["TV"].as_str();
    assert!(uuid::Uuid::parse_str(qb).is_ok());
    assert_ne!(qb, tv);
    assert_eq!(
        main["client"][qb]["name"].as_str(),
        Some("Home.qBittorrent")
    );
    assert_eq!(main["client"][qb]["type"].as_str(), Some("qbittorrent"));
    assert_eq!(main["client"][qb]["increment-ban"].as_bool(), Some(true));
    assert_eq!(main["client"][tv]["rpc-url"].as_str(), Some("/torrent/rpc"));
    assert_eq!(main["proxy"]["setting"].as_i64(), Some(2));
    assert_eq!(
        main["btn"]["config-url"].as_str(),
        Some("https://sparkle.pbh-btn.com/ping/config")
    );
    assert_eq!(main["pbh-plus-key"][0].as_str(), Some("sample-license"));
    assert_eq!(main["custom"].as_str(), Some("keep-me"));
    let upgraded = main.clone();
    let repeated = migrate_main(&mut main).unwrap();
    assert!(repeated.downloader_id_changes.is_empty());
    assert!(!repeated.changed);
    assert_eq!(main, upgraded);
}

#[test]
fn preserves_database_connection_settings_and_translates_numeric_engines() {
    for (old, expected) in [
        (0, "sqlite"),
        (1, "h2"),
        (2, "mysql"),
        (3, "postgresql"),
        (10, "mysql"),
        (11, "postgresql"),
    ] {
        let mut main = yaml(&format!(
            "config-version: 41\ndatabase:\n  type: {old}\n  host: database.local\n"
        ));
        migrate_main(&mut main).unwrap();
        assert_eq!(main["database"]["type"].as_str(), Some(expected));
        assert_eq!(main["database"]["host"].as_str(), Some("database.local"));
    }
}

#[test]
fn early_main_sets_downloaders_and_proxy_before_assigning_ids() {
    let mut main = yaml(
        r#"
config-version: 0
client:
  qbit:
    type: QBittorrent
  tr:
    type: Transmission
"#,
    );
    let report = migrate_main(&mut main).unwrap();
    let qb = report.downloader_id_changes["qbit"].as_str();
    let tr = report.downloader_id_changes["tr"].as_str();
    assert_eq!(main["client"][qb]["increment-ban"].as_bool(), Some(false));
    assert_eq!(
        main["client"][tr]["rpc-url"].as_str(),
        Some("/transmission/rpc")
    );
    assert_eq!(
        main["proxy"]["non-proxy-hosts"].as_str(),
        Some("127.0.0.1|localhost")
    );
    assert_eq!(main["persist"]["banlist"].as_bool(), Some(true));
    assert!(uuid::Uuid::parse_str(main["server"]["token"].as_str().unwrap()).is_ok());
}

#[test]
fn legacy_rules_keep_order_and_negation_and_remove_superseded_defaults() {
    let mut profile = yaml(
        r#"
config-version: 1
module:
  peer-id-blacklist:
    banned-peer-id: ['StartsWith@-XL', 'length@8', 'bad format']
    exclude-peer-id: ['regex@MY@Peer', 'equals@My-Others', 'length@2']
  client-name-blacklist:
    banned-client-name: ['Contains@Xunlei', 'EndsWith@Custom']
    exclude-client-name: ['StartsWith@SafeClient']
  active-probing:
    enabled: true
  expression-engine:
    enabled: true
"#,
    );
    let report = migrate_profile(&mut profile).unwrap();

    assert_eq!((report.before, report.after), (1, PROFILE_VERSION));
    let modules = &profile["module"];
    let peers = &modules["peer-id-blacklist"]["banned-peer-id"];
    assert_eq!(
        peers[0].as_str(),
        Some(r#"{"method":"REGEX","content":"my@peer","success":"NEGATIVE"}"#)
    );
    assert_eq!(
        peers[1].as_str(),
        Some(r#"{"method":"EQUALS","content":"my-others","success":"FALSE"}"#)
    );
    assert_eq!(
        peers[2].as_str(),
        Some(r#"{"method":"LENGTH","min":2,"max":2,"hit":"FALSE"}"#)
    );
    assert_eq!(
        peers[3].as_str(),
        Some(r#"{"method":"LENGTH","min":8,"max":8}"#)
    );
    let clients = &modules["client-name-blacklist"]["banned-client-name"];
    assert_eq!(
        clients[0].as_str(),
        Some(r#"{"method":"STARTS_WITH","content":"safeclient","hit":"FALSE"}"#)
    );
    assert_eq!(
        clients[1].as_str(),
        Some(r#"{"method":"ENDS_WITH","content":"custom"}"#)
    );
    assert_eq!(
        clients.as_sequence().unwrap().last().unwrap().as_str(),
        Some(r#"{"method":"STARTS_WITH","content":"Gopeed bt-"}"#)
    );
    assert!(modules["client-name-blacklist"]["exclude-client-name"].is_null());
    assert!(modules["active-probing"].is_null());
    assert_eq!(
        modules["active-probing-removed"]["enabled"].as_bool(),
        Some(true)
    );
    assert!(modules["expression-engine"].is_null());
    assert!(!migrate_profile(&mut profile).unwrap().changed);
}

#[test]
fn profile_moves_network_types_monitoring_values_and_subscription_urls() {
    let mut profile = yaml(
        r#"
config-version: 28
module:
  ip-address-blocker:
    net-type:
      wideband: false
      base-station: true
      datacenter: true
  ip-address-blocker-rules:
    rules:
      legacy-provider:
        url: https://bcr.pbh-btn.ghorg.ghostchu-services.top/combine/all.txt
      custom-provider:
        url: https://example.net/rules
  active-monitoring:
    data-retention-time: 445
    data-cleanup-interval: 123
  swarm-tracking:
    enabled: false
"#,
    );
    migrate_profile(&mut profile).unwrap();
    let module = &profile["module"];

    assert_eq!(
        module["ip-address-blocker"]["net-type"],
        yaml("[baseStation, dataCenter]")
    );
    let rules = &module["ip-address-blocker-rules"]["rules"];
    assert_eq!(
        rules["legacy-provider"]["url"].as_str(),
        Some("https://bcr.pbh-btn.com/combine/all.txt")
    );
    assert_eq!(
        rules["custom-provider"]["url"].as_str(),
        Some("https://example.net/rules")
    );
    let analyse = &module["peer-analyse-service"];
    assert_eq!(
        analyse["peer-recording"]["data-retention-time"].as_i64(),
        Some(445)
    );
    assert_eq!(
        analyse["peer-recording"]["data-cleanup-interval"].as_i64(),
        Some(123)
    );
    assert_eq!(analyse["swarm-tracking"]["enabled"].as_bool(), Some(false));
    assert!(module["active-monitoring"]["data-retention-time"].is_null());
    assert!(module["swarm-tracking"].is_null());
}

#[test]
fn current_and_future_versions_leave_custom_data_in_place() {
    let mut current = yaml("config-version: 48\ninstallation-id: existing\nproxy: {setting: 1}");
    let original = current.clone();
    assert!(!migrate_main(&mut current).unwrap().changed);
    assert_eq!(current, original);

    let mut future = yaml("config-version: 90\nfoo: [1, 2]\n");
    let original = future.clone();
    let report = migrate_main(&mut future).unwrap();
    assert_eq!(
        (report.before, report.after, report.changed),
        (90, 90, false)
    );
    assert_eq!(future, original);
    assert!(!migrate_profile(&mut future).unwrap().changed);
    assert_eq!(future, original);
}

#[test]
fn invalid_input_and_failing_upgrade_leave_the_source_unchanged() {
    for input in [
        "[1, 2]",
        "{config-version: 'old'}",
        "{config-version: 31.9}",
    ] {
        let mut invalid = yaml(input);
        let original = invalid.clone();
        assert!(migrate_main(&mut invalid).is_err());
        assert_eq!(invalid, original);
    }
    let mut invalid = yaml(
        "config-version: 1\nmodule:\n  peer-id-blacklist:\n    banned-peer-id: ['length@abc']\n",
    );
    let original = invalid.clone();
    let error = migrate_profile(&mut invalid).unwrap_err();
    assert!(error.to_string().contains("upgrade profile to version 2"));
    assert_eq!(invalid, original);
}
