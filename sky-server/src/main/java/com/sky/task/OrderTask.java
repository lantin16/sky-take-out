package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类，定时处理订单状态
 */

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理支付超时订单的方法
     * 每分钟检查一次，如果订单下单超过十五分钟还没支付，则变为已取消状态
     */
    @Scheduled(cron = "0 * * * * ?")    // 每分钟触发一次
    public void processTimoutOrder() {
        log.info("定时处理支付超时的订单：{}", LocalDateTime.now());

        // 查询出支付超时订单（状态为待支付且下单时间在十五分钟更早以前）
        List<Orders> orderList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, LocalDateTime.now().minusMinutes(15));

        if (orderList != null && orderList.size() > 0) {
            for (Orders order : orderList) {
                order.setStatus(Orders.CANCELLED);   // 修改订单状态为已取消
                order.setCancelReason("支付超时，系统自动取消");
                order.setCancelTime(LocalDateTime.now());
                orderMapper.update(order);
            }
        }
    }


    /**
     * 处理一直处于派送中的订单，防止商家忘记设置订单已完成而使得订单一直处于派送中状态
     * 每天凌晨一点触发一次即可，修改仍为派送中的订单为已完成状态
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDeliveryOrder() {
        log.info("定时处理派送中的订单：{}", LocalDateTime.now());

        // 查询出派送中的订单（凌晨一点状态仍为派送中且下单时间在前一天）
        List<Orders> orderList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, LocalDateTime.now().minusHours(1));

        if (orderList != null && orderList.size() > 0) {
            for (Orders order : orderList) {
                order.setStatus(Orders.COMPLETED);   // 修改订单状态为已完成
                orderMapper.update(order);
            }
        }
    }
}
