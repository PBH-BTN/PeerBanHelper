package cordelia.rpc;

import com.google.gson.annotations.SerializedName;
import cordelia.client.TypedRequest;
import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public interface RqArguments {

    default Class<? extends RsArguments> answerClass() {
        ReqMethod reqAnn = this.getClass().getAnnotation(ReqMethod.class);
        if (reqAnn == null || reqAnn.answer() == null)
            throw new IllegalArgumentException("no response class provided");
        return reqAnn.answer();
    }

    default TypedRequest toReq(Long tag) {
        ReqMethod reqAnn = this.getClass().getAnnotation(ReqMethod.class);
        if (reqAnn == null || reqAnn.value() == null || reqAnn.value().isEmpty())
            throw new IllegalArgumentException("no method provided");
        return new TypedRequest(tag, reqAnn.value(), collectFields());
    }

    @SneakyThrows
    default Map<String, Object> collectFields() {
        Map<String, Object> arguments = new HashMap<>();
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                field.trySetAccessible();
                Object value = field.get(this);
                if (value != null) {
                    SerializedName jp = field.getAnnotation(SerializedName.class);
                    String fieldName = jp == null ? field.getName() : jp.value();
                    arguments.put(fieldName, value);
                }
            }
        }
        return arguments;
    }

}
