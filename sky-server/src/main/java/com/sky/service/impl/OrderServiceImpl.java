package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        // 1. 处理各种业务异常（地址簿为空，购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            // 地址簿为空，抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 查询当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            // 购物车数据为空，抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 2. 向订单表插入一条订单数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);    // 未支付
        orders.setStatus(Orders.PENDING_PAYMENT);    // 待付款
        orders.setNumber(System.currentTimeMillis() +"-"+userId);   // 订单号就是要当前系统的时间戳+userId
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getDetail()); // TODO address怎么填？详细地址？or 省市区拼接？
        orders.setUserName(addressBook.getConsignee()); // 收货人
        // TODO userName？

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();  // 暂存构造的orderDetail对象
        // 3. 向订单明细表插入n条数据（根据购物车中的数据）
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();    // 订单明细
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId()); // 还需要设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList); // 批量插入效率更高

        // 4. 下单完成后清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        // 5. 封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();

        return orderSubmitVO;
    }


    /**
     * 订单支付（测试用）
     * 由于个人无法申请微信支付，为测试方便跳过微信支付环节，直接修改订单状态
     * 小程序端也注释掉了调起微信支付的相关代码，直接调转到支付成功页面
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        /*
        // 由于个人无法申请微信支付，为测试方便将此代码注释
        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }
         */

        // 为测试方便，跳过微信支付，直接在这里更新订单状态
        Orders orderDB = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        orderDB.setStatus(Orders.TO_BE_CONFIRMED);  // 订单状态改为待接单
        orderDB.setPayStatus(Orders.PAID);  // 支付状态改为已支付
        orderDB.setCheckoutTime(LocalDateTime.now());  // 结账时间
        orderMapper.update(orderDB);

        // 模拟微信支付成功返回的json（其实这里面的key，value都是空的）
        JSONObject jsonObject = new JSONObject();
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }


    /**
     * 订单支付
     * 实现了真实的微信支付，测试时用不到
     *
     * @param ordersPaymentDTO
     * @return
     */
    // public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
    //     // 当前登录用户id
    //     Long userId = BaseContext.getCurrentId();
    //     User user = userMapper.getById(userId);
    //
    //     //调用微信支付接口，生成预支付交易单
    //     JSONObject jsonObject = weChatPayUtil.pay(
    //             ordersPaymentDTO.getOrderNumber(), //商户订单号
    //             new BigDecimal(0.01), //支付金额，单位 元
    //             "苍穹外卖订单", //商品描述
    //             user.getOpenid() //微信用户的openid
    //     );
    //
    //     if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
    //         throw new OrderBusinessException("该订单已支付");
    //     }
    //
    //     OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
    //     vo.setPackageStr(jsonObject.getString("package"));
    //
    //     return vo;
    // }


    /**
     * 支付成功，修改订单状态
     * 测试时由于跳过了微信支付，因此微信后台并不会支付成功回调对应的接口，所以这里也不会执行
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }


    /**
     * 用户历史订单查询
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult historyOrdersList4User(OrdersPageQueryDTO ordersPageQueryDTO) {
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());   // 设置userId为当前登录用户id
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<Orders> ordersList = page.getResult();

        // 对每个订单还要查询其订单明细并封装进VO
        List<OrderVO> orderVOList = new ArrayList<>();
        for (Orders order : ordersList) {
            List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(order.getId());
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(order, orderVO);
            orderVO.setOrderDetailList(orderDetailList);
            orderVOList.add(orderVO);
        }

        PageResult pageResult = new PageResult(page.getTotal(), orderVOList);
        return pageResult;
    }

    /**
     * 用户查询订单详情
     * @param id 订单id
     * @return
     */
    public OrderVO details4User(Long id) {
        // 查询订单基本信息
        Orders order = orderMapper.getById(id);

        // 查询订单明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 封装成VO返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }
}
