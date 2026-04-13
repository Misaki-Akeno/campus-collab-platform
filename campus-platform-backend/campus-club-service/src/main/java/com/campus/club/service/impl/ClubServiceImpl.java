package com.campus.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.api.club.ClubMemberDTO;
import com.campus.club.constant.ClubStatus;
import com.campus.club.constant.MemberRole;
import com.campus.club.constant.MemberStatus;
import com.campus.club.dto.CreateAnnouncementRequest;
import com.campus.club.dto.CreateClubRequest;
import com.campus.club.dto.MemberListItemDTO;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClubServiceImpl implements ClubService {

    private static final int CLUB_MEMBER_BATCH_SIZE = 500;

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

        ClubMember leader = new ClubMember();
        leader.setUserId(leaderId);
        leader.setClubId(club.getId());
        leader.setMemberRole(MemberRole.LEADER.getCode());
        leader.setStatus(MemberStatus.APPROVED.getCode());
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

        Long approvedCount = clubMemberMapper.selectCount(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getUserId, userId)
                        .eq(ClubMember::getStatus, MemberStatus.APPROVED.getCode()));
        if (approvedCount > 0) {
            throw new BizException(ErrorCode.ALREADY_MEMBER);
        }

        Long pendingCount = clubMemberMapper.selectCount(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getUserId, userId)
                        .eq(ClubMember::getStatus, MemberStatus.PENDING.getCode()));
        if (pendingCount > 0) {
            throw new BizException(ErrorCode.JOIN_REQUEST_EXISTS);
        }

        ClubMember member = new ClubMember();
        member.setUserId(userId);
        member.setClubId(clubId);
        member.setMemberRole(MemberRole.MEMBER.getCode());
        member.setStatus(MemberStatus.PENDING.getCode());
        clubMemberMapper.insert(member);
        log.info("入社申请提交: clubId={}, userId={}", clubId, userId);
    }

    @Override
    @Transactional
    public void approveMember(Long clubId, Long operatorId, Long memberId, boolean approved) {
        ClubMember operator = buildApprovedMember(clubId, operatorId);
        if (operator == null || operator.getMemberRole() < MemberRole.VICE_LEADER.getCode()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        ClubMember application = clubMemberMapper.selectOne(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getId, memberId)
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getStatus, MemberStatus.PENDING.getCode()));
        if (application == null) {
            throw new BizException(ErrorCode.JOIN_REQUEST_NOT_FOUND);
        }

        if (approved) {
            clubMemberMapper.update(null, new LambdaUpdateWrapper<ClubMember>()
                    .eq(ClubMember::getId, memberId)
                    .set(ClubMember::getStatus, MemberStatus.APPROVED.getCode())
                    .set(ClubMember::getJoinTime, LocalDateTime.now()));

            clubMapper.update(null, new LambdaUpdateWrapper<Club>()
                    .eq(Club::getId, clubId)
                    .setSql("member_count = member_count + 1"));
            log.info("入社申请通过: clubId={}, memberId={}, userId={}", clubId, memberId, application.getUserId());
        } else {
            clubMemberMapper.update(null, new LambdaUpdateWrapper<ClubMember>()
                    .eq(ClubMember::getId, memberId)
                    .set(ClubMember::getStatus, MemberStatus.REJECTED.getCode()));
            log.info("入社申请拒绝: clubId={}, memberId={}, userId={}", clubId, memberId, application.getUserId());
        }
    }

    @Override
    @Transactional
    public Long createAnnouncement(Long clubId, Long publisherId, CreateAnnouncementRequest request) {
        ClubMember publisher = buildApprovedMember(clubId, publisherId);
        if (publisher == null || publisher.getMemberRole() < MemberRole.VICE_LEADER.getCode()) {
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

    @Override
    public Page<MemberListItemDTO> listMembers(Long clubId, Long operatorId, int pageNum, int pageSize) {
        ClubMember operator = buildApprovedMember(clubId, operatorId);
        if (operator == null) {
            throw new BizException(ErrorCode.NOT_CLUB_MEMBER);
        }

        Page<ClubMember> memberPage = clubMemberMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getStatus, MemberStatus.APPROVED.getCode())
                        .orderByDesc(ClubMember::getMemberRole)
                        .orderByAsc(ClubMember::getJoinTime));

        Page<MemberListItemDTO> resultPage = new Page<>(memberPage.getCurrent(), memberPage.getSize(), memberPage.getTotal());
        List<MemberListItemDTO> dtoList = memberPage.getRecords().stream().map(m -> {
            MemberListItemDTO dto = new MemberListItemDTO();
            dto.setMemberId(m.getId());
            dto.setUserId(m.getUserId());
            dto.setMemberRole(m.getMemberRole());
            dto.setJoinTime(m.getJoinTime());
            return dto;
        }).collect(Collectors.toList());
        resultPage.setRecords(dtoList);
        return resultPage;
    }

    @Override
    @Transactional
    public void kickMember(Long clubId, Long operatorId, Long memberId) {
        ClubMember operator = buildApprovedMember(clubId, operatorId);
        if (operator == null || operator.getMemberRole() < MemberRole.VICE_LEADER.getCode()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        ClubMember target = clubMemberMapper.selectOne(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getId, memberId)
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getStatus, MemberStatus.APPROVED.getCode()));
        if (target == null) {
            throw new BizException(ErrorCode.NOT_CLUB_MEMBER);
        }

        if (target.getMemberRole() >= MemberRole.LEADER.getCode()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        if (operator.getMemberRole() == MemberRole.VICE_LEADER.getCode()
                && target.getMemberRole() == MemberRole.VICE_LEADER.getCode()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        clubMemberMapper.deleteById(memberId);

        int affectedRows = clubMapper.update(null, new LambdaUpdateWrapper<Club>()
                .eq(Club::getId, clubId)
                .gt(Club::getMemberCount, 0)
                .setSql("member_count = member_count - 1"));
        if (affectedRows == 0) {
            log.warn("警告: kickMember 时 member_count 递减失败，当前可能为0: clubId={}, club_member.id={}", clubId, memberId);
        }

        log.info("踢出成员: clubId={}, operatorId={}, memberId={}, targetUserId={}",
                clubId, operatorId, memberId, target.getUserId());
    }

    @Override
    @Transactional
    public void updateMemberRole(Long clubId, Long operatorId, Long targetUserId, int newRole) {
        if (!MemberRole.isUpdatable(newRole)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只允许设置为普通成员(0)或副社长(1)");
        }

        ClubMember operator = buildApprovedMember(clubId, operatorId);
        if (operator == null || operator.getMemberRole() != MemberRole.LEADER.getCode()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        if (operatorId.equals(targetUserId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        ClubMember target = clubMemberMapper.selectOne(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getUserId, targetUserId)
                        .eq(ClubMember::getStatus, MemberStatus.APPROVED.getCode()));
        if (target == null) {
            throw new BizException(ErrorCode.NOT_CLUB_MEMBER);
        }

        clubMemberMapper.update(null, new LambdaUpdateWrapper<ClubMember>()
                .eq(ClubMember::getId, target.getId())
                .set(ClubMember::getMemberRole, newRole));

        log.info("修改成员角色: clubId={}, operatorId={}, targetUserId={}, newRole={}",
                clubId, operatorId, targetUserId, newRole);
    }

    @Override
    @Transactional
    public void quitClub(Long clubId, Long userId) {
        ClubMember member = buildApprovedMember(clubId, userId);
        if (member == null) {
            throw new BizException(ErrorCode.NOT_CLUB_MEMBER);
        }

        if (member.getMemberRole() == MemberRole.LEADER.getCode()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        clubMemberMapper.deleteById(member.getId());

        int affectedRows = clubMapper.update(null, new LambdaUpdateWrapper<Club>()
                .eq(Club::getId, clubId)
                .gt(Club::getMemberCount, 0)
                .setSql("member_count = member_count - 1"));
        if (affectedRows == 0) {
            log.warn("警告: quitClub 时 member_count 递减失败: clubId={}, memberId={}", clubId, member.getId());
        }

        log.info("退出社团: clubId={}, userId={}", clubId, userId);
    }

    @Override
    public List<ClubMemberDTO> getUserClubs(Long userId) {
        List<ClubMember> memberList = clubMemberMapper.selectList(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getUserId, userId)
                        .eq(ClubMember::getStatus, MemberStatus.APPROVED.getCode()));

        if (memberList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> clubIds = memberList.stream()
                .map(ClubMember::getClubId)
                .collect(Collectors.toList());

        List<Club> clubs = batchQueryClubs(clubIds);
        if (clubs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> clubNameMap = clubs.stream()
                .collect(Collectors.toMap(Club::getId, Club::getName));

        return memberList.stream()
                .filter(m -> clubNameMap.containsKey(m.getClubId()))
                .map(m -> {
                    ClubMemberDTO dto = new ClubMemberDTO();
                    dto.setClubId(m.getClubId());
                    dto.setClubName(clubNameMap.get(m.getClubId()));
                    dto.setMemberRole(m.getMemberRole());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private ClubMember buildApprovedMember(Long clubId, Long userId) {
        return clubMemberMapper.selectOne(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getUserId, userId)
                        .eq(ClubMember::getStatus, MemberStatus.APPROVED.getCode()));
    }

    private List<Club> batchQueryClubs(List<Long> clubIds) {
        if (clubIds.size() <= CLUB_MEMBER_BATCH_SIZE) {
            return clubMapper.selectList(
                    new LambdaQueryWrapper<Club>()
                            .in(Club::getId, clubIds)
                            .eq(Club::getStatus, ClubStatus.ACTIVE));
        }

        List<Club> result = new ArrayList<>();
        for (int i = 0; i < clubIds.size(); i += CLUB_MEMBER_BATCH_SIZE) {
            List<Long> batch = clubIds.subList(i, Math.min(i + CLUB_MEMBER_BATCH_SIZE, clubIds.size()));
            result.addAll(clubMapper.selectList(
                    new LambdaQueryWrapper<Club>()
                            .in(Club::getId, batch)
                            .eq(Club::getStatus, ClubStatus.ACTIVE)));
        }
        return result;
    }
}
