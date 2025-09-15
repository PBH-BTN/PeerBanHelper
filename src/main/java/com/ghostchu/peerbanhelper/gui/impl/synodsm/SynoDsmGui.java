package com.ghostchu.peerbanhelper.gui.impl.synodsm;

import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

import java.util.HashMap;
import java.util.Map;
@Slf4j
public class SynoDsmGui extends ConsoleGuiImpl {
    public SynoDsmGui(String[] args) {
        super(args);
    }
    @Override
    public void createNotification(Level level, String title, String description) {
        String mailKey = switch (level){
            case WARN ->  "general_warn";
            case ERROR ->  "general_error";
            default -> "general_info";
        };
        Map<String, Object> map = new HashMap<>();
        map.put("DESKTOP", title);
        map.put("SUBJECT", title);
        map.put("DESCRIPTION", description);
        map.put("DESKTOP_NOTIFY_CLASSNAME", "SYNO.SDS.peerbanhelper.Application");
        try {
            var process = Runtime.getRuntime().exec(new String[]{"/usr/syno/bin/synonotify", mailKey, JsonUtil.tiny().toJson(map)}, null, null);
            long pid = process.pid();
            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            int exitCode = process.waitFor();
            log.info("synonotify exited with code {}, pid {}, stdout: {}, stderr: {}", exitCode, pid, stdout, stderr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
