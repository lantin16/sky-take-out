package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        log.info("根据分类id查询菜品，categoryId={}", categoryId);
        // 可以通过Redis来缓存菜品数据，减少数据库的访问，提高性能

        // 1. 查询Redis中是否存在菜品数据，key的格式为：dish_分类id
        String key = "dish_" + categoryId;
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);    // 放进去的是什么类型的对象，取出来就是什么类型的

        // 2. 如果Redis中存在菜品数据，则直接返回，无需查询数据库
        if (list != null && list.size() > 0) {
            log.info("从Redis中查询到了菜品数据：{}", list);
            return Result.success(list);
        }

        // 3. 如果Redis中不存在菜品数据，则查询数据库，然后将查询到的菜品数据存入Redis中
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品
        list = dishService.listWithFlavor(dish);
        redisTemplate.opsForValue().set(key, list);    // 将查询到的菜品数据存入Redis中
        return Result.success(list);
    }

}
