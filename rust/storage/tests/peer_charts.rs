#[path = "support/peer_charts.rs"]
mod peer_charts_fixture;

#[test]
fn reads_distinct_addresses_sessions_trends_and_ranked_transfers() {
    let mut store = pbh_storage::SqliteStore::open(":memory:").unwrap();
    peer_charts_fixture::exercise!(&mut store);
}
