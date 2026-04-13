package com.campus.api.club;

import com.campus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "campus-club-service", fallbackFactory = ClubFeignClientFallbackFactory.class)
public interface ClubFeignClient {

    @GetMapping("/api/v1/clubs/{clubId}/basic")
    Result<ClubBasicDTO> getClubBasic(@PathVariable("clubId") Long clubId);

    /**
     * 内部调用：查询指定用户参与的所有已通过社团（含社团名）。
     * 对应 club-service GET /api/v1/clubs/internal/members?userId=xxx
     */
    @GetMapping("/api/v1/clubs/internal/members")
    Result<List<ClubMemberDTO>> getUserClubs(@RequestParam("userId") Long userId);
}
