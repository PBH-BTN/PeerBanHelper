//! Counter and decision semantics from the Java ProgressCheatBlocker rule.

use serde_json::{Value, json};

use crate::Action;

#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct PcbState {
    pub last_report_progress: f64,
    pub last_report_uploaded: i64,
    pub tracking_uploaded_increase_total: i64,
    pub rewind_counter: i32,
    pub progress_difference_counter: i32,
    pub first_time_seen_ms: i64,
    pub last_time_seen_ms: i64,
    pub ban_delay_window_end_at_ms: i64,
    pub fast_pcb_test_execute_at_ms: i64,
    pub last_torrent_completed_size: i64,
}

pub struct PcbConfig {
    pub minimum_size: i64,
    pub block_excessive_clients: bool,
    pub excessive_threshold: f64,
    pub maximum_difference: f64,
    pub maximum_rewind: f64,
    pub max_wait_ms: i64,
    pub fast_test_progress: f64,
    pub fast_test_duration_ms: i64,
    pub ban_duration_ms: i64,
    pub disable_completed_excessive: bool,
}

pub struct PcbObservation {
    pub now_ms: i64,
    pub torrent_size: i64,
    pub completed_size: i64,
    pub reported_uploaded: i64,
    pub reported_progress: f64,
    pub upload_speed: i64,
    pub supports_unban: bool,
    pub handshaking: bool,
}

#[derive(Debug, PartialEq)]
pub struct PcbFinding {
    pub action: Action,
    pub duration_ms: i64,
    pub rule: &'static str,
    pub reason: &'static str,
    pub reason_params: Vec<Value>,
    pub details: Value,
}

pub fn check_progress(
    config: &PcbConfig,
    peer: &PcbObservation,
    range: &mut PcbState,
    address: &mut PcbState,
) -> Option<PcbFinding> {
    if peer.handshaking {
        return None;
    }

    let previous_uploaded = address.last_report_uploaded;
    let increment = if peer.reported_uploaded < previous_uploaded {
        peer.reported_uploaded
    } else {
        peer.reported_uploaded.wrapping_sub(previous_uploaded)
    };
    range.tracking_uploaded_increase_total = range
        .tracking_uploaded_increase_total
        .wrapping_add(increment);
    address.tracking_uploaded_increase_total = address
        .tracking_uploaded_increase_total
        .wrapping_add(increment);
    let computed_uploaded = peer
        .reported_uploaded
        .max(range.tracking_uploaded_increase_total)
        .max(address.tracking_uploaded_increase_total);
    let computed_completed = peer
        .completed_size
        .max(range.last_torrent_completed_size)
        .max(address.last_torrent_completed_size);
    let size = peer.torrent_size;
    let small = size < config.minimum_size;
    let uploading = peer.reported_uploaded > 0 || peer.upload_speed > 0;
    let result = if size > 0 && uploading {
        let details = json!({
            "torrentSize": size,
            "completedSize": peer.completed_size,
            "computedCompletedSize": computed_completed,
            "peerReportUploaded": peer.reported_uploaded,
            "peerLastReportUploaded": previous_uploaded,
            "prefixLastReportUploaded": range.last_report_uploaded,
            "peerTrackingUploadedIncreaseTotal": address.tracking_uploaded_increase_total,
            "prefixTrackingUploadedIncreaseTotal": range.tracking_uploaded_increase_total,
            "actualUploaded": computed_uploaded,
            "uploadedIncremental": increment,
            "fileTooSmall": small,
            "fastPcbTestPercentage": config.fast_test_progress,
        });
        let mut tracking = Tracking { range, address };
        classify(
            config,
            peer,
            &mut tracking,
            computed_uploaded,
            computed_completed,
            details,
        )
    } else {
        None
    };

    for state in [range, address] {
        if state.first_time_seen_ms == 0 {
            state.first_time_seen_ms = peer.now_ms;
        }
        state.last_report_uploaded = peer.reported_uploaded;
        if peer.reported_progress != 0.0 {
            state.last_report_progress = peer.reported_progress;
        }
        state.last_torrent_completed_size =
            state.last_torrent_completed_size.max(peer.completed_size);
        state.last_time_seen_ms = peer.now_ms;
    }
    result
}

struct Tracking<'a> {
    range: &'a mut PcbState,
    address: &'a mut PcbState,
}

