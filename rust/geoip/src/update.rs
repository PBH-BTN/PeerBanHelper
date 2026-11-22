//! Startup updates for the existing PeerBanHelper MMDB files.

use std::fs;
use std::io::{self, Read};
use std::path::Path;
use std::time::{Duration, Instant};

use lzma_rust2::XzReader;
use pbh_network::ProxyPolicy;
use reqwest::blocking::{Client, Response};
use reqwest::{StatusCode, Url, redirect};
use sha2::{Digest, Sha256};
use tempfile::NamedTempFile;
use thiserror::Error;

use crate::{Database, Mmdb};

const MIRRORS: [&str; 3] = [
    "https://github.com/PBH-BTN/GeoLite.mmdb/releases/latest/download/",
    "https://pbh-static.paulzzh.com/ipdb/",
    "https://pbh-static.ghostchu.com/ipdb/",
];
const MAX_AGE: Duration = Duration::from_secs(45 * 24 * 60 * 60);
const CALL_TIMEOUT: Duration = Duration::from_secs(180);
const MAX_COMPRESSED_BYTES: u64 = 128 * 1024 * 1024;
const MAX_DATABASE_BYTES: u64 = 512 * 1024 * 1024;
const DECODER_MEMORY_KIB: u32 = 128 * 1024;

#[derive(Clone)]
pub struct DatabaseSource {
    pub name: String,
    /// An optional SHA-256 over the compressed asset, from trusted metadata.
    pub sha256: Option<[u8; 32]>,
}

impl DatabaseSource {
    pub fn new(name: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            sha256: None,
        }
    }
}

#[derive(Clone)]
pub struct Credentials {
    pub account_id: String,
    pub license_key: String,
}

#[derive(Clone)]
pub struct UpdateOptions {
    pub auto_update: bool,
    pub city: DatabaseSource,
    pub asn: DatabaseSource,
    pub geocn: DatabaseSource,
    pub credentials: Option<Credentials>,
}

impl Default for UpdateOptions {
    fn default() -> Self {
        Self {
            auto_update: true,
            city: DatabaseSource::new("GeoLite2-City"),
            asn: DatabaseSource::new("GeoLite2-ASN"),
            geocn: DatabaseSource::new("GeoCN"),
            credentials: None,
        }
    }
}

#[derive(Debug)]
pub struct UpdateResult {
    pub database: Database,
    pub status: UpdateStatus,
}

#[derive(Debug)]
pub struct UpdateProgress {
    pub database: Database,
    pub phase: ProgressPhase,
}

#[derive(Debug)]
pub enum ProgressPhase {
    Started,
    Requesting {
        url: String,
    },
    Transfer {
        decoded_bytes: u64,
        compressed_total: Option<u64>,
    },
    Validating,
    Finished {
        error: Option<String>,
    },
}

#[derive(Debug)]
pub enum UpdateStatus {
    Current,
    Installed(DownloadMetadata),
    Unavailable {
        retained_existing: bool,
        error: UpdateError,
    },
}

#[derive(Debug)]
pub struct DownloadMetadata {
    pub mirror_index: usize,
    pub compressed_bytes: u64,
    pub database_bytes: u64,
    pub sha256: [u8; 32],
}

