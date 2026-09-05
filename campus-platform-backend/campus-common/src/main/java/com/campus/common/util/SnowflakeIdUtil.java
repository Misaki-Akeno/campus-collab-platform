package com.campus.common.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;

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

    private static final long MAX_NODE_ID = 31L;
    private static final String WORKER_PROPERTY = "campus.snowflake.worker-id";
    private static final String DATACENTER_PROPERTY = "campus.snowflake.datacenter-id";
    private static final String WORKER_ENV = "CAMPUS_SNOWFLAKE_WORKER_ID";
    private static final String DATACENTER_ENV = "CAMPUS_SNOWFLAKE_DATACENTER_ID";

    private static final long WORKER_ID = resolveNodeId(
            WORKER_PROPERTY, WORKER_ENV, processIdentity());
    private static final long DATACENTER_ID = resolveNodeId(
            DATACENTER_PROPERTY, DATACENTER_ENV, hostIdentity());
    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(WORKER_ID, DATACENTER_ID);

    private SnowflakeIdUtil() {}

    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    public static String nextIdStr() {
        return SNOWFLAKE.nextIdStr();
    }

    /**
     * 节点 ID 优先由 JVM 参数或环境变量显式指定；未指定时分别按进程和主机身份派生。
     * 生产集群应显式分配 0..31，避免派生值在大型集群中发生哈希碰撞。
     */
    static long resolveNodeId(String propertyName, String environmentName, String fallbackIdentity) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(environmentName);
        }
        if (configured != null && !configured.isBlank()) {
            try {
                long value = Long.parseLong(configured);
                if (value < 0 || value > MAX_NODE_ID) {
                    throw new IllegalStateException(propertyName + " must be between 0 and " + MAX_NODE_ID);
                }
                return value;
            } catch (NumberFormatException e) {
                throw new IllegalStateException(propertyName + " must be an integer", e);
            }
        }
        return Integer.toUnsignedLong(fallbackIdentity.hashCode()) % (MAX_NODE_ID + 1);
    }

    private static String processIdentity() {
        return ManagementFactory.getRuntimeMXBean().getName();
    }

    private static String hostIdentity() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getProperty("os.name", "unknown-host") + ':'
                    + System.getProperty("user.name", "unknown-user");
        }
    }
}