impl Tracking<'_> {
    fn window_scheduled(&self) -> bool {
        self.range.ban_delay_window_end_at_ms > 0 || self.address.ban_delay_window_end_at_ms > 0
    }

    fn window_expired(&self, now_ms: i64) -> bool {
        (1..now_ms).contains(&self.range.ban_delay_window_end_at_ms)
            || (1..now_ms).contains(&self.address.ban_delay_window_end_at_ms)
    }

    fn schedule(&mut self, now_ms: i64, wait_ms: i64) {
        for state in [&mut self.range, &mut self.address] {
            if state.ban_delay_window_end_at_ms <= 0 {
                state.ban_delay_window_end_at_ms = now_ms.saturating_add(wait_ms);
            }
        }
    }

    fn reset(&mut self) {
        for state in [&mut self.range, &mut self.address] {
            if state.ban_delay_window_end_at_ms > 0 {
                state.ban_delay_window_end_at_ms = 0;
            }
        }
    }
}

fn classify(
    config: &PcbConfig,
    peer: &PcbObservation,
    tracking: &mut Tracking<'_>,
    uploaded: i64,
    completed: i64,
    mut details: Value,
) -> Option<PcbFinding> {
    let size = peer.torrent_size;
    let large_enough = size >= config.minimum_size;
    if config.fast_test_progress > 0.0
        && large_enough
        && peer.supports_unban
        && (tracking.range.fast_pcb_test_execute_at_ms == 0
            || tracking.address.fast_pcb_test_execute_at_ms == 0)
        && uploaded as f64 >= config.fast_test_progress * size as f64
    {
        tracking.range.fast_pcb_test_execute_at_ms = peer.now_ms;
        tracking.address.fast_pcb_test_execute_at_ms = peer.now_ms;
        details["type"] = json!("fastPcbTest");
        return Some(PcbFinding {
            action: Action::BanForDisconnect,
            duration_ms: config.fast_test_duration_ms,
            rule: "PCB_RULE_PEER_PROGRESS_CHEAT_TESTING",
            reason: "PCB_DESCRIPTION_PEER_PROGRESS_CHEAT_TESTING",
            reason_params: Vec::new(),
            details,
        });
    }

    let computed_progress = uploaded as f64 / size as f64;
    let reported_progress = peer.reported_progress;
    details["computedProgress"] = json!(computed_progress);
    details["clientReportedProgress"] = json!(reported_progress);
    if let Some(finding) = excessive(config, peer, tracking, uploaded, completed, &mut details) {
        return Some(finding);
    }
    if computed_progress <= reported_progress {
        return None;
    }

    let difference = (computed_progress - reported_progress).abs();
    details["difference"] = json!(difference);
    if large_enough && difference > config.maximum_difference {
        if !tracking.window_scheduled() {
            tracking.schedule(peer.now_ms, config.max_wait_ms);
        } else if tracking.window_expired(peer.now_ms) {
            tracking.range.progress_difference_counter =
                tracking.range.progress_difference_counter.wrapping_add(1);
            tracking.address.progress_difference_counter =
                tracking.address.progress_difference_counter.wrapping_add(1);
            tracking.reset();
            details["type"] = json!("deSyncDifference");
            return Some(ban(
                config,
                "PCB_RULE_REACHED_MAX_DIFFERENCE",
                "MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS",
                vec![
                    percentage(reported_progress),
                    percentage(computed_progress),
                    percentage(difference),
                ],
                details,
            ));
        }
    }

    if !large_enough || config.maximum_rewind <= 0.0 {
        return None;
    }
    let last_progress = tracking
        .address
        .last_report_progress
        .max(tracking.range.last_report_progress);
    let rewind = last_progress - reported_progress;
    details["peerLastReportProgress"] = json!(tracking.address.last_report_progress);
    details["rangeLastRecordProgress"] = json!(tracking.range.last_report_progress);
    details["lastReportProgress"] = json!(last_progress);
    details["rewind"] = json!(rewind);
    details["rewindMaximumDifference"] = json!(config.maximum_rewind);
    if rewind > config.maximum_rewind {
        if reported_progress > 0.0 || tracking.window_expired(peer.now_ms) {
            tracking.range.rewind_counter = tracking.range.rewind_counter.wrapping_add(1);
            tracking.address.rewind_counter = tracking.address.rewind_counter.wrapping_add(1);
            tracking.reset();
            details["type"] = json!("rewindProgress");
            return Some(ban(
                config,
                "PCB_RULE_PROGRESS_REWIND",
                "MODULE_PCB_PEER_BAN_REWIND",
                vec![
                    percentage(reported_progress),
                    percentage(computed_progress),
                    percentage(last_progress),
                    percentage(rewind),
                    percentage(config.maximum_rewind),
                ],
                details,
            ));
        }
        if !tracking.window_scheduled() {
            tracking.schedule(peer.now_ms, config.max_wait_ms);
        }
    }
    None
}

