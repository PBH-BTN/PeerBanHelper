package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.pbhplus.bean.License;
import com.ghostchu.peerbanhelper.pbhplus.data.LicenseStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class LicenseKeyPairDTO {
    private String licenseId;
    private int version;
    private LicenseStatus status;
    private License data;
}
