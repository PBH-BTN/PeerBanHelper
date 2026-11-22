use std::collections::BTreeMap;
use std::sync::Arc;

use reqwest::{Method, RequestBuilder, StatusCode, Url};
use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use crate::{
    BtnClient, Error, MAX_COMMENT_COUNT_BATCH,
    counts::{CommentCounts, CountResponse},
    json,
};

#[derive(Clone)]
pub struct IpQuery {
    client: BtnClient,
    endpoint: Url,
    lookup: Option<Url>,
    comments: Option<Url>,
    comment_counts: Option<Url>,
    iframe: Option<Url>,
    proof_endpoint: Option<Url>,
    require_pow: bool,
    comment_counts_pow: bool,
    cache: Arc<CommentCounts>,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateComment {
    pub scope: String,
    pub parent_id: Option<i64>,
    pub content: String,
}

#[derive(Serialize, Deserialize)]
pub struct UpdateComment {
    pub content: String,
    pub revision: Option<i32>,
}

impl IpQuery {
    pub(crate) fn from_config(
        client: BtnClient,
        ability: &Value,
        proof_endpoint: Option<Url>,
    ) -> Result<Self, Error> {
        let endpoint = ability
            .get("endpoint")
            .and_then(Value::as_str)
            .ok_or(Error::InvalidResponse("IP query configuration"))?;
        let optional = |key| {
            ability
                .get(key)
                .and_then(Value::as_str)
                .and_then(|url| client.endpoint(url, key).ok())
        };
        Ok(Self {
            endpoint: client.endpoint(endpoint, "IP query")?,
            lookup: optional("lookup_endpoint"),
            comments: optional("comments_endpoint"),
            comment_counts: optional("comment_counts_endpoint"),
            iframe: optional("iframe_endpoint"),
            proof_endpoint,
            require_pow: ability
                .get("pow_captcha")
                .and_then(Value::as_bool)
                .unwrap_or(false),
            comment_counts_pow: ability
                .get("comment_counts_pow_captcha")
                .and_then(Value::as_bool)
                .unwrap_or(false),
            cache: Arc::default(),
            client,
        })
    }

    pub fn lookup_endpoint(&self) -> Option<&Url> {
        self.lookup.as_ref()
    }
    pub fn comments_endpoint(&self) -> Option<&Url> {
        self.comments.as_ref()
    }
    pub fn comment_counts_endpoint(&self) -> Option<&Url> {
        self.comment_counts.as_ref()
    }
    pub fn iframe_endpoint(&self) -> Option<&Url> {
        self.iframe.as_ref()
    }

    /// The legacy iframe contract carries BTN credentials in the query string.
    /// Return this URL only to authenticated callers and keep it out of logs.
    pub fn iframe_url(&self, ip: &str) -> Result<Url, Error> {
        let mut url = self
            .iframe
            .clone()
            .ok_or(Error::EndpointUnavailable("IP query iframe"))?;
        let identity = &self.client.iframe_identity;
        url.query_pairs_mut()
            .append_pair("ip", ip)
            .append_pair("appId", &identity.app_id)
            .append_pair("appSecret", &identity.app_secret)
            .append_pair("hardwareId", &identity.hardware_id)
            .append_pair("installationId", &identity.installation_id);
        Ok(url)
    }

    pub async fn query(&self, address: &str) -> Result<Option<Value>, Error> {
        let mut endpoint = self.endpoint.clone();
        endpoint.query_pairs_mut().append_pair("ip", address);
        let request = self.client.http.get(endpoint);
        let request = self.with_proof(request, self.require_pow).await?;
        let response = self.client.send(request, "IP query").await?;
        if response.status() == StatusCode::NO_CONTENT {
            return Ok(None);
        }
        let result: Option<Map<String, Value>> = json(response, "IP query").await?;
        Ok(result.map(|mut fields| {
            fields
                .entry("labels")
                .or_insert_with(|| Value::Array(Vec::new()));
            for field in ["color", "bans", "swarms", "traffic", "torrents"] {
                fields.entry(field).or_insert(Value::Null);
            }
            Value::Object(fields)
        }))
    }

