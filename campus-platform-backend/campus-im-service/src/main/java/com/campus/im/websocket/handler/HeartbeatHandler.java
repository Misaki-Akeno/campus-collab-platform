package com.campus.im.websocket.handler;

import com.campus.im.websocket.WsSessionManager;
import com.campus.im.websocket.dto.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatHandler {

    private final WsSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public void handle(Long userId, WsMessage msg) {
        try {
            WsMessage pong = new WsMessage();
            pong.setCmd("HEARTBEAT");
            pong.setPayload(new Pong(System.currentTimeMillis()));
            sessionManager.push(userId, objectMapper.writeValueAsString(pong));
        } catch (Exception e) {
            log.warn("[WS] Heartbeat 响应失败: userId={}", userId, e);
        }
    }

    record Pong(long timestamp) {}
}
