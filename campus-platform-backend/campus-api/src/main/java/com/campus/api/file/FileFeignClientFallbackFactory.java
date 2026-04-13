package com.campus.api.file;

import com.campus.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FileFeignClientFallbackFactory implements FallbackFactory<FileFeignClient> {
    @Override
    public FileFeignClient create(Throwable cause) {
        log.warn("file-service 调用降级: {}", cause.getMessage());
        return fileId -> Result.fail(com.campus.common.exception.ErrorCode.SYSTEM_ERROR, "文件服务暂不可用");
    }
}
