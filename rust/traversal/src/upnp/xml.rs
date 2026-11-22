use std::collections::HashMap;
use std::net::Ipv4Addr;
use std::str::FromStr;

use reqwest::{Response, StatusCode, Url};
use roxmltree::{Document, Node, ParsingOptions};

use super::{UpnpError, endpoint_on, http_error};

const MAX_XML_BYTES: usize = 1024 * 1024;
const SOAP_ENVELOPE: &str = "http://schemas.xmlsoap.org/soap/envelope/";
const WAN_SERVICES: [&str; 3] = [
    "urn:schemas-upnp-org:service:WANIPConnection:2",
    "urn:schemas-upnp-org:service:WANIPConnection:1",
    "urn:schemas-upnp-org:service:WANPPPConnection:1",
];

pub(super) async fn read_body(mut response: Response) -> Result<String, UpnpError> {
    if response
        .content_length()
        .is_some_and(|length| length > MAX_XML_BYTES as u64)
    {
        return Err(UpnpError::ResponseTooLarge);
    }
    let mut body = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(http_error)? {
        if body.len() + chunk.len() > MAX_XML_BYTES {
            return Err(UpnpError::ResponseTooLarge);
        }
        body.extend_from_slice(&chunk);
    }
    String::from_utf8(body).map_err(|_| UpnpError::InvalidXml)
}

pub(super) fn service(
    body: &str,
    location: &Url,
    responder: Ipv4Addr,
) -> Result<(&'static str, Url), UpnpError> {
    let doc = document(body)?;
    let root = doc.root_element();
    if !tag(root, "root") {
        return Err(UpnpError::InvalidXml);
    }
    let base = match text(root, "URLBase").filter(|text| !text.is_empty()) {
        Some(value) => Url::parse(value).map_err(|_| UpnpError::UnexpectedEndpoint)?,
        None => location.clone(),
    };
    if !endpoint_on(&base, responder) {
        return Err(UpnpError::UnexpectedEndpoint);
    }

    let mut unsafe_control = false;
    for kind in WAN_SERVICES {
        let controls = root
            .descendants()
            .filter(|node| {
                tag(*node, "service")
                    && node
                        .parent()
                        .is_some_and(|parent| tag(parent, "serviceList"))
            })
            .filter(|&entry| text(entry, "serviceType") == Some(kind))
            .filter_map(|entry| text(entry, "controlURL").filter(|url| !url.is_empty()));
        for control in controls {
            let Ok(control) = base.join(control) else {
                continue;
            };
            if endpoint_on(&control, responder) {
                return Ok((kind, control));
            }
            unsafe_control = true;
        }
    }
    Err(if unsafe_control {
        UpnpError::UnexpectedEndpoint
    } else {
        UpnpError::NoService
    })
}

pub(super) fn valid_text(value: &str) -> bool {
    value.chars().all(|c| matches!(c, '\u{9}' | '\u{a}' | '\u{d}' | '\u{20}'..='\u{d7ff}' | '\u{e000}'..='\u{fffd}' | '\u{10000}'..='\u{10ffff}'))
}

pub(super) fn soap_body(service: &str, action: &str, args: &[(&str, String)]) -> String {
    let mut body = format!(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"{SOAP_ENVELOPE}\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:{action} xmlns:u=\"{service}\">"
    );
    for (key, value) in args {
        body.push('<');
        body.push_str(key);
        body.push('>');
        for char in value.chars() {
            match char {
                '&' => body.push_str("&amp;"),
                '<' => body.push_str("&lt;"),
                '>' => body.push_str("&gt;"),
                other => body.push(other),
            }
        }
        body.push_str("</");
        body.push_str(key);
        body.push('>');
    }
    body.push_str(&format!("</u:{action}></s:Body></s:Envelope>"));
    body
}

pub(super) struct SoapReply(HashMap<String, String>);

impl SoapReply {
    pub(super) fn field(&self, field: &str) -> Result<&str, UpnpError> {
        self.0
            .get(field)
            .map(String::as_str)
            .ok_or(UpnpError::InvalidReply)
    }

    pub(super) fn parse_field<T: FromStr>(&self, field: &str) -> Result<T, UpnpError> {
        self.field(field)?
            .parse()
            .map_err(|_| UpnpError::InvalidReply)
    }
}

pub(super) fn soap_reply(
    body: &str,
    service: &str,
    action: &str,
    status: StatusCode,
) -> Result<SoapReply, UpnpError> {
    let doc = document(body).map_err(|error| {
        if status.is_success() {
            error
        } else {
            UpnpError::Status(status)
        }
    })?;
    let envelope = doc.root_element();
    if !envelope.has_tag_name((SOAP_ENVELOPE, "Envelope")) {
        return Err(UpnpError::InvalidReply);
    }
    let payload = envelope
        .children()
        .find(|node| node.has_tag_name((SOAP_ENVELOPE, "Body")))
        .and_then(|node| node.children().find(Node::is_element))
        .ok_or(UpnpError::InvalidReply)?;
    if payload.has_tag_name((SOAP_ENVELOPE, "Fault")) {
        let code = payload
            .descendants()
            .find(|node| tag(*node, "errorCode"))
            .and_then(|node| node.text())
            .and_then(|value| value.trim().parse().ok())
            .ok_or(UpnpError::InvalidReply)?;
        return Err(UpnpError::SoapFault(code));
    }
    if !status.is_success() {
        return Err(UpnpError::Status(status));
    }
    let expected = format!("{action}Response");
    if !payload.has_tag_name((service, expected.as_str())) {
        return Err(UpnpError::InvalidReply);
    }
    let fields = payload
        .children()
        .filter(Node::is_element)
        .map(|node| {
            (
                node.tag_name().name().to_owned(),
                node.text().unwrap_or_default().trim().to_owned(),
            )
        })
        .collect();
    Ok(SoapReply(fields))
}

fn document(body: &str) -> Result<Document<'_>, UpnpError> {
    Document::parse_with_options(
        body,
        ParsingOptions {
            allow_dtd: false,
            nodes_limit: 25_000,
            ..ParsingOptions::default()
        },
    )
    .map_err(|_| UpnpError::InvalidXml)
}

fn tag(node: Node<'_, '_>, local_name: &str) -> bool {
    node.is_element() && node.tag_name().name() == local_name
}

fn text<'a>(node: Node<'a, '_>, local_name: &str) -> Option<&'a str> {
    node.children()
        .find(|child| tag(*child, local_name))?
        .text()
        .map(str::trim)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_external_control_endpoints_and_dtd() {
        let location = Url::parse("http://127.0.0.1:1901/root.xml").unwrap();
        let mut body = format!(
            "<root><device><serviceList><service><serviceType>{}</serviceType><controlURL>http://127.0.0.2:1901/control</controlURL></service></serviceList></device></root>",
            WAN_SERVICES[0]
        );
        assert!(matches!(
            service(&body, &location, Ipv4Addr::LOCALHOST),
            Err(UpnpError::UnexpectedEndpoint)
        ));
        body.insert_str(0, "<!DOCTYPE root [<!ELEMENT root ANY>]>");
        assert!(matches!(
            service(&body, &location, Ipv4Addr::LOCALHOST),
            Err(UpnpError::InvalidXml)
        ));
    }
}
