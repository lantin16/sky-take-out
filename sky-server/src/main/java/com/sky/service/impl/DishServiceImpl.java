package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;

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
}
