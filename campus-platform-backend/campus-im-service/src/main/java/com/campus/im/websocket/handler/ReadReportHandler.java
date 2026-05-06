package com.campus.im.websocket.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.im.entity.ImConversationMember;
import com.campus.im.mapper.ImConversationMemberMapper;
import com.campus.im.websocket.dto.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadReportHandler {

    private final ImConversationMemberMapper memberMapper;
    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;

    public void handle(Long userId, WsMessage msg) {
        if (msg.getPayload() == null) {
            return;
        }
        WsMessage.ReadReportPayload payload;
        try {
            payload = objectMapper.convertValue(msg.getPayload(), WsMessage.ReadReportPayload.class);
        } catch (Exception e) {
            log.warn("[WS] READ_REPORT payload 解析失败: userId={}", userId, e);
            return;
        }

        // 更新数据库中的已读位置
        memberMapper.update(null, new LambdaUpdateWrapper<ImConversationMember>()
                .eq(ImConversationMember::getUserId, userId)
                .eq(ImConversationMember::getConversationId, payload.getConversationId())
                .set(ImConversationMember::getReadMsgId, payload.getLastReadMsgId()));

        // 清零 Redis 未读计数
        RMap<String, Long> unreadMap = redisson.getMap("im:unread:" + userId);
        unreadMap.remove(payload.getConversationId());
    }
}
