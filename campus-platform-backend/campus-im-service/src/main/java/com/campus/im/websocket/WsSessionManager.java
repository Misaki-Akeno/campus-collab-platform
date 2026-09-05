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

    private static final String KICK_CONTROL_PREFIX = "__KICK__:";

    private final ConcurrentHashMap<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionToUser = new ConcurrentHashMap<>();

    /**
     * 原子替换用户当前连接。旧连接的反向索引会保留到其 close 回调执行，
     * 使回调能够识别用户，但不会删除已经替换的新连接。
     */
    public WebSocketSession register(Long userId, WebSocketSession session) {
        WebSocketSession previous = userSessions.put(userId, session);
        sessionToUser.put(session.getId(), userId);
        log.debug("[WS] Registered: userId={}, sessionId={}", userId, session.getId());
        return previous;
    }

    /** 仅当关闭的 session 仍是该用户当前连接时才移除正向映射。 */
    public boolean unregister(WebSocketSession session) {
        Long userId = sessionToUser.remove(session.getId());
        boolean removed = userId != null && userSessions.remove(userId, session);
        if (removed) {
            log.debug("[WS] Unregistered current session: userId={}, sessionId={}",
                    userId, session.getId());
        }
        return removed;
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
    public void kick(WebSocketSession old) {
        if (old != null && old.isOpen()) {
            try {
                synchronized (old) {
                    if (old.isOpen()) {
                        old.sendMessage(new TextMessage(
                                "{\"cmd\":\"KICK_OFF\",\"payload\":{\"reason\":\"other_device_login\"}}"));
                        old.close();
                    }
                }
            } catch (Exception e) {
                log.warn("[WS] Kick failed: sessionId={}", old.getId(), e);
            }
        }
    }

    /** 处理节点间控制消息或普通用户推送。 */
    public void handleNodeMessage(String raw) {
        if (!raw.startsWith(KICK_CONTROL_PREFIX)) {
            broadcastRaw(raw);
            return;
        }
        String[] fields = raw.substring(KICK_CONTROL_PREFIX.length()).split(":", 2);
        if (fields.length != 2) {
            log.warn("[WS] kick control format error");
            return;
        }
        try {
            Long userId = Long.parseLong(fields[0]);
            WebSocketSession current = userSessions.get(userId);
            if (current != null && current.getId().equals(fields[1])) {
                kick(current);
            }
        } catch (NumberFormatException e) {
            log.warn("[WS] kick control userId parse failed");
        }
    }

    public static String buildKickControl(Long userId, String sessionId) {
        return KICK_CONTROL_PREFIX + userId + ':' + sessionId;
    }
}
