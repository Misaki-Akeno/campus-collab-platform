package com.campus.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.constant.ClubStatus;
import com.campus.club.entity.Club;
import com.campus.club.entity.ClubMember;
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

    private final ClubMapper clubMapper;
    private final ClubMemberMapper clubMemberMapper;

    @Override
    @Transactional
    public Long createClub(String name, String description, String category, Long leaderId) {
        Long count = clubMapper.selectCount(
                new LambdaQueryWrapper<Club>().eq(Club::getName, name));
        if (count > 0) {
            throw new BizException(ErrorCode.CLUB_NAME_EXISTS);
        }

        Club club = new Club();
        club.setName(name);
        club.setDescription(description);
        club.setCategory(category);
        club.setLeaderId(leaderId);
        club.setStatus(ClubStatus.PENDING);
        club.setMemberCount(1);
        clubMapper.insert(club);

        // 创建者自动成为社长成员
        ClubMember leader = new ClubMember();
        leader.setUserId(leaderId);
        leader.setClubId(club.getId());
        leader.setMemberRole(MEMBER_ROLE_LEADER);
        leader.setJoinTime(LocalDateTime.now());
        clubMemberMapper.insert(leader);

        log.info("社团创建成功: clubId={}, name={}, leaderId={}", club.getId(), name, leaderId);
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
        // status=2（已解散）语义仅用于已运营社团被管理员解散；
        // 对待审核社团拒绝时应设 status=3（审核拒绝），与"解散"严格区分
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

        Long count = clubMemberMapper.selectCount(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getUserId, userId));
        if (count > 0) {
            throw new BizException(ErrorCode.ALREADY_MEMBER);
        }

        ClubMember member = new ClubMember();
        member.setUserId(userId);
        member.setClubId(clubId);
        member.setMemberRole(MEMBER_ROLE_NORMAL);
        member.setJoinTime(LocalDateTime.now());
        clubMemberMapper.insert(member);

        // 原子递增成员数，避免并发读写丢失更新
        clubMapper.update(null, new LambdaUpdateWrapper<Club>()
                .eq(Club::getId, clubId)
                .setSql("member_count = member_count + 1"));
    }
}
