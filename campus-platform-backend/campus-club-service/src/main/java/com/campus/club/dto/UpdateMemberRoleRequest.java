package com.campus.club.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改成员角色请求，仅社长可操作。
 * 禁止通过此接口赋予社长角色（2），社长转移需专用流程。
 */
@Data
public class UpdateMemberRoleRequest {

    @NotNull(message = "目标角色不能为空")
    @Min(value = 0, message = "角色值最小为0（普通成员）")
    @Max(value = 1, message = "角色值最大为1（副社长），社长角色不可通过此接口设置")
    private Integer memberRole;
}
