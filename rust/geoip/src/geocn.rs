use std::collections::BTreeMap;
use std::net::IpAddr;
use std::sync::OnceLock;

use serde::Deserialize;

use crate::{AsNetwork, CityData, GeoData, Mmdb, NetworkData};

const DIVISION_CSV: &str = include_str!("../../../src/main/resources/ok_data_level3.csv");
static DIVISIONS: OnceLock<BTreeMap<String, String>> = OnceLock::new();

pub(crate) struct GeoCn(Mmdb);

impl GeoCn {
    pub(crate) fn new(reader: Mmdb) -> Self {
        Self(reader)
    }

    pub(crate) fn enrich(&self, ip: IpAddr, result: &mut GeoData) {
        let Ok(found) = self.0.lookup(ip) else { return };
        let Ok(Some(row)) = found.decode::<Row>() else {
            return;
        };
        if let Some(code) = &row.division_code {
            let Some(city) = modern_city(code) else {
                return;
            };
            merge_city(result, city);
            merge_network(
                result,
                NetworkData {
                    isp: row.isp,
                    net_type: row.net_type,
                },
            );
            if let Ok(network) = found.network() {
                result.asn.get_or_insert_default().network = Some(AsNetwork {
                    ip_address: network.network(),
                    prefix_length: network.prefix(),
                });
            }
        } else if row.province.is_some()
            && let Some(city) = legacy_city(&row)
        {
            merge_city(result, city);
            merge_network(
                result,
                NetworkData {
                    isp: row.isp.filter(|value| !value.trim().is_empty()),
                    net_type: row.net.filter(|value| !value.trim().is_empty()),
                },
            );
        }
    }
}

#[derive(Deserialize)]
struct Row {
    division_code: Option<Code>,
    isp: Option<String>,
    #[serde(rename = "type")]
    net_type: Option<String>,
    net: Option<String>,
    province: Option<String>,
    city: Option<String>,
    districts: Option<String>,
    #[serde(rename = "provinceCode")]
    province_code: Option<Code>,
    #[serde(rename = "cityCode")]
    city_code: Option<Code>,
    #[serde(rename = "districtsCode")]
    districts_code: Option<Code>,
}

#[derive(Deserialize)]
#[serde(untagged)]
enum Code {
    Number(i64),
    Text(String),
}

impl Code {
    fn numeric(&self) -> Option<i64> {
        match self {
            Self::Number(code) => Some(*code),
            Self::Text(code) => code.parse().ok(),
        }
    }

    fn text(&self) -> std::borrow::Cow<'_, str> {
        match self {
            Self::Number(code) => code.to_string().into(),
            Self::Text(code) => code.as_str().into(),
        }
    }
}

#[derive(Deserialize)]
struct Division {
    id: String,
    ext_name: String,
}

fn modern_city(code: &Code) -> Option<CityData> {
    let divisions = DIVISIONS.get_or_init(|| {
        csv::Reader::from_reader(DIVISION_CSV.as_bytes())
            .into_deserialize::<Division>()
            .filter_map(Result::ok)
            .map(|row| (row.id, row.ext_name))
            .collect()
    });
    let text = code.text();
    let parts = text
        .char_indices()
        .skip(1)
        .map(|(end, _)| end)
        .chain(std::iter::once(text.len()))
        .filter_map(|end| divisions.get(&text[..end]).map(String::as_str))
        .collect::<Vec<_>>();
    if parts.is_empty() {
        return None;
    }
    let districts = parts
        .get(2..parts.len().min(4))
        .map(|values| values.join(" "));
    Some(CityData {
        name: Some(parts.join(" ")),
        iso: Some(code.numeric().unwrap_or(0)),
        cn_province: parts.first().map(|value| (*value).to_owned()),
        cn_city: parts.get(1).map(|value| (*value).to_owned()),
        cn_districts: districts.filter(|values| !values.is_empty()),
    })
}

fn legacy_city(row: &Row) -> Option<CityData> {
    let mut code = None;
    for value in [&row.province_code, &row.city_code, &row.districts_code]
        .into_iter()
        .flatten()
    {
        code = Some(value.numeric()? as i32);
    }
    let iso = format!("86{}", code?).parse().ok()?;
    let name = format!(
        "{} {} {}",
        row.province.as_deref().unwrap_or("null"),
        row.city.as_deref().unwrap_or("null"),
        row.districts.as_deref().unwrap_or("null"),
    );
    let name = name.trim();
    Some(CityData {
        name: (!name.is_empty()).then(|| name.to_owned()),
        iso: Some(iso),
        cn_province: row.province.clone(),
        cn_city: row.city.clone(),
        cn_districts: row.districts.clone(),
    })
}

fn merge_city(result: &mut GeoData, incoming: CityData) {
    let old = result.city.take().unwrap_or_default();
    result.city = Some(CityData {
        name: incoming.name.or(old.name),
        iso: incoming.iso.or(old.iso),
        cn_province: incoming.cn_province.or(old.cn_province),
        cn_city: incoming.cn_city.or(old.cn_city),
        cn_districts: incoming.cn_districts.or(old.cn_districts),
    });
}

fn merge_network(result: &mut GeoData, incoming: NetworkData) {
    let old = result.network.take().unwrap_or_default();
    result.network = Some(NetworkData {
        isp: incoming.isp.or(old.isp),
        net_type: incoming.net_type.or(old.net_type),
    });
}
