use std::time::Duration;

use pbh_network::ProxyPolicy;
use reqwest::header::{HeaderName, HeaderValue};
use reqwest::{Client, Method, RequestBuilder, Response, StatusCode, Url};
use serde::Deserialize;
use serde::de::DeserializeOwned;
use serde_json::{Value, json};
use tokio::sync::Mutex;

use crate::{Config, Error};

const SESSION_HEADER: HeaderName = HeaderName::from_static("x-transmission-session-id");

pub(crate) struct Rpc {
    client: Client,
    endpoint: Url,
    username: String,
    password: String,
    session: Mutex<Option<HeaderValue>>,
}

#[derive(Deserialize)]
struct Reply {
    result: String,
    #[serde(default)]
    arguments: Value,
}

impl Rpc {
    pub fn new(endpoint: Url, config: &Config, proxy: &ProxyPolicy) -> Result<Self, Error> {
        let client = proxy
            .apply(Client::builder())
            .redirect(reqwest::redirect::Policy::none())
            .connect_timeout(Duration::from_secs(10))
            .timeout(Duration::from_secs(30))
            .danger_accept_invalid_certs(!config.verify_ssl)
            .user_agent(concat!("PeerBanHelper/", env!("CARGO_PKG_VERSION")))
            .build()
            .map_err(|err| request_error("build HTTP client", err))?;
        Ok(Self {
            client,
            endpoint,
            username: config.username.clone(),
            password: config.password.clone(),
            session: Mutex::default(),
        })
    }

    pub async fn read<T: DeserializeOwned>(
        &self,
        method: &'static str,
        args: Value,
    ) -> Result<T, Error> {
        let arguments = self.execute(method, args).await?;
        serde_json::from_value(arguments).map_err(|_| Error::InvalidArguments { method })
    }

    pub async fn write(&self, method: &'static str, args: Value) -> Result<(), Error> {
        self.execute(method, args).await.map(|_| ())
    }

    async fn execute(&self, method: &'static str, args: Value) -> Result<Value, Error> {
        let payload = json!({"method": method, "arguments": args});
        let session = self.session_id(None).await?;
        let mut response = self.post(method, &payload, &session).await?;
        if response.status() == StatusCode::CONFLICT {
            let refreshed = self.session_id(Some(&session)).await?;
            response = self.post(method, &payload, &refreshed).await?;
            if response.status() == StatusCode::CONFLICT {
                return Err(Error::SessionExpired);
            }
        }
        validate_status(&response, method)?;
        let reply: Reply = response
            .json()
            .await
            .map_err(|err| request_error(method, err))?;
        if reply.result == "success" {
            Ok(reply.arguments)
        } else {
            Err(Error::Rejected { method })
        }
    }

    async fn session_id(&self, expired: Option<&HeaderValue>) -> Result<HeaderValue, Error> {
        let mut cached = self.session.lock().await;
        if let Some(session) = cached.as_ref()
            && Some(session) != expired
        {
            return Ok(session.clone());
        }
        let method = "session negotiation";
        let response = self.send(self.request(Method::GET), method).await?;
        if response.status() != StatusCode::CONFLICT {
            validate_status(&response, method)?;
        }
        let mut session = response
            .headers()
            .get(&SESSION_HEADER)
            .filter(|value| !value.is_empty())
            .cloned()
            .ok_or(Error::SessionMissing)?;
        session.set_sensitive(true);
        *cached = Some(session.clone());
        Ok(session)
    }

    async fn post(
        &self,
        method: &'static str,
        payload: &Value,
        session: &HeaderValue,
    ) -> Result<Response, Error> {
        let request = self
            .request(Method::POST)
            .header(SESSION_HEADER, session)
            .json(payload);
        self.send(request, method).await
    }

    fn request(&self, method: Method) -> RequestBuilder {
        self.client.request(method, self.endpoint.clone())
    }

    async fn send(&self, request: RequestBuilder, method: &'static str) -> Result<Response, Error> {
        let with_credentials = request.try_clone();
        let response = request
            .send()
            .await
            .map_err(|err| request_error(method, err))?;
        if response.status() == StatusCode::UNAUTHORIZED
            && let Some(request) = with_credentials
        {
            return request
                .basic_auth(&self.username, Some(&self.password))
                .send()
                .await
                .map_err(|err| request_error(method, err));
        }
        Ok(response)
    }
}

fn validate_status(response: &Response, method: &'static str) -> Result<(), Error> {
    let status = response.status();
    if status == StatusCode::UNAUTHORIZED {
        Err(Error::Authentication)
    } else if !status.is_success() {
        Err(Error::Status { method, status })
    } else {
        Ok(())
    }
}

fn request_error(method: &'static str, error: reqwest::Error) -> Error {
    Error::Request {
        method,
        source: error.without_url(),
    }
}
