//! Bound reverse lookup work independently of HTTP request lifetimes.

use std::collections::HashMap;
use std::net::{IpAddr, SocketAddr};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use futures_util::{StreamExt, stream::FuturesUnordered};
use hickory_proto::op::{Message, MessageType, Query, ResponseCode};
use hickory_proto::rr::{Name, RData, RecordType};
use pbh_network::ProxyPolicy;
use reqwest::{Client, Url, header, redirect};
use serde::Deserialize;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpStream, UdpSocket, lookup_host};
use tokio::sync::{Semaphore, oneshot};

pub(crate) const TIMEOUT: Duration = Duration::from_secs(3);
const TTL: Duration = Duration::from_secs(300);

type Cache = HashMap<IpAddr, (Instant, Option<String>)>;

#[derive(Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct ResolverConfig {
    use_system: bool,
    servers: Vec<String>,
}

impl Default for ResolverConfig {
    fn default() -> Self {
        Self {
            use_system: true,
            servers: Vec::new(),
        }
    }
}

#[derive(Clone)]
pub(crate) struct Resolver(Arc<Runtime>);

struct Runtime {
    system: bool,
    servers: Vec<Endpoint>,
    client: Option<Client>,
    cache: Mutex<Cache>,
    slots: Arc<Semaphore>,
}

enum Endpoint {
    Direct(String, u16),
    DnsOverHttp(Url),
}

impl Resolver {
    pub(crate) fn new(config: &ResolverConfig, proxy: &ProxyPolicy) -> reqwest::Result<Self> {
        let servers: Vec<_> = config
            .servers
            .iter()
            .filter_map(|server| Endpoint::parse(server))
            .collect();
        let client = if servers
            .iter()
            .any(|server| matches!(server, Endpoint::DnsOverHttp(_)))
        {
            Some(
                proxy
                    .apply(Client::builder())
                    .timeout(TIMEOUT)
                    .redirect(redirect::Policy::none())
                    .build()?,
            )
        } else {
            None
        };
        Ok(Self(Arc::new(Runtime {
            system: config.use_system,
            servers,
            client,
            cache: Mutex::default(),
            slots: Arc::new(Semaphore::new(16)),
        })))
    }

    pub(crate) async fn ptr_lookup(&self, ip: IpAddr) -> Option<String> {
        let ip = ip.to_canonical();
        if let Some((created, answer)) = self.0.cache.lock().ok()?.get(&ip)
            && created.elapsed() < TTL
        {
            return answer.clone();
        }
        let result = tokio::time::timeout(TIMEOUT, self.resolve(ip))
            .await
            .ok()
            .flatten();
        if let Ok(mut cache) = self.0.cache.lock() {
            if cache.len() >= 1_024
                && let Some(oldest) = cache
                    .iter()
                    .min_by_key(|(_, (created, _))| *created)
                    .map(|(ip, _)| *ip)
            {
                cache.remove(&oldest);
            }
            cache.insert(ip, (Instant::now(), result.clone()));
        }
        result
    }

    async fn resolve(&self, ip: IpAddr) -> Option<String> {
        let mut request = Message::query();
        request.metadata.recursion_desired = true;
        request.add_query(Query::query(Name::from(ip), RecordType::PTR));
        let wire = request.to_vec().ok()?;
        let configured = async {
            for servers in self.0.servers.chunks(4) {
                let mut queries = FuturesUnordered::new();
                for server in servers {
                    queries.push(server.answer(&request, &wire, self.0.client.as_ref()));
                }
                while let Some(answer) = queries.next().await {
                    if answer.is_some() {
                        return answer;
                    }
                }
            }
            None
        };
        if !self.0.system {
            return configured.await;
        }
        tokio::pin!(configured);
        let system = system_lookup(ip, self.0.slots.clone());
        tokio::pin!(system);
        tokio::select! {
            answer = &mut configured => match answer {
                Some(_) => answer,
                None => system.await,
            },
            answer = &mut system => match answer {
                Some(_) => answer,
                None => configured.await,
            },
        }
    }
}

