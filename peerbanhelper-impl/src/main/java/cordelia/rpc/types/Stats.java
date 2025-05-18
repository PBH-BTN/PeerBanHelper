package cordelia.rpc.types;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public final class Stats {

    private Long uploadedBytes;
    private Long downloadedBytes;
    private Long filesAdded;
    private Long sessionCount;
    private Long secondsActive;

}
