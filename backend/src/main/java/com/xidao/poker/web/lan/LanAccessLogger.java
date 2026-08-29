package com.xidao.poker.web.lan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.SocketException;
import java.util.List;

/** 服务启动后列出全部局域网候选地址，由主机按实际 Wi-Fi/网线选择。 */
@Component
public final class LanAccessLogger {
    private static final Logger log = LoggerFactory.getLogger(LanAccessLogger.class);

    private final ServletWebServerApplicationContext context;

    public LanAccessLogger(ServletWebServerApplicationContext context) {
        this.context = context;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announceAccessUrls() {
        int port = context.getWebServer().getPort();
        log.info("LAN_ACCESS_LOCAL url=http://localhost:{}", port);
        try {
            List<LanAddressCandidate> candidates = LanAddressDiscovery.discover();
            if (candidates.isEmpty()) {
                log.warn("LAN_ACCESS_NO_PRIVATE_IPV4 hint=check-network-and-firewall");
                return;
            }
            for (LanAddressCandidate candidate : candidates) {
                log.info("LAN_ACCESS_URL interface={} displayName={} url=http://{}:{}",
                        candidate.interfaceName(), candidate.displayName(), candidate.address(), port);
            }
            if (candidates.size() > 1) {
                log.info("LAN_ACCESS_MULTIPLE_INTERFACES count={} hint=choose-address-on-player-network",
                        candidates.size());
            }
        } catch (SocketException error) {
            log.warn("LAN_ACCESS_DISCOVERY_FAILED code={}", error.getClass().getSimpleName());
        }
    }
}
