package com.campus.im.websocket.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.im.config.ImNodeConfig;
import com.campus.im.entity.ImConversationMember;
import com.campus.im.mapper.ImConversationMemberMapper;
import com.campus.im.websocket.WsSessionManager;
import com.campus.im.websocket.dto.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TypingHandler {

    private final WsSessionManager sessionManager;
    private final ImConversationMemberMapper memberMapper;
    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;

    public void handle(Long userId, WsMessage msg) {
        if (msg.getPayload() == null) {
            return;
        }
        WsMessage.TypingPayload payload;
        try {
            payload = objectMapper.convertValue(msg.getPayload(), WsMessage.TypingPayload.class);
        } catch (Exception e) {
            log.warn("[WS] TYPING payload 解析失败: userId={}", userId, e);
            return;
        }

        // 广播 TYPING 给会话内其他在线成员，不落库
        List<ImConversationMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ImConversationMember>()
                        .eq(ImConversationMember::getConversationId, payload.getConversationId())
                        .ne(ImConversationMember::getUserId, userId));

        String notifyJson;
        try {
            WsMessage notify = new WsMessage();
            notify.setCmd("TYPING");
            notify.setPayload(new TypingNotify(userId, payload.getConversationId()));
            notifyJson = objectMapper.writeValueAsString(notify);
        } catch (Exception e) {
            log.warn("[WS] TYPING 序列化失败: userId={}", userId, e);
            return;
        }

        for (ImConversationMember member : members) {
            Long targetUserId = member.getUserId();
            if (sessionManager.isLocal(targetUserId)) {
                sessionManager.push(targetUserId, notifyJson);
            } else {
                String targetNode = (String) redisson.getBucket("im:online:" + targetUserId).get();
                if (targetNode != null) {
                    redisson.getTopic("im:node:" + targetNode)
                            .publish(targetUserId + ":" + notifyJson);
                }
            }
        }
    }

    record TypingNotify(Long senderId, String conversationId) {}
}
