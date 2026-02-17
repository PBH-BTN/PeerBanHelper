package com.cdnbye.core.nat;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class StunChangeRequest {

    private boolean changeIp;

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
