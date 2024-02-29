package com.sky.utils;


import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import com.sky.properties.QcloudCosProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Data
@AllArgsConstructor
@Slf4j
public class QcloudCosUtil {

    private String secretId;
    private String secretKey;
    private String region;
    private String bucketName;
    private String folder;
    private String url;

    /**
     * 上传文件到腾讯云COS
     *
     * @param file
     * @return 文件访问路径
     */
    public String upload(MultipartFile file) throws IOException {
        log.info("上传文件到腾讯云COS...");

        // 避免文件覆盖，文件名使用uuid
        String originalFilename = file.getOriginalFilename();   // 获取原始文件名
        String uuid = UUID.randomUUID().toString(); // 生成一个UUID
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));  // 获取文件后缀名
        String fileName = uuid + suffix;    // uuid拼接文件后缀生成一个新的文件名

        // 指定文件上传到 COS 上的路径，即对象键。例如对象键为 folder/picture.jpg，则表示将文件 picture.jpg 上传到 folder 路径下
        String key = folder + fileName;

        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 2 设置 bucket 的地域, COS 地域的简称请参见 https://cloud.tencent.com/document/product/436/6224
        // clientConfig 中包含了设置 region, https(默认 http), 超时, 代理等 set 方法, 使用可参见源码或者常见问题 Java SDK 部分。
        Region r = new Region(region);
        ClientConfig clientConfig = new ClientConfig(r);

        // 这里建议设置使用 https 协议
        // 从 5.6.54 版本开始，默认使用了 https
        clientConfig.setHttpProtocol(HttpProtocol.https);
        // 3 生成 cos 客户端。
        COSClient cosClient = new COSClient(cred, clientConfig);

        File localFile = File.createTempFile("temp-" + uuid, suffix);
        file.transferTo(localFile); // 将用户上传的file暂时存储在服务端本地磁盘的localFile，待之后上传到COS

        // 上传文件到 COS
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, localFile);
        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
        System.out.println(putObjectResult.getRequestId());

        if (localFile != null) {
            localFile.delete(); // 删除本地临时文件
        }

        if (cosClient != null) {
            cosClient.shutdown();   // 关闭cosClient
        }

        String path = url + "/" + key;  // 文件访问路径
        return path;// 把cos上的文件访问路径返回
    }

}
