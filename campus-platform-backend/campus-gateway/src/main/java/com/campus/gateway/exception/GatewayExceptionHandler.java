package com.campus.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Gateway 全局异常处理（WebFlux 响应式栈）
 * <p>优先级高于 Spring 默认的 DefaultErrorWebExceptionHandler，order=-1 即可</p>
 */
@Slf4j
@Order(-1)
@Component
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 响应已提交时（如 partial write 后），无法再修改 status 或写入 body
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "系统内部错误";

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
        }

        log.error("Gateway 异常: path={}, status={}, msg={}",
                exchange.getRequest().getURI().getPath(), status.value(), ex.getMessage());

        int code = status.value();
        String body;
        try {
            body = objectMapper.writeValueAsString(
                    Map.of("code", code, "msg", message, "data", ""));
        } catch (JsonProcessingException e) {
            body = "{\"code\":" + code + ",\"msg\":\"" + message + "\",\"data\":\"\"}";
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
