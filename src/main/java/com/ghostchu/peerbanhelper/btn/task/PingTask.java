package com.ghostchu.peerbanhelper.btn.task;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.btn.ping.*;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

public class PingTask extends TimerTask {
    private final PeerBanHelperServer server;

    public PingTask(PeerBanHelperServer server) {
        this.server = server;
    }

    @Override
    public void run() {
        try {
            List<ClientPing> clientPings = new ArrayList<>();
            List<PeerConnection> peerConnections = new ArrayList<>();
            for (Downloader downloader : server.getDownloaders()) {
                downloader.login();
                for (Torrent torrent : downloader.getTorrents()) {
                    TorrentInfo torrentInfo = new TorrentInfo();
                    torrentInfo.setIdentifier(torrent.getHash());
                    torrentInfo.setSize(torrent.getSize());
                    for (Peer peer : downloader.getPeers(torrent)) {
                        PeerInfo peerInfo = new PeerInfo();
                        peerInfo.setAddress(new PeerAddress(peer.getAddress().getIp(), peer.getAddress().getPort()));
                        peerInfo.setClientName(peer.getClientName());
                        peerInfo.setPeerId(peer.getPeerId());
                        peerInfo.setFlag("N/A");
                        peerInfo.setProgress(peer.getProgress());
                        peerInfo.setDownloaded(peer.getDownloaded());
                        peerInfo.setRtDownloadSpeed(peer.getDownloadSpeed());
                        peerInfo.setUploaded(peer.getUploaded());
                        peerInfo.setRtUploadSpeed(peer.getUploadedSpeed());
                        peerConnections.add(new PeerConnection(torrentInfo, peerInfo));
                    }

                }
                ClientPing ping = new ClientPing();
                ping.setAppId("1");
                ping.setAppSecret("2");
                ping.setPopulateAt(System.currentTimeMillis());
                ping.setDownloader(downloader.getDownloaderName());
                ping.setPeers(peerConnections);
                clientPings.add(ping);
            }
            submitPings(clientPings);
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private void submitPings(List<ClientPing> clientPings) {
        Gson gson = new Gson();
        clientPings.forEach(ping->{
            HttpClient client = HttpClient
                    .newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.of(30, ChronoUnit.SECONDS))
                    .build();
            try {
                HttpResponse<Void> resp = client.send(HttpRequest.newBuilder(new URI("http://localhost:9988/ping/submit"))
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(ping)))
                        .header("User-Agent", Main.getUserAgent())
                                .header("Content-Type", "application/json")
                        .timeout(Duration.of(30, ChronoUnit.SECONDS))
                        .build(), HttpResponse.BodyHandlers.discarding());
                System.out.println(resp.statusCode());
            } catch (IOException | InterruptedException | URISyntaxException e) {
                e.printStackTrace();
            }

        });
    }
}
