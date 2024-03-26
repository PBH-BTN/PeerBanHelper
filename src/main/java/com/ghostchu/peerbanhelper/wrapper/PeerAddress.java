package com.ghostchu.peerbanhelper.wrapper;

public class PeerAddress implements Comparable<PeerAddress> {

    private String ip;
    /**
     * 端口可能为 0 （代表未设置）
     */
    private int port;

    public PeerAddress(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public PeerAddress() {
    }

    @Override
    public int compareTo(PeerAddress o) {
        int r = ip.compareTo(o.ip);
        if (r == 0) {
            return Integer.compare(port, o.port);
        }
        return r;
    }

    public String getIp() {
        return this.ip;
    }

    public int getPort() {
        return this.port;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String toString() {
        return "PeerAddress(ip=" + this.getIp() + ", port=" + this.getPort() + ")";
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof PeerAddress)) return false;
        final PeerAddress other = (PeerAddress) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$ip = this.getIp();
        final Object other$ip = other.getIp();
        if (this$ip == null ? other$ip != null : !this$ip.equals(other$ip)) return false;
        if (this.getPort() != other.getPort()) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof PeerAddress;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $ip = this.getIp();
        result = result * PRIME + ($ip == null ? 43 : $ip.hashCode());
        result = result * PRIME + this.getPort();
        return result;
    }
}
