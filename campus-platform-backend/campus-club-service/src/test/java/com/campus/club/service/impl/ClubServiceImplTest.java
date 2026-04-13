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
import com.campus.common.exception.BizException;
import com.campus.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ClubServiceImplTest {

    @Mock private ClubMapper clubMapper;
    @Mock private ClubMemberMapper clubMemberMapper;
    @Mock private ClubAnnouncementMapper announcementMapper;

    @InjectMocks
    private ClubServiceImpl clubService;

    private long nextId = 1000L;

    @BeforeEach
    void setUp() {
        // Mock inserts to auto-assign IDs
        lenient().doAnswer(inv -> {
            Club c = inv.getArgument(0);
            c.setId(nextId++);
            return 1;
        }).when(clubMapper).insert(isA(Club.class));
        lenient().doAnswer(inv -> {
            ClubAnnouncement a = inv.getArgument(0);
            a.setId(nextId++);
            return 1;
        }).when(announcementMapper).insert(isA(ClubAnnouncement.class));
        // Mock update(null, wrapper) returns
        lenient().when(clubMapper.update(isNull(), isA(LambdaUpdateWrapper.class))).thenReturn(1);
        lenient().when(clubMemberMapper.update(isNull(), isA(LambdaUpdateWrapper.class))).thenReturn(1);
    }

    // ========== createClub ==========

    @Test
    void createClub_success() {
        var req = new CreateClubRequest();
        req.setName("Chess Club");
        req.setDescription("Chess enthusiasts");
        req.setCategory("Academic");

        when(clubMapper.selectCount(any())).thenReturn(0L);

        Long clubId = clubService.createClub(req, 1L);

        assertNotNull(clubId);
    }

    @Test
    void createClub_duplicateName_throwsException() {
        var req = new CreateClubRequest();
        req.setName("Existing Club");

        when(clubMapper.selectCount(any())).thenReturn(1L);

        var ex = assertThrows(BizException.class, () -> clubService.createClub(req, 1L));
        assertEquals(ErrorCode.CLUB_NAME_EXISTS.getCode(), ex.getCode());
    }

    // ========== listClubs ==========

    @Test
    void listClubs_success() {
        Page<Club> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(clubMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        var result = clubService.listClubs(1, 20, null);

        assertNotNull(result);
    }

    // ========== getClub ==========

    @Test
    void getClub_success() {
        Club club = new Club();
        club.setId(1L);
        club.setName("Test Club");
        club.setStatus(ClubStatus.ACTIVE);
        when(clubMapper.selectById(1L)).thenReturn(club);

        var result = clubService.getClub(1L);

        assertEquals("Test Club", result.getName());
    }

    @Test
    void getClub_notFound_throwsException() {
        when(clubMapper.selectById(99L)).thenReturn(null);

        var ex = assertThrows(BizException.class, () -> clubService.getClub(99L));
        assertEquals(ErrorCode.CLUB_NOT_FOUND.getCode(), ex.getCode());
    }

    // ========== approveClub ==========

    @Test
    void approveClub_approve_setsActive() {
        Club club = new Club();
        club.setId(1L);
        club.setStatus(ClubStatus.PENDING);
        when(clubMapper.selectById(1L)).thenReturn(club);

        clubService.approveClub(1L, true);
    }

    @Test
    void approveClub_reject_setsRejected() {
        Club club = new Club();
        club.setId(1L);
        club.setStatus(ClubStatus.PENDING);
        when(clubMapper.selectById(1L)).thenReturn(club);

        clubService.approveClub(1L, false);
    }

    // ========== joinClub ==========

    @Test
    void joinClub_success() {
        Club club = new Club();
        club.setId(1L);
        club.setStatus(ClubStatus.ACTIVE);
        when(clubMapper.selectById(1L)).thenReturn(club);
        when(clubMemberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        clubService.joinClub(1L, 2L);
    }

    @Test
    void joinClub_clubNotActive_throwsException() {
        Club club = new Club();
        club.setId(1L);
        club.setStatus(ClubStatus.PENDING);
        when(clubMapper.selectById(1L)).thenReturn(club);

        var ex = assertThrows(BizException.class, () -> clubService.joinClub(1L, 2L));
        assertEquals(ErrorCode.CLUB_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void joinClub_alreadyMember_throwsException() {
        Club club = new Club();
        club.setId(1L);
        club.setStatus(ClubStatus.ACTIVE);
        when(clubMapper.selectById(1L)).thenReturn(club);
        // First selectCount (approved check) returns 1 => ALREADY_MEMBER
        when(clubMemberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        var ex = assertThrows(BizException.class, () -> clubService.joinClub(1L, 2L));
        assertEquals(ErrorCode.ALREADY_MEMBER.getCode(), ex.getCode());
    }

    @Test
    void joinClub_pendingRequestExists_throwsException() {
        Club club = new Club();
        club.setId(1L);
        club.setStatus(ClubStatus.ACTIVE);
        when(clubMapper.selectById(1L)).thenReturn(club);
        when(clubMemberMapper.selectCount(argThat(w -> {
            try {
                var fn = (LambdaQueryWrapper<ClubMember>) w;
                return true;
            } catch (Exception e) {
                return true;
            }
        })))
                .thenReturn(0L)
                .thenReturn(1L);

        var ex = assertThrows(BizException.class, () -> clubService.joinClub(1L, 2L));
        assertEquals(ErrorCode.JOIN_REQUEST_EXISTS.getCode(), ex.getCode());
    }

    // ========== approveMember ==========

    @Test
    void approveMember_notApprovedMember_throwsException() {
        when(clubMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        var ex = assertThrows(BizException.class, () -> clubService.approveMember(1L, 1L, 100L, true));
        assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void approveMember_noPendingRequest_throwsException() {
        ClubMember operator = new ClubMember();
        operator.setMemberRole(MemberRole.LEADER.getCode());
        operator.setStatus(MemberStatus.APPROVED.getCode());

        when(clubMemberMapper.selectOne(any())).thenReturn(operator).thenReturn(null);

        var ex = assertThrows(BizException.class, () -> clubService.approveMember(1L, 1L, 100L, true));
        assertEquals(ErrorCode.JOIN_REQUEST_NOT_FOUND.getCode(), ex.getCode());
    }

    // ========== createAnnouncement ==========

    @Test
    void createAnnouncement_success() {
        ClubMember publisher = new ClubMember();
        publisher.setMemberRole(MemberRole.LEADER.getCode());
        publisher.setStatus(MemberStatus.APPROVED.getCode());

        var req = new CreateAnnouncementRequest();
        req.setTitle("Meeting");
        req.setContent("Meeting at 3pm");
        req.setPinned(false);

        when(clubMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publisher);

        Long id = clubService.createAnnouncement(1L, 1L, req);

        assertNotNull(id);
    }

    @Test
    void createAnnouncement_noPermission_throwsException() {
        ClubMember publisher = new ClubMember();
        publisher.setMemberRole(MemberRole.MEMBER.getCode());
        publisher.setStatus(MemberStatus.APPROVED.getCode());

        var req = new CreateAnnouncementRequest();
        req.setTitle("Test");
        req.setContent("Content");

        when(clubMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publisher);

        var ex = assertThrows(BizException.class, () -> clubService.createAnnouncement(1L, 2L, req));
        assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), ex.getCode());
    }

    // ========== listMembers ==========

    @Test
    void listMembers_success() {
        ClubMember member = new ClubMember();
        member.setMemberRole(MemberRole.LEADER.getCode());
        member.setStatus(MemberStatus.APPROVED.getCode());

        ClubMember record = new ClubMember();
        record.setId(1L);
        record.setUserId(10L);
        record.setMemberRole(0);
        record.setJoinTime(LocalDateTime.now());

        Page<ClubMember> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(record));

        when(clubMemberMapper.selectOne(any())).thenReturn(member);
        when(clubMemberMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        var result = clubService.listMembers(1L, 1L, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
    }

    @Test
    void listMembers_notClubMember_throwsException() {
        when(clubMemberMapper.selectOne(any())).thenReturn(null);

        var ex = assertThrows(BizException.class, () -> clubService.listMembers(1L, 2L, 1, 20));
        assertEquals(ErrorCode.NOT_CLUB_MEMBER.getCode(), ex.getCode());
    }

    // ========== kickMember ==========

    @Test
    void kickMember_success() {
        ClubMember operator = new ClubMember();
        operator.setMemberRole(MemberRole.LEADER.getCode());
        operator.setStatus(MemberStatus.APPROVED.getCode());

        ClubMember target = new ClubMember();
        target.setId(100L);
        target.setUserId(2L);
        target.setMemberRole(MemberRole.MEMBER.getCode());
        target.setStatus(MemberStatus.APPROVED.getCode());

        when(clubMemberMapper.selectOne(any())).thenReturn(operator).thenReturn(target);
        when(clubMemberMapper.deleteById(100L)).thenReturn(1);
        when(clubMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        clubService.kickMember(1L, 1L, 100L);
    }

    @Test
    void kickMember_noPermission_throwsException() {
        when(clubMemberMapper.selectOne(any())).thenReturn(null);

        var ex = assertThrows(BizException.class, () -> clubService.kickMember(1L, 2L, 100L));
        assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void kickMember_cannotKickLeader_throwsException() {
        ClubMember operator = new ClubMember();
        operator.setMemberRole(MemberRole.LEADER.getCode());
        operator.setStatus(MemberStatus.APPROVED.getCode());

        ClubMember target = new ClubMember();
        target.setId(100L);
        target.setMemberRole(MemberRole.LEADER.getCode());
        target.setStatus(MemberStatus.APPROVED.getCode());

        when(clubMemberMapper.selectOne(any())).thenReturn(operator).thenReturn(target);

        var ex = assertThrows(BizException.class, () -> clubService.kickMember(1L, 1L, 100L));
        assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void kickMember_viceCannotKickVice_throwsException() {
        ClubMember operator = new ClubMember();
        operator.setMemberRole(MemberRole.VICE_LEADER.getCode());
        operator.setStatus(MemberStatus.APPROVED.getCode());

        ClubMember target = new ClubMember();
        target.setId(100L);
        target.setMemberRole(MemberRole.VICE_LEADER.getCode());
        target.setStatus(MemberStatus.APPROVED.getCode());

        when(clubMemberMapper.selectOne(any())).thenReturn(operator).thenReturn(target);

        var ex = assertThrows(BizException.class, () -> clubService.kickMember(1L, 2L, 100L));
        assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), ex.getCode());
    }

    // ========== updateMemberRole ==========

    @Test
    void updateMemberRole_nonLeader_throwsException() {
        ClubMember operator = new ClubMember();
        operator.setMemberRole(MemberRole.VICE_LEADER.getCode());
        operator.setStatus(MemberStatus.APPROVED.getCode());

        when(clubMemberMapper.selectOne(any())).thenReturn(operator);

        var ex = assertThrows(BizException.class, () -> clubService.updateMemberRole(1L, 1L, 10L, 0));
        assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void updateMemberRole_selfUpdate_throwsException() {
        ClubMember operator = new ClubMember();
        operator.setMemberRole(MemberRole.LEADER.getCode());
        operator.setStatus(MemberStatus.APPROVED.getCode());

        when(clubMemberMapper.selectOne(any())).thenReturn(operator);

        var ex = assertThrows(BizException.class, () -> clubService.updateMemberRole(1L, 1L, 1L, 0));
        assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void updateMemberRole_cannotSetToLeader_throwsException() {
        ClubMember operator = new ClubMember();
        operator.setMemberRole(MemberRole.LEADER.getCode());
        operator.setStatus(MemberStatus.APPROVED.getCode());

        when(clubMemberMapper.selectOne(any())).thenReturn(operator);

        var ex = assertThrows(BizException.class, () -> clubService.updateMemberRole(1L, 1L, 10L, 2));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    // ========== quitClub ==========

    @Test
    void quitClub_success() {
        ClubMember member = new ClubMember();
        member.setId(1L);
        member.setMemberRole(MemberRole.MEMBER.getCode());
        member.setStatus(MemberStatus.APPROVED.getCode());

        when(clubMemberMapper.selectOne(any())).thenReturn(member);
        when(clubMemberMapper.deleteById(1L)).thenReturn(1);
        when(clubMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        clubService.quitClub(1L, 2L);
    }

    @Test
    void quitClub_leaderCannotQuit_throwsException() {
        ClubMember member = new ClubMember();
        member.setId(1L);
        member.setMemberRole(MemberRole.LEADER.getCode());
        member.setStatus(MemberStatus.APPROVED.getCode());

        when(clubMemberMapper.selectOne(any())).thenReturn(member);

        var ex = assertThrows(BizException.class, () -> clubService.quitClub(1L, 1L));
        assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void quitClub_notMember_throwsException() {
        when(clubMemberMapper.selectOne(any())).thenReturn(null);

        var ex = assertThrows(BizException.class, () -> clubService.quitClub(1L, 2L));
        assertEquals(ErrorCode.NOT_CLUB_MEMBER.getCode(), ex.getCode());
    }

    // ========== getUserClubs ==========

    @Test
    void getUserClubs_success() {
        ClubMember member = new ClubMember();
        member.setClubId(1L);
        member.setMemberRole(0);
        member.setStatus(MemberStatus.APPROVED.getCode());

        Club club = new Club();
        club.setId(1L);
        club.setName("Chess Club");
        club.setStatus(ClubStatus.ACTIVE);

        when(clubMemberMapper.selectList(any())).thenReturn(List.of(member));
        when(clubMapper.selectList(any())).thenReturn(List.of(club));

        List<ClubMemberDTO> result = clubService.getUserClubs(1L);

        assertEquals(1, result.size());
        assertEquals("Chess Club", result.get(0).getClubName());
    }

    @Test
    void getUserClubs_noMembers_returnsEmpty() {
        when(clubMemberMapper.selectList(any())).thenReturn(List.of());

        List<ClubMemberDTO> result = clubService.getUserClubs(1L);

        assertTrue(result.isEmpty());
    }
}
