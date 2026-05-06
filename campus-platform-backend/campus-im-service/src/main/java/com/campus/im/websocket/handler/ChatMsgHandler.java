package com.campus.im.websocket.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.common.util.SnowflakeIdUtil;
import com.campus.im.config.ImNodeConfig;
import com.campus.im.entity.ImConversationMember;
import com.campus.im.mapper.ImConversationMemberMapper;
import com.campus.im.websocket.WsSessionManager;
import com.campus.im.websocket.dto.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMsgHandler {

    private static final String TOPIC_PERSIST = "im-message-persist";

    private final WsSessionManager sessionManager;
    private final RedissonClient redisson;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ImConversationMemberMapper memberMapper;
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

        // Step 1: 幂等去重（5 分钟内相同 clientMsgId 不重复处理）
        RBucket<String> dedupBucket = redisson.getBucket("im:dedup:" + clientMsgId);
        boolean isNew = dedupBucket.setIfAbsent("1", Duration.ofMinutes(5));
        if (!isNew) {
            sessionManager.push(senderId, buildAck(clientMsgId, "S-DEDUP", "DUPLICATE"));
            return;
        }

        // Step 2: 权限校验（发送者必须是会话成员）
        validateMembership(senderId, payload.getConversationId());

        // Step 3: 生成服务端消息 ID
        String serverMsgId = "S-" + SnowflakeIdUtil.nextIdStr();

        // Step 4: 立即回 ACK，客户端停止重发计时
        sessionManager.push(senderId, buildAck(clientMsgId, serverMsgId, "OK"));

        // Step 5: 投递 Kafka 持久化（按 conversationId hash 分区，保证会话内消息有序）
        try {
            WsMessageEvent event = new WsMessageEvent(serverMsgId, senderId, payload);
            kafkaTemplate.send(TOPIC_PERSIST, payload.getConversationId(),
                               objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("[WS] Kafka 投递失败: serverMsgId={}", serverMsgId, e);
        }

        // Step 6: 向会话所有成员推送（含发送者，让发送者看到服务端确认的消息）
        pushToConversationMembers(senderId, payload.getConversationId(), serverMsgId, payload);
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
                    redisson.getTopic("im:node:" + targetNode)
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
