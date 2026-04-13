package com.campus.club.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.api.club.ClubMemberDTO;
import com.campus.club.dto.CreateAnnouncementRequest;
import com.campus.club.dto.CreateClubRequest;
import com.campus.club.dto.MemberListItemDTO;
import com.campus.club.entity.Club;
import com.campus.club.entity.ClubAnnouncement;

import java.util.List;

public interface ClubService {

    /** 创建社团（提交待审核） */
    Long createClub(CreateClubRequest request, Long leaderId);

    /** 社团列表（公开，分页） */
    Page<Club> listClubs(int pageNum, int pageSize, String category);

    /** 社团详情 */
    Club getClub(Long clubId);

    /** 审核社团（管理员） */
    void approveClub(Long clubId, boolean approved);

    /**
     * 申请加入社团：创建待审核成员记录（status=0），不计入 member_count。
     * 不允许重复申请或对已是成员的用户再申请。
     */
    void joinClub(Long clubId, Long userId);

    /**
     * 社长/副社长审批入社申请。
     * 通过时：member_count+1、joinTime 置为当前时间、status=1。
     * 拒绝时：status=2。
     *
     * @param clubId     社团 ID
     * @param operatorId 操作人（需 memberRole >= 1）
     * @param memberId   club_member 记录 ID（即申请记录 ID）
     * @param approved   true=通过 false=拒绝
     */
    void approveMember(Long clubId, Long operatorId, Long memberId, boolean approved);

    /** 发布公告（社长/副社长，memberRole >= 1） */
    Long createAnnouncement(Long clubId, Long publisherId, CreateAnnouncementRequest request);

    /** 公告列表（分页，置顶优先，按创建时间倒序） */
    Page<ClubAnnouncement> listAnnouncements(Long clubId, int pageNum, int pageSize);

    /**
     * 社团成员列表（已通过成员，分页）。
     * 操作人须是该社团的已通过成员（任意角色均可查看）。
     */
    Page<MemberListItemDTO> listMembers(Long clubId, Long operatorId, int pageNum, int pageSize);

    /**
     * 踢出成员（社长/副社长操作）。
     * 不能踢社长；副社长不能踢副社长。
     *
     * @param memberId club_member.id（成员记录 ID，非 userId）
     */
    void kickMember(Long clubId, Long operatorId, Long memberId);

    /**
     * 修改成员角色（仅社长可操作）。
     * 不能修改自己；目标角色不能为 2（社长），社长转移需专用流程。
     *
     * @param targetUserId 目标用户 userId
     * @param newRole      目标角色（0-普通成员，1-副社长）
     */
    void updateMemberRole(Long clubId, Long operatorId, Long targetUserId, int newRole);

    /**
     * 退出社团（社长不能退出，须先转移社长身份）。
     */
    void quitClub(Long clubId, Long userId);

    /**
     * 内部查询：获取指定用户在所有已通过社团中的角色信息（供 user-service Feign 调用）。
     * 使用 IN 查询批量获取社团名称，避免 N+1。
     */
    List<ClubMemberDTO> getUserClubs(Long userId);
}
