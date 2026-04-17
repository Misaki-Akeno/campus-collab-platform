package com.campus.gateway.filter;

import com.campus.common.constant.RedisKeyConstant;
import com.campus.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    /**
     * 白名单：仅对路径精确匹配（不含查询参数）。
     * <p>
     * - 集合列表接口加 /** 支持子路径（如 /clubs/{id}），但须限定到公开的 GET 场景；
     * - POST /seckill/api/v1/activities/{id}/book 是核心报名接口，必须鉴权，不放入白名单；
     * - /club/api/v1/clubs 及 /clubs/{id} 是公开浏览，放行。
     * </p>
     */
    private static final List<String> WHITE_LIST = List.of(
            "/user/api/v1/register",
            "/user/api/v1/login",
            "/user/api/v1/token/refresh",
            // 秒杀：仅活动列表和详情公开，POST /book 需鉴权，不使用通配
            "/seckill/api/v1/activities",
            "/seckill/api/v1/activities/{id}",
            // 社团：列表和详情公开
            "/club/api/v1/clubs",
            "/club/api/v1/clubs/{id}"
    );

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // 仅取 path，不含查询参数，避免 ?page=1 干扰匹配
        String path = request.getURI().getPath();

        for (String pattern : WHITE_LIST) {
            if (pathMatcher.match(pattern, path)) {
                return chain.filter(exchange);
            }
        }

        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("请求缺少Token: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtUtil.parseToken(token);

            // 黑名单校验（注销/强制下线场景）
            String jti = jwtUtil.getJti(claims);
            String blacklistKey = String.format(RedisKeyConstant.USER_BLACKLIST, jti);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
                log.warn("Token已被加入黑名单: jti={}, path={}", jti, path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            Long userId = jwtUtil.getUserId(claims);
            Integer role = jwtUtil.getRole(claims);

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-User-Role", String.valueOf(role))
                    // 移除原始 Authorization，防止内部服务滥用
                    .headers(headers -> headers.remove(AUTHORIZATION_HEADER))
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        } catch (Exception e) {
            log.warn("Token校验失败: path={}, errorType={}", path, e.getClass().getSimpleName());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
