package com.ghostchu.peerbanhelper.databasent.p6spy;

import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.spy.P6Factory;
import com.p6spy.engine.spy.P6LoadableOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;
import org.springframework.stereotype.Component;

@Component
public class PeerBanHelperP6SpyFactory implements P6Factory {
    @Override
    public JdbcEventListener getJdbcEventListener() {
        return new SlowQueryListener();
    }

    @Override
    public P6LoadableOptions getOptions(P6OptionsRepository optionsRepository) {
        return null;
    }
}
