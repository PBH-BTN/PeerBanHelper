package com.ghostchu.peerbanhelper.util.update;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Install4jUpdateConfigurationTest {
    @Test
    void launchersCannotRunPreviouslyScheduledInstallers() throws Exception {
        Document project = readProject();
        var xpath = XPathFactory.newInstance().newXPath();
        assertEquals("0", xpath.evaluate("count(/install4j/launchers/launcher[@checkUpdater='true'])", project));
        assertEquals("0", xpath.evaluate(
                "count(/install4j/installerGui/applications/application[@automaticLauncherIntegration='true'])", project));
    }

    @Test
    void installedVersionIsCheckedBeforePreviousInstallationIsRemoved() throws Exception {
        Document project = readProject();
        var xpath = XPathFactory.newInstance().newXPath();
        String guard = xpath.evaluate("//action[@id='270']/preceding-sibling::action[1]/@name", project);
        assertEquals("Reject stale managed update", guard);
        String script = xpath.evaluate("//action[@id='353']//property[@name='value']", project);
        assertTrue(script.contains("getApplicationInfoByDir(context.getInstallationDirectory())"));
        assertTrue(script.contains("isVersionGreaterThan(context.getVersion(), installed.getVersion())"));
    }

    private Document readProject() throws Exception {
        return DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder()
                .parse(Path.of("install4j", "project.install4j").toFile());
    }
}
