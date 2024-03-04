package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 新增菜品，并且还需要保存关联的菜品口味
     * 涉及到多张表的插入操作，需要保证数据的一致性
     * 使用事务管理
     *
     * @param dishDTO
     */
    @Transactional  // 注意需要在启动类上加上@EnableTransactionManagement注解，开启注解方式的事务管理
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        // 将dishDTO的属性拷贝到dish中（只会拷贝命名一致的属性）
        BeanUtils.copyProperties(dishDTO, dish);
        dish.setStatus(StatusConstant.DISABLE);

        // 向菜品表插入一条数据（每次只能新增一个菜品）
        dishMapper.insert(dish);    // 插入完后，菜品才会有id值。如何拿到这个id呢？——在DishMapper.xml中配置useGeneratedKeys和keyProperty属性
        // 获取insert语句生成的菜品的主键值
        Long dishId = dish.getId();

        // 向口味表插入n条数据（每个菜品可以有多个口味）
        List<DishFlavor> flavors = dishDTO.getFlavors();
        // 判断下是否确实有口味数据（因为口味数据非必须）
        if (flavors != null && flavors.size() > 0) {
            // 注意，菜品口味对象的dishId需要我们手动设置正确的值，前端一般不会传这个值过来
            // for (DishFlavor flavor : flavors) {
            //     flavor.setDishId(dishId);
            // }
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);
            });
            // 可以直接将集合传入，批量插入
            dishFlavorMapper.insertBatch(flavors);
        }

    }


    /**
     * 菜品分页查询
     * 注意：联表查询在sql语句中去做，而不是在业务层java代码中去做
     *
     * @param dishPageQueryDTO
     * @return
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO); // 注意返回的分类名称还需要到分类表中查，这里是DishVO，而不是Dish
        return new PageResult(page.getTotal(), page.getResult());
    }


    /**
     * 批量删除菜品
     * 涉及到多个表的删除，开启事务管理
     *
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        for (Long id : ids) {
            // 判断当前菜品是否能够删除
            Dish dish = dishMapper.getById(id);
            // 1. 起售中的菜品不能删除
            if (dish.getStatus() == StatusConstant.ENABLE) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);    // 抛出自定义的业务异常
            }
        }

        // 2. 存在被套餐关联的菜品则本次批量删除操作都不能进行
        // 这里这么写意思就是，只要请求的菜品中有一个被套餐关联了，那么本次批量删除请求就都不能删除，包括ids中没有被套餐关联的菜品
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && setmealIds.size() > 0) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        // 批量删除菜品表中的菜品数据
        dishMapper.deleteBatch(ids);

        // 删除菜品后，关联的口味数据也需要删除掉
        dishFlavorMapper.deleteByDishIds(ids);
    }


    /**
     * 根据id查询菜品和对应的口味数据
     *
     * @param id
     * @return
     */
    public DishVO getByIdWithFlavor(Long id) {
        // 根据id查询菜品数据
        Dish dish = dishMapper.getById(id);

        // 根据菜品id查询口味信息
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        // 将查询到的数据封装到VO对象中
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    /**
     * 修改菜品基本信息和对应的口味信息
     * 口味的更新可能新增、删除、修改，分开处理比较麻烦
     * 可以统一处理：先删除该菜品原有的口味数据，然后再插入本次更新传来的口味数据
     *
     * @param dishDTO
     */
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        Long dishId = dishDTO.getId();
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        // 更新菜品表基本信息
        dishMapper.update(dish);

        // 删除该菜品原有的口味数据
        dishFlavorMapper.deleteByDishId(dishId);

        // 重新插入本次更新传来的口味数据
        List<DishFlavor> dishFlavors = dishDTO.getFlavors();
        if (dishFlavors != null && dishFlavors.size() > 0) {
            dishFlavors.forEach(dishFlavor -> { // 可能为新增口味数据,前端也不会将dishId传过来,因此也需要手动设置
                dishFlavor.setDishId(dishId);
            });
            dishFlavorMapper.insertBatch(dishFlavors);
        }
    }


    /**
     * 根据分类id查询启售的菜品
     * 新增套餐时添加菜品的下拉框会调用
     *
     * @param categoryId
     * @return
     */
    public List<Dish> list(Long categoryId) {
        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        return dishMapper.list(dish);
    }

    /**
     * 菜品启售停售
     * 菜品停售，则包含菜品的套餐同时停售
     *
     * @param status
     * @param id
     */
    @Transactional
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        // 更新菜品状态
        dishMapper.update(dish);

        // 菜品停售，则包含菜品的套餐同时停售
        if (status == StatusConstant.DISABLE) {
            // 查询套餐菜品表，获取与菜品相关的套餐ids
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishId(id);
            // 对包含该停售菜品的套餐也进行停售
            for (Long setmealId : setmealIds) {
                Setmeal setmeal = Setmeal.builder()
                        .id(setmealId)
                        .status(StatusConstant.DISABLE)
                        .build();
                setmealMapper.update(setmeal);
            }
        }
    }
}
