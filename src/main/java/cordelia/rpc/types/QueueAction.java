package cordelia.rpc.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum QueueAction {

    TOP("queue-move-top"),
    UP("queue-move-up"),
    DOWN("queue-move-down"),
    BOTTOM("queue-move-bottom");

    @Getter
    private final String method;
}
