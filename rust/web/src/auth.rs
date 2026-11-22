use std::collections::HashMap;
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use axum::body::Bytes;
use axum::extract::{ConnectInfo, Query, Request, State};
use axum::http::{HeaderMap, HeaderValue, StatusCode, header};
use axum::middleware::Next;
use axum::response::Response;
use serde::Deserialize;
use serde_json::{Value, json};
use subtle::ConstantTimeEq;
use tower_sessions::Session;

use crate::{WebState, messages, response};

const SESSION_AUTHENTICATED: &str = "authenticated";
const FAILED_LIMIT: u8 = 11;
const FAILURE_WINDOW: Duration = Duration::from_secs(15 * 60);

#[derive(Clone, Default)]
pub(crate) struct Authentication {
    failures: Arc<Mutex<HashMap<IpAddr, Failures>>>,
}

struct Failures {
    created: Instant,
    count: u8,
}

enum TokenMatch {
    Correct,
    Wrong,
    Missing,
}

#[derive(Deserialize)]
struct LoginBody {
    token: Option<String>,
}

#[derive(Default, Deserialize)]
struct QueryToken {
    token: Option<String>,
}

impl Authentication {
    fn check(&self, address: IpAddr, token: TokenMatch) -> Result<TokenMatch, StatusCode> {
        let mut failures = self
            .failures
            .lock()
            .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
        let now = Instant::now();
        failures.retain(|_, failure| now.duration_since(failure.created) < FAILURE_WINDOW);
        let prefix = prefix(address);
        let record = failures.entry(prefix).or_insert(Failures {
            created: now,
            count: 0,
        });
        if record.count >= FAILED_LIMIT {
            return Err(StatusCode::TOO_MANY_REQUESTS);
        }
        match token {
            TokenMatch::Correct => record.count = 0,
            TokenMatch::Wrong => record.count += 1,
            TokenMatch::Missing => (),
        }
        Ok(token)
    }
}

fn prefix(ip: IpAddr) -> IpAddr {
    match ip.to_canonical() {
        IpAddr::V4(ip) => Ipv4Addr::from_bits(ip.to_bits() & (u32::MAX << 8)).into(),
        IpAddr::V6(ip) => Ipv6Addr::from_bits(ip.to_bits() & (u128::MAX << 78)).into(),
    }
}

fn classify(expected: &str, supplied: Option<&str>) -> TokenMatch {
    match supplied.filter(|token| !token.is_empty()) {
        Some(supplied) if bool::from(expected.as_bytes().ct_eq(supplied.as_bytes())) => {
            TokenMatch::Correct
        }
        Some(_) => TokenMatch::Wrong,
        None => TokenMatch::Missing,
    }
}

fn need_init(messages: &messages::Messages) -> Response {
    let mut reply = response(
        StatusCode::SEE_OTHER,
        false,
        Some(messages.need_init),
        json!({"location": "/init"}),
    );
    reply
        .headers_mut()
        .insert(header::LOCATION, HeaderValue::from_static("/init"));
    reply
}

fn auth_error(status: StatusCode, messages: &messages::Messages) -> Response {
    let message = match status {
        StatusCode::TOO_MANY_REQUESTS => messages.auth_limited,
        StatusCode::FORBIDDEN => messages.auth_required,
        StatusCode::INTERNAL_SERVER_ERROR => "Internal server error",
        _ => messages.auth_invalid,
    };
    response(status, false, Some(message), Value::Null)
}

pub(crate) async fn login(
    State(state): State<WebState>,
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    session: Session,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    let messages = messages::preferred(&headers);
    let expected = state.token();
    if expected.trim().is_empty() {
        return need_init(messages);
    }
    let Ok(Some(LoginBody { token })) = serde_json::from_slice::<Option<LoginBody>>(&body) else {
        return auth_error(StatusCode::BAD_REQUEST, messages);
    };
    let token = match token.as_deref() {
        None => TokenMatch::Wrong,
        value => classify(expected, value),
    };
    match state.authentication.check(peer.ip(), token) {
        Ok(TokenMatch::Correct) => {
            if session.cycle_id().await.is_err()
                || session.insert(SESSION_AUTHENTICATED, true).await.is_err()
            {
                return auth_error(StatusCode::INTERNAL_SERVER_ERROR, messages);
            }
            response(StatusCode::OK, true, Some(messages.auth_ok), Value::Null)
        }
        Ok(_) => auth_error(StatusCode::UNAUTHORIZED, messages),
        Err(status) => auth_error(status, messages),
    }
}

pub(crate) async fn logout(session: Session, headers: HeaderMap) -> Response {
    if session.flush().await.is_err() {
        return auth_error(
            StatusCode::INTERNAL_SERVER_ERROR,
            messages::preferred(&headers),
        );
    }
    response(StatusCode::OK, true, Some("success"), Value::Null)
}

pub(crate) async fn authorize(
    State(state): State<WebState>,
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    session: Session,
    request: Request,
    next: Next,
) -> Response {
    let messages = messages::preferred(request.headers());
    let expected = state.token();
    if expected.trim().is_empty() {
        return need_init(messages);
    }
    match session.get::<bool>(SESSION_AUTHENTICATED).await {
        Ok(Some(true)) => return next.run(request).await,
        Err(_) => return auth_error(StatusCode::INTERNAL_SERVER_ERROR, messages),
        _ => (),
    }
    let query;
    let token = if let Some(header) = request.headers().get(header::AUTHORIZATION) {
        header
            .to_str()
            .ok()
            .and_then(|value| value.strip_prefix("Bearer "))
    } else {
        query = Query::<QueryToken>::try_from_uri(request.uri())
            .map(|Query(query)| query)
            .unwrap_or_default();
        query.token.as_deref()
    };
    match state
        .authentication
        .check(peer.ip(), classify(expected, token))
    {
        Ok(TokenMatch::Correct) => next.run(request).await,
        Ok(_) => auth_error(StatusCode::FORBIDDEN, messages),
        Err(status) => auth_error(status, messages),
    }
}
