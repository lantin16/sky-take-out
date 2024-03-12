package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 统计指定时间区间内的营业额
     * 营业额：订单状态为已完成的订单的总金额
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = getDateList(begin, end);

        // 将日期列表转换为字符串，日期之间用逗号分隔
        String dateListStr = StringUtils.join(dateList, ",");

        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date: dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);    // 00:00:00
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);      // 23:59:59

            // 查询date日期的营业额数据
            Map map = new HashMap<>();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);    // 通过map封装动态查询条件
            // 如果当天没有营业额，查询返回的turnover就为null，拼接null不合理，因此需要转换为0
            if (turnover == null) {
                turnover = 0.0;
            }
            turnoverList.add(turnover);
        }

        String turnoverListStr = StringUtils.join(turnoverList, ",");

        return TurnoverReportVO.builder()
                .dateList(dateListStr)
                .turnoverList(turnoverListStr)
                .build();
    }


    /**
     * 获取begin~end之间的日期列表
     * @param begin
     * @param end
     * @return
     */
    private List<LocalDate> getDateList(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();   // 用于存放begin到end之间的每天的日期（包括begin和end）
        while (!begin.isAfter(end)) {
            dateList.add(begin);
            begin = begin.plusDays(1);
        }
        return dateList;
    }

    /**
     * 统计指定时间区间内的用户数据（每日新增和用户总量）
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = getDateList(begin, end);

        List<Integer> newUserList = new ArrayList<>();  // 用于存放每天的新增用户数
        List<Integer> totalUserList = new ArrayList<>();    // 用于存放每天的用户总量
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();
            map.put("end", endTime);
            Integer totalUser = userMapper.countByMap(map);   // 先查用户总量，因为只需要限定end
            totalUser = totalUser == null ? 0 : totalUser;    // 如果totalUser为null，说明当天没有新增用户，需要转换为0
            totalUserList.add(totalUser);

            map.put("begin", beginTime);    // 再查新增用户数，需要限定begin和end，只需要在map中再加入begin即可
            Integer newUser = userMapper.countByMap(map);
            newUser = newUser == null ? 0 : newUser;
            newUserList.add(newUser);
        }

        // 封装结果数据
        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .build();
    }

    /**
     * 统计指定时间区间内的订单数据
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = getDateList(begin, end);

        List<Integer> orderCountList = new ArrayList<>();  // 用于存放每日订单总数
        List<Integer> validOrderCountList = new ArrayList<>();    // 用于存放每日有效订单数
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            // 查询每日订单总数
            Integer orderCount = getOrderCount(beginTime, endTime, null);
            orderCountList.add(orderCount);

            // 查询每日有效订单数（当日已完成的订单数）
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);
            validOrderCountList.add(validOrderCount);
        }

        // 计算时间区间内的订单总数量
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();

        // 计算时间区间内的有效订单总数量
        Integer totalValidOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();

        // 计算订单完成率，=有效订单总数/订单总数
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0) {
            orderCompletionRate = totalValidOrderCount.doubleValue() / totalOrderCount;
        }

        // 封装结果数据
        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(totalValidOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 根据条件统计订单数量
     * 将公共代码抽取处理另作一个方法
     * @param begin
     * @param end
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime begin, LocalDateTime end, Integer status) {
        Map map = new HashMap();
        map.put("begin", begin);
        map.put("end", end);
        map.put("status", status);

        Integer orderCount = orderMapper.countByMap(map);
        return orderCount == null ? 0 : orderCount;
    }

    /**
     * 统计指定时间区间内的销量排名前十的商品
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);

        // 通过stream将List<GoodsSalesDTO>中的name和number分别提取出来，然后用逗号拼接成字符串
        List<String> names = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList = StringUtils.join(names, ",");

        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList = StringUtils.join(numbers, ",");

        // 封装结果数据
        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }
}
