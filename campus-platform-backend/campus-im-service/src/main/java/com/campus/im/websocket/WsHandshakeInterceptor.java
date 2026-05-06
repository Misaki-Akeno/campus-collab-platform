package com.campus.im.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器。
 * 将 Gateway 注入的 X-User-Id Header 写入 userProperties，
 * 供 WsServer.onOpen 读取，避免 @ServerEndpoint 无法访问 HTTP Header 的问题。
 */
@Slf4j
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String userId = servletRequest.getServletRequest().getHeader("X-User-Id");
            if (StringUtils.hasText(userId)) {
                try {
                    attributes.put("userId", Long.parseLong(userId));
                    return true;
                } catch (NumberFormatException e) {
                    log.warn("[WS] X-User-Id 格式错误: {}", userId);
                    return false;
                }
            }
        }
        log.warn("[WS] 握手请求缺少 X-User-Id，拒绝连接");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