#[derive(Debug, Error)]
pub enum UpdateError {
    #[error("invalid or missing GeoIP mirror URL")]
    InvalidMirror,
    #[error("invalid GeoIP database asset name")]
    InvalidAssetName,
    #[error("GeoIP HTTP request failed")]
    Http(#[source] reqwest::Error),
    #[error("GeoIP HTTP request timed out")]
    TimedOut,
    #[error("GeoIP download returned HTTP {0}")]
    Status(StatusCode),
    #[error("GeoIP compressed asset exceeds the download limit")]
    DownloadLimit,
    #[error("GeoIP database exceeds the decompressed size limit")]
    DatabaseLimit,
    #[error("GeoIP compressed asset SHA-256 does not match")]
    Checksum,
    #[error("unable to read the GeoIP XZ stream or write the staging file")]
    Decode(#[source] io::Error),
    #[error("unable to access the GeoIP directory or replace a database")]
    Filesystem(#[source] io::Error),
    #[error("unable to validate the downloaded MMDB")]
    InvalidDatabase(#[source] maxminddb::MaxMindDbError),
    #[error("the downloaded MMDB type does not match its target")]
    WrongType,
}

pub struct DatabaseUpdater {
    mirrors: Vec<Url>,
    client: Client,
}

impl DatabaseUpdater {
    pub fn new() -> Result<Self, UpdateError> {
        Self::with_proxy(&ProxyPolicy::default())
    }

    pub fn with_proxy(proxy: &ProxyPolicy) -> Result<Self, UpdateError> {
        let mirrors = MIRRORS
            .into_iter()
            .map(Url::parse)
            .collect::<Result<_, _>>()
            .map_err(|_| UpdateError::InvalidMirror)?;
        Self::build(mirrors, proxy)
    }

    /// Build with explicit base URLs. Production mirrors require HTTPS.
    pub fn with_mirrors(mirrors: Vec<Url>) -> Result<Self, UpdateError> {
        Self::build(mirrors, &ProxyPolicy::default())
    }

    fn build(mut mirrors: Vec<Url>, proxy: &ProxyPolicy) -> Result<Self, UpdateError> {
        if mirrors.is_empty() {
            return Err(UpdateError::InvalidMirror);
        }
        for mirror in &mut mirrors {
            if !safe_url(mirror) || mirror.query().is_some() {
                return Err(UpdateError::InvalidMirror);
            }
            if !mirror.path().ends_with('/') {
                mirror
                    .path_segments_mut()
                    .map_err(|_| UpdateError::InvalidMirror)?
                    .push("");
            }
        }
        let builder = Client::builder()
            .connect_timeout(Duration::from_secs(15))
            .timeout(CALL_TIMEOUT)
            .referer(false)
            .redirect(redirect::Policy::custom(|attempt| {
                if attempt.previous().len() >= 10
                    || !safe_url(attempt.url())
                    || (attempt.url().scheme() == "http"
                        && attempt.previous().iter().any(|url| url.scheme() != "http"))
                {
                    attempt.stop()
                } else {
                    attempt.follow()
                }
            }));
        let client = proxy.apply_blocking(builder).build().map_err(http_error)?;
        Ok(Self { mirrors, client })
    }

    /// Run off the async executor. The installed files remain readable while
    /// downloads are staged, and results are reported independently per database.
    pub fn refresh(&self, data_dir: &Path, options: &UpdateOptions) -> Vec<UpdateResult> {
        self.refresh_with_progress(data_dir, options, |_| {})
    }

    /// Report only databases that actually enter the update workflow.
    pub fn refresh_with_progress(
        &self,
        data_dir: &Path,
        options: &UpdateOptions,
        mut progress: impl FnMut(UpdateProgress),
    ) -> Vec<UpdateResult> {
        let dir = data_dir.join("ipdb").join("geoip");
        [
            (Database::City, &options.city),
            (Database::Asn, &options.asn),
            (Database::GeoCn, &options.geocn),
        ]
        .into_iter()
        .map(|(database, source)| UpdateResult {
            database,
            status: self.refresh_one(&dir, database, source, options, &mut |phase| {
                progress(UpdateProgress { database, phase });
            }),
        })
        .collect()
    }

    fn refresh_one(
        &self,
        directory: &Path,
        database: Database,
        source: &DatabaseSource,
        options: &UpdateOptions,
        progress: &mut dyn FnMut(ProgressPhase),
    ) -> UpdateStatus {
        let target = directory.join(database.filename());
        match needs_update(&target, options.auto_update) {
            Ok(false) => return UpdateStatus::Current,
            Ok(true) => {}
            Err(error) => {
                return UpdateStatus::Unavailable {
                    retained_existing: target.is_file(),
                    error,
                };
            }
        }
        progress(ProgressPhase::Started);
        let result = self.install(
            directory,
            &target,
            database,
            source,
            options.credentials.as_ref(),
            progress,
        );
        progress(ProgressPhase::Finished {
            error: result.as_ref().err().map(ToString::to_string),
        });
        match result {
            Ok(data) => UpdateStatus::Installed(data),
            Err(error) => UpdateStatus::Unavailable {
                retained_existing: target.is_file(),
                error,
            },
        }
    }

    fn install(
        &self,
        directory: &Path,
        target: &Path,
        database: Database,
        source: &DatabaseSource,
        credentials: Option<&Credentials>,
        progress: &mut dyn FnMut(ProgressPhase),
    ) -> Result<DownloadMetadata, UpdateError> {
        if !valid_asset_name(&source.name) {
            return Err(UpdateError::InvalidAssetName);
        }
        fs::create_dir_all(directory).map_err(UpdateError::Filesystem)?;
        let asset = format!("{}.mmdb.xz", source.name);
        let mut last_failure = UpdateError::InvalidMirror;
        for (mirror_index, mirror) in self.mirrors.iter().enumerate() {
            let url = mirror
                .join(&asset)
                .map_err(|_| UpdateError::InvalidMirror)?;
            progress(ProgressPhase::Requesting {
                url: url.to_string(),
            });
            let result = self.fetch(url, credentials).and_then(|response| {
                install_response(
                    response,
                    target,
                    directory,
                    database,
                    source.sha256,
                    mirror_index,
                    progress,
                )
            });
            match result {
                Ok(metadata) => return Ok(metadata),
                Err(error) => last_failure = error,
            }
        }
        Err(last_failure)
    }

    fn fetch(&self, url: Url, credentials: Option<&Credentials>) -> Result<Response, UpdateError> {
        let start = Instant::now();
        let mut response = self
            .client
            .get(url.clone())
            .timeout(CALL_TIMEOUT)
            .send()
            .map_err(http_error)?;
        if response.status() == StatusCode::UNAUTHORIZED
            && response.url().origin() == url.origin()
            && let Some(credentials) = credentials
        {
            let remaining = CALL_TIMEOUT
                .checked_sub(start.elapsed())
                .ok_or(UpdateError::TimedOut)?;
            response = self
                .client
                .get(response.url().clone())
                .basic_auth(&credentials.account_id, Some(&credentials.license_key))
                .timeout(remaining)
                .send()
                .map_err(http_error)?;
        }
        if response.status() != StatusCode::OK {
            return Err(UpdateError::Status(response.status()));
        }
        if response
            .content_length()
            .is_some_and(|size| size > MAX_COMPRESSED_BYTES)
        {
            return Err(UpdateError::DownloadLimit);
        }
        Ok(response)
    }
}

fn needs_update(target: &Path, auto_update: bool) -> Result<bool, UpdateError> {
    let metadata = match target.metadata() {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(true),
        Err(error) => return Err(UpdateError::Filesystem(error)),
    };
    if !auto_update {
        return Ok(false);
    }
    let modified = metadata.modified().map_err(UpdateError::Filesystem)?;
    Ok(modified.elapsed().is_ok_and(|age| age > MAX_AGE))
}

fn install_response(
    response: Response,
    target: &Path,
    directory: &Path,
    database: Database,
    expected: Option<[u8; 32]>,
    mirror_index: usize,
    progress: &mut dyn FnMut(ProgressPhase),
) -> Result<DownloadMetadata, UpdateError> {
    let mut staging = NamedTempFile::new_in(directory).map_err(UpdateError::Filesystem)?;
    let compressed_total = response.content_length();
    progress(ProgressPhase::Transfer {
        decoded_bytes: 0,
        compressed_total,
    });
    let decoded = {
        let mut output = ProgressWriter {
            inner: &mut staging,
            decoded_bytes: 0,
            notify: |decoded_bytes| {
                progress(ProgressPhase::Transfer {
                    decoded_bytes,
                    compressed_total,
                })
            },
        };
        decode(
            response,
            &mut output,
            MAX_COMPRESSED_BYTES,
            MAX_DATABASE_BYTES,
        )
    }
    .and_then(|archive| {
        progress(ProgressPhase::Validating);
        if expected.is_some_and(|expected| archive.sha256 != expected) {
            return Err(UpdateError::Checksum);
        }
        staging
            .as_file()
            .sync_all()
            .map_err(UpdateError::Filesystem)?;
        Ok(archive)
    });
    // Close the handle before deleting a failed stage or renaming a validated
    // file. A Windows deletion can remain pending while the handle is open.
    let staging = staging.into_temp_path();
    let decoded = decoded?;
    let reader = Mmdb::open_readfile(&*staging).map_err(UpdateError::InvalidDatabase)?;
    if !database.supports_type(&reader.metadata().database_type) {
        return Err(UpdateError::WrongType);
    }
    reader.verify().map_err(UpdateError::InvalidDatabase)?;
    staging
        .persist(target)
        .map_err(|error| UpdateError::Filesystem(error.error))?;
    Ok(DownloadMetadata {
        mirror_index,
        compressed_bytes: decoded.compressed_bytes,
        database_bytes: decoded.database_bytes,
        sha256: decoded.sha256,
    })
}

struct ProgressWriter<W, F> {
    inner: W,
    decoded_bytes: u64,
    notify: F,
}

impl<W: io::Write, F: FnMut(u64)> io::Write for ProgressWriter<W, F> {
    fn write(&mut self, buffer: &[u8]) -> io::Result<usize> {
        let count = self.inner.write(buffer)?;
        self.decoded_bytes += count as u64;
        (self.notify)(self.decoded_bytes);
        Ok(count)
    }

    fn flush(&mut self) -> io::Result<()> {
        self.inner.flush()
    }
}

struct DecodedArchive {
    compressed_bytes: u64,
    database_bytes: u64,
    sha256: [u8; 32],
}

fn decode(
    input: impl Read,
    output: &mut impl io::Write,
    max_compressed: u64,
    max_database: u64,
) -> Result<DecodedArchive, UpdateError> {
    let mut digest = DownloadDigest {
        input: input.take(max_compressed + 1),
        hash: Sha256::new(),
        bytes: 0,
    };
    let mut decoder = XzReader::new_mem_limit(&mut digest, true, DECODER_MEMORY_KIB);
    let written = io::copy(&mut decoder.by_ref().take(max_database + 1), output);
    if digest.bytes > max_compressed {
        return Err(UpdateError::DownloadLimit);
    }
    let database_bytes = written.map_err(UpdateError::Decode)?;
    if database_bytes > max_database {
        return Err(UpdateError::DatabaseLimit);
    }
    Ok(DecodedArchive {
        compressed_bytes: digest.bytes,
        database_bytes,
        sha256: digest.hash.finalize().into(),
    })
}

struct DownloadDigest<R> {
    input: R,
    hash: Sha256,
    bytes: u64,
}

impl<R: Read> Read for DownloadDigest<R> {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        let read = self.input.read(buffer)?;
        self.hash.update(&buffer[..read]);
        self.bytes += read as u64;
        Ok(read)
    }
}

fn safe_url(url: &Url) -> bool {
    let Some(host) = url.host_str() else {
        return false;
    };
    let loopback = host == "localhost"
        || host
            .trim_matches(['[', ']'])
            .parse::<std::net::IpAddr>()
            .is_ok_and(|ip| ip.is_loopback());
    (url.scheme() == "https" || (url.scheme() == "http" && loopback))
        && url.username().is_empty()
        && url.password().is_none()
        && url.fragment().is_none()
}

fn valid_asset_name(asset: &str) -> bool {
    asset.len() <= 128
        && asset
            .as_bytes()
            .first()
            .is_some_and(u8::is_ascii_alphanumeric)
        && asset.bytes().all(|character| {
            character.is_ascii_alphanumeric() || matches!(character, b'-' | b'_' | b'.')
        })
}

fn http_error(error: reqwest::Error) -> UpdateError {
    if error.is_timeout() {
        UpdateError::TimedOut
    } else {
        UpdateError::Http(error.without_url())
    }
}

#[cfg(test)]
mod tests {
    use std::io::Write;

    use lzma_rust2::{XzOptions, XzWriter};

    use super::{ProgressWriter, UpdateError, decode};

    #[test]
    fn download_progress_counts_bytes_successfully_written() {
        struct Partial(Vec<u8>);
        impl Write for Partial {
            fn write(&mut self, data: &[u8]) -> std::io::Result<usize> {
                let count = data.len().min(2);
                self.0.extend_from_slice(&data[..count]);
                Ok(count)
            }

            fn flush(&mut self) -> std::io::Result<()> {
                Ok(())
            }
        }
        let mut reported = Vec::new();
        let mut output = ProgressWriter {
            inner: Partial(Vec::new()),
            decoded_bytes: 0,
            notify: |count| reported.push(count),
        };
        output.write_all(b"abcde").unwrap();
        assert_eq!(output.inner.0, b"abcde");
        assert_eq!(reported, [2, 4, 5]);
    }

    #[test]
    fn bounds_xz_and_decoded_size_and_rejects_corrupt_footer() {
        let data = vec![1u8; 1024];
        let mut writer = XzWriter::new(Vec::new(), XzOptions::with_preset(1)).unwrap();
        writer.write_all(&data).unwrap();
        let mut compressed = writer.finish().unwrap();
        let mut output = Vec::new();
        let limit = compressed.len() as u64;
        assert!(matches!(
            decode(compressed.as_slice(), &mut output, limit - 1, 1024),
            Err(UpdateError::DownloadLimit)
        ));
        output.clear();
        assert!(matches!(
            decode(compressed.as_slice(), &mut output, limit, 512),
            Err(UpdateError::DatabaseLimit)
        ));
        assert_eq!(output.len(), 513);
        compressed.pop();
        output.clear();
        assert!(matches!(
            decode(compressed.as_slice(), &mut output, limit, 1024),
            Err(UpdateError::Decode(_))
        ));
    }
}
