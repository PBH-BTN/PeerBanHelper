use std::collections::BTreeMap;
use std::path::Path;

use anyhow::{Result, bail};
use pbh_storage::{AlertDeduplication, AlertRecord, NewAlert};
use pbh_storage::{
    BanHistoryEntry, BanHistoryPage, BanHistoryQuery, BanRecord, MySqlConfig, MySqlStore,
    PcbAddressKey, PcbRangeKey, PcbState, PostgresConfig, PostgresStore, SQLITE_FILENAME,
    SqliteStore,
};
use pbh_storage::{ConnectionMetricsBucket, PeerSessionObservation};
use pbh_storage::{PeerRecord, PeerRecordCursor, PeerRecordObservation};
use pbh_storage::{SubscriptionLog, SubscriptionLogPage};
use pbh_storage::{TrackedSwarmCursor, TrackedSwarmObservation, TrackedSwarmRecord};
use pbh_storage::{TrafficHour, TrafficSample};

use crate::config::DatabaseConfig;

pub(crate) enum Database {
    Sqlite(SqliteStore),
    Postgres(PostgresStore),
    MySql(MySqlStore),
}

impl Database {
    pub(crate) async fn open(config: &DatabaseConfig, directory: &Path) -> Result<Self> {
        match config.kind.to_ascii_lowercase().as_str() {
            "sqlite" => Ok(Self::Sqlite(SqliteStore::open(
                directory.join(SQLITE_FILENAME),
            )?)),
            "postgresql" => {
                let mut postgres = PostgresConfig::new();
                postgres
                    .host(&config.host)
                    .port(config.port.unwrap_or(5432))
                    .user(&config.username)
                    .password(&config.password)
                    .dbname(&config.database);
                Ok(Self::Postgres(PostgresStore::open(postgres).await?))
            }
            "mysql" | "mariadb" => {
                let mysql = MySqlConfig::default()
                    .ip_or_hostname(&config.host)
                    .tcp_port(config.port.unwrap_or(3306))
                    .prefer_socket(false)
                    .user(Some(&config.username))
                    .pass(Some(&config.password))
                    .db_name(Some(&config.database));
                Ok(Self::MySql(MySqlStore::open(mysql).await?))
            }
            "h2" => {
                bail!("H2 is retired in the Rust backend; convert the database before using it")
            }
            _ => bail!(
                "database type {} is unavailable in the preview",
                config.kind
            ),
        }
    }

    pub(crate) async fn load_bans(&mut self) -> Result<Vec<BanRecord>> {
        Ok(match self {
            Self::Sqlite(database) => database.load_bans()?,
            Self::Postgres(database) => database.load_bans().await?,
            Self::MySql(database) => database.load_bans().await?,
        })
    }

    pub(crate) async fn publish_alert(
        &mut self,
        alert: &NewAlert<'_>,
        deduplication: AlertDeduplication,
    ) -> Result<bool> {
        Ok(match self {
            Self::Sqlite(database) => database.publish_alert(alert, deduplication)?,
            Self::Postgres(database) => database.publish_alert(alert, deduplication).await?,
            Self::MySql(database) => database.publish_alert(alert, deduplication).await?,
        })
    }

    pub(crate) async fn unread_alerts(&mut self) -> Result<Vec<AlertRecord>> {
        Ok(match self {
            Self::Sqlite(database) => database.unread_alerts()?,
            Self::Postgres(database) => database.unread_alerts().await?,
            Self::MySql(database) => database.unread_alerts().await?,
        })
    }

    pub(crate) async fn mark_alert_read(&mut self, id: i64, now_ms: i64) -> Result<bool> {
        Ok(match self {
            Self::Sqlite(database) => database.mark_alert_read(id, now_ms)?,
            Self::Postgres(database) => database.mark_alert_read(id, now_ms).await?,
            Self::MySql(database) => database.mark_alert_read(id, now_ms).await?,
        })
    }

    pub(crate) async fn mark_all_alerts_read(&mut self, now_ms: i64) -> Result<u64> {
        Ok(match self {
            Self::Sqlite(database) => database.mark_all_alerts_read(now_ms)?,
            Self::Postgres(database) => database.mark_all_alerts_read(now_ms).await?,
            Self::MySql(database) => database.mark_all_alerts_read(now_ms).await?,
        })
    }

