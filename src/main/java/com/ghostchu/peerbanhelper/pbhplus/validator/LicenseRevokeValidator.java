package com.ghostchu.peerbanhelper.pbhplus.validator;

import com.ghostchu.peerbanhelper.pbhplus.bean.License;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface LicenseRevokeValidator {
    Collection<License> checkRevoked(@NotNull Collection<License> license);
}
