//! PeerBanHelper GeoIP database lookup.

mod geocn;
pub mod update;

use std::collections::BTreeMap;
use std::fmt;
use std::io;
use std::net::IpAddr;
use std::path::Path;

use maxminddb::{MaxMindDbError, Reader};
use serde::{Deserialize, Serialize};

use geocn::GeoCn;

type Mmdb = Reader<Vec<u8>>;

/// The public/persisted IPGeoData shape. Internal rule and chart fields are kept
/// available to Rust callers but omitted from JSON, as with Java's @JsonUtil.Hidden.
#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
pub struct GeoData {
    pub city: Option<CityData>,
    pub country: Option<CountryData>,
    #[serde(rename = "as")]
    pub asn: Option<AsData>,
    pub network: Option<NetworkData>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
pub struct CityData {
    pub name: Option<String>,
    #[serde(skip)]
    pub iso: Option<i64>,
    #[serde(skip)]
    pub cn_province: Option<String>,
    #[serde(skip)]
    pub cn_city: Option<String>,
    #[serde(skip)]
    pub cn_districts: Option<String>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
pub struct CountryData {
    #[serde(skip)]
    pub name: Option<String>,
    pub iso: Option<String>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
pub struct AsData {
    pub number: Option<i64>,
    #[serde(skip)]
    pub organization: Option<String>,
    #[serde(skip)]
    pub ip_address: Option<IpAddr>,
    #[serde(skip)]
    pub network: Option<AsNetwork>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct AsNetwork {
    pub ip_address: IpAddr,
    pub prefix_length: u8,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
pub struct NetworkData {
    pub isp: Option<String>,
    #[serde(rename = "netType")]
    pub net_type: Option<String>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Database {
    City,
    Asn,
    GeoCn,
}

impl Database {
    pub fn filename(self) -> &'static str {
        match self {
            Self::City => "GeoIP-City.mmdb",
            Self::Asn => "GeoIP-ASN.mmdb",
            Self::GeoCn => "GeoCN.mmdb",
        }
    }

    fn supports_type(self, kind: &str) -> bool {
        match self {
            Self::City => supports_city(kind) || kind.contains("Country"),
            Self::Asn => kind.contains("GeoLite2-ASN"),
            Self::GeoCn => true,
        }
    }
}

#[derive(Debug)]
pub struct LoadError {
    pub database: Database,
    pub message: String,
}

impl fmt::Display for LoadError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}: {}", self.database.filename(), self.message)
    }
}

/// Immutable, independently loaded databases, owned in memory so the original
/// files remain replaceable during startup updates and on Windows.
pub struct GeoIp {
    city: Option<Mmdb>,
    asn: Option<Mmdb>,
    geocn: Option<GeoCn>,
    locale: String,
    load_errors: Vec<LoadError>,
}

impl GeoIp {
    /// Load the existing `ipdb/geoip` directory under the PeerBanHelper data dir.
    /// Missing files are optional. An unreadable file is recorded independently.
    pub fn open(data_dir: &Path, locale: &str) -> Self {
        let directory = data_dir.join("ipdb").join("geoip");
        let mut load_errors = Vec::new();
        let city = load(&directory, Database::City, &mut load_errors);
        let asn = load(&directory, Database::Asn, &mut load_errors);
        let geocn = load(&directory, Database::GeoCn, &mut load_errors).map(GeoCn::new);
        Self {
            city,
            asn,
            geocn,
            locale: locale.to_owned(),
            load_errors,
        }
    }

    pub fn load_errors(&self) -> &[LoadError] {
        &self.load_errors
    }

    pub fn has_databases(&self) -> bool {
        self.city.is_some() || self.asn.is_some() || self.geocn.is_some()
    }

    /// Lookups miss independently and keep the other databases' available data.
    /// An initialized manager produces an all-null object when nothing matches.
    pub fn lookup(&self, ip: IpAddr) -> GeoData {
        let ip = ip.to_canonical();
        let mut result = GeoData::default();
        if let Some(reader) = &self.city {
            self.lookup_city(reader, ip, &mut result);
        }
        if let Some(reader) = &self.asn {
            lookup_as(reader, ip, &mut result);
        }
        if let Some(geocn) = &self.geocn
            && let Some(iso) = result
                .country
                .as_ref()
                .and_then(|country| country.iso.as_deref())
            && ["CN", "TW", "HK", "MO"]
                .iter()
                .any(|region| iso.eq_ignore_ascii_case(region))
        {
            geocn.enrich(ip, &mut result);
        }
        result
    }

    fn lookup_city(&self, reader: &Mmdb, ip: IpAddr, out: &mut GeoData) {
        let Ok(found) = reader.lookup(ip) else { return };
        if !found.has_data() {
            return;
        }
        if let Ok(country) = found.decode_path::<Region>(&maxminddb::path!["country"]) {
            let country = country.unwrap_or_default();
            let locale = self.locale.to_ascii_lowercase().replace('-', "_");
            let mut name = country.name(&self.locale);
            if matches!(locale.as_str(), "zh_cn" | "zh_hk" | "zh_mo")
                && matches!(country.iso_code.as_deref(), Some("TW" | "HK" | "MO"))
            {
                name = Some(format!("中国{}", name.as_deref().unwrap_or("null")));
            }
            out.country = Some(CountryData {
                iso: country.iso_code,
                name,
            });
        }
        if supports_city(&reader.metadata().database_type)
            && let Ok(city) = found.decode_path::<Region>(&maxminddb::path!["city"])
        {
            let city = city.unwrap_or_default();
            out.city = Some(CityData {
                name: city.name(&self.locale),
                iso: city.geoname_id,
                ..CityData::default()
            });
        }
    }
}

fn load(directory: &Path, database: Database, errors: &mut Vec<LoadError>) -> Option<Mmdb> {
    let reader = match Reader::open_readfile(directory.join(database.filename())) {
        Ok(reader) => reader,
        Err(MaxMindDbError::Io(error)) if error.kind() == io::ErrorKind::NotFound => return None,
        Err(error) => {
            errors.push(LoadError {
                database,
                message: error.to_string(),
            });
            return None;
        }
    };
    let kind = &reader.metadata().database_type;
    if !database.supports_type(kind) {
        errors.push(LoadError {
            database,
            message: format!("unsupported database type {kind}"),
        });
        return None;
    }
    Some(reader)
}

fn supports_city(kind: &str) -> bool {
    kind.contains("City") || kind.contains("Enterprise")
}

fn lookup_as(reader: &Mmdb, ip: IpAddr, out: &mut GeoData) {
    let Ok(found) = reader.lookup(ip) else { return };
    let Ok(Some(asn)) = found.decode::<maxminddb::geoip2::Asn>() else {
        return;
    };
    let organization = asn.autonomous_system_organization.map(str::to_owned);
    out.network = Some(NetworkData {
        isp: organization.clone(),
        net_type: None,
    });
    let network = found.network().ok().map(|network| AsNetwork {
        ip_address: network.network(),
        prefix_length: network.prefix(),
    });
    out.asn = Some(AsData {
        number: asn.autonomous_system_number.map(i64::from),
        organization,
        ip_address: Some(ip),
        network,
    });
}

#[derive(Default, Deserialize)]
struct Region {
    #[serde(default)]
    names: BTreeMap<String, String>,
    geoname_id: Option<i64>,
    iso_code: Option<String>,
}

impl Region {
    fn name(&self, locale: &str) -> Option<String> {
        self.names
            .get(locale)
            .or_else(|| self.names.get("en"))
            .cloned()
    }
}
