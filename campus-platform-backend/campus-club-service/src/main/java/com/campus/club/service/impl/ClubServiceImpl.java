package com.campus.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.constant.ClubStatus;
import com.campus.club.dto.CreateAnnouncementRequest;
import com.campus.club.dto.CreateClubRequest;
import com.campus.club.entity.Club;
import com.campus.club.entity.ClubAnnouncement;
import com.campus.club.entity.ClubMember;
import com.campus.club.mapper.ClubAnnouncementMapper;
import com.campus.club.mapper.ClubMapper;
import com.campus.club.mapper.ClubMemberMapper;
import com.campus.club.service.ClubService;
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClubServiceImpl implements ClubService {

    private static final int MEMBER_ROLE_LEADER = 2;
    private static final int MEMBER_ROLE_NORMAL = 0;

    /** 成员记录状态 */
    private static final int MEMBER_STATUS_PENDING  = 0;
    private static final int MEMBER_STATUS_APPROVED = 1;
    private static final int MEMBER_STATUS_REJECTED = 2;

    private final ClubMapper clubMapper;
    private final ClubMemberMapper clubMemberMapper;
    private final ClubAnnouncementMapper announcementMapper;

    @Override
    @Transactional
    public Long createClub(CreateClubRequest request, Long leaderId) {
        Long count = clubMapper.selectCount(
                new LambdaQueryWrapper<Club>().eq(Club::getName, request.getName()));
        if (count > 0) {
            throw new BizException(ErrorCode.CLUB_NAME_EXISTS);
        }

        Club club = new Club();
        club.setName(request.getName());
        club.setDescription(request.getDescription());
        club.setCategory(request.getCategory());
        club.setLeaderId(leaderId);
        club.setStatus(ClubStatus.PENDING);
        club.setMemberCount(1);
        clubMapper.insert(club);

        // 创建者自动成为社长，直接通过无需审批
        ClubMember leader = new ClubMember();
        leader.setUserId(leaderId);
        leader.setClubId(club.getId());
        leader.setMemberRole(MEMBER_ROLE_LEADER);
        leader.setStatus(MEMBER_STATUS_APPROVED);
        leader.setJoinTime(LocalDateTime.now());
        clubMemberMapper.insert(leader);

        log.info("社团创建成功: clubId={}, name={}, leaderId={}", club.getId(), request.getName(), leaderId);
        return club.getId();
    }

    @Override
    public Page<Club> listClubs(int pageNum, int pageSize, String category) {
        LambdaQueryWrapper<Club> wrapper = new LambdaQueryWrapper<Club>()
                .eq(Club::getStatus, ClubStatus.ACTIVE)
                .eq(StringUtils.hasText(category), Club::getCategory, category)
                .orderByDesc(Club::getCreateTime);
        return clubMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Club getClub(Long clubId) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw new BizException(ErrorCode.CLUB_NOT_FOUND);
        }
        return club;
    }

    @Override
    @Transactional
    public void approveClub(Long clubId, boolean approved) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw new BizException(ErrorCode.CLUB_NOT_FOUND);
        }
        int newStatus = approved ? ClubStatus.ACTIVE : ClubStatus.REJECTED;
        Club update = new Club();
        update.setId(clubId);
        update.setStatus(newStatus);
        clubMapper.updateById(update);
        log.info("社团审核: clubId={}, approved={}, newStatus={}", clubId, approved, newStatus);
    }

    @Override
    @Transactional
    public void joinClub(Long clubId, Long userId) {
        Club club = clubMapper.selectById(clubId);
        if (club == null || club.getStatus() != ClubStatus.ACTIVE) {
            throw new BizException(ErrorCode.CLUB_NOT_FOUND);
        }

        // 已是通过状态的成员
        Long approvedCount = clubMemberMapper.selectCount(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getUserId, userId)
                        .eq(ClubMember::getStatus, MEMBER_STATUS_APPROVED));
        if (approvedCount > 0) {
            throw new BizException(ErrorCode.ALREADY_MEMBER);
        }

        // 已有待审核申请
        Long pendingCount = clubMemberMapper.selectCount(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getUserId, userId)
                        .eq(ClubMember::getStatus, MEMBER_STATUS_PENDING));
        if (pendingCount > 0) {
            throw new BizException(ErrorCode.JOIN_REQUEST_EXISTS);
        }

        // 创建待审核申请（不填 joinTime，不增加 member_count）
        ClubMember member = new ClubMember();
        member.setUserId(userId);
        member.setClubId(clubId);
        member.setMemberRole(MEMBER_ROLE_NORMAL);
        member.setStatus(MEMBER_STATUS_PENDING);
        clubMemberMapper.insert(member);
        log.info("入社申请提交: clubId={}, userId={}", clubId, userId);
    }

    @Override
    @Transactional
    public void approveMember(Long clubId, Long operatorId, Long memberId, boolean approved) {
        // 校验操作人是社长或副社长
        ClubMember operator = clubMemberMapper.selectOne(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getUserId, operatorId)
                        .eq(ClubMember::getStatus, MEMBER_STATUS_APPROVED));
        if (operator == null || operator.getMemberRole() < 1) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        // 查找待审批的申请记录
        ClubMember application = clubMemberMapper.selectOne(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getId, memberId)
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getStatus, MEMBER_STATUS_PENDING));
        if (application == null) {
            throw new BizException(ErrorCode.JOIN_REQUEST_NOT_FOUND);
        }

        if (approved) {
            // 通过：设置 joinTime，更新状态，原子递增 member_count
            clubMemberMapper.update(null, new LambdaUpdateWrapper<ClubMember>()
                    .eq(ClubMember::getId, memberId)
                    .set(ClubMember::getStatus, MEMBER_STATUS_APPROVED)
                    .set(ClubMember::getJoinTime, LocalDateTime.now()));

            clubMapper.update(null, new LambdaUpdateWrapper<Club>()
                    .eq(Club::getId, clubId)
                    .setSql("member_count = member_count + 1"));
            log.info("入社申请通过: clubId={}, memberId={}, userId={}", clubId, memberId, application.getUserId());
        } else {
            clubMemberMapper.update(null, new LambdaUpdateWrapper<ClubMember>()
                    .eq(ClubMember::getId, memberId)
                    .set(ClubMember::getStatus, MEMBER_STATUS_REJECTED));
            log.info("入社申请拒绝: clubId={}, memberId={}, userId={}", clubId, memberId, application.getUserId());
        }
    }

    @Override
    @Transactional
    public Long createAnnouncement(Long clubId, Long publisherId, CreateAnnouncementRequest request) {
        // 发布者须是社长或副社长（已通过状态）
        ClubMember publisher = clubMemberMapper.selectOne(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getUserId, publisherId)
                        .eq(ClubMember::getStatus, MEMBER_STATUS_APPROVED));
        if (publisher == null || publisher.getMemberRole() < 1) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        ClubAnnouncement announcement = new ClubAnnouncement();
        announcement.setClubId(clubId);
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setPublisherId(publisherId);
        announcement.setIsPinned(request.isPinned() ? 1 : 0);
        announcementMapper.insert(announcement);

        log.info("公告发布: clubId={}, announcementId={}, publisherId={}", clubId, announcement.getId(), publisherId);
        return announcement.getId();
    }

    @Override
    public Page<ClubAnnouncement> listAnnouncements(Long clubId, int pageNum, int pageSize) {
        LambdaQueryWrapper<ClubAnnouncement> wrapper = new LambdaQueryWrapper<ClubAnnouncement>()
                .eq(ClubAnnouncement::getClubId, clubId)
                .orderByDesc(ClubAnnouncement::getIsPinned)
                .orderByDesc(ClubAnnouncement::getCreateTime);
        return announcementMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}
