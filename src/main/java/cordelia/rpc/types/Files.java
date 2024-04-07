package cordelia.rpc.types;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public final class Files {

    private Long bytesCompleted;
    private Long length;
    private String name;
    private Integer beginPiece;
    private Integer endPiece;

}
