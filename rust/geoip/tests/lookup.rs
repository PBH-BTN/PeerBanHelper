use std::fs;
use std::net::IpAddr;
use std::path::Path;

use pbh_geoip::{AsNetwork, Database, GeoData, GeoIp};
use serde_json::json;
use tempfile::TempDir;

const CITY: &[u8] = include_bytes!("fixtures/GeoIP2-City-Test.mmdb");
const ASN: &[u8] = include_bytes!("fixtures/GeoLite2-ASN-Test.mmdb");

fn install(directory: &Path, database: Database, bytes: &[u8]) {
    let directory = directory.join("ipdb").join("geoip");
    fs::create_dir_all(&directory).unwrap();
    fs::write(directory.join(database.filename()), bytes).unwrap();
}

#[test]
fn loads_city_and_asn_independently_with_java_json_shape() {
    let temp = TempDir::new().unwrap();
    install(temp.path(), Database::City, CITY);
    install(temp.path(), Database::Asn, ASN);
    let geo = GeoIp::open(temp.path(), "zh-CN");
    assert!(geo.has_databases());
    assert!(geo.load_errors().is_empty());
    let sweden = geo.lookup("89.160.20.128".parse().unwrap());
    assert_eq!(sweden.city.as_ref().unwrap().iso, Some(2694762));
    assert_eq!(
        sweden.country.as_ref().unwrap().name.as_deref(),
        Some("瑞典")
    );
    let wire = serde_json::to_value(sweden).unwrap();
    assert_eq!(wire["city"], json!({"name":"林雪平"}));
    assert_eq!(wire["country"], json!({"iso":"SE"}));

    let ipv4 = IpAddr::from([1, 0, 0, 1]);
    let data = geo.lookup(ipv4);
    let asn = data.asn.as_ref().unwrap();
    assert_eq!(asn.ip_address, Some(ipv4));
    assert_eq!(asn.organization.as_deref(), Some("Google Inc."));
    assert_eq!(
        asn.network,
        Some(AsNetwork {
            ip_address: IpAddr::from([1, 0, 0, 0]),
            prefix_length: 24,
        })
    );
    assert_eq!(
        serde_json::to_value(&data).unwrap(),
        json!({
            "city":null, "country":null,"as":{"number":15169},"network":{"isp":"Google Inc.","netType":null}
        })
    );
    assert_eq!(geo.lookup("::ffff:1.0.0.1".parse().unwrap()), data);
    let geo = GeoIp::open(temp.path(), "not-a-db-locale");
    assert_eq!(
        geo.lookup("89.160.20.128".parse().unwrap())
            .city
            .unwrap()
            .name
            .as_deref(),
        Some("Linköping")
    );
    let taiwan = GeoIp::open(temp.path(), "zh-CN").lookup("2001:238::42".parse().unwrap());
    assert_eq!(taiwan.city.unwrap().name, None);
    assert_eq!(taiwan.country.unwrap().name.as_deref(), Some("中国台湾"));
}

#[test]
fn missing_invalid_and_wrong_type_files_leave_working_sources_available() {
    let temp = TempDir::new().unwrap();
    let missing = GeoIp::open(temp.path(), "en");
    assert!(!missing.has_databases());
    assert!(missing.load_errors().is_empty());
    assert_eq!(
        serde_json::to_value(missing.lookup("192.0.2.1".parse().unwrap())).unwrap(),
        json!({"city":null,"country":null,"as":null,"network":null})
    );

    install(temp.path(), Database::City, b"invalid database");
    install(temp.path(), Database::GeoCn, b"invalid database");
    install(temp.path(), Database::Asn, ASN);
    let partial = GeoIp::open(temp.path(), "en");
    assert_eq!(
        partial
            .load_errors()
            .iter()
            .map(|item| item.database)
            .collect::<Vec<_>>(),
        [Database::City, Database::GeoCn]
    );
    assert_eq!(
        partial
            .lookup("1.0.0.1".parse().unwrap())
            .asn
            .unwrap()
            .number,
        Some(15169)
    );
    assert_eq!(
        fs::read(temp.path().join("ipdb/geoip/GeoIP-City.mmdb")).unwrap(),
        b"invalid database"
    );

    install(temp.path(), Database::City, ASN);
    install(temp.path(), Database::Asn, CITY);
    let invalid = GeoIp::open(temp.path(), "en");
    assert_eq!(invalid.load_errors().len(), 3);
    assert!(!invalid.has_databases());
}

