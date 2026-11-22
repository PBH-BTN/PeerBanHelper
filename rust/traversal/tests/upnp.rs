use std::net::{Ipv4Addr, SocketAddr, SocketAddrV4};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use axum::Router;
use axum::extract::State;
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Redirect, Response};
use axum::routing::{get, post};
use pbh_traversal::upnp::{IgdClient, PortMapping, Protocol, UpnpError};
use tokio::net::{TcpListener, UdpSocket};
use tokio::task::JoinHandle;

const SERVICE: &str = "urn:schemas-upnp-org:service:WANIPConnection:2";
const SSDP_TARGET: &str = "urn:schemas-upnp-org:device:InternetGatewayDevice:1";
const SOAP: &str = "http://schemas.xmlsoap.org/soap/envelope/";

#[derive(Default)]
struct StateData {
    calls: Vec<String>,
    mapping: Option<(u16, Ipv4Addr)>,
    add_fault: Option<u16>,
    invalid_status_body: bool,
}

struct Fixture {
    discovery: SocketAddrV4,
    http: SocketAddr,
    state: Arc<Mutex<StateData>>,
    udp_task: JoinHandle<()>,
    http_task: JoinHandle<()>,
}

impl Drop for Fixture {
    fn drop(&mut self) {
        self.udp_task.abort();
        self.http_task.abort();
    }
}

impl Fixture {
    async fn start(description_path: &'static str) -> Self {
        let http = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let http_addr = http.local_addr().unwrap();
        let url = format!("http://{http_addr}{description_path}");
        let udp = UdpSocket::bind("127.0.0.1:0").await.unwrap();
        let SocketAddr::V4(discovery) = udp.local_addr().unwrap() else {
            unreachable!()
        };
        let state = Arc::new(Mutex::new(StateData::default()));
        let http_task = tokio::spawn(serve_http(http, http_addr, state.clone()));
        let udp_task = tokio::spawn(async move {
            let mut buffer = [0; 1024];
            loop {
                let (length, peer) = udp.recv_from(&mut buffer).await.unwrap();
                let request = std::str::from_utf8(&buffer[..length]).unwrap();
                assert!(request.starts_with("M-SEARCH * HTTP/1.1\r\n"));
                assert!(request.contains("MAN: \"ssdp:discover\"\r\n"));
                if !request.contains(&format!("ST: {SSDP_TARGET}\r\n")) {
                    continue;
                }
                let packet =
                    format!("HTTP/1.1 200 OK\r\nLoCaTiOn: {url}\r\nST: {SSDP_TARGET}\r\n\r\n");
                for _ in 0..2 {
                    udp.send_to(packet.as_bytes(), peer).await.unwrap();
                }
            }
        });
        Self {
            discovery,
            http: http_addr,
            state,
            udp_task,
            http_task,
        }
    }

    async fn gateways(&self) -> Result<Vec<pbh_traversal::upnp::Gateway>, UpnpError> {
        IgdClient::new(Ipv4Addr::LOCALHOST)
            .unwrap()
            .discover_at(self.discovery, Duration::from_millis(200))
            .await
    }
}

#[tokio::test]
async fn discovers_selects_wan_service_and_uses_mapping_lifecycle() {
    let fixture = Fixture::start("/bridge/desc.xml").await;
    let mut gateways = fixture.gateways().await.unwrap();
    assert_eq!(gateways.len(), 1);
    let gateway = gateways.pop().unwrap();
    assert_eq!(
        gateway.control_url().as_str(),
        format!("http://{}/wan/control", fixture.http)
    );
    assert_eq!(gateway.service_type(), SERVICE);
    assert_eq!(gateway.local_address(), Ipv4Addr::LOCALHOST);
    assert!(gateway.is_connected().await.unwrap());
    assert_eq!(
        gateway.external_address().await.unwrap(),
        Ipv4Addr::new(192, 0, 2, 33)
    );

    let request = example_mapping();
    let mut lease = gateway.add_mapping(&request).await.unwrap();
    assert_eq!(lease.mapping(), &request);
    assert_eq!(lease.effective_duration(), Duration::from_secs(2));
    lease.refresh().await.unwrap();
    lease.remove().await.unwrap();
    lease.remove().await.unwrap();
    let state = fixture.state.lock().unwrap();
    assert!(state.mapping.is_none());
    assert_eq!(
        state.calls,
        [
            "GetStatusInfo",
            "GetExternalIPAddress",
            "AddPortMapping",
            "AddPortMapping",
            "GetSpecificPortMappingEntry",
            "DeletePortMapping",
            "GetSpecificPortMappingEntry"
        ]
    );
}

#[tokio::test]
async fn surfaces_router_faults_and_preserves_reassigned_mappings() {
    let fixture = Fixture::start("/bridge/desc.xml").await;
    let gateway = fixture.gateways().await.unwrap().pop().unwrap();
    let mut request = example_mapping();
    fixture.state.lock().unwrap().add_fault = Some(718);
    assert!(matches!(
        gateway.add_mapping(&request).await,
        Err(UpnpError::SoapFault(718))
    ));
    fixture.state.lock().unwrap().add_fault = Some(725);
    assert!(matches!(
        gateway.add_mapping(&request).await,
        Err(UpnpError::SoapFault(725))
    ));
    request.external_port = 0;
    assert!(matches!(
        gateway.add_mapping(&request).await,
        Err(UpnpError::InvalidMapping)
    ));
    request.external_port = 4500;
    request.lease_duration = Duration::ZERO;
    fixture.state.lock().unwrap().add_fault = None;
    let lease = gateway.add_mapping(&request).await.unwrap();
    assert!(lease.effective_duration().is_zero());
    fixture.state.lock().unwrap().mapping = Some((4505, Ipv4Addr::LOCALHOST));
    assert!(matches!(
        lease.remove().await,
        Err(UpnpError::MappingChanged)
    ));
    fixture.state.lock().unwrap().invalid_status_body = true;
    assert!(matches!(
        gateway.is_connected().await,
        Err(UpnpError::InvalidReply)
    ));
    let state = fixture.state.lock().unwrap();
    assert_eq!(state.mapping, Some((4505, Ipv4Addr::LOCALHOST)));
    assert!(!state.calls.iter().any(|call| call == "DeletePortMapping"));
    assert_eq!(
        state
            .calls
            .iter()
            .filter(|call| *call == "AddPortMapping")
            .count(),
        3
    );
}

