//! WebUI operations for IP rule sources and their update history.

use std::path::{Path as FilePath, PathBuf};
use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::{
    Json, Router,
    routing::{get, post, put},
};
use pbh_web::config_file;
use serde::Deserialize;
use serde_json::{Value, json};
use serde_yaml_ng::{Mapping, Value as Yaml};
use tokio::sync::Mutex;

use crate::ban_api::BanLanguage;
use crate::storage::Database;
use crate::subscriptions::{RuleSource, SubscriptionPolicy, Subscriptions, valid_id};

const MODULE: &str = "ip-address-blocker-rules";

#[derive(Clone)]
struct Api {
    path: Arc<PathBuf>,
    runtime: Subscriptions,
    database: Arc<Mutex<Database>>,
    demo: bool,
    edits: Arc<Mutex<()>>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RuleDraft {
    rule_id: Option<String>,
    rule_name: Option<String>,
    sub_url: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Interval {
    check_interval: u64,
}

#[derive(Deserialize)]
struct Enabled {
    enabled: bool,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Page {
    page: Option<i64>,
    page_size: Option<i64>,
}

pub(crate) fn router(
    path: PathBuf,
    runtime: Subscriptions,
    database: Arc<Mutex<Database>>,
    demo: bool,
) -> Router {
    Router::new()
        .route("/api/sub/interval", get(interval).patch(set_interval))
        .route("/api/sub/rules", get(list))
        .route("/api/sub/rule", put(add))
        .route(
            "/api/sub/rule/{id}",
            get(detail).post(edit_rule).patch(toggle).delete(remove),
        )
        .route("/api/sub/rule/{id}/update", post(update))
        .route("/api/sub/rules/update", post(update_all))
        .route("/api/sub/logs", get(logs))
        .route("/api/sub/logs/{id}", get(rule_logs))
        .with_state(Api {
            path: Arc::new(path),
            runtime,
            database,
            demo,
            edits: Arc::default(),
        })
}

async fn interval(State(api): State<Api>, headers: HeaderMap) -> Response {
    let language = BanLanguage::from_headers(&headers);
    match read_policy(&api.path) {
        Ok(policy) => reply(
            StatusCode::OK,
            true,
            language,
            "IP_BAN_RULE_CHECK_INTERVAL_QUERY_SUCCESS",
            json!([]),
            json!(policy.check_interval),
        ),
        Err(_) => internal(language),
    }
}

async fn list(State(api): State<Api>, headers: HeaderMap) -> Response {
    subscription_info(api, None, headers).await
}

async fn detail(State(api): State<Api>, Path(id): Path<String>, headers: HeaderMap) -> Response {
    subscription_info(api, Some(id), headers).await
}

async fn subscription_info(api: Api, id: Option<String>, headers: HeaderMap) -> Response {
    let language = BanLanguage::from_headers(&headers);
    let Ok(policy) = read_policy(&api.path) else {
        return internal(language);
    };
    let mut rows = Vec::new();
    let mut database = api.database.lock().await;
    for (name, rule) in policy.rules {
        if id.as_ref().is_some_and(|id| id != &name) {
            continue;
        }
        let Ok(latest) = database.latest_subscription_log(&name).await else {
            return internal(language);
        };
        rows.push(json!({
            "ruleId": name,
            "ruleName": rule.name.as_deref().unwrap_or(&name),
            "enabled": rule.enabled,
            "subUrl": if api.demo { "" } else { &rule.url },
            "lastUpdate": latest.as_ref().map_or(0, |row| row.update_ms),
            "entCount": latest.map_or(0, |row| row.count),
        }));
    }
    let data = if id.is_some() {
        rows.pop().unwrap_or_default()
    } else {
        json!(rows)
    };
    reply(
        StatusCode::OK,
        true,
        language,
        "IP_BAN_RULE_INFO_QUERY_SUCCESS",
        json!([]),
        data,
    )
}

async fn logs(State(api): State<Api>, headers: HeaderMap, Query(page): Query<Page>) -> Response {
    update_logs(api, headers, page, None).await
}

async fn rule_logs(
    State(api): State<Api>,
    Path(id): Path<String>,
    headers: HeaderMap,
    Query(page): Query<Page>,
) -> Response {
    update_logs(api, headers, page, Some(id)).await
}

async fn update_logs(api: Api, headers: HeaderMap, page: Page, id: Option<String>) -> Response {
    let language = BanLanguage::from_headers(&headers);
    let result = api
        .database
        .lock()
        .await
        .subscription_logs(
            id.as_deref(),
            page.page.unwrap_or(1),
            page.page_size.unwrap_or(10),
        )
        .await;
    let Ok(page) = result else {
        return reply(
            StatusCode::BAD_REQUEST,
            false,
            language,
            "IP_BAN_RULE_LOG_QUERY_WRONG_PARAM",
            json!([]),
            Value::Null,
        );
    };
    let rows = page.records.into_iter().map(|row| json!({
        "id": row.id, "ruleId": row.rule_id, "updateTime": row.update_ms, "count": row.count,
        "updateType": if row.manual {"MANUAL"} else {"AUTO"},
    })).collect::<Vec<_>>();
    reply(
        StatusCode::OK,
        true,
        language,
        "IP_BAN_RULE_LOG_QUERY_SUCCESS",
        json!([]),
        json!({
            "page": page.page, "size": page.size, "total": page.total, "results": rows
        }),
    )
}

enum Edit {
    Interval(u64),
    Save {
        id: String,
        draft: RuleDraft,
        create: bool,
    },
    Toggle(String, bool),
    Delete(String),
}

async fn set_interval(
    State(api): State<Api>,
    headers: HeaderMap,
    Json(value): Json<Interval>,
) -> Response {
    mutate(api, headers, Edit::Interval(value.check_interval)).await
}

async fn add(State(api): State<Api>, headers: HeaderMap, Json(draft): Json<RuleDraft>) -> Response {
    mutate(
        api,
        headers,
        Edit::Save {
            id: draft.rule_id.clone().unwrap_or_default(),
            draft,
            create: true,
        },
    )
    .await
}

async fn edit_rule(
    State(api): State<Api>,
    Path(id): Path<String>,
    headers: HeaderMap,
    Json(draft): Json<RuleDraft>,
) -> Response {
    mutate(
        api,
        headers,
        Edit::Save {
            id,
            draft,
            create: false,
        },
    )
    .await
}

async fn toggle(
    State(api): State<Api>,
    Path(id): Path<String>,
    headers: HeaderMap,
    Json(value): Json<Enabled>,
) -> Response {
    mutate(api, headers, Edit::Toggle(id, value.enabled)).await
}

async fn remove(State(api): State<Api>, Path(id): Path<String>, headers: HeaderMap) -> Response {
    mutate(api, headers, Edit::Delete(id)).await
}

type Mutation = (&'static str, Value, Option<String>);

async fn mutate(api: Api, headers: HeaderMap, edit: Edit) -> Response {
    let language = BanLanguage::from_headers(&headers);
    if api.demo {
        return reply(
            StatusCode::BAD_REQUEST,
            false,
            language,
            "DEMO_MODE_OPERATION_NOT_PERMITTED",
            json!([]),
            Value::Null,
        );
    }
    let _guard = api.edits.lock().await;
    let Ok(previous) = read_policy(&api.path) else {
        return internal(language);
    };
    let mut policy = previous.clone();
    let (message, params, refresh) = match apply_edit(&mut policy, edit) {
        Ok(result) => result,
        Err((message, params)) => {
            return reply(
                StatusCode::BAD_REQUEST,
                false,
                language,
                message,
                params,
                Value::Null,
            );
        }
    };
    if let Some(id) = &refresh
        && api
            .runtime
            .validate_source(&policy.rules[id])
            .await
            .is_err()
    {
        return reply(
            StatusCode::BAD_REQUEST,
            false,
            language,
            "IP_BAN_RULE_URL_WRONG",
            json!([policy.rules[id].name]),
            Value::Null,
        );
    }
    let path = api.path.clone();
    let updated = policy.clone();
    let result = tokio::task::spawn_blocking(move || {
        config_file::edit(&path, |yaml| {
            let module = yaml
                .get_mut("module")
                .and_then(Yaml::as_mapping_mut)
                .ok_or(())?;
            let current = module.get(MODULE).ok_or(())?.clone();
            if serde_yaml_ng::from_value::<SubscriptionPolicy>(current).map_err(|_| ())? != previous
            {
                return Err(());
            }
            module.insert(
                MODULE.into(),
                serde_yaml_ng::to_value(updated).map_err(|_| ())?,
            );
            Ok(())
        })
    })
    .await;
    if !matches!(result, Ok(Ok(()))) || api.runtime.reload(&policy).await.is_err() {
        return internal(language);
    }
    if let Some(id) = refresh
        && api.runtime.manual_refresh(&id).await.is_err()
    {
        return reply(
            StatusCode::BAD_REQUEST,
            false,
            language,
            "IP_BAN_RULE_UPDATE_FAILED",
            json!([id]),
            Value::Null,
        );
    }
    reply(StatusCode::OK, true, language, message, params, Value::Null)
}

fn apply_edit(
    policy: &mut SubscriptionPolicy,
    edit: Edit,
) -> Result<Mutation, (&'static str, Value)> {
    match edit {
        Edit::Interval(interval) => {
            if interval == 0 {
                return Err(("IP_BAN_RULE_CHECK_INTERVAL_WRONG_PARAM", json!([])));
            }
            policy.check_interval = interval;
            Ok(("IP_BAN_RULE_CHECK_INTERVAL_UPDATED", json!([]), None))
        }
        Edit::Save { id, draft, create } => {
            if !valid_id(&id) {
                return Err(("IP_BAN_RULE_NO_ID", json!([])));
            }
            let old = policy.rules.get(&id);
            if create && old.is_some() {
                return Err(("IP_BAN_RULE_ID_CONFLICT", json!([id])));
            }
            if !create && old.is_none() {
                return Err(("IP_BAN_RULE_CANT_FIND", json!([id])));
            }
            let name = draft
                .rule_name
                .or_else(|| old.and_then(|rule| rule.name.clone()));
            let url = draft
                .sub_url
                .or_else(|| old.map(|rule| rule.url.clone()))
                .unwrap_or_default();
            if name
                .as_deref()
                .is_none_or(|name| name.trim().is_empty() || name.contains('.'))
                || url.trim().is_empty()
            {
                return Err(("IP_BAN_RULE_PARAM_WRONG", json!([])));
            }
            let enabled = old.is_none_or(|rule| rule.enabled);
            policy
                .rules
                .insert(id.clone(), RuleSource { name, url, enabled });
            Ok(("IP_BAN_RULE_SAVED", json!([]), enabled.then_some(id)))
        }
        Edit::Toggle(id, enabled) => {
            let rule = policy
                .rules
                .get_mut(&id)
                .ok_or_else(|| ("IP_BAN_RULE_CANT_FIND", json!([id])))?;
            let key = if enabled {
                "IP_BAN_RULE_ENABLED"
            } else {
                "IP_BAN_RULE_DISABLED"
            };
            rule.enabled = enabled;
            Ok((
                key,
                json!([rule.name.as_deref().unwrap_or(&id)]),
                enabled.then_some(id),
            ))
        }
        Edit::Delete(id) => {
            let rule = policy
                .rules
                .remove(&id)
                .ok_or_else(|| ("IP_BAN_RULE_CANT_FIND", json!([id])))?;
            Ok((
                "IP_BAN_RULE_DELETED",
                json!([rule.name.unwrap_or(id)]),
                None,
            ))
        }
    }
}

async fn update(State(api): State<Api>, Path(id): Path<String>, headers: HeaderMap) -> Response {
    refresh(api, headers, Some(id)).await
}

async fn update_all(State(api): State<Api>, headers: HeaderMap) -> Response {
    refresh(api, headers, None).await
}

async fn refresh(api: Api, headers: HeaderMap, id: Option<String>) -> Response {
    let language = BanLanguage::from_headers(&headers);
    if api.demo {
        return reply(
            StatusCode::BAD_REQUEST,
            false,
            language,
            "DEMO_MODE_OPERATION_NOT_PERMITTED",
            json!([]),
            Value::Null,
        );
    }
    let Ok(policy) = read_policy(&api.path) else {
        return internal(language);
    };
    if let Some(id) = id.as_ref()
        && !policy.rules.contains_key(id)
    {
        return reply(
            StatusCode::OK,
            false,
            language,
            "IP_BAN_RULE_CANT_FIND",
            json!([id]),
            Value::Null,
        );
    }
    for (key, rule) in policy.rules {
        if id.as_ref().is_some_and(|id| id != &key) {
            continue;
        }
        if !rule.enabled && id.is_none() {
            continue;
        }
        if api.runtime.manual_refresh(&key).await.is_err() {
            return reply(
                StatusCode::OK,
                false,
                language,
                "IP_BAN_RULE_UPDATE_FAILED",
                json!([rule.name.unwrap_or(key)]),
                Value::Null,
            );
        }
    }
    let (key, params) = id.map_or(("IP_BAN_RULE_ALL_UPDATED", json!([])), |id| {
        ("IP_BAN_RULE_UPDATED", json!([id]))
    });
    reply(StatusCode::OK, true, language, key, params, Value::Null)
}

fn read_policy(path: &FilePath) -> anyhow::Result<SubscriptionPolicy> {
    let yaml: Mapping = crate::config::read_yaml(path)?;
    let section = yaml
        .get("module")
        .and_then(|value| value.get(MODULE))
        .cloned()
        .unwrap_or_default();
    Ok(serde_yaml_ng::from_value(section)?)
}

fn internal(language: BanLanguage) -> Response {
    reply(
        StatusCode::INTERNAL_SERVER_ERROR,
        false,
        language,
        "WEBAPI_INTERNAL_ERROR",
        json!([]),
        Value::Null,
    )
}

fn reply(
    status: StatusCode,
    success: bool,
    language: BanLanguage,
    key: &str,
    params: Value,
    data: Value,
) -> Response {
    let message = language.render(&json!({"key": key, "params": params}));
    (
        status,
        Json(json!({"success": success, "message": message, "data": data})),
    )
        .into_response()
}
