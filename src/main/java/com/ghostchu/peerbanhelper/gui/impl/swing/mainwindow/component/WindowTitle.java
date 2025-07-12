package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.SwingMainWindow;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.google.common.eventbus.Subscribe;

import javax.swing.*;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class WindowTitle {
    private final SwingMainWindow parent;

    public WindowTitle(SwingMainWindow parent) {
        this.parent = parent;
        parent.setTitle(tlUI(Lang.GUI_TITLE_LOADING, Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
        Main.getEventBus().register(this);
    }

    @Subscribe
    public void onPBHFullyStarted(PBHServerStartedEvent event) {
        CommonUtil.getScheduler().scheduleAtFixedRate(() -> {
            StringBuilder builder = new StringBuilder();
            builder.append(tlUI(Lang.GUI_TITLE_LOADED, "Swing UI²", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
            StringJoiner joiner = new StringJoiner("", " [", "]");
            joiner.setEmptyValue("");
            ExchangeMap.GUI_DISPLAY_FLAGS.forEach(flag -> joiner.add(flag.getContent()));
            SwingUtilities.invokeLater(() -> parent.setTitle(builder.append(joiner).toString()));
        }, 0L, 1L, TimeUnit.SECONDS);
    }
}
