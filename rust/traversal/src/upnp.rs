//! IPv4 UPnP Internet Gateway Device discovery and port mapping.

mod discovery;
mod xml;

use std::io;
use std::net::{IpAddr, Ipv4Addr, SocketAddrV4};
use std::time::Duration;

use futures_util::{StreamExt, stream};
use reqwest::{Client, StatusCode, Url};
use thiserror::Error;

const SSDP: SocketAddrV4 = SocketAddrV4::new(Ipv4Addr::new(239, 255, 255, 250), 1900);
const HTTP_TIMEOUT: Duration = Duration::from_secs(4);
const NO_SUCH_ENTRY: u16 = 714;

#[derive(Debug, Error)]
pub enum UpnpError {
    #[error("UPnP requires a selected local IPv4 interface address")]
    InvalidInterface,
    #[error("SSDP discovery failed")]
    Discovery(#[from] io::Error),
    #[error("UPnP HTTP request failed")]
    Http(#[source] reqwest::Error),
    #[error("UPnP HTTP request returned status {0}")]
    Status(StatusCode),
    #[error("UPnP description or control route does not belong to the discovery responder")]
    UnexpectedEndpoint,
    #[error("UPnP returned invalid XML")]
    InvalidXml,
    #[error("UPnP HTTP response exceeded the size limit")]
    ResponseTooLarge,
    #[error("UPnP description has no supported WAN connection control service")]
    NoService,
    #[error("UPnP returned an incomplete or unexpected SOAP response")]
    InvalidReply,
    #[error("UPnP gateway declined the action with code {0}")]
    SoapFault(u16),
    #[error("UPnP mapping parameters are invalid")]
    InvalidMapping,
    #[error("UPnP port now belongs to a different mapping")]
    MappingChanged,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Protocol {
    Tcp,
    Udp,
}

impl Protocol {
    fn wire(self) -> &'static str {
        match self {
            Self::Tcp => "TCP",
            Self::Udp => "UDP",
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PortMapping {
    pub external_port: u16,
    pub internal_port: u16,
    pub protocol: Protocol,
    pub description: String,
    /// Zero requests a permanent mapping, as used by PBH's Java mapper.
    pub lease_duration: Duration,
}

pub struct MappingLease {
    gateway: Gateway,
    mapping: PortMapping,
    effective_duration: Duration,
}

impl MappingLease {
    pub fn mapping(&self) -> &PortMapping {
        &self.mapping
    }

    /// Returns the duration submitted in the last successful mapping request.
    pub fn effective_duration(&self) -> Duration {
        self.effective_duration
    }

    /// Reasserts this same port mapping. The caller schedules refreshes as needed.
    pub async fn refresh(&mut self) -> Result<(), UpnpError> {
        self.effective_duration = self
            .gateway
            .add(&self.mapping, self.effective_duration)
            .await?;
        Ok(())
    }

    /// Removes the mapping if the gateway still reports this local destination.
    pub async fn remove(&self) -> Result<(), UpnpError> {
        let args = mapping_key(self.mapping.external_port, self.mapping.protocol)?;
        let response = match self
            .gateway
            .action("GetSpecificPortMappingEntry", &args)
            .await
        {
            Ok(response) => response,
            Err(UpnpError::SoapFault(NO_SUCH_ENTRY)) => return Ok(()),
            Err(error) => return Err(error),
        };
        let port: u16 = response.parse_field("NewInternalPort")?;
        let host: Ipv4Addr = response.parse_field("NewInternalClient")?;
        if port != self.mapping.internal_port || host != self.gateway.interface {
            return Err(UpnpError::MappingChanged);
        }
        self.gateway
            .delete_mapping(self.mapping.external_port, self.mapping.protocol)
            .await
    }
}

pub struct IgdClient {
    interface: Ipv4Addr,
    http: Client,
}

impl IgdClient {
    pub fn new(interface: Ipv4Addr) -> Result<Self, UpnpError> {
        if interface.is_unspecified() || interface.is_multicast() || interface.is_broadcast() {
            return Err(UpnpError::InvalidInterface);
        }
        let http = Client::builder()
            .local_address(IpAddr::V4(interface))
            .no_proxy()
            .redirect(reqwest::redirect::Policy::none())
            .timeout(HTTP_TIMEOUT)
            .build()
            .map_err(http_error)?;
        Ok(Self { interface, http })
    }

    pub async fn discover(&self, wait: Duration) -> Result<Vec<Gateway>, UpnpError> {
        self.discover_at(SSDP, wait).await
    }

    /// Uses the given multicast or unicast SSDP destination.
    pub async fn discover_at(
        &self,
        destination: SocketAddrV4,
        wait: Duration,
    ) -> Result<Vec<Gateway>, UpnpError> {
        let replies = discovery::search(self.interface, destination, wait).await?;
        let mut descriptions = stream::iter(replies)
            .map(|reply| self.load(reply))
            .buffer_unordered(4);
        let mut gateways = Vec::<Gateway>::new();
        let mut failure = None;
        while let Some(result) = descriptions.next().await {
            match result {
                Ok(gateway)
                    if !gateways
                        .iter()
                        .any(|known| known.control == gateway.control) =>
                {
                    gateways.push(gateway);
                }
                Ok(_) => {}
                Err(error) => failure = Some(error),
            }
        }
        if gateways.is_empty()
            && let Some(error) = failure
        {
            return Err(error);
        }
        Ok(gateways)
    }

    async fn load(&self, reply: discovery::Announcement) -> Result<Gateway, UpnpError> {
        let response = self
            .http
            .get(reply.location.clone())
            .send()
            .await
            .map_err(http_error)?;
        if !response.status().is_success() {
            return Err(UpnpError::Status(response.status()));
        }
        let body = xml::read_body(response).await?;
        let (service_type, control) = xml::service(&body, &reply.location, reply.responder)?;
        Ok(Gateway {
            interface: self.interface,
            http: self.http.clone(),
            service_type,
            control,
        })
    }
}

#[derive(Clone)]
pub struct Gateway {
    interface: Ipv4Addr,
    http: Client,
    service_type: &'static str,
    control: Url,
}

impl Gateway {
    pub fn local_address(&self) -> Ipv4Addr {
        self.interface
    }

    pub fn control_url(&self) -> &Url {
        &self.control
    }

    pub fn service_type(&self) -> &'static str {
        self.service_type
    }

    pub async fn is_connected(&self) -> Result<bool, UpnpError> {
        let response = self.action("GetStatusInfo", &[]).await?;
        Ok(response.field("NewConnectionStatus")? == "Connected")
    }

    pub async fn external_address(&self) -> Result<Ipv4Addr, UpnpError> {
        self.action("GetExternalIPAddress", &[])
            .await?
            .parse_field("NewExternalIPAddress")
    }

    pub async fn add_mapping(&self, mapping: &PortMapping) -> Result<MappingLease, UpnpError> {
        let effective_duration = self.add(mapping, mapping.lease_duration).await?;
        Ok(MappingLease {
            gateway: self.clone(),
            mapping: mapping.clone(),
            effective_duration,
        })
    }

    /// Explicitly deletes the port and protocol. Tracked leases can verify ownership first.
    pub async fn delete_mapping(&self, port: u16, protocol: Protocol) -> Result<(), UpnpError> {
        let args = mapping_key(port, protocol)?;
        match self.action("DeletePortMapping", &args).await {
            Ok(_) | Err(UpnpError::SoapFault(NO_SUCH_ENTRY)) => Ok(()),
            Err(error) => Err(error),
        }
    }

    async fn add(&self, mapping: &PortMapping, duration: Duration) -> Result<Duration, UpnpError> {
        if mapping.internal_port == 0 || !xml::valid_text(&mapping.description) {
            return Err(UpnpError::InvalidMapping);
        }
        let seconds = u32::try_from(
            duration
                .as_secs()
                .saturating_add(u64::from(duration.subsec_nanos() > 0)),
        )
        .map_err(|_| UpnpError::InvalidMapping)?;
        let mut args = mapping_key(mapping.external_port, mapping.protocol)?.to_vec();
        args.extend([
            ("NewInternalPort", mapping.internal_port.to_string()),
            ("NewInternalClient", self.interface.to_string()),
            ("NewEnabled", "1".into()),
            ("NewPortMappingDescription", mapping.description.clone()),
            ("NewLeaseDuration", seconds.to_string()),
        ]);
        self.action("AddPortMapping", &args).await?;
        Ok(Duration::from_secs(u64::from(seconds)))
    }

    async fn action(
        &self,
        name: &'static str,
        args: &[(&str, String)],
    ) -> Result<xml::SoapReply, UpnpError> {
        let action = format!("\"{}#{name}\"", self.service_type);
        let body = xml::soap_body(self.service_type, name, args);
        let response = self
            .http
            .post(self.control.clone())
            .header("SOAPAction", action)
            .header(reqwest::header::CONTENT_TYPE, "text/xml; charset=\"utf-8\"")
            .body(body)
            .send()
            .await
            .map_err(http_error)?;
        let status = response.status();
        let body = xml::read_body(response).await?;
        xml::soap_reply(&body, self.service_type, name, status)
    }
}

fn mapping_key(port: u16, protocol: Protocol) -> Result<[(&'static str, String); 3], UpnpError> {
    if port == 0 {
        return Err(UpnpError::InvalidMapping);
    }
    Ok([
        ("NewRemoteHost", String::new()),
        ("NewExternalPort", port.to_string()),
        ("NewProtocol", protocol.wire().into()),
    ])
}

fn http_error(error: reqwest::Error) -> UpnpError {
    UpnpError::Http(error.without_url())
}

fn endpoint_on(url: &Url, responder: Ipv4Addr) -> bool {
    matches!(url.scheme(), "http" | "https")
        && url.host_str().and_then(|host| host.parse().ok()) == Some(responder)
        && url.username().is_empty()
        && url.password().is_none()
        && url.fragment().is_none()
        && url.port_or_known_default().is_some_and(|port| port != 0)
}
