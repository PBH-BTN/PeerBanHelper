package raccoonfink.deluge;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class DelugeEvent {

    public DelugeEvent(final JsonNode data) {
    }

    public JsonNode toJSON() {
        return new ObjectNode(null);
    }

}
