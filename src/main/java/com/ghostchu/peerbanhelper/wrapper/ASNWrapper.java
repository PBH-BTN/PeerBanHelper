package com.ghostchu.peerbanhelper.wrapper;

import com.maxmind.geoip2.model.AsnResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ASNWrapper {
    private long asn;
    private String asOrganization;
    private String asNetwork;

    public ASNWrapper(@NotNull AsnResponse asnResponse) {
        this.asn = asnResponse.getAutonomousSystemNumber();
        this.asOrganization = asnResponse.getAutonomousSystemOrganization();
        this.asNetwork = asnResponse.getNetwork().toString();
    }
}
