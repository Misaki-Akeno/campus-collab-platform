package com.campus.api.user;

import com.campus.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {
    @Override
    public UserFeignClient create(Throwable cause) {
        log.warn("user-service 调用降级: {}", cause.getMessage());
        return new UserFeignClient() {
            @Override
            public Result<UserBasicDTO> getUserBasic(Long userId) {
                return Result.error("用户服务暂不可用");
            }
        };
    }
}
