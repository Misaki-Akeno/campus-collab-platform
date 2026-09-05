package com.campus.im.websocket;

/** Redis 在线路由值，携带 session 所有权，防止旧连接关闭时删除新路由。 */
public final class ImOnlineRoute {

    private static final String SEPARATOR = "|";

    private ImOnlineRoute() {}

    public static String encode(String nodeId, String sessionId) {
        return nodeId + SEPARATOR + sessionId;
    }

    public static String nodeId(String route) {
        int separator = route.lastIndexOf(SEPARATOR);
        return separator < 0 ? route : route.substring(0, separator);
    }

    public static String sessionId(String route) {
        int separator = route.lastIndexOf(SEPARATOR);
        return separator < 0 ? null : route.substring(separator + 1);
    }
}
