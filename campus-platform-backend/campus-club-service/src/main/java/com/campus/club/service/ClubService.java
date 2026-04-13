package com.campus.club.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.entity.Club;

public interface ClubService {

    /** 创建社团（提交待审核） */
    Long createClub(String name, String description, String category, Long leaderId);

    /** 社团列表（公开，分页） */
    Page<Club> listClubs(int pageNum, int pageSize, String category);

    /** 社团详情 */
    Club getClub(Long clubId);

    /** 审核社团（管理员） */
    void approveClub(Long clubId, boolean approved);

    /** 申请加入社团 */
    void joinClub(Long clubId, Long userId);
}
