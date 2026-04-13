package com.campus.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_conversation_member")
public class ImConversationMember extends BaseEntity {
    private String conversationId;
    private Long userId;
    private String readMsgId;
    private Integer muted;
    /** 0-普通 1-管理员 2-群主 */
    private Integer memberRole;
    private LocalDateTime joinTime;
}
