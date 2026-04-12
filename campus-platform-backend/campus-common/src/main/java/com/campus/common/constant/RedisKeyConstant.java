package com.campus.common.constant;

public class RedisKeyConstant {

    private RedisKeyConstant() {}

    public static final String USER_REFRESH_TOKEN = "user:refresh:%s";
    public static final String USER_BLACKLIST = "user:blacklist:%s";

    public static final String SECKILL_STOCK = "seckill:stock:%s";
    public static final String SECKILL_BOOKED = "seckill:booked:%s";

    public static final String FILE_CHUNK = "file:chunk:%s";
    public static final String FILE_UPLOAD_STATUS = "file:upload:%s";

    public static final String IM_USER_SERVER = "im:user:server:%s";
    public static final String IM_OFFLINE_MSG = "im:offline:%s";
}
