package com.campus.im.controller;

import com.campus.common.result.Result;
import com.campus.im.entity.ImConversation;
import com.campus.im.entity.ImMessage;
import com.campus.im.service.ImService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ImController {

    private final ImService imService;

    /** GET /api/v1/conversations — 会话列表（需登录） */
    @GetMapping("/conversations")
    public Result<List<ImConversation>> listConversations(
            @RequestHeader("X-User-Id") Long userId) {
        return Result.ok(imService.listConversations(userId));
    }

    /**
     * GET /api/v1/messages/sync — 离线消息拉取（需登录）
     * @param lastMsgId       上次已收到的最新消息 ID（可选）
     * @param conversationId  指定会话（可选，不传则拉取所有）
     */
    @GetMapping("/messages/sync")
    public Result<List<ImMessage>> syncMessages(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String lastMsgId,
            @RequestParam(required = false) String conversationId) {
        return Result.ok(imService.syncMessages(userId, lastMsgId, conversationId));
    }
}
