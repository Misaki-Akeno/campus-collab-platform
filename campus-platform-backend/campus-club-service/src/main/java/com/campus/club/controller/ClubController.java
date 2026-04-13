package com.campus.club.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.entity.Club;
import com.campus.club.service.ClubService;
import com.campus.common.result.Result;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;

    /** POST /api/v1/clubs — 创建社团（需登录） */
    @PostMapping
    public Result<Map<String, Object>> createClub(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String category) {
        Long clubId = clubService.createClub(name, description, category, userId);
        return Result.ok("社团创建成功，待审核", Map.of("clubId", clubId));
    }

    /** GET /api/v1/clubs — 社团列表（公开） */
    @GetMapping
    public Result<Page<Club>> listClubs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category) {
        return Result.ok(clubService.listClubs(page, size, category));
    }

    /** GET /api/v1/clubs/{clubId} — 社团详情 */
    @GetMapping("/{clubId}")
    public Result<Club> getClub(@PathVariable Long clubId) {
        return Result.ok(clubService.getClub(clubId));
    }

    /** POST /api/v1/clubs/{clubId}/approve — 审核社团（管理员，role=2） */
    @PostMapping("/{clubId}/approve")
    public Result<Void> approveClub(
            @RequestHeader("X-User-Role") Integer role,
            @PathVariable Long clubId,
            @RequestParam boolean approved) {
        if (role < 2) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        clubService.approveClub(clubId, approved);
        return Result.ok();
    }

    /** POST /api/v1/clubs/{clubId}/join — 申请加入社团（需登录） */
    @PostMapping("/{clubId}/join")
    public Result<Void> joinClub(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long clubId) {
        clubService.joinClub(clubId, userId);
        return Result.ok("加入成功");
    }
}
