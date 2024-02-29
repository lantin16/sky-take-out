package com.sky.config;

import com.sky.properties.QcloudCosProperties;
import com.sky.utils.QcloudCosUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类
 * 程序启动时会自动创建QcloudCosUtil对象，并交给IOC容器管理
 */

@Configuration
@Slf4j
public class CosConfiguration {

    @Bean
    @ConditionalOnMissingBean   // 当容器中没有指定的Bean的情况下才创建该对象
    public QcloudCosUtil qcloudCosUtil(QcloudCosProperties qcloudCosProperties) {
        log.info("开始创建腾讯云文件上传工具类对象：{}", qcloudCosProperties);
        return new QcloudCosUtil(qcloudCosProperties.getSecretId(),
                                qcloudCosProperties.getSecretKey(),
                                qcloudCosProperties.getRegion(),
                                qcloudCosProperties.getBucketName(),
                                qcloudCosProperties.getFolder(),
                                qcloudCosProperties.getUrl());
    }
}
