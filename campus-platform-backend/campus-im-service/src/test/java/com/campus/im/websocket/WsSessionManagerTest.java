package com.campus.im.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WsSessionManagerTest {

    private WsSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new WsSessionManager();
    }

    // ── register / isLocal / getUserId ─────────────────────────

    @Test
    void register_isLocal_returnsTrue() {
        WebSocketSession session = openSession("s1");
        manager.register(1L, session);

        assertTrue(manager.isLocal(1L));
        assertEquals(1L, manager.getUserId(session));
    }

    @Test
    void isLocal_unknownUser_returnsFalse() {
        assertFalse(manager.isLocal(999L));
    }

    @Test
    void isLocal_closedSession_returnsFalse() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s2");
        when(session.isOpen()).thenReturn(false);

        manager.register(2L, session);
        assertFalse(manager.isLocal(2L));
    }

    // ── push ───────────────────────────────────────────────────

    @Test
    void push_openSession_sendsMessage() throws Exception {
        WebSocketSession session = openSession("s3");
        manager.register(3L, session);

        boolean result = manager.push(3L, "{\"cmd\":\"ACK\"}");

        assertTrue(result);
        verify(session).sendMessage(new TextMessage("{\"cmd\":\"ACK\"}"));
    }

    @Test
    void push_noSession_returnsFalse() {
        assertFalse(manager.push(42L, "{}"));
    }

    @Test
    void push_sessionThrows_returnsFalse() throws Exception {
        WebSocketSession session = openSession("s4");
        doThrow(new RuntimeException("send error")).when(session).sendMessage(any());
        manager.register(4L, session);

        assertFalse(manager.push(4L, "{}"));
    }

    // ── unregister ─────────────────────────────────────────────

    @Test
    void unregister_removesSession() {
        WebSocketSession session = openSession("s5");
        manager.register(5L, session);

        assertTrue(manager.unregister(session));

        assertFalse(manager.isLocal(5L));
        assertNull(manager.getUserId(session));
    }

    // ── kickExisting ───────────────────────────────────────────

    @Test
    void registerNewSession_oldCloseCannotRemoveNewSession() throws Exception {
        WebSocketSession old = openSession("s6");
        manager.register(6L, old);
        WebSocketSession replacement = openSession("s6-new");
        WebSocketSession displaced = manager.register(6L, replacement);

        manager.kick(displaced);
        assertFalse(manager.unregister(old));

        verify(old).close();
        assertTrue(manager.isLocal(6L));
        assertEquals(6L, manager.getUserId(replacement));
    }

    @Test
    void kick_noExisting_noOp() {
        // 不应抛异常
        assertDoesNotThrow(() -> manager.kick(null));
    }

    @Test
    void handleNodeMessage_matchingKickControl_closesCurrentSession() throws Exception {
        WebSocketSession session = openSession("remote-old");
        manager.register(8L, session);

        manager.handleNodeMessage(WsSessionManager.buildKickControl(8L, "remote-old"));

        verify(session).close();
    }

    @Test
    void handleNodeMessage_staleKickControl_doesNotCloseReplacement() throws Exception {
        WebSocketSession replacement = openSession("remote-new");
        manager.register(8L, replacement);

        manager.handleNodeMessage(WsSessionManager.buildKickControl(8L, "remote-old"));

        verify(replacement, never()).close();
    }

    // ── broadcastRaw ───────────────────────────────────────────

    @Test
    void broadcastRaw_validFormat_pushesToUser() throws Exception {
        WebSocketSession session = openSession("s7");
        manager.register(7L, session);

        manager.broadcastRaw("7:{\"cmd\":\"PUSH_MSG\"}");

        verify(session).sendMessage(new TextMessage("{\"cmd\":\"PUSH_MSG\"}"));
    }

    @Test
    void broadcastRaw_invalidFormat_noOp() {
        // 不含 ':' 分隔符，不应抛异常
        assertDoesNotThrow(() -> manager.broadcastRaw("malformed"));
    }

    @Test
    void broadcastRaw_nonNumericUserId_noOp() {
        assertDoesNotThrow(() -> manager.broadcastRaw("not-a-number:{\"cmd\":\"ACK\"}"));
    }

    // ── helper ─────────────────────────────────────────────────

    private WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
