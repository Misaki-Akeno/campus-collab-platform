package com.campus.api.user;

import com.campus.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserFeignClientFallbackFactoryTest {

    private final UserFeignClientFallbackFactory factory = new UserFeignClientFallbackFactory();
    private final UserFeignClient fallback = factory.create(new RuntimeException("Connection refused"));

    @Test
    void getUserBasic_shouldReturnSystemError() {
        var result = fallback.getUserBasic(1L);
        assertFalse(result.isSuccess());
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), result.getCode());
        assertTrue(result.getMsg().contains("用户服务暂不可用"));
    }
}
