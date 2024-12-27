package com.ghostchu.peerbanhelper.util.dns;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xbill.DNS.Record;
import org.xbill.DNS.*;
import oshi.SystemInfo;

import java.net.UnknownHostException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class DNSLookup implements Reloadable {
    private volatile ExtendedResolver resolver = new ExtendedResolver();

    public DNSLookup() {
        reloadConfig();
        Main.getReloadManager().register(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    private void reloadConfig() {
        // get system dns via oshi
        SystemInfo systemInfo = new SystemInfo();
        var dnsServers = systemInfo.getOperatingSystem().getNetworkParams().getDnsServers();
        List<String> dns = Main.getMainConfig().getStringList("resolvers.servers");
        if (Main.getMainConfig().getBoolean("resolvers.use-system",true)) {
            dns.addAll(Arrays.asList(dnsServers));
        }
        applyDnsServers(dns);
    }

    private void applyDnsServers(List<String> servers) {
        List<Resolver> resolvers = new ArrayList<>();
        for (String dns : servers) {
            if (dns.startsWith("http")) {
                resolvers.add(new DohResolver(dns));
                log.debug("Added DoH resolver: {}", dns);
            } else {
                try {
                    resolvers.add(new SimpleResolver(dns));
                    log.debug("Added resolver: {}", dns);
                } catch (UnknownHostException e) {
                    log.warn("Failed to add resolver: {}", dns, e);
                }
            }
        }
        var replace = new ExtendedResolver(resolvers.toArray(new Resolver[0]));
        replace.setLoadBalance(true);
        replace.setTimeout(Duration.of(3, ChronoUnit.SECONDS));
        resolver = replace;
    }

    public CompletableFuture<Optional<String>> ptr(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Lookup lookup = new Lookup(query, Type.PTR);
                lookup.setResolver(resolver);
                lookup.run();
                if (lookup.getResult() == Lookup.SUCCESSFUL) {
                    Record[] records = lookup.getAnswers();
                    for (Record record : records) {
                        if (record instanceof PTRRecord ptr) {
                            return Optional.of(ptr.getTarget().toString());
                        }
                    }
                    return Optional.empty();
                }
                return Optional.empty();
            } catch (TextParseException ignored) {
                return Optional.empty();
            }
        });
    }

}
