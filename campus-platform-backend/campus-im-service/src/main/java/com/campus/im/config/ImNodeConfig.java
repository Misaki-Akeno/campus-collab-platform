package com.campus.im.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.campus.im.websocket.WsSessionManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImNodeConfig {

    @Value("${server.port:8083}")
    private int port;

    private final RedissonClient redisson;
    private final WsSessionManager sessionManager;

    private static String nodeId;

    @PostConstruct
    public void init() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            nodeId = hostname + ":" + port;
        } catch (Exception e) {
            nodeId = "unknown:" + port;
            log.warn("[IM] 无法获取hostname，使用默认nodeId: {}", nodeId);
        }

        redisson.getBucket("im:node:info:" + nodeId).set(System.currentTimeMillis());

        RTopic topic = redisson.getTopic("im:node:" + nodeId);
        topic.addListener(String.class, (channel, raw) -> {
            try {
                sessionManager.broadcastRaw(raw);
            } catch (Exception ex) {
                log.error("[IM] 跨节点消息处理失败", ex);
            }
        });

        log.info("[IM] Node started: {}", nodeId);
    }

    @PreDestroy
    public void destroy() {
        redisson.getBucket("im:node:info:" + nodeId).delete();
        redisson.getTopic("im:node:" + nodeId).removeAllListeners();
        log.info("[IM] Node shutdown: {}", nodeId);
    }

    public static String getNodeId() {
        return nodeId;
    }
}
