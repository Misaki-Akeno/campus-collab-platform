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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ImServiceImplTest {

    @Mock private ImConversationMapper conversationMapper;
    @Mock private ImConversationMemberMapper memberMapper;
    @Mock private ImMessageMapper messageMapper;

    @InjectMocks
    private ImServiceImpl imService;

    private final Long userId = 100L;
    private final String validConvId = "CONV_P_100_200";
    private final String invalidConvId = "CONV_P_99_200";

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            ImConversationMember m = invocation.getArgument(0);
            m.setId((long) (java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 1000)));
            return 1;
        }).when(memberMapper).insert(isA(ImConversationMember.class));
    }

    @Test
    void listConversations_empty() {
        when(memberMapper.selectList(isA(LambdaQueryWrapper.class))).thenReturn(List.of());
        List<ImConversation> result = imService.listConversations(userId);
        assertTrue(result.isEmpty());
    }

    @Test
    void syncMessages_validConversation_returnsMessages() {
        when(memberMapper.selectCount(isA(LambdaQueryWrapper.class))).thenReturn(1L);
        ImMessage msg = new ImMessage();
        msg.setConversationId(validConvId);
        msg.setContent("hello");
        when(messageMapper.selectList(isA(LambdaQueryWrapper.class))).thenReturn(List.of(msg));

        List<ImMessage> result = imService.syncMessages(userId, null, validConvId);
        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).getContent());
    }

    // --- P0 Security Regression: IM 同步越权验证 ---

    @Test
    void syncMessages_invalidConversation_throwsForbidden() {
        // 用户不是该会话成员
        when(memberMapper.selectCount(isA(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThrows(BizException.class, () ->
                imService.syncMessages(userId, null, invalidConvId));

        // 验证异常类型
        try {
            imService.syncMessages(userId, null, invalidConvId);
        } catch (BizException e) {
            assertEquals(ErrorCode.NOT_CONVERSATION_MEMBER.getCode(), e.getCode());
        }
    }

    @Test
    void syncMessages_fullSync_noPermissionLeak() {
        // 全量拉取：用户只有一个会话，不应该拿到其他会话的消息
        ImConversationMember membership = new ImConversationMember();
        membership.setUserId(userId);
        membership.setConversationId(validConvId);
        when(memberMapper.selectList(isA(LambdaQueryWrapper.class))).thenReturn(List.of(membership));

        ImConversation conv = new ImConversation();
        conv.setConversationId(validConvId);
        when(conversationMapper.selectList(isA(LambdaQueryWrapper.class))).thenReturn(List.of(conv));

        ImMessage msg = new ImMessage();
        msg.setConversationId(validConvId);
        msg.setContent("test message");
        when(messageMapper.selectList(isA(LambdaQueryWrapper.class))).thenReturn(List.of(msg));

        List<ImMessage> result = imService.syncMessages(userId, null, null);
        assertEquals(1, result.size());
        assertEquals(validConvId, result.get(0).getConversationId());
        // 验证 messageMapper 只被调用一次（只查询用户参与的会话）
        verify(messageMapper, times(1)).selectList(isA(LambdaQueryWrapper.class));
    }
}
