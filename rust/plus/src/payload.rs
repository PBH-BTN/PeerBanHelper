use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::Error;

#[derive(Serialize)]
#[serde(untagged)]
pub(crate) enum Payload {
    V1(V1),
    V2(V2),
}

#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Common {
    verify_magic: String,
    source: Option<String>,
    pub(crate) license_to: Option<String>,
    #[serde(default)]
    create_at: i64,
    #[serde(default)]
    pub(crate) expire_at: i64,
    pub(crate) description: Option<String>,
    hidden: Option<String>,
}

#[derive(Deserialize, Serialize)]
pub(crate) struct V1 {
    #[serde(flatten)]
    common: Common,
    #[serde(rename = "type", default = "v1_default_type")]
    license_type: Option<String>,
}

fn v1_default_type() -> Option<String> {
    Some("afdian".into())
}

#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct V2 {
    #[serde(flatten)]
    common: Common,
    license_version: u8,
    #[serde(rename = "type")]
    license_type: Option<String>,
    order_id: Option<String>,
    payment_gateway: Option<String>,
    payment_order_id: Option<String>,
    email: Option<String>,
    sku: Option<String>,
    paid: Option<serde_json::Number>,
    #[serde(default)]
    start_at: i64,
    features: Option<Vec<String>>,
}

impl Payload {
    pub(crate) fn local_trial(
        owner: &str,
        source: &str,
        description: &str,
        now_ms: i64,
        expire_at: i64,
    ) -> Self {
        Self::V2(V2 {
            common: Common {
                verify_magic: "PeerBanHelper".into(),
                source: Some(source.into()),
                license_to: Some(owner.into()),
                create_at: now_ms,
                expire_at,
                description: Some(description.into()),
                hidden: Some("Local License".into()),
            },
            license_version: 2,
            license_type: Some("local".into()),
            order_id: None,
            payment_gateway: None,
            payment_order_id: None,
            email: None,
            sku: None,
            paid: Some(0.into()),
            start_at: now_ms,
            features: Some(vec!["basic".into()]),
        })
    }

    pub(crate) fn parse(verified_json: &[u8]) -> Result<Self, Error> {
        let value: Value =
            serde_json::from_slice(verified_json).map_err(|_| Error::InvalidPayload)?;
        if !value.is_object() || value["verifyMagic"].as_str() != Some("PeerBanHelper") {
            return Err(Error::InvalidPayload);
        }
        match value.get("licenseVersion") {
            None => serde_json::from_value(value)
                .map(Self::V1)
                .map_err(|_| Error::InvalidPayload),
            Some(version) if version.as_u64() == Some(2) => serde_json::from_value(value)
                .map(Self::V2)
                .map_err(|_| Error::InvalidPayload),
            _ => Err(Error::UnsupportedVersion),
        }
    }

    pub(crate) fn version(&self) -> u8 {
        match self {
            Self::V1(_) => 1,
            Self::V2(_) => 2,
        }
    }

    pub(crate) fn common(&self) -> &Common {
        match self {
            Self::V1(license) => &license.common,
            Self::V2(license) => &license.common,
        }
    }

    pub(crate) fn license_type(&self) -> Option<&str> {
        match self {
            Self::V1(license) => license.license_type.as_deref(),
            Self::V2(license) => license.license_type.as_deref(),
        }
    }

    pub(crate) fn start_at(&self) -> i64 {
        match self {
            Self::V1(_) => -1,
            Self::V2(license) => license.start_at,
        }
    }

    pub(crate) fn features(&self) -> impl Iterator<Item = &str> {
        let (legacy, explicit): (&[&str], &[String]) = match self {
            Self::V1(_) => (&["basic", "paid"], &[]),
            Self::V2(license) => (&[], license.features.as_deref().unwrap_or(&[])),
        };
        legacy
            .iter()
            .copied()
            .chain(explicit.iter().map(String::as_str))
    }

    pub(crate) fn revoke_fields(&self) -> [Option<&str>; 5] {
        let (order_id, payment_order_id, email) = match self {
            Self::V1(_) => (None, None, None),
            Self::V2(license) => (
                license.order_id.as_deref(),
                license.payment_order_id.as_deref(),
                license.email.as_deref(),
            ),
        };
        [
            self.common().license_to.as_deref(),
            self.common().description.as_deref(),
            order_id,
            payment_order_id,
            email,
        ]
    }

    pub(crate) fn remote_fields(&self) -> [(&'static str, Option<&str>); 8] {
        let common = self.common();
        let [_, _, order_id, payment_order_id, _] = self.revoke_fields();
        let (gateway, sku) = match self {
            Self::V1(_) => (None, None),
            Self::V2(license) => (license.payment_gateway.as_deref(), license.sku.as_deref()),
        };
        [
            ("licenseTo", common.license_to.as_deref()),
            ("description", common.description.as_deref()),
            ("orderId", order_id),
            ("paymentOrderId", payment_order_id),
            ("paymentGateway", gateway),
            ("sku", sku),
            ("source", common.source.as_deref()),
            ("type", self.license_type()),
        ]
    }
}
