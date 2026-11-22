use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::future::Future;
use std::sync::Mutex;
use std::time::{Duration, Instant};

use serde_json::value::RawValue;

use crate::Error;

const CAPACITY: usize = 4096;
const TTL: Duration = Duration::from_secs(60);

#[derive(Default)]
pub(crate) struct CommentCounts {
    state: Mutex<HashMap<String, Entry>>,
    network: tokio::sync::Mutex<()>,
}

pub(crate) type CountResponse = BTreeMap<String, Box<RawValue>>;

struct Entry {
    count: i64,
    stored_at: Instant,
}

impl CommentCounts {
    pub(crate) async fn query<F, Fut>(
        &self,
        addresses: &[String],
        fetch: F,
    ) -> Result<BTreeMap<String, i64>, Error>
    where
        F: FnOnce(Vec<String>) -> Fut,
        Fut: Future<Output = Result<CountResponse, Error>>,
    {
        let mut counts = self.read(addresses);
        if addresses.iter().all(|address| counts.contains_key(address)) {
            return Ok(counts);
        }
        let _request = self.network.try_lock().map_err(|_| Error::Upstream {
            status: 429,
            message: None,
        })?;
        counts.extend(self.read(addresses));
        let mut seen = BTreeSet::new();
        let missing: Vec<_> = addresses
            .iter()
            .filter(|address| !counts.contains_key(*address) && seen.insert(*address))
            .cloned()
            .collect();
        if !missing.is_empty() {
            let reply = fetch(missing.clone()).await?;
            let fresh = missing
                .into_iter()
                .map(|key| {
                    let count = reply
                        .get(&key)
                        .and_then(|value| exact_count(value.get()))
                        .ok_or(Error::InvalidResponse("IP comment counts"))?;
                    Ok((key, count))
                })
                .collect::<Result<BTreeMap<_, _>, Error>>()?;
            self.store(&fresh);
            counts.extend(fresh);
        }
        Ok(counts)
    }

    fn read(&self, addresses: &[String]) -> BTreeMap<String, i64> {
        let mut state = self.state.lock().expect("comment count cache lock");
        state.retain(|_, entry| entry.stored_at.elapsed() < TTL);
        addresses
            .iter()
            .filter_map(|address| {
                state
                    .get(address)
                    .map(|entry| (address.clone(), entry.count))
            })
            .collect()
    }

    fn store(&self, counts: &BTreeMap<String, i64>) {
        let mut state = self.state.lock().expect("comment count cache lock");
        let stored_at = Instant::now();
        for (address, count) in counts {
            state.insert(
                address.clone(),
                Entry {
                    count: *count,
                    stored_at,
                },
            );
        }
        if state.len() > CAPACITY {
            let overflow = state.len() - CAPACITY;
            let mut oldest: Vec<_> = state
                .iter()
                .map(|(address, entry)| (entry.stored_at, address.clone()))
                .collect();
            oldest.sort_unstable();
            for (_, expired) in oldest.into_iter().take(overflow) {
                state.remove(&expired);
            }
        }
    }
}

/// Gson's BigDecimal.longValueExact accepts integral decimal/exponent forms.
/// Use digit arithmetic so precision and exponent handling stay bounded.
fn exact_count(raw: &str) -> Option<i64> {
    if let Ok(value) = raw.parse::<i64>() {
        return (value >= 0).then_some(value);
    }
    let negative = raw.starts_with('-');
    let raw = raw.trim_start_matches('-');
    let (coefficient, exponent) = match raw.split_once(['e', 'E']) {
        Some((coefficient, exponent)) => (coefficient, exponent.parse::<i32>().ok()?),
        None => (raw, 0),
    };
    let (integer, fraction) = coefficient.split_once('.').unwrap_or((coefficient, ""));
    let digits = format!("{integer}{fraction}");
    let digits = digits.trim_start_matches('0');
    let scale = i32::try_from(fraction.len()).ok()?.checked_sub(exponent)?;
    if digits.is_empty() {
        return Some(0);
    }
    if negative {
        return None;
    }
    if scale > 0 {
        let split = digits.len().checked_sub(scale as usize)?;
        let (whole, remainder) = digits.split_at(split);
        if remainder.bytes().all(|digit| digit == b'0') {
            return whole.parse::<i64>().ok();
        }
        return None;
    }
    let zeros = usize::try_from(-i64::from(scale)).ok()?;
    if digits.len().checked_add(zeros)? > 19 {
        return None;
    }
    let value = digits.parse::<i64>().ok()?;
    value.checked_mul(10_i64.checked_pow(zeros as u32)?)
}

#[cfg(test)]
mod tests {
    use super::{CommentCounts, exact_count};
    use crate::Error;

    #[test]
    fn validates_nonnegative_signed_long_without_floating_point() {
        for (input, expected) in [
            ("0", Some(0)),
            ("-0.000e+01", Some(0)),
            ("3.00e2", Some(300)),
            ("1.1", None),
            ("-1", None),
            ("1e9999999999", None),
            ("9223372036854775807.0", Some(i64::MAX)),
            ("9223372036854775808.0", None),
        ] {
            assert_eq!(exact_count(input), expected, "{input}");
        }
    }

    #[tokio::test]
    async fn additional_cache_miss_is_rate_limited_during_a_fetch() {
        let cache = CommentCounts::default();
        let ips = vec![String::from("192.0.2.1")];
        let first = cache.query(&ips, |_| async {
            tokio::task::yield_now().await;
            Ok(serde_json::from_str(r#"{"192.0.2.1": 0}"#).unwrap())
        });
        let overlapping = cache.query(&ips, |_| async {
            panic!("second request reached upstream")
        });
        let (first, overlapping) = tokio::join!(first, overlapping);
        assert_eq!(first.unwrap()["192.0.2.1"], 0);
        assert!(matches!(
            overlapping,
            Err(Error::Upstream {
                status: 429,
                message: None
            })
        ));
    }
}
