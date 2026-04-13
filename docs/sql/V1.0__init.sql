CREATE DATABASE IF NOT EXISTS campus_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_platform;

-- ============================================================
-- 用户表
-- ============================================================
CREATE TABLE `sys_user` (
  `id`          bigint       NOT NULL                              COMMENT '用户ID(雪花算法)',
  `username`    varchar(64)  NOT NULL                              COMMENT '登录用户名',
  `password`    varchar(128) NOT NULL                              COMMENT 'BCrypt 加密密码',
  `nickname`    varchar(64)  DEFAULT NULL                          COMMENT '显示昵称',
  `avatar_url`  varchar(512) DEFAULT NULL                          COMMENT '头像 OSS 地址',
  `email`       varchar(128) DEFAULT NULL                          COMMENT '邮箱',
  `phone`       varchar(20)  DEFAULT NULL                          COMMENT '手机号',
  `role`        tinyint      NOT NULL DEFAULT '0'                  COMMENT '全局角色: 0-学生 1-社长 2-管理员',
  `status`      tinyint      NOT NULL DEFAULT '1'                  COMMENT '账号状态: 0-禁用 1-正常',
  `last_login`  datetime     DEFAULT NULL                          COMMENT '最后登录时间',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  tinyint      NOT NULL DEFAULT '0'                  COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_email` (`email`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- ============================================================
-- 社团表
-- ============================================================
CREATE TABLE `club` (
  `id`          bigint       NOT NULL                              COMMENT '社团ID',
  `name`        varchar(128) NOT NULL                              COMMENT '社团名称',
  `description` text                                               COMMENT '社团简介',
  `logo_url`    varchar(512) DEFAULT NULL                          COMMENT '社团Logo',
  `leader_id`   bigint       NOT NULL                              COMMENT '社长用户ID',
  `category`    varchar(32)  DEFAULT NULL                          COMMENT '分类',
  `status`      tinyint      NOT NULL DEFAULT '0'                  COMMENT '审核状态: 0-待审核 1-正常 2-已解散 3-审核拒绝',
  `member_count`int          NOT NULL DEFAULT '1'                  COMMENT '成员数冗余',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  tinyint      NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_leader` (`leader_id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社团表';

-- ============================================================
-- 社团成员关联表
-- ============================================================
CREATE TABLE `club_member` (
  `id`          bigint       NOT NULL                              COMMENT '主键',
  `user_id`     bigint       NOT NULL                              COMMENT '用户ID',
  `club_id`     bigint       NOT NULL                              COMMENT '社团ID',
  `member_role` tinyint      NOT NULL DEFAULT '0'                  COMMENT '0-成员 1-副社长 2-社长',
  `status`      tinyint      NOT NULL DEFAULT '0'                  COMMENT '申请状态: 0-待审核 1-已通过 2-已拒绝',
  `join_time`   datetime     DEFAULT NULL                          COMMENT '审批通过时间（待审核时为NULL）',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  tinyint      NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_club` (`user_id`, `club_id`),
  KEY `idx_club_id` (`club_id`),
  KEY `idx_club_status` (`club_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社团成员关联表';

-- ============================================================
-- 公告表
-- ============================================================
CREATE TABLE `club_announcement` (
  `id`           bigint       NOT NULL                              COMMENT '公告ID',
  `club_id`      bigint       NOT NULL                              COMMENT '所属社团ID',
  `title`        varchar(256) NOT NULL                              COMMENT '公告标题',
  `content`      text         NOT NULL                              COMMENT '公告内容',
  `publisher_id` bigint       NOT NULL                              COMMENT '发布者用户ID',
  `is_pinned`    tinyint      NOT NULL DEFAULT '0'                  COMMENT '是否置顶',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`   tinyint      NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_club_pinned` (`club_id`, `is_pinned`, `create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社团公告表';

-- ============================================================
-- 秒杀活动表
-- ============================================================
CREATE TABLE `seckill_activity` (
  `id`              bigint       NOT NULL                          COMMENT '活动ID',
  `club_id`         bigint       NOT NULL                          COMMENT '所属社团ID',
  `title`           varchar(128) NOT NULL                          COMMENT '活动标题',
  `description`     text                                           COMMENT '活动详情',
  `cover_url`       varchar(512) DEFAULT NULL                      COMMENT '活动封面图',
  `location`        varchar(256) DEFAULT NULL                      COMMENT '活动地点',
  `activity_time`   datetime     DEFAULT NULL                      COMMENT '活动举办时间',
  `total_stock`     int          NOT NULL                          COMMENT '总名额',
  `available_stock` int          NOT NULL                          COMMENT '可用名额',
  `start_time`      datetime     NOT NULL                          COMMENT '报名开始时间',
  `end_time`        datetime     NOT NULL                          COMMENT '报名结束时间',
  `status`          tinyint      NOT NULL DEFAULT '0'              COMMENT '0-未开始 1-进行中 2-已结束 3-已取消',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      tinyint      NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_club_id` (`club_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动表';

-- ============================================================
-- 秒杀订单表
-- ============================================================
CREATE TABLE `seckill_order` (
  `id`            bigint       NOT NULL                            COMMENT '订单ID',
  `user_id`       bigint       NOT NULL                            COMMENT '报名用户ID',
  `activity_id`   bigint       NOT NULL                            COMMENT '活动ID',
  `status`        tinyint      NOT NULL DEFAULT '1'                COMMENT '1-成功 2-已取消/失败',
  `cancel_reason` varchar(256) DEFAULT NULL                        COMMENT '取消原因',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    tinyint      NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_activity` (`user_id`, `activity_id`),
  KEY `idx_activity_status` (`activity_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀报名订单表';

-- ============================================================
-- IM 会话表
-- ============================================================
CREATE TABLE `im_conversation` (
  `conversation_id` varchar(64)  NOT NULL                          COMMENT '会话ID',
  `type`            tinyint      NOT NULL                          COMMENT '1-单聊 2-群聊',
  `name`            varchar(128) DEFAULT NULL                      COMMENT '群聊名称',
  `avatar_url`      varchar(512) DEFAULT NULL                      COMMENT '群头像',
  `owner_id`        bigint       DEFAULT NULL                      COMMENT '群主ID',
  `max_members`     int          NOT NULL DEFAULT '200'            COMMENT '最大成员数',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      tinyint      NOT NULL DEFAULT '0',
  PRIMARY KEY (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM会话表';

-- ============================================================
-- IM 会话成员表
-- ============================================================
CREATE TABLE `im_conversation_member` (
  `id`              bigint       NOT NULL                          COMMENT '主键',
  `conversation_id` varchar(64)  NOT NULL                          COMMENT '会话ID',
  `user_id`         bigint       NOT NULL                          COMMENT '用户ID',
  `read_msg_id`     varchar(64)  DEFAULT NULL                      COMMENT '最后已读消息ID',
  `muted`           tinyint      NOT NULL DEFAULT '0'              COMMENT '免打扰',
  `member_role`     tinyint      NOT NULL DEFAULT '0'              COMMENT '0-普通 1-管理员 2-群主',
  `join_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      tinyint      NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conv_user` (`conversation_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM会话成员表';

-- ============================================================
-- IM 消息表
-- ============================================================
CREATE TABLE `im_message` (
  `msg_id`          varchar(64)  NOT NULL                          COMMENT '全局唯一消息ID',
  `conversation_id` varchar(64)  NOT NULL                          COMMENT '会话ID',
  `sender_id`       bigint       NOT NULL                          COMMENT '发送者用户ID',
  `msg_type`        tinyint      NOT NULL                          COMMENT '1-文本 2-图片 3-文件 4-系统通知 5-@消息',
  `content`         text         NOT NULL                          COMMENT '消息体(JSON)',
  `at_user_ids`     varchar(512) DEFAULT NULL                      COMMENT '@用户ID列表',
  `reply_msg_id`    varchar(64)  DEFAULT NULL                      COMMENT '引用回复消息ID',
  `is_recalled`     tinyint      NOT NULL DEFAULT '0'              COMMENT '是否已撤回',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      tinyint      NOT NULL DEFAULT '0',
  PRIMARY KEY (`msg_id`),
  KEY `idx_conversation_time` (`conversation_id`, `create_time`),
  KEY `idx_sender` (`sender_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM消息表';

-- ============================================================
-- 文件元数据表
-- ============================================================
CREATE TABLE `file_meta` (
  `file_id`       varchar(64)  NOT NULL                            COMMENT '文件MD5',
  `file_name`     varchar(255) NOT NULL                            COMMENT '原始文件名',
  `file_size`     bigint       NOT NULL                            COMMENT '文件大小(字节)',
  `file_type`     varchar(64)  DEFAULT NULL                        COMMENT 'MIME类型',
  `file_url`      varchar(512) NOT NULL                            COMMENT 'OSS地址',
  `chunk_count`   int          NOT NULL DEFAULT '1'                COMMENT '分片总数',
  `upload_status` tinyint      NOT NULL DEFAULT '0'                COMMENT '0-上传中 1-已完成',
  `uploader_id`   bigint       DEFAULT NULL                        COMMENT '首次上传者ID',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`file_id`),
  KEY `idx_uploader` (`uploader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据表';
