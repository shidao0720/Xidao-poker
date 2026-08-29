package com.xidao.poker.web.lan;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/** 枚举全部可信私有 IPv4 候选，并将常见实体网卡排在虚拟/VPN 网卡之前。 */
public final class LanAddressDiscovery {
    private LanAddressDiscovery() {
    }

    public static List<LanAddressCandidate> discover() throws SocketException {
        List<LanAddressCandidate> candidates = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return List.of();

        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address
                        && address.isSiteLocalAddress()
                        && !address.isLoopbackAddress()) {
                    candidates.add(new LanAddressCandidate(
                            networkInterface.getName(),
                            networkInterface.getDisplayName(),
                            address.getHostAddress()
                    ));
                }
            }
        }
        return order(candidates);
    }

    static List<LanAddressCandidate> order(List<LanAddressCandidate> candidates) {
        return candidates.stream()
                .distinct()
                .sorted(Comparator
                        .comparingInt(LanAddressDiscovery::interfacePriority)
                        .thenComparing(LanAddressCandidate::interfaceName)
                        .thenComparing(LanAddressCandidate::address))
                .toList();
    }

    private static int interfacePriority(LanAddressCandidate candidate) {
        String value = (candidate.interfaceName() + " " + candidate.displayName())
                .toLowerCase(Locale.ROOT);
        if (containsAny(value, "wi-fi", "wifi", "wlan", "ethernet", "以太网", "无线")) return 0;
        if (containsAny(value, "docker", "vmware", "virtualbox", "hyper-v", "vpn", "tunnel", "vethernet")) {
            return 2;
        }
        return 1;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}