impl Endpoint {
    fn parse(value: &str) -> Option<Self> {
        let value = value.trim();
        if value.starts_with("http") {
            let url = Url::parse(value).ok()?;
            if matches!(url.scheme(), "http" | "https")
                && url.host().is_some()
                && url.username().is_empty()
                && url.password().is_none()
            {
                return Some(Self::DnsOverHttp(url));
            }
            return None;
        }
        if let Ok(address) = value.parse::<SocketAddr>() {
            return Some(Self::Direct(address.ip().to_string(), address.port()));
        }
        if let Ok(address) = value.parse::<IpAddr>() {
            return Some(Self::Direct(address.to_string(), 53));
        }
        let (host, port) = match value.rsplit_once(':') {
            Some((host, written)) => (host, written.parse().ok()?),
            None => (value, 53),
        };
        (!host.is_empty()).then(|| Self::Direct(host.to_owned(), port))
    }

    async fn answer(
        &self,
        request: &Message,
        wire: &[u8],
        http: Option<&Client>,
    ) -> Option<String> {
        let response = match self {
            Self::Direct(host, port) => {
                let address = lookup_host((host.as_str(), *port)).await.ok()?.next()?;
                let bind = if address.is_ipv4() {
                    "0.0.0.0:0"
                } else {
                    "[::]:0"
                };
                let socket = UdpSocket::bind(bind).await.ok()?;
                socket.connect(address).await.ok()?;
                socket.send(wire).await.ok()?;
                let mut buffer = [0; 4096];
                let response = loop {
                    let len = socket.recv(&mut buffer).await.ok()?;
                    if let Some(response) = matching_response(request, &buffer[..len]) {
                        break response;
                    }
                };
                if response.metadata.truncation {
                    let mut tcp = TcpStream::connect(address).await.ok()?;
                    tcp.write_u16(u16::try_from(wire.len()).ok()?).await.ok()?;
                    tcp.write_all(wire).await.ok()?;
                    let len = tcp.read_u16().await.ok()?;
                    let mut bytes = vec![0; usize::from(len)];
                    tcp.read_exact(&mut bytes).await.ok()?;
                    matching_response(request, &bytes)?
                } else {
                    response
                }
            }
            Self::DnsOverHttp(url) => {
                const DNS_MESSAGE: &str = "application/dns-message";
                let mut response = http?
                    .post(url.clone())
                    .header(header::ACCEPT, DNS_MESSAGE)
                    .header(header::CONTENT_TYPE, DNS_MESSAGE)
                    .body(wire.to_vec())
                    .send()
                    .await
                    .ok()?
                    .error_for_status()
                    .ok()?;
                let mut bytes = Vec::new();
                while let Some(chunk) = response.chunk().await.ok()? {
                    if bytes.len() + chunk.len() > usize::from(u16::MAX) {
                        return None;
                    }
                    bytes.extend_from_slice(&chunk);
                }
                matching_response(request, &bytes)?
            }
        };
        if response.metadata.response_code != ResponseCode::NoError || response.metadata.truncation
        {
            return None;
        }
        let mut name = request.queries.first()?.name();
        for _ in 0..8 {
            let mut alias = None;
            for record in &response.answers {
                if &record.name == name {
                    match &record.data {
                        RData::PTR(ptr) => return Some(ptr.0.to_string()),
                        RData::CNAME(target) => alias = Some(&target.0),
                        _ => {}
                    }
                }
            }
            name = alias?;
        }
        None
    }
}

fn matching_response(request: &Message, bytes: &[u8]) -> Option<Message> {
    let response = Message::from_vec(bytes).ok()?;
    (response.metadata.id == request.metadata.id
        && response.metadata.message_type == MessageType::Response
        && response.queries == request.queries)
        .then_some(response)
}