#[test]
fn geocn_revision_two_uses_numeric_divisions_and_preserves_as_number() {
    let temp = TempDir::new().unwrap();
    install(temp.path(), Database::City, &synthetic_city("CN"));
    install(temp.path(), Database::Asn, ASN);
    let row = map(&[
        ("division_code", unsigned(6, 110101)),
        ("type", string("基站")),
    ]);
    install(temp.path(), Database::GeoCn, &fixture("GeoCN", &row));
    let geo = GeoIp::open(temp.path(), "en");
    assert!(geo.load_errors().is_empty());
    let ip = "1.0.0.1".parse().unwrap();
    let data = geo.lookup(ip);
    let city = data.city.as_ref().unwrap();
    assert_eq!(city.name.as_deref(), Some("北京市 北京市 东城区"));
    assert_eq!(city.iso, Some(110101));
    assert_eq!(city.cn_province.as_deref(), Some("北京市"));
    assert_eq!(city.cn_city.as_deref(), Some("北京市"));
    assert_eq!(city.cn_districts.as_deref(), Some("东城区"));
    assert_eq!(data.country.as_ref().unwrap().iso.as_deref(), Some("CN"));
    assert_eq!(data.asn.as_ref().unwrap().number, Some(15169));
    assert_eq!(
        data.asn
            .as_ref()
            .unwrap()
            .network
            .as_ref()
            .unwrap()
            .prefix_length,
        1
    );
    assert_eq!(
        serde_json::to_value(&data.network).unwrap(),
        json!({"isp":"Google Inc.","netType":"基站"})
    );

    install(temp.path(), Database::City, &synthetic_city("ZZ"));
    let other = GeoIp::open(temp.path(), "en").lookup(ip);
    assert_eq!(other.city.unwrap().name.as_deref(), Some("Baseline"));
    assert_eq!(other.asn.unwrap().network.unwrap().prefix_length, 24);
    assert_eq!(other.network.unwrap().net_type, None);
}

#[test]
fn geocn_revision_one_and_revision_two_missing_divisions() {
    let temp = TempDir::new().unwrap();
    install(temp.path(), Database::City, &synthetic_city("HK"));
    let fields = [
        ("province", string("旧省")),
        ("provinceCode", unsigned(6, 11)),
        ("city", string("旧市")),
        ("cityCode", string("1101")),
        ("districts", string("旧区")),
        ("districtsCode", unsigned(6, 110101)),
        ("isp", string("测试 ISP")),
        ("net", string("宽带")),
    ];
    install(
        temp.path(),
        Database::GeoCn,
        &fixture("GeoCN", &map(&fields)),
    );
    let ip = "192.0.2.1".parse().unwrap();
    let data = GeoIp::open(temp.path(), "en").lookup(ip);
    assert_eq!(data.city.as_ref().unwrap().iso, Some(86110101));
    assert_eq!(data.city.unwrap().cn_districts.as_deref(), Some("旧区"));
    assert_eq!(data.asn, None);
    assert_eq!(
        serde_json::to_value(data.network).unwrap(),
        json!({"isp":"测试 ISP","netType":"宽带"})
    );

    let mut fields = fields.to_vec();
    fields.push(("division_code", string("unknown")));
    install(
        temp.path(),
        Database::GeoCn,
        &fixture("GeoCN", &map(&fields)),
    );
    let data = GeoIp::open(temp.path(), "en").lookup(ip);
    assert_eq!(data.city.unwrap().name.as_deref(), Some("Baseline"));
    assert_eq!(data.network, None);
}

#[test]
fn stored_geoip_json_can_be_read_without_hidden_metadata() {
    let json = json!({"city":{"name":null},"country":{"iso":"CN"},"as":{"number":12345},"network":{"isp":null,"netType":null}});
    let data: GeoData = serde_json::from_value(json.clone()).unwrap();
    assert_eq!(data.city.as_ref().unwrap().iso, None);
    assert_eq!(data.asn.as_ref().unwrap().organization, None);
    assert_eq!(serde_json::to_value(data).unwrap(), json);
}

fn synthetic_city(iso: &str) -> Vec<u8> {
    fixture(
        "GeoLite2-City",
        &map(&[
            ("country", map(&[("iso_code", string(iso))])),
            (
                "city",
                map(&[("names", map(&[("en", string("Baseline"))]))]),
            ),
        ]),
    )
}

// A minimal IPv4 MMDB with one 24-bit tree node and one data value.
// Both child pointers refer to the same value at node_count + 16.
fn fixture(kind: &str, row: &[u8]) -> Vec<u8> {
    let mut bytes = vec![0, 0, 17, 0, 0, 17];
    bytes.extend([0; 16]);
    bytes.extend(row);
    bytes.extend(b"\xab\xcd\xefMaxMind.com");
    bytes.extend(map(&[
        ("binary_format_major_version", unsigned(5, 2)),
        ("binary_format_minor_version", unsigned(5, 0)),
        ("build_epoch", unsigned(9, 0)),
        ("database_type", string(kind)),
        ("description", map(&[])),
        ("ip_version", unsigned(5, 4)),
        ("languages", vec![0, 4]),
        ("node_count", unsigned(6, 1)),
        ("record_size", unsigned(5, 24)),
    ]));
    bytes
}

fn map(fields: &[(&str, Vec<u8>)]) -> Vec<u8> {
    assert!(fields.len() < 29);
    let mut bytes = vec![7 << 5 | fields.len() as u8];
    for (key, value) in fields {
        bytes.extend(string(key));
        bytes.extend(value);
    }
    bytes
}

fn string(value: &str) -> Vec<u8> {
    assert!(value.len() < 29);
    let mut bytes = vec![2 << 5 | value.len() as u8];
    bytes.extend(value.as_bytes());
    bytes
}

fn unsigned(kind: u8, value: u32) -> Vec<u8> {
    let value = value.to_be_bytes();
    let value = &value[value
        .iter()
        .position(|byte| *byte != 0)
        .unwrap_or(value.len())..];
    let mut bytes = if kind < 8 {
        vec![kind << 5 | value.len() as u8]
    } else {
        vec![value.len() as u8, kind - 7]
    };
    bytes.extend(value);
    bytes
}