fn excessive(
    config: &PcbConfig,
    peer: &PcbObservation,
    tracking: &mut Tracking<'_>,
    uploaded: i64,
    completed: i64,
    details: &mut Value,
) -> Option<PcbFinding> {
    if !config.block_excessive_clients || uploaded == -1 {
        return None;
    }
    let torrent_size = peer.torrent_size;
    let (base, reason, subtype) = if uploaded > torrent_size {
        (
            torrent_size,
            "MODULE_PCB_EXCESSIVE_DOWNLOAD",
            "excessiveMaxDownloadThreshold",
        )
    } else if !config.disable_completed_excessive
        && peer.completed_size > 0
        && uploaded > peer.completed_size
    {
        (
            completed,
            "MODULE_PCB_EXCESSIVE_DOWNLOAD_INCOMPLETE",
            "excessiveMaxDownloadThresholdForIncompleteTask",
        )
    } else {
        return None;
    };
    let threshold = (base.max(config.minimum_size) as f64 * config.excessive_threshold) as i64;
    details["maxAllowedExcessiveThreshold"] = json!(threshold);
    if uploaded <= threshold {
        return None;
    }
    tracking.reset();
    details["type"] = json!(subtype);
    let mut params = vec![json!(torrent_size)];
    if base == completed && uploaded <= torrent_size {
        params.push(json!(peer.completed_size));
    }
    params.extend([json!(uploaded), json!(threshold)]);
    Some(ban(
        config,
        "PCB_RULE_REACHED_MAX_ALLOWED_EXCESSIVE_THRESHOLD",
        reason,
        params,
        details.clone(),
    ))
}

fn percentage(progress: f64) -> Value {
    json!(format!("{:.2}%", progress * 100.0))
}

fn ban(
    config: &PcbConfig,
    rule: &'static str,
    reason: &'static str,
    reason_params: Vec<Value>,
    details: Value,
) -> PcbFinding {
    PcbFinding {
        action: Action::Ban,
        duration_ms: config.ban_duration_ms,
        rule,
        reason,
        reason_params,
        details,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn config() -> PcbConfig {
        PcbConfig {
            minimum_size: 100,
            block_excessive_clients: true,
            excessive_threshold: 2.0,
            maximum_difference: 0.1,
            maximum_rewind: 0.15,
            max_wait_ms: 300,
            fast_test_progress: 0.0,
            fast_test_duration_ms: 90,
            ban_duration_ms: 600,
            disable_completed_excessive: false,
        }
    }

    fn peer(now_ms: i64, reported_uploaded: i64, reported_progress: f64) -> PcbObservation {
        PcbObservation {
            now_ms,
            torrent_size: 1000,
            completed_size: 1000,
            reported_uploaded,
            reported_progress,
            upload_speed: 1,
            supports_unban: true,
            handshaking: false,
        }
    }

    #[test]
    fn counts_counter_reset_and_waits_strictly_past_the_delay() {
        let (mut range, mut address) = (PcbState::default(), PcbState::default());
        let config = config();
        assert!(
            check_progress(&config, &peer(1000, 400, 0.05), &mut range, &mut address).is_none()
        );
        assert_eq!(address.ban_delay_window_end_at_ms, 1300);
        assert!(check_progress(&config, &peer(1300, 10, 0.05), &mut range, &mut address).is_none());
        let result =
            check_progress(&config, &peer(1301, 15, 0.05), &mut range, &mut address).unwrap();
        assert_eq!(address.tracking_uploaded_increase_total, 415);
        assert_eq!(range.progress_difference_counter, 1);
        assert_eq!(result.details["type"], "deSyncDifference");
        assert_eq!(address.ban_delay_window_end_at_ms, 0);
    }

    #[test]
    fn fast_test_precedes_a_full_ban_and_zero_does_not_replace_last_progress() {
        let (mut range, mut address) = (PcbState::default(), PcbState::default());
        let mut config = config();
        config.fast_test_progress = 0.25;
        let result =
            check_progress(&config, &peer(10, 2500, 0.3), &mut range, &mut address).unwrap();
        assert_eq!(result.action, Action::BanForDisconnect);
        assert_eq!(result.duration_ms, 90);
        let result =
            check_progress(&config, &peer(20, 2500, 0.0), &mut range, &mut address).unwrap();
        assert_eq!(result.action, Action::Ban);
        assert_eq!(result.details["type"], "excessiveMaxDownloadThreshold");
        assert_eq!(address.last_report_progress, 0.3);
    }
}
