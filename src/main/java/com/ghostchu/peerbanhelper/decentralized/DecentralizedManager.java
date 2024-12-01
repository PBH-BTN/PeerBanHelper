package com.ghostchu.peerbanhelper.decentralized;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.lab.Experiments;
import com.ghostchu.peerbanhelper.lab.Laboratory;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import io.ipfs.api.IPFS;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class DecentralizedManager implements AutoCloseable, Reloadable {
    private final Laboratory laboratory;
    @Getter
    @Nullable
    private IPFS ipfs;

    public DecentralizedManager(Laboratory laboratory) {
        this.laboratory = laboratory;
        startupIPFS();
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        startupIPFS();
        return Reloadable.super.reloadModule();
    }

    private void startupIPFS() {
        if (Main.getMainConfig().getBoolean("decentralized.enabled", false)
            || !laboratory.isExperimentActivated(Experiments.IPFS.getExperiment())) {
            this.ipfs = null;
            return;
        }
        try {
            var ipfsRpc = Main.getMainConfig().getString("decentralized.kubo-rpc", "/ip4/127.0.0.1/tcp/5001");
            if (System.getProperty("pbh.kuboRPC") != null) {
                ipfsRpc = System.getProperty("pbh.kuboRPC");
            }
            if(System.getenv("PBH_KUBO_RPC") != null) {
                ipfsRpc = System.getenv("PBH_KUBO_RPC");
            }
            this.ipfs = new IPFS(ipfsRpc);
            log.info(tlUI(Lang.IPFS_INIT_WELCOME, ipfs.version()));
        } catch (Exception e) {
            log.error(tlUI(Lang.IPFS_INIT_FAILED), e);
        }
    }

    @Override
    public void close() throws Exception {

    }
}
