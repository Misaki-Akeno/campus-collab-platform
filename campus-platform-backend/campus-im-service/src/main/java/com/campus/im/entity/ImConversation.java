package com.campus.im.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 会话（主键为 varchar conversation_id，非雪花 bigint）
 */
@Data
@TableName("im_conversation")
public class ImConversation {
    @TableId
    private String conversationId;
    /** 1-单聊 2-群聊 */
    private Integer type;
    private String name;
    private String avatarUrl;
    private Long ownerId;
    private Integer maxMembers;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
