use std::time::Duration;

use pbh_network::{ProxyConfig, ProxyError, ProxyPolicy};
use reqwest::{Client, Url};
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::{TcpListener, TcpStream};

#[test]
fn interprets_current_java_configuration_and_whole_host_bypass_patterns() {
    let config: ProxyConfig = serde_yaml_ng::from_str(
        "setting: 1\nhost: 127.0.0.1\nport: 8123\nnon-proxy-hosts: 'example.net|*.lan|srv?|['\n",
    )
    .unwrap();
    let policy = ProxyPolicy::new(&config).unwrap();
    assert_eq!(policy.invalid_bypass_count(), 1);

    for host in [
        "example.net",
        "abc.lan",
        "abcxlan",
        "srv",
        "srv1",
        "10.1.1.2",
        "169.254.3.4",
        "224.0.0.9",
        "232.1.3.5",
        "239.255.1.1",
    ] {
        assert!(
            policy.proxy_for(&parse_host(host)).is_none(),
            "expected direct route for {host}"
        );
    }
    for host in [
        "example.net.example",
        "srv12",
        "127.0.0.1",
        "100.64.1.2",
        "224.0.1.9",
        "232.0.0.1",
        "[::1]",
    ] {
        assert_eq!(
            policy.proxy_for(&parse_host(host)).unwrap().as_str(),
            "http://127.0.0.1:8123/",
            "expected proxy for {host}"
        );
    }

    let policy = ProxyPolicy::new(&ProxyConfig {
        non_proxy_hosts: "127.*|\\[::1\\]".into(),
        ..config.clone()
    })
    .unwrap();
    assert!(policy.proxy_for(&parse_host("127.0.0.1")).is_none());
    assert!(policy.proxy_for(&parse_host("[::1]")).is_none());
    let ignored = ProxyConfig {
        setting: 9,
        host: "unset".into(),
        port: -1,
        ..config
    };
    assert!(
        ProxyPolicy::new(&ignored)
            .unwrap()
            .proxy_for(&parse_host("example.org"))
            .is_none()
    );
}

#[test]
fn validates_proxy_addresses_without_echoing_credentials_in_errors() {
    for host in [
        "",
        "user:private@127.0.0.1",
        "127.0.0.1/path",
        "http://127.0.0.1",
        "127.0.0.1:9000",
        "[[::1]]",
        "[127.0.0.1]",
    ] {
        let config = ProxyConfig {
            setting: 1,
            host: host.to_owned(),
            ..ProxyConfig::default()
        };
        assert!(matches!(
            ProxyPolicy::new(&config),
            Err(ProxyError::InvalidAddress)
        ));
    }
    let config = ProxyConfig {
        setting: 2,
        host: "[::1]".into(),
        ..ProxyConfig::default()
    };
    let policy = ProxyPolicy::new(&config).unwrap();
    assert_eq!(
        policy
            .proxy_for(&parse_host("example.org"))
            .unwrap()
            .host_str(),
        Some("[::1]")
    );
}

#[tokio::test]
async fn configured_http_proxy_and_bypassed_origin_receive_the_expected_requests() {
    let origin = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let target = format!("http://{}/probe", origin.local_addr().unwrap());
    let proxy = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let proxy_port = proxy.local_addr().unwrap().port();
    let absolute_target = target.clone();
    let proxy_task = tokio::spawn(async move {
        for _ in 0..2 {
            let (mut stream, _) = proxy.accept().await.unwrap();
            assert_eq!(
                request_line(&mut stream).await,
                format!("GET {absolute_target} HTTP/1.1")
            );
            reply(&mut stream, "proxy").await;
        }
    });
    let origin_task = tokio::spawn(async move {
        for _ in 0..2 {
            let (mut stream, _) = origin.accept().await.unwrap();
            assert_eq!(request_line(&mut stream).await, "GET /probe HTTP/1.1");
            reply(&mut stream, "origin").await;
        }
    });
    let config = ProxyConfig {
        setting: 1,
        port: i32::from(proxy_port),
        ..ProxyConfig::default()
    };
    let policy = ProxyPolicy::new(&config).unwrap();
    assert_eq!(get(&policy, &target).await, "proxy");
    let blocking_target = target.clone();
    let blocking = tokio::task::spawn_blocking(move || {
        policy
            .apply_blocking(reqwest::blocking::Client::builder().timeout(Duration::from_secs(3)))
            .build()
            .unwrap()
            .get(blocking_target)
            .send()
            .unwrap()
            .text()
            .unwrap()
    });
    assert_eq!(blocking.await.unwrap(), "proxy");
    let bypass = ProxyConfig {
        non_proxy_hosts: "127.*".into(),
        ..config
    };
    assert_eq!(
        get(&ProxyPolicy::new(&bypass).unwrap(), &target).await,
        "origin"
    );
    assert_eq!(get(&ProxyPolicy::default(), &target).await, "origin");
    proxy_task.await.unwrap();
    origin_task.await.unwrap();
}

