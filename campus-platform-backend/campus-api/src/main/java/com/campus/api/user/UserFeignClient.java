package com.campus.api.user;

import com.campus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "campus-user-service", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    @GetMapping("/user/api/v1/users/{userId}/basic")
    Result<UserBasicDTO> getUserBasic(@PathVariable("userId") Long userId);
}
