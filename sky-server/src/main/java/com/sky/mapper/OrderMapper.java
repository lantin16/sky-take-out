package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper {

    /**
     * 插入订单数据
     * 需要返回主键值，因为在订单明细中需要使用订单id
     * @param orders
     */
    void insert(Orders orders);
}
