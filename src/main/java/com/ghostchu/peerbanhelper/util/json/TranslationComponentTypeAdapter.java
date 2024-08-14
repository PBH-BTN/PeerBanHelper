package com.ghostchu.peerbanhelper.util.json;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.google.gson.*;

import java.lang.reflect.Type;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class TranslationComponentTypeAdapter implements JsonSerializer<TranslationComponent> {
    public  static final TranslationComponentTypeAdapter INSTANCE= new TranslationComponentTypeAdapter();
    @Override
     public JsonElement serialize(TranslationComponent ts, Type t, JsonSerializationContext jsc) {
         return new JsonPrimitive(tlUI(ts));
     }
 }