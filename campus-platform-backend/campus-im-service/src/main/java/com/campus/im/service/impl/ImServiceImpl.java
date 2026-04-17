package com.campus.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
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
            validateConversationMembership(userId, conversationId);
            LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<ImMessage>()
                    .eq(ImMessage::getConversationId, conversationId)
                    .orderByAsc(ImMessage::getCreateTime)
                    .orderByAsc(ImMessage::getMsgId)
                    .last("LIMIT 100");
            if (StringUtils.hasText(lastMsgId)) {
                wrapper.gt(ImMessage::getMsgId, lastMsgId);
            }
            return messageMapper.selectList(wrapper);
        }

        // 全量拉取：用 IN 一次性查出所有会话的近期消息，避免 N+1
        // 注：全局 LIMIT 500 不做会话间配额拆分，热门会话可能独占限额，
        //     Phase 3 换 Kafka offset 方案解决此问题
        List<ImConversation> conversations = listConversations(userId);
        if (conversations.isEmpty()) {
            return List.of();
        }
        List<String> convIds = conversations.stream()
                .map(ImConversation::getConversationId)
                .collect(Collectors.toList());
        // 按会话批量拉取，使用 createTime + msgId 双排序保证稳定性
        return messageMapper.selectList(
                new LambdaQueryWrapper<ImMessage>()
                        .in(ImMessage::getConversationId, convIds)
                        .orderByAsc(ImMessage::getCreateTime)
                        .orderByAsc(ImMessage::getMsgId)
                        .last("LIMIT 500"));
    }

    private void validateConversationMembership(Long userId, String conversationId) {
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ImConversationMember>()
                        .eq(ImConversationMember::getUserId, userId)
                        .eq(ImConversationMember::getConversationId, conversationId));
        if (count == null || count == 0) {
            throw new BizException(ErrorCode.NOT_CONVERSATION_MEMBER);
        }
    }
}
