package cordelia.rpc;

import cordelia.rpc.types.Torrents;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public final class RsTorrentGet implements RsArguments {

    private List<Torrents> torrents;

}
