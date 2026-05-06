package com.campus.im.websocket;

import com.campus.im.websocket.handler.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.campus.im.websocket.dto.WsMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsMessageDispatcher {

    private final ChatMsgHandler chatMsgHandler;
    private final HeartbeatHandler heartbeatHandler;
    private final RecallHandler recallHandler;
    private final ReadReportHandler readReportHandler;
    private final TypingHandler typingHandler;

    public void dispatch(Long userId, WsMessage msg) {
        if (msg.getCmd() == null) {
            log.warn("[WS] Received message with null cmd, userId={}", userId);
            return;
        }
        switch (msg.getCmd()) {
            case "CHAT_MSG"    -> chatMsgHandler.handle(userId, msg);
            case "HEARTBEAT"   -> heartbeatHandler.handle(userId, msg);
            case "RECALL"      -> recallHandler.handle(userId, msg);
            case "READ_REPORT" -> readReportHandler.handle(userId, msg);
            case "TYPING"      -> typingHandler.handle(userId, msg);
            default            -> log.warn("[WS] Unknown cmd: {}, userId={}", msg.getCmd(), userId);
        }
    }
}
