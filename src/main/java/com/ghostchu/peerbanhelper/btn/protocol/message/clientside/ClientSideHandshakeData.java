package com.ghostchu.peerbanhelper.btn.protocol.message.clientside;

import com.ghostchu.peerbanhelper.btn.protocol.message.BtnMessageData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ClientSideHandshakeData extends BtnMessageData {
    private String client;
    private String version;
    private String userAgent;
    private String preferLanguage;
}
