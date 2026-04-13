package com.campus.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.im.entity.ImConversation;
import com.campus.im.entity.ImConversationMember;
import com.campus.im.entity.ImMessage;
import com.campus.im.mapper.ImConversationMapper;
import com.campus.im.mapper.ImConversationMemberMapper;
import com.campus.im.mapper.ImMessageMapper;
import com.campus.im.service.ImService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImServiceImpl implements ImService {

    private final ImConversationMapper conversationMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;

    @Override
    public List<ImConversation> listConversations(Long userId) {
        // 查出用户参与的所有 conversationId
        List<ImConversationMember> memberships = memberMapper.selectList(
                new LambdaQueryWrapper<ImConversationMember>()
                        .eq(ImConversationMember::getUserId, userId));
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<String> convIds = memberships.stream()
                .map(ImConversationMember::getConversationId)
                .collect(Collectors.toList());
        return conversationMapper.selectList(
                new LambdaQueryWrapper<ImConversation>()
                        .in(ImConversation::getConversationId, convIds));
    }

    @Override
    public List<ImMessage> syncMessages(Long userId, String lastMsgId, String conversationId) {
        // 若指定会话，直接拉取该会话 lastMsgId 之后的消息
        if (StringUtils.hasText(conversationId)) {
            LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<ImMessage>()
                    .eq(ImMessage::getConversationId, conversationId)
                    .orderByAsc(ImMessage::getCreateTime)
                    .last("LIMIT 100");
            if (StringUtils.hasText(lastMsgId)) {
                ImMessage lastMsg = messageMapper.selectById(lastMsgId);
                if (lastMsg != null) {
                    wrapper.gt(ImMessage::getCreateTime, lastMsg.getCreateTime());
                }
            }
            return messageMapper.selectList(wrapper);
        }

        // 全量拉取：用 IN 一次性查出所有会话的近期消息，避免 N+1
        List<ImConversation> conversations = listConversations(userId);
        if (conversations.isEmpty()) {
            return List.of();
        }
        List<String> convIds = conversations.stream()
                .map(ImConversation::getConversationId)
                .collect(Collectors.toList());
        // 按会话批量拉取，每会话取最近 50 条由业务层做截断（此处取全量，Phase 3 换 Kafka offset 方案）
        return messageMapper.selectList(
                new LambdaQueryWrapper<ImMessage>()
                        .in(ImMessage::getConversationId, convIds)
                        .orderByAsc(ImMessage::getCreateTime)
                        .last("LIMIT 500"));
    }
}