    pub(crate) async fn delete_alert(&mut self, id: i64) -> Result<bool> {
        Ok(match self {
            Self::Sqlite(database) => database.delete_alert(id)?,
            Self::Postgres(database) => database.delete_alert(id).await?,
            Self::MySql(database) => database.delete_alert(id).await?,
        })
    }

    pub(crate) async fn cleanup_alerts(&mut self, before_ms: i64) -> Result<u64> {
        Ok(match self {
            Self::Sqlite(database) => database.cleanup_alerts(before_ms)?,
            Self::Postgres(database) => database.cleanup_alerts(before_ms).await?,
            Self::MySql(database) => database.cleanup_alerts(before_ms).await?,
        })
    }

    pub(crate) async fn ban_history(&mut self, query: &BanHistoryQuery) -> Result<BanHistoryPage> {
        Ok(match self {
            Self::Sqlite(database) => database.ban_history(query)?,
            Self::Postgres(database) => database.ban_history(query).await?,
            Self::MySql(database) => database.ban_history(query).await?,
        })
    }

    pub(crate) async fn cleanup_ban_history(&mut self, before_ms: i64) -> Result<u64> {
        Ok(match self {
            Self::Sqlite(database) => database.cleanup_ban_history(before_ms)?,
            Self::Postgres(database) => database.cleanup_ban_history(before_ms).await?,
            Self::MySql(database) => database.cleanup_ban_history(before_ms).await?,
        })
    }

    pub(crate) async fn append_subscription_log(
        &mut self,
        rule_id: &str,
        update_ms: i64,
        count: i32,
        manual: bool,
    ) -> Result<()> {
        match self {
            Self::Sqlite(store) => {
                store.append_subscription_log(rule_id, update_ms, count, manual)?
            }
            Self::Postgres(store) => {
                store
                    .append_subscription_log(rule_id, update_ms, count, manual)
                    .await?
            }
            Self::MySql(store) => {
                store
                    .append_subscription_log(rule_id, update_ms, count, manual)
                    .await?
            }
        }
        Ok(())
    }

    pub(crate) async fn subscription_logs(
        &mut self,
        rule_id: Option<&str>,
        page: i64,
        size: i64,
    ) -> Result<SubscriptionLogPage> {
        Ok(match self {
            Self::Sqlite(store) => store.subscription_logs(rule_id, page, size)?,
            Self::Postgres(store) => store.subscription_logs(rule_id, page, size).await?,
            Self::MySql(store) => store.subscription_logs(rule_id, page, size).await?,
        })
    }

    pub(crate) async fn latest_subscription_log(
        &mut self,
        rule_id: &str,
    ) -> Result<Option<SubscriptionLog>> {
        Ok(match self {
            Self::Sqlite(store) => store.latest_subscription_log(rule_id)?,
            Self::Postgres(store) => store.latest_subscription_log(rule_id).await?,
            Self::MySql(store) => store.latest_subscription_log(rule_id).await?,
        })
    }

    pub(crate) async fn global_total_connections_count(
        &mut self,
        start_ms: i64,
        end_ms: i64,
    ) -> Result<i64> {
        Ok(match self {
            Self::Sqlite(database) => database.global_total_connections_count(start_ms, end_ms)?,
            Self::Postgres(database) => {
                database
                    .global_total_connections_count(start_ms, end_ms)
                    .await?
            }
            Self::MySql(database) => {
                database
                    .global_total_connections_count(start_ms, end_ms)
                    .await?
            }
        })
    }

    pub(crate) async fn connection_metrics(
        &mut self,
        start_ms: i64,
        end_ms: i64,
        downloader: Option<&str>,
    ) -> Result<Vec<ConnectionMetricsBucket>> {
        Ok(match self {
            Self::Sqlite(database) => database.connection_metrics(start_ms, end_ms, downloader)?,
            Self::Postgres(database) => {
                database
                    .connection_metrics(start_ms, end_ms, downloader)
                    .await?
            }
            Self::MySql(database) => {
                database
                    .connection_metrics(start_ms, end_ms, downloader)
                    .await?
            }
        })
    }

