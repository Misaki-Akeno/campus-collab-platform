package com.campus.im.websocket.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import com.campus.im.entity.ImConversationMember;
import com.campus.im.mapper.ImConversationMemberMapper;
import com.campus.im.websocket.WsSessionManager;
import com.campus.im.websocket.dto.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.Spy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatMsgHandlerTest {

    @Mock private WsSessionManager sessionManager;
    @Mock private RedissonClient redisson;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ImConversationMemberMapper memberMapper;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ChatMsgHandler handler;

    // Redisson 子 mock 需手动创建，@Mock 字段无法自动链式注入
    @Mock private RBucket<String> dedupBucket;
    @Mock private RBucket<String> onlineBucket;
    @Mock private RTopic topic;

    private final Long senderId = 10L;
    private final Long receiverId = 20L;
    private final String convId = "CONV_P_10_20";

    @BeforeEach
    void setUp() {
        // redisson.getBucket(...) 返回 mock RBucket
        when(redisson.getBucket(startsWith("im:dedup:"))).thenReturn((RBucket) dedupBucket);
        when(redisson.getBucket(startsWith("im:online:"))).thenReturn((RBucket) onlineBucket);
        when(redisson.getTopic(anyString())).thenReturn(topic);
    }

    // ── 幂等去重 ────────────────────────────────────────────────

    @Test
    void handle_duplicateMsgId_returnsAckDuplicate() {
        when(dedupBucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(false);

        handler.handle(senderId, buildMsg("dup-001"));

        // Kafka 不应被调用（重复消息不落库）
        verifyNoInteractions(kafkaTemplate);
        // 应回 ACK DUPLICATE
        verify(sessionManager).push(eq(senderId), argThat(json -> json.contains("DUPLICATE")));
    }

    @Test
    void handle_newMsgId_processesNormally() {
        setupValidScenario();

        handler.handle(senderId, buildMsg("new-001"));

        // 应回 ACK OK
        verify(sessionManager).push(eq(senderId), argThat(json -> json.contains("\"OK\"")));
        // Kafka 应被调用一次
        verify(kafkaTemplate).send(eq("im-message-persist"), eq(convId), anyString());
    }

    // ── 权限校验 ─────────────────────────────────────────────────

    @Test
    void handle_notConversationMember_throwsBizException() {
        when(dedupBucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
        when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        BizException ex = assertThrows(BizException.class,
                () -> handler.handle(senderId, buildMsg("auth-fail-001")));
        assertEquals(ErrorCode.NOT_CONVERSATION_MEMBER.getCode(), ex.getCode());
        verifyNoInteractions(kafkaTemplate);
    }

    // ── 同节点推送 ───────────────────────────────────────────────

    @Test
    void handle_receiverOnSameNode_pushesDirectly() {
        setupValidScenario();
        // receiverId 在本节点
        when(sessionManager.isLocal(receiverId)).thenReturn(true);

        handler.handle(senderId, buildMsg("local-001"));

        // receiverId 和 senderId 都应收到 PUSH_MSG
        verify(sessionManager, atLeastOnce()).push(eq(receiverId), argThat(j -> j.contains("PUSH_MSG")));
        // 不应走 Redisson Pub/Sub
        verify(topic, never()).publish(any());
    }

    // ── 跨节点推送 ───────────────────────────────────────────────

    @Test
    void handle_receiverOnOtherNode_publishesToTopic() {
        setupValidScenario();
        when(sessionManager.isLocal(receiverId)).thenReturn(false);
        when(onlineBucket.get()).thenReturn("other-node:8083");

        handler.handle(senderId, buildMsg("cross-001"));

        // 应通过 Redisson RTopic 发布给目标节点
        verify(topic).publish(argThat(raw -> ((String) raw).startsWith(receiverId + ":")));
    }

    // ── 离线用户 ─────────────────────────────────────────────────

    @Test
    void handle_receiverOffline_onlyKafka_noTopicPublish() {
        setupValidScenario();
        when(sessionManager.isLocal(receiverId)).thenReturn(false);
        when(onlineBucket.get()).thenReturn(null);  // 离线，Redis 无 nodeId

        handler.handle(senderId, buildMsg("offline-001"));

        // Kafka 落库（离线兜底）
        verify(kafkaTemplate).send(eq("im-message-persist"), eq(convId), anyString());
        // 不应广播给离线用户
        verify(topic, never()).publish(any());
    }

    // ── 缺少必填字段 ──────────────────────────────────────────────

    @Test
    void handle_nullMsgId_logsAndReturns() {
        WsMessage msg = new WsMessage();
        msg.setCmd("CHAT_MSG");
        msg.setMsgId(null);
        msg.setPayload(null);

        assertDoesNotThrow(() -> handler.handle(senderId, msg));
        verifyNoInteractions(kafkaTemplate, dedupBucket);
    }

    // ── helper ───────────────────────────────────────────────────

    private void setupValidScenario() {
        // 幂等通过（新消息）
        when(dedupBucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);

        // 发送者是成员 + 接收者也是成员
        ImConversationMember senderMember = member(senderId);
        ImConversationMember receiverMember = member(receiverId);

        // selectCount 用于权限校验（发送者）
        when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        // selectList 用于查询所有成员进行推送
        when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(senderMember, receiverMember));

        // 发送者在本节点
        when(sessionManager.isLocal(senderId)).thenReturn(true);
    }

    private WsMessage buildMsg(String msgId) {
        WsMessage msg = new WsMessage();
        msg.setCmd("CHAT_MSG");
        msg.setMsgId(msgId);

        WsMessage.ChatMsgPayload payload = new WsMessage.ChatMsgPayload();
        payload.setConversationId(convId);
        payload.setType(1);
        payload.setContent("{\"text\":\"hello\"}");
        msg.setPayload(payload);
        return msg;
    }

    private ImConversationMember member(Long userId) {
        ImConversationMember m = new ImConversationMember();
        m.setUserId(userId);
        m.setConversationId(convId);
        return m;
    }
}