async fn system_lookup(ip: IpAddr, slots: Arc<Semaphore>) -> Option<String> {
    let permit = slots.try_acquire_owned().ok()?;
    let (reply, receiver) = oneshot::channel();
    // The system resolver may outlast the caller's timeout. Keep its capacity
    // permit until it returns, including when the caller has disconnected.
    std::thread::Builder::new()
        .name("peerbanhelper-ptr".into())
        .spawn(move || {
            let _permit = permit;
            let result = dns_lookup::lookup_addr(&ip)
                .ok()
                .filter(|host| host != &ip.to_string());
            let _ = reply.send(result);
        })
        .ok()?;
    receiver.await.ok().flatten()
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{Router, body::Bytes, routing::post};
    use hickory_proto::rr::{Record, rdata::PTR};
    use tokio::net::TcpListener;

    fn reply(query: &[u8], reverse_name: &str) -> Message {
        let request = Message::from_vec(query).unwrap();
        let query = &request.queries[0];
        assert_eq!(query.name().to_string(), reverse_name);
        assert_eq!(query.query_type(), RecordType::PTR);
        let mut reply = Message::response(request.metadata.id, request.metadata.op_code);
        reply.add_query(query.clone());
        reply.add_answer(Record::from_rdata(
            query.name().clone(),
            60,
            RData::PTR(PTR(Name::from_ascii("fixture.example.").unwrap())),
        ));
        reply
    }

    #[tokio::test]
    async fn configured_udp_rejects_wrong_id_and_retries_truncated_response_over_tcp() {
        let tcp = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = tcp.local_addr().unwrap();
        let udp = UdpSocket::bind(address).await.unwrap();
        let handler = tokio::spawn(async move {
            let mut query = [0; 512];
            let (len, peer) = udp.recv_from(&mut query).await.unwrap();
            let mut wrong = reply(&query[..len], "113.0.0.203.in-addr.arpa.");
            wrong.metadata.id = wrong.metadata.id.wrapping_add(1);
            udp.send_to(&wrong.to_vec().unwrap(), peer).await.unwrap();
            let truncated = reply(&query[..len], "113.0.0.203.in-addr.arpa.").truncate();
            udp.send_to(&truncated.to_vec().unwrap(), peer)
                .await
                .unwrap();
            let (mut stream, _) = tcp.accept().await.unwrap();
            let len = stream.read_u16().await.unwrap();
            stream
                .read_exact(&mut query[..usize::from(len)])
                .await
                .unwrap();
            let answer = reply(&query[..usize::from(len)], "113.0.0.203.in-addr.arpa.")
                .to_vec()
                .unwrap();
            stream.write_u16(answer.len() as u16).await.unwrap();
            stream.write_all(&answer).await.unwrap();
        });
        let resolver = Resolver::new(
            &ResolverConfig {
                use_system: false,
                servers: vec![address.to_string()],
            },
            &ProxyPolicy::default(),
        )
        .unwrap();
        let ip = "203.0.0.113".parse().unwrap();
        assert_eq!(
            resolver.ptr_lookup(ip).await.as_deref(),
            Some("fixture.example.")
        );
        handler.await.unwrap();
        assert_eq!(
            resolver.ptr_lookup(ip).await.as_deref(),
            Some("fixture.example.")
        );
    }

    #[tokio::test]
    async fn dns_over_http_uses_the_configured_path_and_binary_content_type() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let app = Router::new().route(
            "/custom-resolver",
            post(|headers: axum::http::HeaderMap, bytes: Bytes| async move {
                assert_eq!(headers[header::CONTENT_TYPE], "application/dns-message");
                let answer = reply(&bytes, "113.0.0.203.in-addr.arpa.").to_vec().unwrap();
                ([(header::CONTENT_TYPE, "application/dns-message")], answer)
            }),
        );
        let task = tokio::spawn(async move {
            axum::serve(listener, app).await.unwrap();
        });
        let resolver = Resolver::new(
            &ResolverConfig {
                use_system: false,
                servers: vec![format!("http://{address}/custom-resolver")],
            },
            &ProxyPolicy::default(),
        )
        .unwrap();
        assert_eq!(
            resolver
                .ptr_lookup("203.0.0.113".parse().unwrap())
                .await
                .as_deref(),
            Some("fixture.example.")
        );
        task.abort();
    }
}
