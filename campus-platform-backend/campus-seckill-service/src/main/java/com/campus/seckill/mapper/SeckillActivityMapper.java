package com.campus.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.seckill.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /** 以数据库为库存事实源，条件更新保证并发下 available_stock 永不小于 0。 */
    @Update("""
            UPDATE seckill_activity
               SET available_stock = available_stock - 1,
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{activityId}
               AND status = 1
               AND available_stock > 0
               AND is_deleted = 0
            """)
    int deductAvailableStock(@Param("activityId") Long activityId);
}
