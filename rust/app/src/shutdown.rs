use std::io;
use std::sync::LazyLock;

use tokio::sync::watch;

static STOP: LazyLock<watch::Sender<bool>> = LazyLock::new(|| watch::Sender::new(false));

pub(crate) fn request() {
    STOP.send_replace(true);
}

pub(crate) async fn signal() -> io::Result<()> {
    let mut stop = STOP.subscribe();
    tokio::select! {
        result = platform_signal() => result,
        _ = stop.wait_for(|requested| *requested) => Ok(()),
    }
}

#[cfg(unix)]
async fn platform_signal() -> io::Result<()> {
    let mut terminate = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())?;
    tokio::select! {
        result = tokio::signal::ctrl_c() => result,
        _ = terminate.recv() => Ok(()),
    }
}

#[cfg(not(unix))]
async fn platform_signal() -> io::Result<()> {
    tokio::signal::ctrl_c().await
}
