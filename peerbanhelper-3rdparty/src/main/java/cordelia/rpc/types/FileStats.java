package cordelia.rpc.types;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public final class FileStats {

    private Long bytesCompleted;
    private Boolean wanted;
    private Integer priority;

}