#[tokio::test]
async fn does_not_follow_description_redirects() {
    let fixture = Fixture::start("/redirect").await;
    assert!(matches!(
        fixture.gateways().await,
        Err(UpnpError::Status(StatusCode::TEMPORARY_REDIRECT))
    ));
}

fn example_mapping() -> PortMapping {
    PortMapping {
        external_port: 4500,
        internal_port: 4501,
        protocol: Protocol::Tcp,
        description: "测试 & <PeerBanHelper>".to_owned(),
        lease_duration: Duration::from_millis(1500),
    }
}

async fn serve_http(listener: TcpListener, address: SocketAddr, state: Arc<Mutex<StateData>>) {
    axum::serve(
        listener,
        Router::new()
            .route(
                "/bridge/desc.xml",
                get(move || async move { description(address) }),
            )
            .route(
                "/redirect",
                get(|| async { Redirect::temporary("/bridge/desc.xml") }),
            )
            .route("/wan/control", post(action))
            .with_state(state),
    )
    .await
    .unwrap();
}

fn description(address: SocketAddr) -> String {
    format!(
        r#"<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
    <URLBase>http://{address}/wan/</URLBase>
    <device><deviceList><device><serviceList>
        <service><serviceType>urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1</serviceType>
            <controlURL>/wrong</controlURL></service>
        <service><serviceType>urn:schemas-upnp-org:service:WANPPPConnection:1</serviceType>
            <controlURL>/wrong-ppp</controlURL></service>
        <service><serviceType>{SERVICE}</serviceType><controlURL>control</controlURL></service>
    </serviceList></device></deviceList></device>
</root>"#
    )
}

async fn action(
    State(state): State<Arc<Mutex<StateData>>>,
    headers: HeaderMap,
    body: String,
) -> Response {
    let doc = roxmltree::Document::parse(&body).unwrap();
    let root = doc.root_element();
    assert!(root.has_tag_name((SOAP, "Envelope")));
    let node = root
        .children()
        .find(|n| n.has_tag_name((SOAP, "Body")))
        .unwrap()
        .first_element_child()
        .unwrap();
    let name = node.tag_name().name();
    assert_eq!(node.tag_name().namespace(), Some(SERVICE));
    assert_eq!(headers["soapaction"], format!("\"{SERVICE}#{name}\""));
    let read = |key| {
        node.children()
            .find(|n| n.has_tag_name(key))
            .unwrap()
            .text()
            .unwrap_or_default()
    };
    let mut state = state.lock().unwrap();
    state.calls.push(name.into());
    let fields = match name {
        "GetStatusInfo" if state.invalid_status_body => {
            return response("DifferentAction", "").into_response();
        }
        "GetStatusInfo" => "<NewConnectionStatus>Connected</NewConnectionStatus>".into(),
        "GetExternalIPAddress" => "<NewExternalIPAddress>192.0.2.33</NewExternalIPAddress>".into(),
        "AddPortMapping" => {
            assert_eq!(read("NewRemoteHost"), "");
            assert_eq!(read("NewExternalPort"), "4500");
            assert_eq!(read("NewInternalPort"), "4501");
            assert_eq!(read("NewInternalClient"), "127.0.0.1");
            assert_eq!(read("NewPortMappingDescription"), "测试 & <PeerBanHelper>");
            assert_eq!(read("NewEnabled"), "1");
            assert_eq!(read("NewProtocol"), "TCP");
            assert!(matches!(read("NewLeaseDuration"), "0" | "2"));
            if let Some(code) = state.add_fault {
                return fault(code);
            }
            state.mapping = Some((4501, Ipv4Addr::LOCALHOST));
            String::new()
        }
        "GetSpecificPortMappingEntry" => match state.mapping {
            Some((port, address)) => format!(
                "<NewInternalPort>{port}</NewInternalPort><NewInternalClient>{address}</NewInternalClient>"
            ),
            None => return fault(714),
        },
        "DeletePortMapping" => {
            assert_eq!(read("NewExternalPort"), "4500");
            assert_eq!(read("NewProtocol"), "TCP");
            state.mapping = None;
            String::new()
        }
        _ => panic!("unexpected SOAP action {name}"),
    };
    response(name, &fields).into_response()
}

fn response(name: &str, fields: &str) -> String {
    format!(
        r#"<s:Envelope xmlns:s="{SOAP}"><s:Body><u:{name}Response xmlns:u="{SERVICE}">{fields}</u:{name}Response></s:Body></s:Envelope>"#
    )
}

fn fault(code: u16) -> Response {
    let body = format!(
        r#"<s:Envelope xmlns:s="{SOAP}"><s:Body><s:Fault><detail>
        <UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>{code}</errorCode></UPnPError>
    </detail></s:Fault></s:Body></s:Envelope>"#
    );
    (StatusCode::INTERNAL_SERVER_ERROR, body).into_response()
}
