package com.campus.api.file;

import com.campus.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileFeignClientFallbackFactoryTest {

    private final FileFeignClientFallbackFactory factory = new FileFeignClientFallbackFactory();
    private final FileFeignClient fallback = factory.create(new RuntimeException("Connection refused"));

    @Test
    void getFileMeta_shouldReturnSystemError() {
        var result = fallback.getFileMeta("file-001");
        assertFalse(result.isSuccess());
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), result.getCode());
        assertTrue(result.getMsg().contains("文件服务暂不可用"));
    }
}
