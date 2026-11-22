//! Serialize configuration edits across HTTP handlers and leave a complete YAML file on failure.

use std::fmt::{self, Display};
use std::fs::{self, OpenOptions};
use std::io;
use std::path::Path;

use serde_yaml_ng::Mapping;

#[derive(Debug)]
pub enum EditError<E> {
    Io(io::Error),
    InvalidYaml(serde_yaml_ng::Error),
    Rejected(E),
}

impl<E: Display> Display for EditError<E> {
    fn fmt(&self, output: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Io(error) => write!(output, "Could not update configuration file: {error}"),
            Self::InvalidYaml(error) => write!(
                output,
                "Could not parse or write configuration YAML at {:?}",
                error.location()
            ),
            Self::Rejected(error) => error.fmt(output),
        }
    }
}

impl<E: std::error::Error + 'static> std::error::Error for EditError<E> {}

/// Apply one edit while holding the configuration lock. Call on a blocking thread.
pub fn edit<T, E>(
    path: &Path,
    change: impl FnOnce(&mut Mapping) -> Result<T, E>,
) -> Result<T, EditError<E>> {
    let directory = path.parent().ok_or_else(|| {
        EditError::Io(io::Error::new(
            io::ErrorKind::InvalidInput,
            "configuration requires a parent directory",
        ))
    })?;
    let lock = OpenOptions::new()
        .create(true)
        .truncate(false)
        .read(true)
        .write(true)
        .open(path.with_extension("yml.lock"))
        .map_err(EditError::Io)?;
    lock.lock().map_err(EditError::Io)?;
    let contents = fs::read_to_string(path).map_err(EditError::Io)?;
    let mut yaml = serde_yaml_ng::from_str(&contents).map_err(EditError::InvalidYaml)?;
    let result = change(&mut yaml).map_err(EditError::Rejected)?;

    let permissions = fs::metadata(path).map_err(EditError::Io)?.permissions();
    let mut draft = tempfile::NamedTempFile::new_in(directory).map_err(EditError::Io)?;
    serde_yaml_ng::to_writer(&mut draft, &yaml).map_err(EditError::InvalidYaml)?;
    draft
        .as_file()
        .set_permissions(permissions)
        .map_err(EditError::Io)?;
    draft.as_file().sync_all().map_err(EditError::Io)?;
    draft
        .persist(path)
        .map_err(|error| EditError::Io(error.error))?;
    Ok(result)
}
