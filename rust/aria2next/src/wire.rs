use std::{fmt::Display, str::FromStr};

use serde::{Deserialize, Deserializer, de};

/// Aria protocols return counters and booleans as strings. Accept JSON scalars
/// too, as extension implementations have used both encodings.
pub(crate) fn scalar<'de, T, D>(deserializer: D) -> Result<T, D::Error>
where
    T: Deserialize<'de> + Default + FromStr,
    T::Err: Display,
    D: Deserializer<'de>,
{
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum Wire<T> {
        Scalar(T),
        Text(String),
    }

    match Option::<Wire<T>>::deserialize(deserializer)? {
        Some(Wire::Scalar(value)) => Ok(value),
        Some(Wire::Text(text)) => text.parse().map_err(de::Error::custom),
        None => Ok(T::default()),
    }
}
