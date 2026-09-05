ALTER TABLE `im_message`
  ADD COLUMN `client_msg_id` varchar(64) DEFAULT NULL COMMENT '客户端幂等消息ID' AFTER `msg_id`,
  ADD UNIQUE KEY `uk_sender_client_msg` (`sender_id`, `client_msg_id`);
