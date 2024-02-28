package com.sky.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SetmealMapper {


    /**
     * 根据分类id删除套餐
     * @param id
     */
    @Delete("delete from setmeal where category_id = #{id}")
    void deleteByCategoryId(Long id);

    /**
     * 统计某分类下的套餐数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from setmeal where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);
}
