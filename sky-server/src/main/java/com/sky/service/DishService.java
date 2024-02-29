package com.sky.service;

import com.sky.dto.DishDTO;

public interface DishService {

    /**
     * 新增菜品，并且还需要保存关联的菜品口味
     *
     * @param dishDTO
     */
    void saveWithFlavor(DishDTO dishDTO);
}
