package cordelia.rpc.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QueueAction {

    TOP("queue-move-top"),
    UP("queue-move-up"),
    DOWN("queue-move-down"),
    BOTTOM("queue-move-bottom");

    private final String method;
}
