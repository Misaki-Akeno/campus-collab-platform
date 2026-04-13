package com.campus.im.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 消息（主键为 varchar msg_id）
 */
@Data
@TableName("im_message")
public class ImMessage {
    @TableId
    private String msgId;
    private String conversationId;
    private Long senderId;
    /** 1-文本 2-图片 3-文件 4-系统通知 5-@消息 */
    private Integer msgType;
    private String content;
    private String atUserIds;
    private String replyMsgId;
    private Integer isRecalled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
