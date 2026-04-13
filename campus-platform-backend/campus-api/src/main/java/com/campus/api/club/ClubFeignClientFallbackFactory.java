package com.campus.api.club;

import com.campus.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClubFeignClientFallbackFactory implements FallbackFactory<ClubFeignClient> {
    @Override
    public ClubFeignClient create(Throwable cause) {
        log.warn("club-service 调用降级: {}", cause.getMessage());
        return clubId -> Result.fail(com.campus.common.exception.ErrorCode.SYSTEM_ERROR, "社团服务暂不可用");
    }
}