    pub(crate) async fn upsert_peer_sessions(
        &mut self,
        sessions: &[PeerSessionObservation<'_>],
    ) -> Result<()> {
        match self {
            Self::Sqlite(database) => database.upsert_peer_sessions(sessions)?,
            Self::Postgres(database) => database.upsert_peer_sessions(sessions).await?,
            Self::MySql(database) => database.upsert_peer_sessions(sessions).await?,
        }
        Ok(())
    }

    pub(crate) async fn aggregate_peer_sessions(&mut self, today_start_ms: i64) -> Result<u64> {
        Ok(match self {
            Self::Sqlite(database) => database.aggregate_peer_sessions(today_start_ms)?,
            Self::Postgres(database) => database.aggregate_peer_sessions(today_start_ms).await?,
            Self::MySql(database) => database.aggregate_peer_sessions(today_start_ms).await?,
        })
    }

    pub(crate) async fn cleanup_connection_metrics(&mut self, before_ms: i64) -> Result<u64> {
        Ok(match self {
            Self::Sqlite(database) => database.cleanup_connection_metrics(before_ms)?,
            Self::Postgres(database) => database.cleanup_connection_metrics(before_ms).await?,
            Self::MySql(database) => database.cleanup_connection_metrics(before_ms).await?,
        })
    }

    pub(crate) async fn upsert_peer_records(
        &mut self,
        observations: &[PeerRecordObservation<'_>],
    ) -> Result<()> {
        match self {
            Self::Sqlite(database) => {
                database.upsert_peer_records(observations)?;
            }
            Self::Postgres(database) => {
                database.upsert_peer_records(observations).await?;
            }
            Self::MySql(database) => {
                database.upsert_peer_records(observations).await?;
            }
        }
        Ok(())
    }

    pub(crate) async fn pending_peer_records(
        &mut self,
        cursor: PeerRecordCursor,
        limit: u32,
    ) -> Result<Vec<PeerRecord>> {
        Ok(match self {
            Self::Sqlite(database) => database.pending_peer_records(cursor, limit)?,
            Self::Postgres(database) => database.pending_peer_records(cursor, limit).await?,
            Self::MySql(database) => database.pending_peer_records(cursor, limit).await?,
        })
    }

    pub(crate) async fn cleanup_peer_records(&mut self, cutoff_ms: i64) -> Result<()> {
        match self {
            Self::Sqlite(database) => {
                database.cleanup_peer_records(cutoff_ms)?;
            }
            Self::Postgres(database) => {
                database.cleanup_peer_records(cutoff_ms).await?;
            }
            Self::MySql(database) => {
                database.cleanup_peer_records(cutoff_ms).await?;
            }
        }
        Ok(())
    }

    pub(crate) async fn upsert_traffic_samples(
        &mut self,
        samples: &[TrafficSample<'_>],
    ) -> Result<()> {
        match self {
            Self::Sqlite(database) => database.upsert_traffic_samples(samples)?,
            Self::Postgres(database) => database.upsert_traffic_samples(samples).await?,
            Self::MySql(database) => database.upsert_traffic_samples(samples).await?,
        }
        Ok(())
    }

    pub(crate) async fn traffic_hours(
        &mut self,
        downloader: Option<&str>,
        start_ms: i64,
        end_ms: i64,
    ) -> Result<Vec<TrafficHour>> {
        Ok(match self {
            Self::Sqlite(database) => database.traffic_hours(downloader, start_ms, end_ms)?,
            Self::Postgres(database) => {
                database.traffic_hours(downloader, start_ms, end_ms).await?
            }
            Self::MySql(database) => database.traffic_hours(downloader, start_ms, end_ms).await?,
        })
    }

    pub(crate) async fn reset_tracked_swarm(&mut self) -> Result<()> {
        match self {
            Self::Sqlite(database) => {
                database.reset_tracked_swarm()?;
            }
            Self::Postgres(database) => {
                database.reset_tracked_swarm().await?;
            }
            Self::MySql(database) => {
                database.reset_tracked_swarm().await?;
            }
        }
        Ok(())
    }

    pub(crate) async fn upsert_tracked_swarm(
        &mut self,
        rows: &[TrackedSwarmObservation<'_>],
    ) -> Result<()> {
        match self {
            Self::Sqlite(database) => {
                database.upsert_tracked_swarm(rows)?;
            }
            Self::Postgres(database) => {
                database.upsert_tracked_swarm(rows).await?;
            }
            Self::MySql(database) => {
                database.upsert_tracked_swarm(rows).await?;
            }
        }
        Ok(())
    }

    pub(crate) async fn pending_tracked_swarm(
        &mut self,
        cursor: TrackedSwarmCursor,
        limit: usize,
    ) -> Result<Vec<TrackedSwarmRecord>> {
        Ok(match self {
            Self::Sqlite(database) => database.pending_tracked_swarm(cursor, limit)?,
            Self::Postgres(database) => database.pending_tracked_swarm(cursor, limit).await?,
            Self::MySql(database) => database.pending_tracked_swarm(cursor, limit).await?,
        })
    }

    pub(crate) async fn read_metadata(
        &mut self,
        keys: &[&str],
    ) -> Result<BTreeMap<String, Option<String>>> {
        Ok(match self {
            Self::Sqlite(database) => database.read_metadata(keys)?,
            Self::Postgres(database) => database.read_metadata(keys).await?,
            Self::MySql(database) => database.read_metadata(keys).await?,
        })
    }

    pub(crate) async fn write_metadata(&mut self, values: &[(&str, Option<&str>)]) -> Result<()> {
        match self {
            Self::Sqlite(database) => database.write_metadata(values)?,
            Self::Postgres(database) => database.write_metadata(values).await?,
            Self::MySql(database) => database.write_metadata(values).await?,
        }
        Ok(())
    }

    pub(crate) async fn load_pcb_address(
        &mut self,
        key: PcbAddressKey<'_>,
    ) -> Result<Option<PcbState>> {
        Ok(match self {
            Self::Sqlite(database) => database.load_pcb_address(key)?,
            Self::Postgres(database) => database.load_pcb_address(key).await?,
            Self::MySql(database) => database.load_pcb_address(key).await?,
        })
    }

    pub(crate) async fn load_pcb_range(
        &mut self,
        key: PcbRangeKey<'_>,
    ) -> Result<Option<PcbState>> {
        Ok(match self {
            Self::Sqlite(database) => database.load_pcb_range(key)?,
            Self::Postgres(database) => database.load_pcb_range(key).await?,
            Self::MySql(database) => database.load_pcb_range(key).await?,
        })
    }

    pub(crate) async fn merge_pcb(
        &mut self,
        addresses: &[(PcbAddressKey<'_>, PcbState)],
        ranges: &[(PcbRangeKey<'_>, PcbState)],
    ) -> Result<()> {
        match self {
            Self::Sqlite(database) => {
                database.merge_pcb(addresses, ranges)?;
            }
            Self::Postgres(database) => {
                database.merge_pcb(addresses, ranges).await?;
            }
            Self::MySql(database) => {
                database.merge_pcb(addresses, ranges).await?;
            }
        }
        Ok(())
    }

    pub(crate) async fn cleanup_pcb(&mut self, cutoff_ms: i64) -> Result<()> {
        match self {
            Self::Sqlite(database) => {
                database.cleanup_pcb(cutoff_ms)?;
            }
            Self::Postgres(database) => {
                database.cleanup_pcb(cutoff_ms).await?;
            }
            Self::MySql(database) => {
                database.cleanup_pcb(cutoff_ms).await?;
            }
        }
        Ok(())
    }

    pub(crate) async fn delete_pcb_address(
        &mut self,
        torrent_id: &str,
        address: std::net::IpAddr,
    ) -> Result<()> {
        match self {
            Self::Sqlite(database) => {
                database.delete_pcb_address(torrent_id, address)?;
            }
            Self::Postgres(database) => {
                database.delete_pcb_address(torrent_id, address).await?;
            }
            Self::MySql(database) => {
                database.delete_pcb_address(torrent_id, address).await?;
            }
        }
        Ok(())
    }

    pub(crate) async fn commit_ban_wave(
        &mut self,
        bans: Option<&[BanRecord]>,
        history: &[BanHistoryEntry<'_>],
    ) -> Result<()> {
        match self {
            Self::Sqlite(database) => database.commit_ban_wave(bans, history)?,
            Self::Postgres(database) => database.commit_ban_wave(bans, history).await?,
            Self::MySql(database) => database.commit_ban_wave(bans, history).await?,
        }
        Ok(())
    }
}
