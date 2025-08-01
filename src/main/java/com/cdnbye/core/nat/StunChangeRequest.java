package com.cdnbye.core.nat;

public class StunChangeRequest {

    public boolean isChangeIp() {
        return changeIp;
    }

    public boolean isChangePort() {
        return changePort;
    }

    private boolean changeIp;

    public void setChangeIp(boolean changeIp) {
        this.changeIp = changeIp;
    }

    public void setChangePort(boolean changePort) {
        this.changePort = changePort;
    }

    private boolean changePort;


    /// <summary>
    /// Default constructor.
    /// </summary>
    /// <param name="changeIp">Specifies if STUN server must send response to different IP than request was received.</param>
    /// <param name="changePort">Specifies if STUN server must send response to different port than request was received.</param>
    public StunChangeRequest(boolean changeIp, boolean changePort) {
        this.changeIp = changeIp;
        this.changePort = changePort;
    }


}
