package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.StringType;

public final class TranslationComponentPersistener extends StringType {

    private static final TranslationComponentPersistener INSTANCE = new TranslationComponentPersistener();

    private TranslationComponentPersistener() {
        super(SqlType.STRING, new Class<?>[]{TranslationComponent.class});
    }

    public static TranslationComponentPersistener getSingleton() {
        return INSTANCE;
    }

    @Override
    public Object javaToSqlArg(FieldType fieldType, Object javaObject) {
        TranslationComponent myFieldClass = (TranslationComponent) javaObject;
        return myFieldClass != null ? getJsonFromMyFieldClass(myFieldClass) : null;
    }

    @Override
    public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos) {
        return sqlArg != null ? getMyFieldClassFromJson((String) sqlArg) : null;
    }

    private String getJsonFromMyFieldClass(TranslationComponent myFieldClass) {
        return JsonUtil.standard().toJson(myFieldClass);
    }

    private TranslationComponent getMyFieldClassFromJson(String json) {
        return JsonUtil.standard().fromJson(json, TranslationComponent.class);
    }
}
