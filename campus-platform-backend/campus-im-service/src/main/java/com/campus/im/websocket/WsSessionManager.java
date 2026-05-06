package com.campus.im.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的 userId ↔ WebSocketSession 双向映射，提供本地节点连接管理。
 */
@Slf4j
@Component
public class WsSessionManager {

    private final ConcurrentHashMap<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionToUser = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        userSessions.put(userId, session);
        sessionToUser.put(session.getId(), userId);
        log.debug("[WS] Registered: userId={}, sessionId={}", userId, session.getId());
    }

    public void unregister(Long userId) {
        WebSocketSession session = userSessions.remove(userId);
        if (session != null) {
            sessionToUser.remove(session.getId());
            log.debug("[WS] Unregistered: userId={}", userId);
        }
    }

    public Long getUserId(WebSocketSession session) {
        return sessionToUser.get(session.getId());
    }

    public boolean isLocal(Long userId) {
        WebSocketSession s = userSessions.get(userId);
        return s != null && s.isOpen();
    }

    /**
     * 向本地在线用户推送 JSON 消息。
     * Spring WebSocketSession.sendMessage() 不是线程安全的，需加锁。
     */
    public boolean push(Long userId, String jsonMsg) {
        WebSocketSession session = userSessions.get(userId);
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMsg));
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("[WS] Push failed: userId={}", userId, e);
        }
        return false;
    }

    /**
     * 跨节点 Pub/Sub 消息格式：{targetUserId}:{msgJson}
     */
    public void broadcastRaw(String raw) {
        int idx = raw.indexOf(':');
        if (idx < 0) {
            log.warn("[WS] broadcastRaw format error");
            return;
        }
        try {
            Long targetUserId = Long.parseLong(raw.substring(0, idx));
            String msgJson = raw.substring(idx + 1);
            push(targetUserId, msgJson);
        } catch (NumberFormatException e) {
            log.warn("[WS] broadcastRaw parse userId failed");
        }
    }

    /**
     * 踢掉同用户的旧连接并发送 KICK_OFF 指令。
     */
    public void kickExisting(Long userId) {
        WebSocketSession old = userSessions.get(userId);
        if (old != null && old.isOpen()) {
            try {
                push(userId, "{\"cmd\":\"KICK_OFF\",\"payload\":{\"reason\":\"other_device_login\"}}");
                old.close();
            } catch (Exception e) {
                log.warn("[WS] Kick failed: userId={}", userId, e);
            }
        }
    }
}
