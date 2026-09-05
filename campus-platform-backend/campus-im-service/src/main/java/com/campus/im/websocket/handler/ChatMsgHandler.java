package com.campus.im.websocket.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.common.util.SnowflakeIdUtil;
import com.campus.im.config.ImNodeConfig;
import com.campus.im.entity.ImConversationMember;
import com.campus.im.entity.ImMessage;
import com.campus.im.mapper.ImConversationMemberMapper;
import com.campus.im.mapper.ImMessageMapper;
import com.campus.im.websocket.WsSessionManager;
import com.campus.im.websocket.ImOnlineRoute;
import com.campus.im.websocket.dto.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMsgHandler {

    private final WsSessionManager sessionManager;
    private final RedissonClient redisson;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public void handle(Long senderId, WsMessage msg) {
        String clientMsgId = msg.getMsgId();
        if (clientMsgId == null || msg.getPayload() == null) {
            log.warn("[WS] CHAT_MSG 缺少 msgId 或 payload: userId={}", senderId);
            return;
        }

        WsMessage.ChatMsgPayload payload;
        try {
            payload = objectMapper.convertValue(msg.getPayload(), WsMessage.ChatMsgPayload.class);
        } catch (Exception e) {
            log.warn("[WS] CHAT_MSG payload 解析失败: userId={}", senderId, e);
            return;
        }

        // Step 1: 权限校验（发送者必须是会话成员）
        validateMembership(senderId, payload.getConversationId());

        // Step 2: 数据库幂等键长期有效；重复请求返回首次持久化的真实消息 ID。
        ImMessage existing = findExisting(senderId, clientMsgId);
        if (existing != null) {
            sessionManager.push(senderId, buildAck(clientMsgId, existing.getMsgId(), "DUPLICATE"));
            return;
        }

        // Step 3: 先同步持久化。insert 返回前不会发送成功 ACK，失败时客户端可安全重试。
        String serverMsgId = "S-" + SnowflakeIdUtil.nextIdStr();
        try {
            messageMapper.insert(toEntity(serverMsgId, clientMsgId, senderId, payload));
        } catch (DuplicateKeyException race) {
            // 两次重试并发到达时，由数据库唯一约束裁决；返回获胜请求的真实 ID。
            existing = findExisting(senderId, clientMsgId);
            if (existing == null) {
                throw race;
            }
            sessionManager.push(senderId, buildAck(clientMsgId, existing.getMsgId(), "DUPLICATE"));
            return;
        }

        // Step 4: 已持久化后再确认并推送。
        sessionManager.push(senderId, buildAck(clientMsgId, serverMsgId, "OK"));
        pushToConversationMembers(senderId, payload.getConversationId(), serverMsgId, payload);
    }

    private ImMessage findExisting(Long senderId, String clientMsgId) {
        return messageMapper.selectOne(new LambdaQueryWrapper<ImMessage>()
                .eq(ImMessage::getSenderId, senderId)
                .eq(ImMessage::getClientMsgId, clientMsgId)
                .last("LIMIT 1"));
    }

    private ImMessage toEntity(String serverMsgId, String clientMsgId, Long senderId,
                               WsMessage.ChatMsgPayload payload) {
        ImMessage message = new ImMessage();
        message.setMsgId(serverMsgId);
        message.setClientMsgId(clientMsgId);
        message.setConversationId(payload.getConversationId());
        message.setSenderId(senderId);
        message.setMsgType(payload.getType());
        message.setContent(payload.getContent());
        message.setIsRecalled(0);
        List<Long> atUsers = payload.getAtUserIds();
        if (atUsers != null && !atUsers.isEmpty()) {
            try {
                message.setAtUserIds(objectMapper.writeValueAsString(atUsers));
            } catch (Exception e) {
                throw new IllegalArgumentException("@用户列表序列化失败", e);
            }
        }
        message.setReplyMsgId(payload.getReplyMsgId());
        return message;
    }

    private void validateMembership(Long userId, String conversationId) {
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ImConversationMember>()
                        .eq(ImConversationMember::getUserId, userId)
                        .eq(ImConversationMember::getConversationId, conversationId));
        if (count == null || count == 0) {
            throw new BizException(ErrorCode.NOT_CONVERSATION_MEMBER);
        }
    }

    private void pushToConversationMembers(Long senderId, String conversationId,
                                           String serverMsgId, WsMessage.ChatMsgPayload payload) {
        List<ImConversationMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ImConversationMember>()
                        .eq(ImConversationMember::getConversationId, conversationId));

        String pushJson = buildPushMsg(serverMsgId, senderId, conversationId, payload);

        for (ImConversationMember member : members) {
            Long targetUserId = member.getUserId();

            if (sessionManager.isLocal(targetUserId)) {
                sessionManager.push(targetUserId, pushJson);
            } else {
                String targetNode = (String) redisson.getBucket("im:online:" + targetUserId).get();
                if (targetNode != null) {
                    // 跨节点推送：格式为 targetUserId:jsonMsg
                    redisson.getTopic("im:node:" + ImOnlineRoute.nodeId(targetNode))
                            .publish(targetUserId + ":" + pushJson);
                }
                // 离线用户：Kafka 落库后，上线通过 /messages/sync 拉取
            }
        }
    }

    private String buildAck(String refMsgId, String serverMsgId, String status) {
        try {
            WsMessage ack = new WsMessage();
            ack.setCmd("ACK");
            ack.setRefMsgId(refMsgId);
            WsMessage.AckPayload p = new WsMessage.AckPayload();
            p.setServerMsgId(serverMsgId);
            p.setTimestamp(System.currentTimeMillis());
            p.setStatus(status);
            ack.setPayload(p);
            return objectMapper.writeValueAsString(ack);
        } catch (Exception e) {
            return "{\"cmd\":\"ACK\",\"refMsgId\":\"" + refMsgId + "\",\"payload\":{\"status\":\"ERROR\"}}";
        }
    }

    private String buildPushMsg(String serverMsgId, Long senderId, String conversationId,
                                WsMessage.ChatMsgPayload payload) {
        try {
            WsMessage push = new WsMessage();
            push.setCmd("PUSH_MSG");
            WsMessage.PushMsgPayload p = new WsMessage.PushMsgPayload();
            p.setMsgId(serverMsgId);
            p.setConversationId(conversationId);
            p.setSenderId(senderId);
            p.setType(payload.getType());
            p.setContent(payload.getContent());
            p.setTimestamp(System.currentTimeMillis());
            push.setPayload(p);
            return objectMapper.writeValueAsString(push);
        } catch (Exception e) {
            log.error("[WS] buildPushMsg 序列化失败", e);
            return "{}";
        }
    }

    // 内部事件 DTO，用于 Kafka 消息持久化
    public record WsMessageEvent(String serverMsgId, Long senderId, WsMessage.ChatMsgPayload payload) {}
}
