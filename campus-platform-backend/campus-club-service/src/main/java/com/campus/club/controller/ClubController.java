package com.campus.club.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.api.club.ClubBasicDTO;
import com.campus.api.club.ClubMemberDTO;
import com.campus.club.dto.CreateAnnouncementRequest;
import com.campus.club.dto.CreateClubRequest;
import com.campus.club.dto.MemberListItemDTO;
import com.campus.club.dto.UpdateMemberRoleRequest;
import com.campus.club.entity.Club;
import com.campus.club.entity.ClubAnnouncement;
import com.campus.club.service.ClubService;
import com.campus.common.result.Result;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
            @Valid @RequestBody CreateClubRequest request) {
        Long clubId = clubService.createClub(request, userId);
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

    /** GET /api/v1/clubs/{clubId}/basic — 社团精简信息（Feign 内部调用） */
    @GetMapping("/{clubId}/basic")
    public Result<ClubBasicDTO> getClubBasic(@PathVariable Long clubId) {
        Club club = clubService.getClub(clubId);
        ClubBasicDTO dto = new ClubBasicDTO();
        dto.setId(club.getId());
        dto.setName(club.getName());
        dto.setLogoUrl(club.getLogoUrl());
        dto.setStatus(club.getStatus());
        return Result.ok(dto);
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
        return Result.ok();
    }

    /** POST /api/v1/clubs/{clubId}/members/{memberId}/approve — 审批入社申请（社长/副社长） */
    @PostMapping("/{clubId}/members/{memberId}/approve")
    public Result<Void> approveMember(
            @RequestHeader("X-User-Id") Long operatorId,
            @PathVariable Long clubId,
            @PathVariable Long memberId,
            @RequestParam boolean approved) {
        clubService.approveMember(clubId, operatorId, memberId, approved);
        return Result.ok();
    }

    /** POST /api/v1/clubs/{clubId}/announcements — 发布公告（社长/副社长） */
    @PostMapping("/{clubId}/announcements")
    public Result<Map<String, Object>> createAnnouncement(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long clubId,
            @Valid @RequestBody CreateAnnouncementRequest request) {
        Long announcementId = clubService.createAnnouncement(clubId, userId, request);
        return Result.ok(Map.of("announcementId", announcementId));
    }

    /** GET /api/v1/clubs/{clubId}/announcements — 公告列表（公开） */
    @GetMapping("/{clubId}/announcements")
    public Result<Page<ClubAnnouncement>> listAnnouncements(
            @PathVariable Long clubId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(clubService.listAnnouncements(clubId, page, size));
    }

    /** GET /api/v1/clubs/{clubId}/members — 社团成员列表（须是社团成员） */
    @GetMapping("/{clubId}/members")
    public Result<Page<MemberListItemDTO>> listMembers(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long clubId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(clubService.listMembers(clubId, userId, page, size));
    }

    /** DELETE /api/v1/clubs/{clubId}/members/{memberId} — 踢出成员（社长/副社长） */
    @DeleteMapping("/{clubId}/members/{memberId}")
    public Result<Void> kickMember(
            @RequestHeader("X-User-Id") Long operatorId,
            @PathVariable Long clubId,
            @PathVariable Long memberId) {
        clubService.kickMember(clubId, operatorId, memberId);
        return Result.ok();
    }

    /** PUT /api/v1/clubs/{clubId}/members/{targetUserId}/role — 修改成员角色（仅社长） */
    @PutMapping("/{clubId}/members/{targetUserId}/role")
    public Result<Void> updateMemberRole(
            @RequestHeader("X-User-Id") Long operatorId,
            @PathVariable Long clubId,
            @PathVariable Long targetUserId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        clubService.updateMemberRole(clubId, operatorId, targetUserId, request.getMemberRole());
        return Result.ok();
    }

    /** POST /api/v1/clubs/{clubId}/quit — 退出社团（社长不可退出） */
    @PostMapping("/{clubId}/quit")
    public Result<Void> quitClub(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long clubId) {
        clubService.quitClub(clubId, userId);
        return Result.ok();
    }

    /**
     * GET /api/v1/clubs/internal/members — 内部查询用户社团关系（供 user-service Feign 调用）。
     * 注：字面量路径 /internal/members 优先于路径变量 /{clubId}/members，Spring MVC 不冲突。
     */
    @GetMapping("/internal/members")
    public Result<List<ClubMemberDTO>> getUserClubs(@RequestParam Long userId) {
        return Result.ok(clubService.getUserClubs(userId));
    }
}
