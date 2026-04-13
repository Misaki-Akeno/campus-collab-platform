package com.campus.common.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 雪花算法 ID 工具（静态工具类）。
 * <p>
 * 适用于无法用 MyBatis-Plus 自动填充的场景，例如：
 * - IM 消息 varchar 主键（msg_id）
 * - 文件上传 uploadId 生成
 * <p>
 * MyBatis-Plus 对 bigint 主键使用 {@code @TableId(type = IdType.ASSIGN_ID)} 即可
 * 由框架内置雪花算法自动填充，无需调用本工具。
 * </p>
 */
public class SnowflakeIdUtil {

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    private SnowflakeIdUtil() {}

    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    public static String nextIdStr() {
        return SNOWFLAKE.nextIdStr();
    }
}
