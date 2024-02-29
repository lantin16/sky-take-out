package com.sky.controller.admin;


import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.QcloudCosUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 通用接口
 */


@RequestMapping("/admin/common")
@RestController
@Slf4j
@Api(tags = "通用接口")
public class CommonController {

    @Autowired
    private QcloudCosUtil qcloudCosUtil;

    /**
     * 文件上传
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file) {  // 注意形参的名字要和前端传递的参数名一致
        log.info("文件上传：{}", file.getOriginalFilename());
        String url = null;
        try {
            url = qcloudCosUtil.upload(file);
            log.info("文件上传成功，访问路径：{}", url);
            return Result.success(url); // 上传成功，返回文件访问路径
        } catch (IOException e) {
            log.error("文件上传失败：{}", e.getMessage());
        }
        return Result.error(MessageConstant.UPLOAD_FAILED); // 上传失败，返回错误信息
    }
}
