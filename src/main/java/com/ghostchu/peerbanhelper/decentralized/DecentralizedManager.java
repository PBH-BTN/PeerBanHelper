package com.ghostchu.peerbanhelper.decentralized;

import com.ghostchu.peerbanhelper.text.Lang;
import io.ipfs.api.IPFS;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class DecentralizedManager implements AutoCloseable{
    @Getter
    @Nullable
    private IPFS ipfs;
    public DecentralizedManager() {
        startupIPFS();
    }

    private void startupIPFS() {
        try {
            this.ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
            log.info(tlUI(Lang.IPFS_INIT_WELCOME, ipfs.version()));
        }catch (Exception e){
            log.error(tlUI(Lang.IPFS_INIT_FAILED), e);
        }
    }

    @Override
    public void close() throws Exception {

    }
}
