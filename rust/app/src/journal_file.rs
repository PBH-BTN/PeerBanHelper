//! Local log retention and rotation compatible with the distribution's log layout.

use std::fs::{self, File, OpenOptions};
use std::io::{self, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};

use flate2::{Compression, write::GzEncoder};
use time::{Date, OffsetDateTime, UtcOffset};

const MAX_BYTES: u64 = 20 * 1024 * 1024;
const RETENTION: Duration = Duration::from_secs(7 * 24 * 60 * 60);

pub(crate) struct LogFile {
    directory: PathBuf,
    active: File,
    length: u64,
    date: Date,
    _lock: File,
}

impl LogFile {
    pub(crate) fn open(directory: PathBuf) -> io::Result<Self> {
        fs::create_dir_all(&directory)?;
        let lock = OpenOptions::new()
            .create(true)
            .truncate(false)
            .write(true)
            .read(true)
            .open(directory.join("latest.log.lock"))?;
        lock.try_lock().map_err(io::Error::from)?;
        let mut active = OpenOptions::new()
            .create(true)
            .truncate(false)
            .write(true)
            .open(directory.join("latest.log"))?;
        active.seek(SeekFrom::End(0))?;
        let metadata = active.metadata()?;
        let date = metadata
            .modified()
            .map(OffsetDateTime::from)
            .unwrap_or_else(|_| OffsetDateTime::now_utc());
        cleanup(&directory);
        Ok(Self {
            directory,
            active,
            length: metadata.len(),
            date: local(date).date(),
            _lock: lock,
        })
    }

    pub(crate) fn write(
        &mut self,
        now: OffsetDateTime,
        thread: &str,
        level: &str,
        message: &str,
    ) -> io::Result<()> {
        let now = local(now);
        if self.length > 0 && (self.date != now.date() || self.length >= MAX_BYTES) {
            self.rotate()?;
        }
        self.date = now.date();
        let line = format!(
            "[{:02}:{:02}:{:02}] [{thread}/{level}]: {message}\n",
            now.hour(),
            now.minute(),
            now.second()
        );
        self.active.write_all(line.as_bytes())?;
        self.length = self.length.saturating_add(line.len() as u64);
        Ok(())
    }

    fn rotate(&mut self) -> io::Result<()> {
        let path = self.directory.join("latest.log");
        let mut source = File::open(path)?;
        let mut archive = tempfile::NamedTempFile::new_in(&self.directory)?;
        let mut gzip = GzEncoder::new(&mut archive, Compression::fast());
        io::copy(&mut source, &mut gzip)?;
        gzip.finish()?;
        archive.as_file().sync_all()?;
        let index = (0_u64..)
            .find(|index| !self.archive_path(*index).exists())
            .ok_or_else(|| io::Error::other("no log archive number available"))?;
        archive
            .persist_noclobber(self.archive_path(index))
            .map_err(|error| error.error)?;
        self.active.set_len(0)?;
        self.active.seek(SeekFrom::Start(0))?;
        self.length = 0;
        Ok(())
    }

    fn archive_path(&self, index: u64) -> PathBuf {
        self.directory.join(format!("{}-{index}.log.gz", self.date))
    }
}

fn local(time: OffsetDateTime) -> OffsetDateTime {
    time.to_offset(UtcOffset::local_offset_at(time).unwrap_or(UtcOffset::UTC))
}

fn cleanup(directory: &Path) {
    let age = SystemTime::now()
        .checked_sub(RETENTION)
        .unwrap_or(SystemTime::UNIX_EPOCH);
    let Ok(entries) = directory.read_dir() else {
        return;
    };
    let mut archives = entries
        .flatten()
        .filter_map(|entry| {
            let kind = entry.file_type().ok()?;
            let name = entry.file_name();
            let name = name.to_str()?;
            if !kind.is_file() || !(name.ends_with(".log.gz") || name.ends_with(".tmp")) {
                return None;
            }
            let time = entry.metadata().ok()?.modified().ok()?;
            Some((time, entry.path()))
        })
        .collect::<Vec<_>>();
    archives.sort_unstable_by_key(|(date, _)| std::cmp::Reverse(*date));
    for (date, archive) in archives.into_iter().skip(5) {
        if date < age {
            let _ = fs::remove_file(archive);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use flate2::read::GzDecoder;
    use std::io::Read;

    #[test]
    fn rotates_before_appending_when_the_active_file_reaches_its_limit() {
        let temporary = tempfile::tempdir().unwrap();
        let mut log = LogFile::open(temporary.path().to_owned()).unwrap();
        assert!(LogFile::open(temporary.path().to_owned()).is_err());
        let now = OffsetDateTime::now_utc();
        log.write(now, "fixture", "INFO", "First").unwrap();
        log.length = MAX_BYTES;
        let archive = log.archive_path(0);
        log.write(now, "fixture", "WARN", "Second").unwrap();
        let mut history = String::new();
        GzDecoder::new(File::open(archive).unwrap())
            .read_to_string(&mut history)
            .unwrap();
        assert!(history.ends_with("[fixture/INFO]: First\n"));
        let current = fs::read_to_string(temporary.path().join("latest.log")).unwrap();
        assert!(current.ends_with("[fixture/WARN]: Second\n"));
    }
}
