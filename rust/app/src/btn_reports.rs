//! BTN reporting transactions advance their cursor only after server acknowledgement.

use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use anyhow::Result;
use pbh_btn::reports::{
    BAN_HISTORY_CURSOR_KEY, BAN_HISTORY_PAGE_SIZE, BanHistory, LegacyBan, LegacyBanEvent,
    LegacyPeer, PEER_HISTORY_CURSOR_KEY, PEER_HISTORY_PAGE_SIZE, PeerHistory, Report,
    SWARM_CURSOR_KEY, SWARM_PAGE_SIZE, Swarm, TorrentIdentifier, peer_history_start,
};
use pbh_storage::{
    BanHistoryFilter, BanHistoryOrder, BanHistoryQuery, BanHistoryRecord, BanRecord,
    PeerRecordCursor, TrackedSwarmCursor,
};
use serde_json::Value;
use tokio::sync::Mutex;

use crate::ban_api::BanLanguage;
use crate::downloader::{Peer, Torrent};
use crate::peer_recording::SwarmRecorder;
use crate::storage::Database;

const PEER_HISTORY_CURSOR_ID: &str = "btn.submithistory.timestamp.id";

pub(crate) fn legacy_peer(torrent: &Torrent, peer: &Peer) -> LegacyPeer {
    LegacyPeer {
        ip_address: peer.ip.to_string(),
        peer_port: i32::from(peer.port),
        peer_id: peer.peer_id.clone(),
        client_name: peer.client_name.clone(),
        torrent_identifier: TorrentIdentifier::from_info_hash(&torrent.hash),
        torrent_is_private: torrent.private,
        torrent_size: torrent.size,
        downloaded: peer.downloaded,
        rt_download_speed: peer.download_speed,
        uploaded: peer.uploaded,
        rt_upload_speed: peer.upload_speed,
        peer_progress: peer.progress,
        downloader_progress: torrent.progress,
        peer_flag: peer.flags.clone(),
    }
}

pub(crate) fn legacy_ban(record: &BanRecord, language: BanLanguage) -> Option<LegacyBanEvent> {
    let metadata = &record.metadata;
    let peer = &metadata["peer"];
    let torrent = &metadata["torrent"];
    let context = metadata["context"].as_str().unwrap_or_default();
    Some(LegacyBanEvent {
        ban_at: metadata["banAt"].as_i64()?,
        disconnect: metadata["banForDisconnect"].as_bool().unwrap_or_default(),
        exclude_from_report: metadata["excludeFromReport"].as_bool().unwrap_or_default(),
        ban: LegacyBan {
            btn_ban: context == "com.ghostchu.peerbanhelper.module.impl.rule.BtnNetworkOnline",
            ban_unique_id: LegacyBan::id_from_seed(&legacy_ban_seed(metadata, language)?),
            module: context.to_owned(),
            rule: language.render(&metadata["description"]),
            structured_data: metadata["structuredData"].clone(),
            peer: LegacyPeer {
                ip_address: peer["address"]["ip"]
                    .as_str()
                    .map_or_else(|| record.address.to_string(), str::to_owned),
                peer_port: peer["address"]["port"]
                    .as_i64()
                    .and_then(|value| value.try_into().ok())
                    .unwrap_or_default(),
                peer_id: peer["id"].as_str().map(str::to_owned),
                client_name: peer["clientName"].as_str().map(str::to_owned),
                torrent_identifier: TorrentIdentifier::from_info_hash(torrent["hash"].as_str()?),
                torrent_is_private: torrent["privateTorrent"].as_bool().unwrap_or_default(),
                torrent_size: torrent["size"].as_i64().unwrap_or_default(),
                downloaded: peer["downloaded"].as_i64().unwrap_or_default(),
                rt_download_speed: peer["downloadSpeed"].as_i64().unwrap_or_default(),
                uploaded: peer["uploaded"].as_i64().unwrap_or_default(),
                rt_upload_speed: peer["uploadSpeed"].as_i64().unwrap_or_default(),
                peer_progress: peer["progress"].as_f64().unwrap_or_default(),
                downloader_progress: torrent["progress"].as_f64().unwrap_or_default(),
                peer_flag: peer["flags"].as_str().map(str::to_owned),
            },
        },
    })
}

fn legacy_ban_seed(metadata: &Value, language: BanLanguage) -> Option<String> {
    let ban_date = java_utc_datetime(metadata["banAt"].as_i64()?)?;
    let end_date = java_utc_datetime(metadata["unbanAt"].as_i64()?)?;
    Some(format!(
        "BanMetadata{{context='{}', randomId='{}', banAt={ban_date}, unbanAt={end_date}, banForDisconnect={}, excludeFromReport={}, excludeFromDisplay={}, rule={}, description={}, structuredData={}}}",
        metadata["context"].as_str().unwrap_or("null"),
        metadata["randomId"].as_str().unwrap_or("null"),
        metadata["banForDisconnect"].as_bool().unwrap_or_default(),
        metadata["excludeFromReport"].as_bool().unwrap_or_default(),
        metadata["excludeFromDisplay"].as_bool().unwrap_or_default(),
        language.render(&metadata["rule"]),
        language.render(&metadata["description"]),
        java_collection(&metadata["structuredData"]),
    ))
}

