package cordelia.rpc;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public final class RsTorrentRename implements RsArguments {

    private String path;
    private String name;
    private Integer id;

}
