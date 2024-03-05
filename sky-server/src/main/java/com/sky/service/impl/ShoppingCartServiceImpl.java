package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 判断当前欲加入购物车的商品是否已经存在于购物车中
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId()); // 从ThreadLocal中取出当前的用户id

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);    // 在这个业务功能中，只可能最多查到一条记录

        // 如果已经存在了，只需要将数量加一即可，不需要插入新的购物车记录
        if (list != null && list.size() > 0) {
            // 实际上list中只有一条数据
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber() + 1);   // 该商品数量+1
            shoppingCartMapper.updateNumberById(cart);
        } else {
            // 如果不存在，需要插入新的购物车记录
            // shoppingCart的商品名称、图片等属性需要到对应的菜品/套餐表中查询

            // 判断本次添加到购物车的是菜品还是套餐（业务功能决定商品必须为菜品或套餐）
            Long dishId = shoppingCartDTO.getDishId();
            if (dishId != null) {   // 本次添加到购物车的是菜品
                Dish dish = dishMapper.getById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setAmount(dish.getPrice());
                shoppingCart.setImage(dish.getImage());
            } else {    // 本次添加到购物车的是套餐
                Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setAmount(setmeal.getPrice());
                shoppingCart.setImage(setmeal.getImage());
            }
            shoppingCart.setNumber(1);  // 首次添加数量为1
            shoppingCart.setCreateTime(LocalDateTime.now());
            // 插入新的购物车记录
            shoppingCartMapper.insert(shoppingCart);
        }
    }
}
