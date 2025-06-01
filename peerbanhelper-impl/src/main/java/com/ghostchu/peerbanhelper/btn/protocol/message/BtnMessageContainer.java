package com.ghostchu.peerbanhelper.btn.protocol.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ghostchu.peerbanhelper.btn.protocol.message.clientside.ClientSideHandshakeData;
import com.ghostchu.peerbanhelper.btn.protocol.message.serverside.ServerSideHandShakeData;
import com.ghostchu.peerbanhelper.btn.protocol.message.serverside.ServerSideMessageData;

public class BtnMessageContainer {
    private String type;

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ClientSideHandshakeData.class, name = "CLIENTSIDE_HANDSHAKE"),
            @JsonSubTypes.Type(value = ServerSideHandShakeData.class, name = "SERVERSIDE_HANDSHAKE"),
            @JsonSubTypes.Type(value = ServerSideMessageData.class, name = "SERVERSIDE_MESSAGE")
    })
    private BtnMessageData data;
}
