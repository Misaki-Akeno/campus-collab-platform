package com.campus.club.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.dto.CreateAnnouncementRequest;
import com.campus.club.dto.CreateClubRequest;
import com.campus.club.entity.Club;
import com.campus.club.entity.ClubAnnouncement;

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
}
