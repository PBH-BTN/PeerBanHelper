package example.pbh;

import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import org.pf4j.Extension;

@Extension
public final class DemoClientRule extends AbstractRuleFeatureModule {
    private static final String DEMO_CLIENT = "PeerBanHelper-Demo/1.0";

    @Override
    public String getName() {
        return "Demo client rule";
    }

    @Override
    public String getConfigName() {
        return "demo-client-rule";
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    @Override
    public CheckResult shouldBanPeer(Torrent torrent, Peer peer, Downloader downloader, PipelineTask<?> task) {
        if (!DEMO_CLIENT.equals(peer.getClientName())) return pass();
        return new CheckResult(getClass(), PeerAction.BAN, 60_000,
                new TranslationComponent("Demo client rule"), new TranslationComponent("Matched " + DEMO_CLIENT),
                StructuredData.create().add("client", DEMO_CLIENT));
    }
}
