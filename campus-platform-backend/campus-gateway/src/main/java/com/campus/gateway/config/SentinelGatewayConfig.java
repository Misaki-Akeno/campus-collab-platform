package com.campus.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SentinelGatewayConfig {

    @PostConstruct
    public void initRules() {
        GatewayCallbackManager.setBlockHandler(new SentinelGatewayBlockHandler());
        loadFlowRules();
    }

    private void loadFlowRules() {
        List<GatewayFlowRule> rules = new ArrayList<>();

        // 路由级别通用限流
        for (String routeId : List.of("user-service", "club-service", "im-service", "file-service")) {
            rules.add(new GatewayFlowRule(routeId)
                    .setCount(100)
                    .setIntervalSec(1));
        }

        // 秒杀服务：路由级 100 QPS
        rules.add(new GatewayFlowRule("seckill-service")
                .setCount(100)
                .setIntervalSec(1));

        // 秒杀服务：同一 IP 每秒最多 20 次（防刷）
        rules.add(new GatewayFlowRule("seckill-service")
                .setCount(20)
                .setIntervalSec(1)
                .setParamItem(new GatewayParamFlowItem()
                        .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_CLIENT_IP)));

        // 用户服务：IP 维度限流，防止单 IP 滥用整个用户中心接口
        // 注意：此规则作用于 user-service 全部路由，不能替代登录接口的细粒度防暴力破解。
        // 如需仅对 /api/v1/auth/login 做 IP 防刷，应通过 GatewayApiDefinition 定义 API 分组并绑定独立规则。
        rules.add(new GatewayFlowRule("user-service")
                .setCount(30)
                .setIntervalSec(1)
                .setParamItem(new GatewayParamFlowItem()
                        .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_CLIENT_IP)));

        GatewayRuleManager.loadRules(new java.util.HashSet<>(rules));
    }
}
