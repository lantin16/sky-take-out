package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
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
    @Autowired
    private WebSocketServer webSocketServer;

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
        orders.setNumber(System.currentTimeMillis() + "-" + userId);   // 订单号就是要当前系统的时间戳+userId
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

        // 模拟支付成功后，通过websocket向商家后台的客户端浏览器推送消息，采用json格式，包含：type, orderId, content
        Map map = new HashMap();
        map.put("type", 1); // 约定：1表示来单提醒，2表示客户催单
        map.put("orderId", orderDB.getId());
        map.put("content", "订单号：" + orderDB.getNumber());
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);

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

        // 支付成功，通过websocket向商家后台的客户端浏览器推送消息，采用json格式，包含：type, orderId, content
        Map map = new HashMap();
        map.put("type", 1); // 约定：1表示来单提醒，2表示客户催单
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号：" + outTradeNo);

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }


    /**
     * 用户历史订单查询
     *
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
     * 查询订单详情
     *
     * @param id 订单id
     * @return
     */
    public OrderVO details(Long id) {
        // 查询订单基本信息
        Orders order = orderMapper.getById(id);

        // 查询订单明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 查询订单包含的菜品信息字符串
        String orderDishes = getOrderDishesStringByOrderId(id);

        // 封装成VO返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        orderVO.setOrderDishes(orderDishes);

        return orderVO;
    }


    /**
     * 用户取消订单
     *
     * @param id
     */
    public void userCancelOrder(Long id) throws Exception {
        // 根据id查询订单
        Orders orderDB = orderMapper.getById(id);

        // 检查订单是否存在
        // 若订单不存在，抛出业务异常
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 检查订单状态，根据不同状态采取对应操作
        Integer status = orderDB.getStatus(); // 订单状态： 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        // 如果订单状态不是待付款或待接单，则需要用户联系商家协商，这里直接抛出业务异常
        if (!status.equals(Orders.PENDING_PAYMENT) && !status.equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders order = new Orders();
        order.setId(orderDB.getId());

        // 若订单状态是待接单，则取消订单的同时还需要退款
        if (status.equals(Orders.TO_BE_CONFIRMED)) {    // 两个Integer对象用==比较是比的内存地址，想比较值要用equals
            // 调用微信支付退款接口
            weChatPayUtil.refund(
                    orderDB.getNumber(), // 商户订单号
                    orderDB.getNumber(), // 商户退款单号
                    new BigDecimal(0.01),// 退款金额，单位 元
                    new BigDecimal(0.01));// 原订单金额
            // 支付状态修改为退款
            order.setPayStatus(Orders.REFUND);
        }

        // 若订单状态是待付款，则可以直接取消订单
        order.setStatus(Orders.CANCELLED);
        order.setCancelReason("用户取消订单");
        order.setCancelTime(LocalDateTime.now());

        orderMapper.update(order);
    }

    /**
     * 再来一单
     * 实现逻辑：将原订单的菜品重新加入购物车中
     *
     * @param id
     */
    public void repeatOrder(Long id) {
        Long userId = BaseContext.getCurrentId();

        // 查询订单明细表获得原订单的菜品明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单明细对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            // 将orderDetail中的商品明细数据拷贝到shoppingCart中（除了"id"字段）
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartList.add(shoppingCart);
        }

        // 将购物车数据插入到购物车表
        shoppingCartMapper.insertBatch(shoppingCartList);
    }


    /**
     * 商家后台：分页条件查询订单
     *
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch4Admin(OrdersPageQueryDTO ordersPageQueryDTO) {

        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<Orders> ordersList = page.getResult();
        List<OrderVO> orderVOList = new ArrayList<>();

        for (Orders order : ordersList) {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(order, orderVO);
            String orderDishes = getOrderDishesStringByOrderId(order.getId());  // 获取订单包含的商品的字符串
            orderVO.setOrderDishes(orderDishes);
            orderVOList.add(orderVO);
        }

        PageResult pageResult = new PageResult(page.getTotal(), orderVOList);
        return pageResult;
    }

    /**
     * 根据订单id查询订单包含的商品并生成字符串返回
     * 商品字符串格式为：宫保鸡丁* 3；红烧带鱼* 2；农家小炒肉* 1；
     *
     * @param orderId
     * @return
     */
    private String getOrderDishesStringByOrderId(Long orderId) {
        String orderDishes = "";    // 订单包含的菜品，以字符串的形式展示
        // 查询订单明细获取包含的菜品
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);
        for (OrderDetail orderDetail : orderDetails) {
            orderDishes += orderDetail.getName() + "*" + orderDetail.getNumber() + "；";
        }
        return orderDishes;
    }

    /**
     * 各个状态的订单数量统计
     * 统计待接单、待派送、派送中的订单数量
     *
     * @return
     */
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countByStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countByStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS);

        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);

        return orderStatisticsVO;
    }

    /**
     * 接单
     * 商家接单后订单状态变为待派送
     * @param ordersConfirmDTO
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders order = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(order);
    }

    /**
     * 拒单
     * 商家拒单后订单状态变为已取消
     * 拒单需要填写拒单原因，且只有待接单的订单才能拒单
     * 如果用户完成了支付，商家还需要为用户退款
     * @param ordersRejectionDTO
     */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        Long orderId = ordersRejectionDTO.getId();

        // 查询该订单
        Orders orderDB = orderMapper.getById(orderId);

        // 如果该订单不存在或不处于待接单状态，抛出业务异常
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        if (!orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders order = new Orders();
        if (orderDB.getPayStatus().equals(Orders.PAID)) {
            //用户已支付，需要退款
            /* 由于没有营业执照，微信支付功能并未实现，因此这里的退款功能也无法实现，测试功能时可以将退款部分代码注释掉，直接更新状态
            String refund = weChatPayUtil.refund(
                    orderDB.getNumber(),
                    orderDB.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            log.info("申请退款：{}", refund);
             */
            order.setPayStatus(Orders.REFUND);  // 支付状态改为退款
        }

        // 根据订单id更新订单状态、拒单原因、取消时间
        order.setId(orderId);
        order.setStatus(Orders.CANCELLED);   // 订单状态改为已取消
        order.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        order.setCancelTime(LocalDateTime.now());

        orderMapper.update(order);
    }


    /**
     * 商家取消订单
     * 取消订单后订单状态变为已取消，且需要注明取消原因
     * 如果用户完成了支付，商家还需要为用户退款
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        Long orderId = ordersCancelDTO.getId();
        Orders orderDB = orderMapper.getById(orderId);

        // 如果订单不存在，抛出业务异常
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Orders order = new Orders();
        if (orderDB.getPayStatus().equals(Orders.PAID)) {
            //用户已支付，需要退款
            /* 由于没有营业执照，微信支付功能并未实现，因此这里的退款功能也无法实现，测试功能时可以将退款部分代码注释掉，直接更新状态
            String refund = weChatPayUtil.refund(
                    orderDB.getNumber(),
                    orderDB.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            log.info("申请退款：{}", refund);
             */
            order.setPayStatus(Orders.REFUND);
        }

        // 根据订单id更新订单状态、拒单原因、取消时间
        order.setId(orderId);
        order.setStatus(Orders.CANCELLED);   // 订单状态改为已取消
        order.setCancelReason(ordersCancelDTO.getCancelReason());
        order.setCancelTime(LocalDateTime.now());

        orderMapper.update(order);
    }

    /**
     * 派送订单
     * 只有已接单的订单才能派送
     * @param id
     */
    public void delivery(Long id) {
        Orders orderDB = orderMapper.getById(id);

        // 如果订单不存在，抛出业务异常
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Integer status = orderDB.getStatus();
        // 如果订单此前不是已接单状态，抛出业务异常
        if (!status.equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders order = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)    // 订单状态改为派送中
                .build();

        orderMapper.update(order);
    }

    /**
     * 完成订单
     * 只有派送中的订单才能完成
     * @param id
     */
    public void complete(Long id) {
        Orders orderDB = orderMapper.getById(id);

        // 如果订单不存在，抛出业务异常
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Integer status = orderDB.getStatus();
        // 如果订单此前不是派送中状态，抛出业务异常
        if (!status.equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders order = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)    // 订单状态改为已完成
                .deliveryTime(LocalDateTime.now())   // 派送时间
                .build();

        orderMapper.update(order);
    }

    /**
     * 客户催单
     * 当订单处于待接单状态时，客户可以催单，服务器会向商家后台的客户端浏览器推送消息
     * @param id
     */
    public void reminder(Long id) {
        Orders orderDB = orderMapper.getById(id);

        // 如果订单不存在，抛出业务异常
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Integer status = orderDB.getStatus();
        // 如果订单不是处于待接单状态，抛出业务异常
        if (!status.equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 通过websocket向商家后台的客户端浏览器推送消息，采用json格式，包含：type, orderId, content
        Map map = new HashMap();
        map.put("type", 2); // 约定：1表示来单提醒，2表示客户催单
        map.put("orderId", id);
        map.put("content", "订单号：" + orderDB.getNumber());
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }
}
