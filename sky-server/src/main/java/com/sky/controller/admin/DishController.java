package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Delete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 需要保证Redis和数据库中的菜品数据的一致性，所以在新增、修改、删除、启停售菜品的时候，需要同时清除Redis中对应分类的菜品缓存数据
 */

@Slf4j
@RequestMapping("/admin/dish")
@RestController("adminDishController")
@Api(tags = "菜品相关接口")
public class DishController {

    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 新增菜品
     *
     * @param dishDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品，dishDTO={}", dishDTO);
        dishService.saveWithFlavor(dishDTO);

        // 清理redis缓存数据，只清除新增的菜品所属分类的缓存数据即可
        clearRedisCache("dish_" + dishDTO.getCategoryId());

        return Result.success();
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */

    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询，dishPageQueryDTO={}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }


    /**
     * 批量删除菜品
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("批量删除菜品")
    public Result delete(@RequestParam List<Long> ids) {    // @RequestParam注解用于接收请求参数，spring MVC会自动解析传入的字符串，根据逗号分隔然后赋给集合ids的元素
        log.info("批量删除菜品，ids={}", ids);
        dishService.deleteBatch(ids);

        // 这里就不用再查数据库到底需要清理哪些分类的菜品缓存数据了，直接将所有分类的菜品缓存数据全部清理掉即可
        // String key = "dish_*";  // 这样写不对，因为删除时是不识别通配符的
        // 需要先将要删除的key查出来，然后再删除
        clearRedisCache("dish_*");

        return Result.success();
    }


    /**
     * 根据id查询菜品和对应的口味数据
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询菜品")
    public Result<DishVO> getById(@PathVariable Long id) {
        log.info("根据id查询菜品，id={}", id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }


    /**
     * 修改菜品基本信息和对应的口味信息
     * @param dishDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改菜品信息")
    public Result update(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品信息，dishDTO={}", dishDTO);
        dishService.updateWithFlavor(dishDTO);

        // 修改菜品信息如果修改了分类，则会影响到两个分类的菜品缓存数据，这里判断的逻辑比较复杂
        // 考虑到修改菜品信息是一个比较少的操作，所以这里直接将所有分类的菜品缓存数据全部清理掉即可
        clearRedisCache("dish_*");

        return Result.success();
    }

    /**
     * 根据分类id查询启售的菜品（要求菜品停售后，前台不展示）
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<Dish>> list(Long categoryId) {
        log.info("根据分类id查询菜品：categoryId={}", categoryId);
        List<Dish> list = dishService.list(categoryId);
        return Result.success(list);
    }

    /**
     * 菜品启售停售
     *
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("菜品启售停售")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("菜品启售停售：status={}, id={}", status, id);
        dishService.startOrStop(status, id);

        // 如果想精确清理还需要做额外的sql查询菜品分类id，这里直接清理所有分类的菜品缓存数据
        clearRedisCache("dish_*");

        return Result.success();
    }


    /**
     * 清理Redis中符合pattern的key的缓存数据
     * 将这部分代码抽取出来，提高代码的复用性
     * @param pattern
     */
    private void clearRedisCache(String pattern) {
        Set keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }

}