pub(crate) fn java_utc_datetime(epoch_ms: i64) -> Option<String> {
    use std::fmt::Write;

    let instant =
        time::OffsetDateTime::from_unix_timestamp_nanos(i128::from(epoch_ms) * 1_000_000).ok()?;
    let mut value = format!(
        "{}T{:02}:{:02}",
        instant.date(),
        instant.hour(),
        instant.minute()
    );
    if instant.second() != 0 || instant.millisecond() != 0 {
        write!(value, ":{:02}", instant.second()).ok()?;
    }
    if instant.millisecond() != 0 {
        write!(value, ".{:03}", instant.millisecond()).ok()?;
    }
    value.push('Z');
    Some(value)
}

fn java_collection(value: &Value) -> String {
    match value {
        Value::Object(values) => format!(
            "{{{}}}",
            values
                .iter()
                .map(|(key, value)| format!("{key}={}", java_collection(value)))
                .collect::<Vec<_>>()
                .join(", ")
        ),
        Value::Array(values) => format!(
            "[{}]",
            values
                .iter()
                .map(java_collection)
                .collect::<Vec<_>>()
                .join(", ")
        ),
        Value::String(value) => value.clone(),
        _ => value.to_string(),
    }
}

pub(crate) async fn send_swarm(
    report: &Report<Swarm>,
    database: &Arc<Mutex<Database>>,
    recorder: Option<&Mutex<SwarmRecorder>>,
) -> Result<usize> {
    let now = i64::try_from(SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis())?;
    if let Some(recorder) = recorder {
        recorder.lock().await.flush(database, now).await?;
    }
    let saved = database
        .lock()
        .await
        .read_metadata(&[SWARM_CURSOR_KEY])
        .await?;
    let mut cursor = saved
        .get(SWARM_CURSOR_KEY)
        .and_then(Option::as_deref)
        .and_then(|cursor| cursor.split_once(','))
        .and_then(|(time, id)| {
            Some(TrackedSwarmCursor {
                last_time_seen_ms: time.parse().ok()?,
                id: id.parse().ok()?,
            })
        })
        .unwrap_or_default();
    let mut reported = 0_usize;
    loop {
        let entries = database
            .lock()
            .await
            .pending_tracked_swarm(cursor, SWARM_PAGE_SIZE as usize)
            .await?;
        let Some(last) = entries.last() else {
            return Ok(reported);
        };
        let rows = entries
            .iter()
            .map(|entry| Swarm {
                torrent_identifier: TorrentIdentifier::from_info_hash(&entry.info_hash),
                torrent_is_private: Some(entry.torrent_is_private),
                torrent_size: entry.torrent_size,
                downloader: Some(entry.downloader.clone()),
                downloader_progress: entry.downloader_progress,
                peer_ip: entry.ip,
                peer_port: entry.port,
                peer_id: entry.peer_id.clone(),
                peer_client_name: entry.client_name.clone(),
                peer_progress: entry.peer_progress,
                to_peer_traffic: entry.uploaded,
                to_peer_traffic_offset: entry.uploaded_offset,
                from_peer_traffic: entry.downloaded,
                from_peer_traffic_offset: entry.downloaded_offset,
                first_time_seen: entry.first_time_seen_ms,
                last_time_seen: entry.last_time_seen_ms,
                peer_last_flags: entry.last_flags.clone(),
                upload_speed: entry.upload_speed,
                download_speed: entry.download_speed,
                download_speed_max: entry.download_speed_max,
                upload_speed_max: entry.upload_speed_max,
            })
            .collect::<Vec<_>>();
        reported = reported.saturating_add(report.send(&rows).await?);
        cursor = last.cursor();
        let persisted = format!("{},{}", cursor.last_time_seen_ms, cursor.id);
        database
            .lock()
            .await
            .write_metadata(&[(SWARM_CURSOR_KEY, Some(&persisted))])
            .await?;
    }
}

pub(crate) async fn send_ban_history(
    report: &Report<BanHistory>,
    database: &Arc<Mutex<Database>>,
) -> Result<usize> {
    let mut cursor = database
        .lock()
        .await
        .read_metadata(&[BAN_HISTORY_CURSOR_KEY])
        .await?
        .get(BAN_HISTORY_CURSOR_KEY)
        .and_then(Option::as_deref)
        .and_then(|record| record.parse().ok())
        .unwrap_or(0);
    let order = BanHistoryOrder::parse("id|asc")?;
    let mut reported = 0_usize;
    loop {
        let page = database
            .lock()
            .await
            .ban_history(&BanHistoryQuery {
                page: 1,
                size: BAN_HISTORY_PAGE_SIZE.into(),
                orders: vec![order],
                filter: Some(BanHistoryFilter::AfterId(cursor)),
            })
            .await?;
        let Some(last) = page.records.last() else {
            return Ok(reported);
        };
        let rows: Vec<_> = page.records.iter().filter_map(ban_record).collect();
        reported = reported.saturating_add(report.send(&rows).await?);
        cursor = last.id;
        database
            .lock()
            .await
            .write_metadata(&[(BAN_HISTORY_CURSOR_KEY, Some(&cursor.to_string()))])
            .await?;
        if page.records.len() < BAN_HISTORY_PAGE_SIZE as usize {
            return Ok(reported);
        }
    }
}