    pub async fn lookup(&self, query: &str) -> Result<Value, Error> {
        let mut endpoint = self
            .lookup
            .clone()
            .ok_or(Error::EndpointUnavailable("IP lookup"))?;
        endpoint.query_pairs_mut().append_pair("query", query);
        self.request_json(self.client.http.get(endpoint), self.require_pow)
            .await
    }

    pub async fn list_comments(
        &self,
        scope: &str,
        page: i32,
        page_size: i32,
    ) -> Result<Value, Error> {
        let mut endpoint = self.comment_url(None)?;
        endpoint
            .query_pairs_mut()
            .append_pair("scope", scope)
            .append_pair("page", &page.to_string())
            .append_pair("size", &page_size.clamp(1, 50).to_string());
        self.request_json(self.client.http.get(endpoint), self.require_pow)
            .await
    }

    pub async fn create_comment(&self, comment: &CreateComment) -> Result<Value, Error> {
        let request = self.client.http.post(self.comment_url(None)?).json(comment);
        self.request_json(request, self.require_pow).await
    }

    pub async fn update_comment(&self, id: i64, comment: &UpdateComment) -> Result<Value, Error> {
        let request = self
            .client
            .http
            .put(self.comment_url(Some(id))?)
            .json(comment);
        self.request_json(request, self.require_pow).await
    }

    pub async fn delete_comment(&self, id: i64) -> Result<(), Error> {
        let request = self
            .client
            .http
            .request(Method::DELETE, self.comment_url(Some(id))?);
        self.request_json(request, self.require_pow)
            .await
            .map(|_| ())
    }

    fn comment_url(&self, id: Option<i64>) -> Result<Url, Error> {
        let mut endpoint = self
            .comments
            .clone()
            .ok_or(Error::EndpointUnavailable("IP comments"))?;
        if let Some(id) = id {
            endpoint
                .path_segments_mut()
                .map_err(|_| Error::InvalidUrl("IP comments"))?
                .pop_if_empty()
                .push(&id.to_string());
        }
        Ok(endpoint)
    }

    pub async fn query_comment_counts(
        &self,
        addresses: &[String],
    ) -> Result<BTreeMap<String, i64>, Error> {
        if addresses.is_empty() || addresses.len() > MAX_COMMENT_COUNT_BATCH {
            return Err(Error::InvalidCommentCountBatch);
        }
        let endpoint = self
            .comment_counts
            .clone()
            .ok_or(Error::EndpointUnavailable("IP comment counts"))?;
        self.cache
            .query(addresses, |missing| async move {
                let request = self.client.http.post(endpoint).json(&missing);
                self.request::<CountResponse>(request, self.comment_counts_pow)
                    .await
            })
            .await
    }

    async fn request_json(
        &self,
        request: RequestBuilder,
        require_pow: bool,
    ) -> Result<Value, Error> {
        self.request::<Map<String, Value>>(request, require_pow)
            .await
            .map(Value::Object)
    }

    async fn request<T: serde::de::DeserializeOwned + Default>(
        &self,
        request: RequestBuilder,
        require_pow: bool,
    ) -> Result<T, Error> {
        let request = self.with_proof(request, require_pow).await?;
        let response = self.client.send(request, "IP query").await?;
        if response.status() == StatusCode::NO_CONTENT {
            return Ok(T::default());
        }
        json(response, "IP query").await
    }

    async fn with_proof(
        &self,
        request: RequestBuilder,
        require_pow: bool,
    ) -> Result<RequestBuilder, Error> {
        self.client
            .with_proof(
                request,
                require_pow,
                self.proof_endpoint.as_ref(),
                "ip_query",
            )
            .await
    }
}
