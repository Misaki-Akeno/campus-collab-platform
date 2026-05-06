package com.campus.im.websocket.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * WebSocket 消息通用 DTO，所有指令共用此结构。
 * cmd 决定 payload 的实际类型，由 Handler 自行反序列化。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsMessage {

    /** 指令类型：CHAT_MSG / ACK / PUSH_MSG / HEARTBEAT / RECALL / READ_REPORT / TYPING / KICK_OFF */
    private String cmd;

    /** 客户端生成的消息 ID（C→S 携带，用于幂等和 ACK 回执） */
    private String msgId;

    /** 服务端引用的客户端 msgId（ACK/PUSH_MSG 等 S→C 时携带） */
    private String refMsgId;

    /** 指令载荷，由各 Handler 按 cmd 解析为具体子类型 */
    private Object payload;

    // ── 内嵌 Payload 子类型 ───────────────────────────────────────

    @Data
    public static class ChatMsgPayload {
        private String conversationId;
        private Integer type;
        private String content;
        private List<Long> atUserIds;
        private String replyMsgId;
    }

    @Data
    public static class AckPayload {
        private String serverMsgId;
        private Long timestamp;
        /** OK / DUPLICATE / ERROR */
        private String status;
    }

    @Data
    public static class PushMsgPayload {
        private String msgId;
        private String conversationId;
        private Long senderId;
        private String senderName;
        private String senderAvatar;
        private Integer type;
        private String content;
        private Long timestamp;
    }

    @Data
    public static class RecallPayload {
        private String targetMsgId;
    }

    @Data
    public static class ReadReportPayload {
        private String conversationId;
        private String lastReadMsgId;
    }

    @Data
    public static class TypingPayload {
        private String conversationId;
    }
}
