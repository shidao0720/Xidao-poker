package com.xidao.poker.web.lan;

/** 可供同一局域网设备访问的地址候选；不擅自把第一个网卡当成正确答案。 */
public record LanAddressCandidate(String interfaceName, String displayName, String address) {
    public LanAddressCandidate {
        if (interfaceName == null || interfaceName.isBlank()) {
            throw new IllegalArgumentException("interface name is required");
        }
        if (displayName == null || displayName.isBlank()) displayName = interfaceName;
        if (address == null || address.isBlank()) throw new IllegalArgumentException("address is required");
    }
}
