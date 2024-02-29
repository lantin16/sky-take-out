package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DishMapper {


    /**
     * 根据分类id删除菜品
     * @param id
     */
    @Delete("delete from dish where category_id = #{id}")
    void deleteByCategoryId(Long id);

    /**
     * 统计某分类下的菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 新增菜品
     * insert语句字段有些多，可以写到xml文件中
     *
     * @param dish
     */
    @AutoFill(OperationType.INSERT)
    void insert(Dish dish);
}
