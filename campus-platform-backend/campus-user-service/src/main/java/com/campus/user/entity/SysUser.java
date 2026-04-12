package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;
    private String password;
    private String nickname;
    private String avatarUrl;
    private String email;
    private String phone;
    private Integer role;
    private Integer status;
    private LocalDateTime lastLogin;
}
