package com.campus.api.club;

import com.campus.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClubFeignClientFallbackFactoryTest {

    private final ClubFeignClientFallbackFactory factory = new ClubFeignClientFallbackFactory();
    private final ClubFeignClient fallback = factory.create(new RuntimeException("Connection refused"));

    @Test
    void getClubBasic_shouldReturnSystemError() {
        var result = fallback.getClubBasic(1L);
        assertFalse(result.isSuccess());
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), result.getCode());
        assertTrue(result.getMsg().contains("社团服务暂不可用"));
    }

    @Test
    void getUserClubs_shouldReturnEmptyList() {
        var result = fallback.getUserClubs(1L);
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertTrue(result.getData().isEmpty());
    }
}