#[tokio::test]
async fn https_origin_uses_a_connect_tunnel_through_the_http_proxy() {
    let proxy = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let config = ProxyConfig {
        setting: 1,
        port: i32::from(proxy.local_addr().unwrap().port()),
        ..ProxyConfig::default()
    };
    let server = tokio::spawn(async move {
        let (mut stream, _) = proxy.accept().await.unwrap();
        assert_eq!(
            request_line(&mut stream).await,
            "CONNECT localhost:9123 HTTP/1.1"
        );
        stream
            .write_all(b"HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n")
            .await
            .unwrap();
    });
    let client = ProxyPolicy::new(&config)
        .unwrap()
        .apply(Client::builder().timeout(Duration::from_secs(3)))
        .build()
        .unwrap();
    assert!(
        client
            .get("https://localhost:9123/probe")
            .send()
            .await
            .unwrap_err()
            .is_connect()
    );
    server.await.unwrap();
}

#[tokio::test]
async fn socks5_keeps_the_origin_hostname_for_proxy_side_dns_resolution() {
    let proxy = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let config = ProxyConfig {
        setting: 2,
        port: i32::from(proxy.local_addr().unwrap().port()),
        ..ProxyConfig::default()
    };
    let server = tokio::spawn(async move {
        let (mut stream, _) = proxy.accept().await.unwrap();
        let mut header = [0; 2];
        stream.read_exact(&mut header).await.unwrap();
        assert_eq!(header[0], 5);
        let mut offered = vec![0; usize::from(header[1])];
        stream.read_exact(&mut offered).await.unwrap();
        assert!(offered.contains(&0));
        stream.write_all(&[5, 0]).await.unwrap();

        let mut request = [0; 5];
        stream.read_exact(&mut request).await.unwrap();
        assert_eq!(&request[..4], &[5, 1, 0, 3]);
        let mut host = vec![0; usize::from(request[4])];
        stream.read_exact(&mut host).await.unwrap();
        assert_eq!(host, b"localhost");
        assert_eq!(stream.read_u16().await.unwrap(), 9123);
        stream
            .write_all(&[5, 0, 0, 1, 127, 0, 0, 1, 0, 0])
            .await
            .unwrap();
        assert_eq!(request_line(&mut stream).await, "GET /socks HTTP/1.1");
        reply(&mut stream, "socks").await;
    });
    assert_eq!(
        get(
            &ProxyPolicy::new(&config).unwrap(),
            "http://localhost:9123/socks"
        )
        .await,
        "socks"
    );
    server.await.unwrap();
}

fn parse_host(host: &str) -> Url {
    Url::parse(&format!("http://{host}/")).unwrap()
}

async fn get(policy: &ProxyPolicy, url: &str) -> String {
    policy
        .apply(Client::builder().timeout(Duration::from_secs(3)))
        .build()
        .unwrap()
        .get(url)
        .send()
        .await
        .unwrap()
        .text()
        .await
        .unwrap()
}

async fn request_line(stream: &mut TcpStream) -> String {
    let mut reader = BufReader::new(stream);
    let mut request = String::new();
    reader.read_line(&mut request).await.unwrap();
    loop {
        let mut line = String::new();
        if reader.read_line(&mut line).await.unwrap() == 0 || line == "\r\n" {
            break;
        }
    }
    request.trim_end().to_owned()
}

async fn reply(stream: &mut TcpStream, body: &str) {
    let message = format!(
        "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
        body.len()
    );
    stream.write_all(message.as_bytes()).await.unwrap();
}
