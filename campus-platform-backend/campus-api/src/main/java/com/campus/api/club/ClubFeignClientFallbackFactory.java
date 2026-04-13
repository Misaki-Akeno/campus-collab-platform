package com.campus.api.club;

import com.campus.common.exception.ErrorCode;
import com.campus.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class ClubFeignClientFallbackFactory implements FallbackFactory<ClubFeignClient> {
    @Override
    public ClubFeignClient create(Throwable cause) {
        return new ClubFeignClient() {
            @Override
            public Result<ClubBasicDTO> getClubBasic(Long clubId) {
                log.warn("club-service getClubBasic 降级: clubId={}, cause={}", clubId, cause.getMessage());
                return Result.fail(ErrorCode.SYSTEM_ERROR, "社团服务暂不可用");
            }

            @Override
            public Result<List<ClubMemberDTO>> getUserClubs(Long userId) {
                log.warn("club-service getUserClubs 降级: userId={}, cause={}", userId, cause.getMessage());
                // 降级返回空列表，不阻断 /me 接口的正常返回
                return Result.ok(Collections.emptyList());
            }
        };
    }
}
