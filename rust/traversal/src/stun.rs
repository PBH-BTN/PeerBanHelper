use std::net::{Ipv4Addr, Ipv6Addr, SocketAddr};

use thiserror::Error;

pub(crate) const HEADER_SIZE: usize = 20;
const MAGIC_COOKIE: [u8; 4] = [0x21, 0x12, 0xa4, 0x42];
const BINDING_REQUEST: u16 = 0x0001;
const BINDING_SUCCESS: u16 = 0x0101;
const BINDING_ERROR: u16 = 0x0111;
const CHANGE_REQUEST: u16 = 0x0003;
const MAPPED_ADDRESS: u16 = 0x0001;
const SOURCE_ADDRESS: u16 = 0x0004;
const CHANGED_ADDRESS: u16 = 0x0005;
const ERROR_CODE: u16 = 0x0009;
const XOR_MAPPED_ADDRESS: u16 = 0x0020;
const FINGERPRINT: u16 = 0x8028;
const RESPONSE_ORIGIN: u16 = 0x802b;
const OTHER_ADDRESS: u16 = 0x802c;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum StunVersion {
    Legacy,
    Modern,
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub enum ChangeRequest {
    #[default]
    None,
    Port,
    Ip,
    IpAndPort,
}

impl ChangeRequest {
    fn flags(self) -> u32 {
        match self {
            Self::None => 0,
            Self::Port => 2,
            Self::Ip => 4,
            Self::IpAndPort => 6,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct BindingResponse {
    pub mapped: SocketAddr,
    /// STUN address advertised in RESPONSE-ORIGIN or SOURCE-ADDRESS.
    pub origin: Option<SocketAddr>,
    /// Alternate server advertised in OTHER-ADDRESS or CHANGED-ADDRESS.
    pub other: Option<SocketAddr>,
}

#[derive(Debug, Error, Eq, PartialEq)]
pub enum PacketError {
    #[error("STUN could not generate a transaction identifier")]
    Random,
    #[error("STUN response is truncated or has an invalid attribute")]
    Malformed,
    #[error("STUN reply has a different transaction identifier")]
    TransactionMismatch,
    #[error("STUN expected a Binding response, received type {0:#06x}")]
    UnexpectedType(u16),
    #[error("STUN Binding request was rejected with code {0}")]
    Server(u16),
    #[error("STUN Binding response did not report a mapped address")]
    MissingMapping,
    #[error("STUN response has an invalid fingerprint")]
    Fingerprint,
}

pub struct BindingRequest {
    transaction: [u8; 16],
    change: ChangeRequest,
}

impl BindingRequest {
    pub fn new(version: StunVersion, change: ChangeRequest) -> Result<Self, PacketError> {
        let mut transaction = [0; 16];
        getrandom::fill(&mut transaction[4..]).map_err(|_| PacketError::Random)?;
        if version == StunVersion::Modern {
            transaction[..4].copy_from_slice(&MAGIC_COOKIE);
        }
        Ok(Self {
            transaction,
            change,
        })
    }

    pub fn encode(&self) -> Vec<u8> {
        let flags = self.change.flags();
        let attributes = if flags == 0 { 0_u16 } else { 8 };
        let mut bytes = Vec::with_capacity(HEADER_SIZE + usize::from(attributes));
        bytes.extend_from_slice(&BINDING_REQUEST.to_be_bytes());
        bytes.extend_from_slice(&attributes.to_be_bytes());
        bytes.extend_from_slice(&self.transaction);
        if attributes != 0 {
            bytes.extend_from_slice(&CHANGE_REQUEST.to_be_bytes());
            bytes.extend_from_slice(&4_u16.to_be_bytes());
            bytes.extend_from_slice(&flags.to_be_bytes());
        }
        bytes
    }

    pub fn decode_response(&self, packet: &[u8]) -> Result<BindingResponse, PacketError> {
        if packet.len() < HEADER_SIZE {
            return Err(PacketError::Malformed);
        }
        if packet[4..20] != self.transaction {
            return Err(PacketError::TransactionMismatch);
        }
        let kind = read_u16(packet);
        if !matches!(kind, BINDING_SUCCESS | BINDING_ERROR) {
            return Err(PacketError::UnexpectedType(kind));
        }
        let length = usize::from(read_u16(&packet[2..]));
        if length % 4 != 0 || packet.len() != HEADER_SIZE + length {
            return Err(PacketError::Malformed);
        }

        let mut attributes = ResponseAttributes::default();
        let mut offset = HEADER_SIZE;
        while offset < packet.len() {
            let header = &packet[offset..];
            let attr_kind = read_u16(header);
            let size = usize::from(read_u16(&header[2..]));
            let end = offset + 4 + size.next_multiple_of(4);
            if end > packet.len() {
                return Err(PacketError::Malformed);
            }
            let value = &header[4..4 + size];
            match attr_kind {
                MAPPED_ADDRESS if attributes.mapped.is_none() => {
                    attributes.mapped = Some(decode_address(value, &[0; 16])?);
                }
                XOR_MAPPED_ADDRESS if attributes.xor_mapped.is_none() => {
                    attributes.xor_mapped = Some(decode_address(value, &self.transaction)?);
                }
                SOURCE_ADDRESS | RESPONSE_ORIGIN if attributes.origin.is_none() => {
                    attributes.origin = Some(decode_address(value, &[0; 16])?);
                }
                CHANGED_ADDRESS | OTHER_ADDRESS if attributes.other.is_none() => {
                    attributes.other = Some(decode_address(value, &[0; 16])?);
                }
                ERROR_CODE if attributes.error.is_none() => {
                    attributes.error = Some(decode_error(value)?);
                }
                FINGERPRINT => check_fingerprint(packet, value, offset, end)?,
                _ => {}
            }
            offset = end;
        }
        if kind == BINDING_ERROR {
            return Err(PacketError::Server(
                attributes.error.ok_or(PacketError::Malformed)?,
            ));
        }
        Ok(BindingResponse {
            mapped: attributes
                .xor_mapped
                .or(attributes.mapped)
                .ok_or(PacketError::MissingMapping)?,
            origin: attributes.origin,
            other: attributes.other,
        })
    }
}

#[derive(Default)]
struct ResponseAttributes {
    mapped: Option<SocketAddr>,
    xor_mapped: Option<SocketAddr>,
    origin: Option<SocketAddr>,
    other: Option<SocketAddr>,
    error: Option<u16>,
}

fn decode_address(value: &[u8], mask: &[u8; 16]) -> Result<SocketAddr, PacketError> {
    let [_, family, high, low, octets @ ..] = value else {
        return Err(PacketError::Malformed);
    };
    let port = u16::from_be_bytes([high ^ mask[0], low ^ mask[1]]);
    let ip = match (*family, octets) {
        (1, [a, b, c, d]) => {
            Ipv4Addr::new(a ^ mask[0], b ^ mask[1], c ^ mask[2], d ^ mask[3]).into()
        }
        (2, octets) if octets.len() == 16 => {
            let mut address = *mask;
            for (byte, octet) in address.iter_mut().zip(octets) {
                *byte ^= octet;
            }
            Ipv6Addr::from(address).into()
        }
        _ => return Err(PacketError::Malformed),
    };
    Ok(SocketAddr::new(ip, port))
}

fn decode_error(value: &[u8]) -> Result<u16, PacketError> {
    let [_, _, class, number, ..] = value else {
        return Err(PacketError::Malformed);
    };
    let class = class & 7;
    if !(3..=6).contains(&class) || *number >= 100 {
        return Err(PacketError::Malformed);
    }
    Ok(u16::from(class) * 100 + u16::from(*number))
}

fn check_fingerprint(
    packet: &[u8],
    value: &[u8],
    offset: usize,
    end: usize,
) -> Result<(), PacketError> {
    let expected = crc32fast::hash(&packet[..offset]) ^ 0x5354_554e;
    if end != packet.len() || value != expected.to_be_bytes() {
        return Err(PacketError::Fingerprint);
    }
    Ok(())
}

fn read_u16(bytes: &[u8]) -> u16 {
    u16::from_be_bytes([bytes[0], bytes[1]])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_rfc5769_ipv4_and_ipv6_vectors_with_padding_and_fingerprint() {
        let request = BindingRequest {
            transaction: [
                0x21, 0x12, 0xa4, 0x42, 0xb7, 0xe7, 0xa7, 0x01, 0xbc, 0x34, 0xd6, 0x86, 0xfa, 0x87,
                0xdf, 0xae,
            ],
            change: ChangeRequest::None,
        };
        let vectors = [
            (
                "0101003c 2112a442 b7e7a701 bc34d686 fa87dfae 8022000b 74657374 20766563
              746f7220 00200008 0001a147 e112a643 00080014 2b91f599 fd9e90c3
              8c7489f9 2af9ba53 f06be7d7 80280004 c07d4c96",
                "192.0.2.1:32853",
            ),
            (
                "01010048 2112a442 b7e7a701 bc34d686 fa87dfae 8022000b 74657374 20766563
              746f7220 00200014 0002a147 0113a9fa a5d3f179 bc25f4b5 bed2b9d9
              00080014 a382954e 4be67bf1 1784c97c 8292c275 bfe3ed41 80280004 c8fb0b4c",
                "[2001:db8:1234:5678:11:2233:4455:6677]:32853",
            ),
        ];
        for (hex, mapped) in vectors {
            let mut packet: Vec<_> = hex
                .split_whitespace()
                .flat_map(|word| {
                    (0..word.len())
                        .step_by(2)
                        .map(|start| u8::from_str_radix(&word[start..start + 2], 16).unwrap())
                })
                .collect();
            assert_eq!(
                request.decode_response(&packet).unwrap().mapped,
                mapped.parse().unwrap()
            );
            *packet.last_mut().unwrap() ^= 1;
            assert_eq!(
                request.decode_response(&packet),
                Err(PacketError::Fingerprint)
            );
            packet.pop();
            assert_eq!(
                request.decode_response(&packet),
                Err(PacketError::Malformed)
            );
        }
    }

    #[test]
    fn rejects_invalid_address_lengths_and_decodes_error_responses() {
        let request = BindingRequest::new(StunVersion::Legacy, ChangeRequest::None).unwrap();
        let mut packet = request.encode();
        packet[..4].copy_from_slice(&[0x01, 0x11, 0x00, 0x08]);
        packet.extend_from_slice(&[0, 9, 0, 4, 0, 0, 4, 20]);
        assert_eq!(
            request.decode_response(&packet),
            Err(PacketError::Server(420))
        );
        packet[1] = 0x01;
        packet[21] = 0x01;
        assert_eq!(
            request.decode_response(&packet),
            Err(PacketError::Malformed)
        );
    }
}