fn ban_record(record: &BanHistoryRecord) -> Option<BanHistory> {
    let torrent = record.torrent.as_ref()?;
    Some(BanHistory {
        ban_at: record.ban_at_ms,
        peer_ip: record.address,
        peer_port: record.port.into(),
        peer_id: record.peer_id.clone(),
        peer_client_name: record.peer_client_name.clone(),
        peer_progress: record.peer_progress,
        peer_flag: record.flags.clone(),
        torrent_identifier: TorrentIdentifier::from_info_hash(&torrent.info_hash),
        torrent_is_private: torrent.private.unwrap_or(false),
        torrent_size: torrent.size,
        from_peer_traffic: record.peer_downloaded.unwrap_or_default(),
        to_peer_traffic: record.peer_uploaded.unwrap_or_default(),
        downloader_progress: record.downloader_progress,
        module: Some(record.module_name.clone()),
        rule: BanLanguage::En.render(&record.rule),
        description: BanLanguage::En.render(&record.description),
        structured_data: record.structured_data.clone().unwrap_or_default(),
    })
}

pub(crate) async fn send_peer_history(
    report: &Report<PeerHistory>,
    database: &Arc<Mutex<Database>>,
) -> Result<usize> {
    let now = i64::try_from(SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis())?;
    let metadata = database
        .lock()
        .await
        .read_metadata(&[PEER_HISTORY_CURSOR_KEY, PEER_HISTORY_CURSOR_ID])
        .await?;
    let saved = metadata
        .get(PEER_HISTORY_CURSOR_KEY)
        .and_then(Option::as_deref);
    let start = peer_history_start(saved, now);
    let id = if saved.and_then(|saved| saved.parse::<i64>().ok()) == Some(start) {
        metadata
            .get(PEER_HISTORY_CURSOR_ID)
            .and_then(Option::as_deref)
            .and_then(|id| id.parse().ok())
            .unwrap_or_default()
    } else {
        database
            .lock()
            .await
            .write_metadata(&[
                (PEER_HISTORY_CURSOR_KEY, Some(&start.to_string())),
                (PEER_HISTORY_CURSOR_ID, None),
            ])
            .await?;
        0
    };
    let mut cursor = PeerRecordCursor {
        last_time_seen_ms: start,
        id,
    };
    let mut last_reported = 0;
    loop {
        let entries = database
            .lock()
            .await
            .pending_peer_records(cursor, PEER_HISTORY_PAGE_SIZE)
            .await?;
        let Some(last) = entries.last() else {
            return Ok(last_reported);
        };
        let rows: Vec<_> = entries
            .iter()
            .filter_map(|entry| {
                let torrent = entry.torrent.as_ref()?;
                Some(PeerHistory {
                    ip_address: entry.address,
                    peer_id: entry.peer_id.clone(),
                    client_name: entry.client_name.clone(),
                    torrent_identifier: TorrentIdentifier::from_info_hash(&torrent.info_hash),
                    torrent_size: torrent.size,
                    downloaded: entry.downloaded,
                    downloaded_offset: entry.downloaded_offset,
                    uploaded: entry.uploaded,
                    uploaded_offset: entry.uploaded_offset,
                    first_time_seen: entry.first_time_seen_ms,
                    last_time_seen: entry.last_time_seen_ms,
                    peer_flag: entry.last_flags.clone(),
                })
            })
            .collect();
        last_reported = report.send(now, &rows).await?;
        cursor = last.cursor();
        database
            .lock()
            .await
            .write_metadata(&[
                (
                    PEER_HISTORY_CURSOR_KEY,
                    Some(&cursor.last_time_seen_ms.to_string()),
                ),
                (PEER_HISTORY_CURSOR_ID, Some(&cursor.id.to_string())),
            ])
            .await?;
    }
}

#[cfg(test)]
mod legacy_tests {
    use super::*;

    #[test]
    fn preserves_legacy_java_metadata_field_order_and_timestamp_format() {
        let record = serde_json::json!({
            "context": "sample", "randomId": "001", "banAt": 0, "unbanAt": 1023,
            "rule": "a rule", "description": "a reason",
            "structuredData": {"first": ["example", 1], "another": true},
        });
        let seed = legacy_ban_seed(&record, BanLanguage::from_locale("en_us")).unwrap();
        assert_eq!(
            seed,
            "BanMetadata{context='sample', randomId='001', banAt=1970-01-01T00:00Z, unbanAt=1970-01-01T00:00:01.023Z, banForDisconnect=false, excludeFromReport=false, excludeFromDisplay=false, rule=a rule, description=a reason, structuredData={first=[example, 1], another=true}}"
        );
    }
}
