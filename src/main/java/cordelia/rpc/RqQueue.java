package cordelia.rpc;

import cordelia.client.TypedRequest;
import cordelia.rpc.types.QueueAction;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@ReqMethod
@RequiredArgsConstructor
public final class RqQueue implements RqArguments {

    private final QueueAction action;
    private final List<Object> ids;

    public RqQueue(QueueAction action) {
        this(action, null);
    }

    @Override
    public TypedRequest toReq(Long tag) {
        Map<String, Object> arguments = collectFields();
        arguments.remove("action");
        return new TypedRequest(tag, action.getMethod(), arguments);
    }
}
