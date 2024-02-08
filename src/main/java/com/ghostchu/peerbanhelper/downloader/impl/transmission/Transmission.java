package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;

import java.util.Collection;
import java.util.List;

public class Transmission implements Downloader {

    private final String name;
    private final UnirestInstance unirest;
    //private final String endpoint;
    public Transmission(String name, String endpoint, String username, String password){
        // TODO: 等待好心人实现
        this.name = name;
        this.unirest = Unirest.spawnInstance();
        this.unirest.config().setDefaultBasicAuth(username, password);
        //this.endpoint =
    }
    @Override
    public String getEndpoint() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public boolean login() {
        return false;
    }

    @Override
    public List<Torrent> getTorrents() {
        return null;
    }

    @Override
    public List<Peer> getPeers(String torrentId) {
        return null;
    }

    @Override
    public List<PeerAddress> getBanList() {
        return null;
    }

    @Override
    public void setBanList(Collection<PeerAddress> peerAddresses) {

    }

    @Override
    public void close() throws Exception {

    }
}
