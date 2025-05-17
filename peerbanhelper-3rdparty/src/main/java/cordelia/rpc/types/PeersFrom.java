package cordelia.rpc.types;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public final class PeersFrom {

    private Integer fromCache;
    private Integer fromDht;
    private Integer fromIncoming;
    private Integer fromLpd;
    private Integer fromLtep;
    private Integer fromPex;
    private Integer fromTracker;

}
