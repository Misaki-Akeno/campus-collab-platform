package com.campus.im.websocket;

import com.campus.im.config.ImNodeConfig;
import com.campus.im.websocket.dto.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsServer extends TextWebSocketHandler {

    private final WsSessionManager sessionManager;
    private final WsMessageDispatcher dispatcher;
    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            closeWithError(session, "UNAUTHORIZED");
            return;
        }

        sessionManager.kickExisting(userId);
        sessionManager.register(userId, session);
        redisson.getBucket("im:online:" + userId).set(ImNodeConfig.getNodeId());

        log.info("[WS] Connected: userId={}, sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = sessionManager.getUserId(session);
        if (userId == null) {
            return;
        }
        try {
            WsMessage msg = objectMapper.readValue(message.getPayload(), WsMessage.class);
            dispatcher.dispatch(userId, msg);
        } catch (Exception e) {
            log.warn("[WS] 消息解析失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = sessionManager.getUserId(session);
        if (userId != null) {
            sessionManager.unregister(userId);
            redisson.getBucket("im:online:" + userId).delete();
            log.info("[WS] Disconnected: userId={}, status={}", userId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[WS] Transport error: sessionId={}", session.getId(), exception);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void closeWithError(WebSocketSession session, String reason) {
        try {
            session.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), reason));
        } catch (Exception e) {
            log.warn("[WS] Close with error failed: {}", reason);
        }
    }
}
