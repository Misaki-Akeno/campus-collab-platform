package com.campus.api.file;

import com.campus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "campus-file-service", fallbackFactory = FileFeignClientFallbackFactory.class)
public interface FileFeignClient {

    @GetMapping("/api/v1/upload/{fileId}")
    Result<FileBasicDTO> getFileMeta(@PathVariable("fileId") String fileId);
}
