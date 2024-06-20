package com.ghostchu.peerbanhelper.event;

import com.ghostchu.peerbanhelper.alert.Alert;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NewAlertCreated {
    private Alert alert;
}
