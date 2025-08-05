package com.ghostchu.peerbanhelper.web.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.event.Level;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class StdWsAlertDTO {
    private Level level;
    private String message;
}
