package com.xidao.poker.web.protocol;

import com.xidao.poker.PokerApplication;

/** 发布前后端共享的兼容性标识；协议变化时必须递增协议版本。 */
public final class ProtocolCompatibility {
    public static final int CURRENT_PROTOCOL_VERSION = 1;
    public static final String DEVELOPMENT_BUILD = "dev";

    private static final String CURRENT_BUILD_VERSION = resolveBuildVersion();

    private ProtocolCompatibility() {
    }

    public static String currentBuildVersion() {
        return CURRENT_BUILD_VERSION;
    }

    public static boolean protocolMatches(int clientProtocolVersion) {
        return clientProtocolVersion == CURRENT_PROTOCOL_VERSION;
    }

    public static boolean buildMatches(String clientBuildVersion) {
        if (clientBuildVersion == null || clientBuildVersion.isBlank()) return false;
        return DEVELOPMENT_BUILD.equals(CURRENT_BUILD_VERSION)
                || DEVELOPMENT_BUILD.equals(clientBuildVersion)
                || CURRENT_BUILD_VERSION.equals(clientBuildVersion);
    }

    private static String resolveBuildVersion() {
        String implementationVersion = PokerApplication.class.getPackage().getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? DEVELOPMENT_BUILD
                : implementationVersion;
    }
}
