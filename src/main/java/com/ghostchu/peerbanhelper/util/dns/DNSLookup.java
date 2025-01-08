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

    /**
     * Constructs a new DNSLookup instance and initializes its DNS configuration.
     *
     * This constructor performs two key actions:
     * 1. Calls {@code reloadConfig()} to set up the DNS resolver configuration
     * 2. Registers the current instance with the application's reload manager
     *
     * @see Main#getReloadManager()
     */
    public DNSLookup() {
        reloadConfig();
        Main.getReloadManager().register(this);
    }

    /**
     * Reloads the DNS configuration and invokes the default reload mechanism.
     *
     * @return The result of the default reload process
     * @throws Exception if an error occurs during the reload process
     */
    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    /**
     * Reloads the DNS configuration by retrieving system DNS servers and merging them with user-defined servers.
     *
     * This method performs the following steps:
     * 1. Retrieves system DNS servers using the OSHI library
     * 2. Fetches user-defined DNS servers from the main configuration
     * 3. Optionally adds system DNS servers based on configuration setting
     * 4. Applies the combined list of DNS servers using {@link #applyDnsServers(List)}
     *
     * @see SystemInfo
     * @see Main#getMainConfig()
     * @see #applyDnsServers(List)
     */
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

    /**
     * Applies a list of DNS servers to the resolver, supporting both standard DNS and DNS over HTTPS (DoH).
     *
     * This method creates a list of resolvers from the provided DNS server addresses. For each address:
     * - If the address starts with "http", a DNS over HTTPS (DoH) resolver is created
     * - Otherwise, a standard SimpleResolver is attempted to be created
     *
     * @param servers A list of DNS server addresses, which can be standard IP addresses or DoH URLs
     * @throws UnknownHostException If a standard DNS resolver cannot be created for a given address
     */
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

    /**
     * Performs an asynchronous DNS PTR (Pointer) record lookup for a given query.
     *
     * This method retrieves the hostname associated with an IP address using a DNS PTR record lookup.
     * The lookup is executed asynchronously using a {@code CompletableFuture} to prevent blocking the main thread.
     *
     * @param query The IP address or hostname to perform the PTR record lookup for
     * @return A {@code CompletableFuture} containing an {@code Optional} with the resolved hostname,
     *         or an empty {@code Optional} if no PTR record is found or an error occurs
     *
     * @see Lookup
     * @see PTRRecord
     */
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
