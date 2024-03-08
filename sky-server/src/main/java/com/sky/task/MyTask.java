package com.sky.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 自定义定时任务类
 * Spring Task是Spring框架提供的任务调度工具，可以按照约定的时间自动执行某个代码逻辑
 */
@Component
@Slf4j
public class MyTask {

    /**
     * 定时任务 每隔5秒触发一次
     */
    // @Scheduled(cron = "0/5 * * * * ?")  // cron表达式，指定任务在什么时候触发
    // public void executeTask() {
    //     log.info("定时任务开始执行：{}", new Date());
    // }
}
