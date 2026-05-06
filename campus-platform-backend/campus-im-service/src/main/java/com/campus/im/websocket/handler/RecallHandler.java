package com.campus.im.websocket.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.im.entity.ImMessage;
import com.campus.im.mapper.ImMessageMapper;
import com.campus.im.websocket.WsSessionManager;
import com.campus.im.websocket.dto.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecallHandler {

    private static final int RECALL_WINDOW_MINUTES = 2;

    private final WsSessionManager sessionManager;
    private final ImMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public void handle(Long userId, WsMessage msg) {
        if (msg.getPayload() == null) {
            return;
        }
        WsMessage.RecallPayload payload;
        try {
            payload = objectMapper.convertValue(msg.getPayload(), WsMessage.RecallPayload.class);
        } catch (Exception e) {
            log.warn("[WS] RECALL payload 解析失败: userId={}", userId, e);
            return;
        }

        ImMessage target = messageMapper.selectById(payload.getTargetMsgId());
        if (target == null) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        if (!target.getSenderId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (target.getCreateTime().isBefore(LocalDateTime.now().minusMinutes(RECALL_WINDOW_MINUTES))) {
            throw new BizException(ErrorCode.RECALL_TIMEOUT);
        }

        messageMapper.update(null, new LambdaUpdateWrapper<ImMessage>()
                .eq(ImMessage::getMsgId, payload.getTargetMsgId())
                .set(ImMessage::getIsRecalled, 1));

        // 广播撤回通知给会话在线成员（简化：仅通知发送者，完整广播依赖成员列表查询）
        try {
            WsMessage notify = new WsMessage();
            notify.setCmd("RECALL");
            notify.setPayload(payload);
            sessionManager.push(userId, objectMapper.writeValueAsString(notify));
        } catch (Exception e) {
            log.warn("[WS] RECALL 通知发送失败: userId={}", userId, e);
        }
    }
}
