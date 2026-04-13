package com.campus.api.club;

import com.campus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "campus-club-service", fallbackFactory = ClubFeignClientFallbackFactory.class)
public interface ClubFeignClient {

    @GetMapping("/api/v1/clubs/{clubId}/basic")
    Result<ClubBasicDTO> getClubBasic(@PathVariable("clubId") Long clubId);
}
