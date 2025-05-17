package cordelia.rpc;

import com.google.gson.annotations.SerializedName;
import lombok.RequiredArgsConstructor;

import java.util.List;

@ReqMethod("torrent-remove")
@RequiredArgsConstructor
public final class RqTorrentRemove implements RqArguments {

    private final List<Object> ids;
    @SerializedName("delete-local-data")
    private final boolean deleteLocalData;

    public RqTorrentRemove(List<Object> ids) {
        this(ids, false);
    }

}
