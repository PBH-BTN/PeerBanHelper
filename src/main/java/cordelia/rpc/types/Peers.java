package cordelia.rpc.types;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public final class Peers {

    private String address;
    private String clientName;
    private Boolean clientIsChocked;
    private Boolean clientIsInterested;
    private String flagStr;
    private Boolean isDownloadingFrom;
    private Boolean isEncrypted;
    private Boolean isIncoming;
    private Boolean isUploadingTo;
    private Boolean isUTP;
    private Boolean peerIsChocked;
    private Boolean peerIsInterested;
    private Integer port;
    private Double progress;
    private Long rateToClient;
    private Long rateToPeer;

}
