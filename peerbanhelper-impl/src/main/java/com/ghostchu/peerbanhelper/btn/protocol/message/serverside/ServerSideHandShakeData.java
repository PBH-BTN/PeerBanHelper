package com.ghostchu.peerbanhelper.btn.protocol.message.serverside;

import com.ghostchu.peerbanhelper.btn.protocol.message.BtnMessageData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ServerSideHandShakeData extends BtnMessageData {
    private String instance;
    private String version;
    private String maintainer;
    private int protocolMin;
    private int protocolMax;
}
