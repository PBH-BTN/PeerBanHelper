package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class CheckResultTest {
    @Test
    void comparesActionBeforeDurationInEitherArrivalOrder() {
        var pass = result(PeerAction.NO_ACTION, 100);
        var disconnect = result(PeerAction.BAN_FOR_DISCONNECT, 50);
        var ban = result(PeerAction.BAN, 10);
        var longerBan = result(PeerAction.BAN, 20);
        var skip = result(PeerAction.SKIP, 0);
        var ordered = new CheckResult[]{pass, disconnect, ban, longerBan, skip};
        for (int i = 1; i < ordered.length; i++) {
            assertSame(ordered[i], CheckResult.preferred(ordered[i - 1], ordered[i]));
            assertSame(ordered[i], CheckResult.preferred(ordered[i], ordered[i - 1]));
        }
    }

    private CheckResult result(PeerAction action, long duration) {
        var message = new TranslationComponent("test");
        return new CheckResult(getClass(), action, duration, message, message, StructuredData.create());
    }
}
