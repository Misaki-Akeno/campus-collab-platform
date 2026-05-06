package com.campus.im.websocket;

import com.campus.im.websocket.dto.WsMessage;
import com.campus.im.websocket.handler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WsMessageDispatcherTest {

    @Mock private ChatMsgHandler chatMsgHandler;
    @Mock private HeartbeatHandler heartbeatHandler;
    @Mock private RecallHandler recallHandler;
    @Mock private ReadReportHandler readReportHandler;
    @Mock private TypingHandler typingHandler;

    @InjectMocks
    private WsMessageDispatcher dispatcher;

    private static final Long USER_ID = 1L;

    @Test
    void dispatch_chatMsg_callsChatMsgHandler() {
        WsMessage msg = msg("CHAT_MSG");
        dispatcher.dispatch(USER_ID, msg);
        verify(chatMsgHandler).handle(USER_ID, msg);
        verifyNoInteractions(heartbeatHandler, recallHandler, readReportHandler, typingHandler);
    }

    @Test
    void dispatch_heartbeat_callsHeartbeatHandler() {
        WsMessage msg = msg("HEARTBEAT");
        dispatcher.dispatch(USER_ID, msg);
        verify(heartbeatHandler).handle(USER_ID, msg);
        verifyNoInteractions(chatMsgHandler, recallHandler, readReportHandler, typingHandler);
    }

    @Test
    void dispatch_recall_callsRecallHandler() {
        WsMessage msg = msg("RECALL");
        dispatcher.dispatch(USER_ID, msg);
        verify(recallHandler).handle(USER_ID, msg);
    }

    @Test
    void dispatch_readReport_callsReadReportHandler() {
        WsMessage msg = msg("READ_REPORT");
        dispatcher.dispatch(USER_ID, msg);
        verify(readReportHandler).handle(USER_ID, msg);
    }

    @Test
    void dispatch_typing_callsTypingHandler() {
        WsMessage msg = msg("TYPING");
        dispatcher.dispatch(USER_ID, msg);
        verify(typingHandler).handle(USER_ID, msg);
    }

    @Test
    void dispatch_unknownCmd_noException_noHandlerCalled() {
        assertDoesNotThrow(() -> dispatcher.dispatch(USER_ID, msg("UNKNOWN_CMD")));
        verifyNoInteractions(chatMsgHandler, heartbeatHandler, recallHandler, readReportHandler, typingHandler);
    }

    @Test
    void dispatch_nullCmd_noException() {
        assertDoesNotThrow(() -> dispatcher.dispatch(USER_ID, msg(null)));
        verifyNoInteractions(chatMsgHandler, heartbeatHandler, recallHandler, readReportHandler, typingHandler);
    }

    private WsMessage msg(String cmd) {
        WsMessage m = new WsMessage();
        m.setCmd(cmd);
        return m;
    }
}
