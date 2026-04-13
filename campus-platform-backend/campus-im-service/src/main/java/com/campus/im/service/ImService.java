package com.campus.im.service;

import com.campus.im.entity.ImConversation;
import com.campus.im.entity.ImMessage;

import java.util.List;

public interface ImService {

    /** 获取当前用户的会话列表 */
    List<ImConversation> listConversations(Long userId);

    /**
     * 离线消息拉取（用户上线后调用）
     * @param userId        用户 ID
     * @param lastMsgId     上次已收到的最新消息 ID
     * @param conversationId 指定会话（null 表示拉取所有）
     */
    List<ImMessage> syncMessages(Long userId, String lastMsgId, String conversationId);
}
