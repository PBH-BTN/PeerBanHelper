package com.ghostchu.peerbanhelper.config.section;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.config.BaseConfigSection;
import com.ghostchu.peerbanhelper.config.ConfigPair;
import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class ClientConfigSection extends BaseConfigSection {

    private Map<String, ClientItem> clients = new HashMap<>();

    public ClientConfigSection(ConfigPair configPair) {
        super(configPair, "client");
    }

    @Override
    public void load() {
        ConfigurationSection section = getConfigSection();

        for (String clientName : section.getKeys(false)) {
            ConfigurationSection clientSection = section.getConfigurationSection(clientName);
            String type = clientSection.getString("type");
            String endpoint = clientSection.getString("endpoint");
            String username = clientSection.getString("username");
            String password = clientSection.getString("password");
            String baUser = clientSection.getString("basic-auth.user");
            String baPass = clientSection.getString("basic-auth.pass");
            boolean verifySSL = clientSection.getBoolean("verify-ssl");
            HttpClient.Version httpVersionEnum = getVersionByStr(clientSection.getString("http-version"));

            clients.put(
                    clientName,
                    new ClientItem(
                            type,
                            endpoint,
                            username,
                            password,
                            baUser == null ? ClientItemBasicAuth.empty() : ClientItemBasicAuth.of(baUser, baPass),
                            verifySSL,
                            httpVersionEnum
                    )
            );
        }
    }

    @Override
    public void save() {
        ConfigurationSection section = getConfigSection();
        clients.forEach((name, client) -> {
            ConfigurationSection clientSection = section.createSection(name);
            clientSection.set("endpoint", client.endpoint);
            clientSection.set("username", client.username);
            clientSection.set("password", client.password);
            clientSection.set("basic-auth.user", client.basicAuth.user);
            clientSection.set("basic-auth.pass", client.basicAuth.pass);
            clientSection.set("verify-ssl", client.verifySSL);
            clientSection.set("http-version", client.httpVersion.toString());
        });
        super.callSave();
    }

    @Override
    public void reload() {
        super.reload();
        Main.getServer().getDownloaderManager().reloadDownloaders();
    }

    public record ClientItem(
            String type,
            String endpoint,
            String username,
            String password,
            ClientItemBasicAuth basicAuth,
            boolean verifySSL,
            HttpClient.Version httpVersion
    ) {
    }

    public record ClientItemBasicAuth(
            String user,
            String pass
    ) {
        public static ClientItemBasicAuth empty() {
            return new ClientItemBasicAuth("", "");
        }

        public static ClientItemBasicAuth of(String user, String pass) {
            return new ClientItemBasicAuth(user, pass);
        }
    }

    private static HttpClient.Version getVersionByStr(String ver) {
        try {
            return HttpClient.Version.valueOf(ver);
        } catch (IllegalArgumentException e) {
            return HttpClient.Version.HTTP_1_1;
        }
    }

}
