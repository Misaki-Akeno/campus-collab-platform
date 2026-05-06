package com.campus.im.mq;

import com.campus.im.entity.ImMessage;
import com.campus.im.mapper.ImMessageMapper;
import com.campus.im.websocket.handler.ChatMsgHandler.WsMessageEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePersistConsumer {

    private final ImMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics   = "im-message-persist",
        groupId  = "im-persist-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            WsMessageEvent event = objectMapper.readValue(record.value(), WsMessageEvent.class);
            ImMessage msg = new ImMessage();
            msg.setMsgId(event.serverMsgId());
            msg.setConversationId(event.payload().getConversationId());
            msg.setSenderId(event.senderId());
            msg.setMsgType(event.payload().getType());
            msg.setContent(event.payload().getContent());
            msg.setIsRecalled(0);

            List<Long> atUsers = event.payload().getAtUserIds();
            if (atUsers != null && !atUsers.isEmpty()) {
                msg.setAtUserIds(objectMapper.writeValueAsString(atUsers));
            }
            msg.setReplyMsgId(event.payload().getReplyMsgId());

            messageMapper.insert(msg);
        } catch (Exception e) {
            log.error("[MQ] 消息持久化失败: key={}, error={}", record.key(), e.getMessage(), e);
        }
    }
}
