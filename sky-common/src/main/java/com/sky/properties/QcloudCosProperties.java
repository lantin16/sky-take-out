package com.sky.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云COS配置属性类
 * 程序运行时会自动在配置文件中搜索前缀是sky.qcloud-cos的配置项，然后注入到这个类的对应属性中
 */


@ConfigurationProperties(prefix = "sky.qcloud-cos")
@Data
@Component
public class QcloudCosProperties {

        private String secretId;
        private String secretKey;
        private String region;
        private String bucketName;
        private String folder;
        private String url;

}
