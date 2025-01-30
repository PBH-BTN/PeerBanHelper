package com.ghostchu.peerbanhelper.util.json;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class TranslationComponentTypeAdapter extends TypeAdapter<TranslationComponent> {
    public static final TranslationComponentTypeAdapter INSTANCE = new TranslationComponentTypeAdapter();

    @Override
    public void write(JsonWriter out, TranslationComponent value) throws IOException {
        out.beginObject();
        out.name("key").value(value.getKey());
        out.name("params");
        out.beginArray();  // 开始 JSON 数组
        // 遍历 params 数组并写入元素
        for (Object param : value.getParams()) {
            if (param instanceof TranslationComponent) {
                // 如果是 TranslationComponent，递归调用 write 方法
                TranslationComponentTypeAdapter.INSTANCE.write(out, (TranslationComponent) param);
            } else {
                out.value(param.toString());
            }
        }
        out.endArray();  // 结束 JSON 数组
        out.endObject();  // 结束 JSON 对象

        // 特别注意嵌套里面不允许出现除了 TranslationComponent 以外的 JsonObject，否则必翻车
    }

    @Override
    public TranslationComponent read(JsonReader in) throws IOException {
        String key = null;
        List<Object> params = new ArrayList<>();

        in.beginObject();  // 开始读取 JSON 对象

        while (in.hasNext()) {  // 循环读取每个字段
            String name = in.nextName();
            if ("key".equals(name)) {
                key = in.nextString();  // 读取 key 字段
            } else if ("params".equals(name)) {
                in.beginArray();  // 开始读取 JSON 数组
                while (in.hasNext()) {
                    // 判断当前元素是字符串还是 TranslationComponent
                    if (in.peek() == JsonToken.STRING) {
                        params.add(in.nextString());  // 如果是字符串，直接添加到 params 列表
                    } else if (in.peek() == JsonToken.BEGIN_OBJECT) {
                        // 如果是对象，则递归读取 TranslationComponent
                        params.add(TranslationComponentTypeAdapter.INSTANCE.read(in));
                    } else {
                        in.skipValue();  // 跳过不需要的值
                    }
                }
                in.endArray();  // 结束数组读取
            } else {
                in.skipValue();  // 跳过不需要的字段
            }
        }

        in.endObject();  // 结束 JSON 对象的读取

        // 构造并返回 TranslationComponent 对象
        return new TranslationComponent(key, params.toArray(new Object[0]));
    }
}