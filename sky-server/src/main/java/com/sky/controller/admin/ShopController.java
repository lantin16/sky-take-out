package com.sky.controller.admin;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")  // 由于用户端和管理员端的ShopController一样，默认的bean名称相同。所以这里需要指定name属性，避免bean名称冲突
@RequestMapping("/admin/shop")
@Slf4j
@Api(tags = "店铺操作相关接口")
public class ShopController {

    // 为了规范化，这里定义一个常量，用于存储redis中表示店铺的营业状态的key
    public static final String KEY = "SHOP_STATUS";

    // 由于营业状态数据非常简单，这里就不用存储到数据库，直接存储到redis中即可
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 设置店铺营业状态
     * @param status
     * @return
     */
    @PutMapping("/{status}")
    @ApiOperation("设置店铺的营业状态")
    public Result setStatus(@PathVariable Integer status) {
        log.info("设置店铺营业状态：status={}", status == 1 ? "营业中" : "打烊中");
        redisTemplate.opsForValue().set(KEY, status);
        return Result.success();
    }

    @GetMapping("/status")
    @ApiOperation("获取店铺的营业状态")
    public Result<Integer> getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);
        log.info("获取店铺的营业状态：status={}", status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }
}
