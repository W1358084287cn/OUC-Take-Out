package edu.ouc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import edu.ouc.common.BaseContext;
import edu.ouc.common.CashRegisterContext;
import edu.ouc.common.CustomException;
import edu.ouc.common.RefundContext;
import edu.ouc.dto.OrderDto;
import edu.ouc.entity.*;
import edu.ouc.kitchen.service.IKitchenOrderService;
import edu.ouc.mapper.OrderMapper;
import edu.ouc.service.IDishService;
import edu.ouc.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Author: Sihang Xie
 * Description: 订单表orders业务层接口实现类
 * Date: 2022/10/27 10:32
 * Version: 0.0.1
 * Modified By:
 */
@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Orders> implements IOrderService {

    private final OrderDetailServiceImpl orderDetailService;
    private final ShoppingCartServiceImpl shoppingCartService;
    private final UserServiceImpl userService;
    private final IKitchenOrderService kitchenOrderService;
    private final IDishService dishService;
    private final RefundContext refundContext;
    private final CashRegisterContext cashRegisterContext;

    public OrderServiceImpl(OrderDetailServiceImpl orderDetailService, ShoppingCartServiceImpl shoppingCartService,
                            UserServiceImpl userService,
                            @org.springframework.context.annotation.Lazy IKitchenOrderService kitchenOrderService,
                            @org.springframework.context.annotation.Lazy IDishService dishService,
                            RefundContext refundContext,
                            CashRegisterContext cashRegisterContext) {
        this.orderDetailService = orderDetailService;
        this.shoppingCartService = shoppingCartService;
        this.userService = userService;
        this.kitchenOrderService = kitchenOrderService;
        this.dishService = dishService;
        this.refundContext = refundContext;
        this.cashRegisterContext = cashRegisterContext;
    }

    // 提交(添加)订单，返回订单对象(含订单号)
    @Override
    @Transactional  // 涉及到两张表的插入操作需要打开事务控制
    public Orders submit(Orders orders) {

        // 1.获取当前登录用户ID
        Long userId = BaseContext.getCurrentUserId();


        // 2.调用购物车ShoppingCart业务层的获取购物车信息
        LambdaQueryWrapper<ShoppingCart> shoppingCartLqw = new LambdaQueryWrapper<>();
        shoppingCartLqw.eq(userId != null, ShoppingCart::getUserId, userId);
        List<ShoppingCart> shoppingCarts = shoppingCartService.list(shoppingCartLqw);
        // 如果购物车为空，则抛出业务异常
        if (shoppingCarts.isEmpty()) {
            throw new CustomException("购物车为空，无法结算");
        }


        // 3.判断是否为桌台点餐模式
        boolean isTableMode = StringUtils.isNotEmpty(orders.getTableNo());


        // 4.调用用户业务层user表获取用户信息
        User user = userService.getById(userId);
        if (user == null) {
            throw new CustomException("用户信息获取失败，请确认已登录C端账号（如同时登录了管理端，请使用无痕窗口打开C端）");
        }


        // 5.为订单对象的属性一一赋值
        long orderId = IdWorker.getId();
        // 使用AtomicReference<BigDecimal>计算商品总金额，保证高并发下的线程安全且不丢失小数精度
        AtomicReference<BigDecimal> amount = new AtomicReference<>(BigDecimal.ZERO);

        // 6.新增订单明细，用购物车的stream流复制
        List<OrderDetail> orderDetails = shoppingCarts.stream().map(shoppingCart -> {
            OrderDetail orderDetail = new OrderDetail();
            // 复制属性值
            orderDetail.setOrderId(orderId);
            orderDetail.setNumber(shoppingCart.getNumber());
            orderDetail.setDishFlavor(shoppingCart.getDishFlavor());
            orderDetail.setDishId(shoppingCart.getDishId());
            orderDetail.setSetmealId(shoppingCart.getSetmealId());
            orderDetail.setName(shoppingCart.getName());
            orderDetail.setImage(shoppingCart.getImage());
            orderDetail.setAmount(shoppingCart.getAmount());
            // 计算订单总金额
            amount.set(amount.get().add(shoppingCart.getAmount().multiply(new BigDecimal(shoppingCart.getNumber()))));
            return orderDetail;
        }).collect(Collectors.toList());

        // 生成并设置订单号
        orders.setId(orderId);
        orders.setNumber(String.valueOf(orderId));
        // 设置下单用户ID
        orders.setUserId(userId);
        // 设置订单状态为待付款(支付完成后再改为制作中)
        orders.setStatus(1);
        // 设置商品总金额
        orders.setAmount(amount.get());
        // 设置订单客户联系方式（堂食用桌号，带走用用户邮箱，截断防超长）
        String contact = isTableMode ? ("T" + orders.getTableNo()) : user.getEmail();
        orders.setPhone(contact.length() > 20 ? contact.substring(0, 20) : contact);
        // 设置收货人（堂食用桌号，带走用用户名）
        orders.setConsignee(isTableMode ? ("桌号" + orders.getTableNo()) : user.getName());
        // 设置下单用户名
        orders.setUserName(user.getName());
        // 设置地址详情（堂食用桌号，带走显示自取）
        orders.setAddress(isTableMode ? ("桌号" + orders.getTableNo()) : "自取");

        // 7.调用订单数据层新增订单
        this.save(orders);


        // 8.批量新增订单明细
        orderDetailService.saveBatch(orderDetails);

        // 9.下单完成后清空购物车数据
        shoppingCartService.remove(shoppingCartLqw);

        // 返回订单对象(含订单号、金额等信息，供前端支付页面使用)
        return orders;
    }

    // 根据ID查询单个订单
    @Override
    public Orders getOrderById(Long id) {
        return this.getById(id);
    }

    // 用户支付，更新订单状态为制作中
    @Override
    @Transactional
    public Boolean pay(Long orderId, Integer payMethod) {
        // 1.根据订单ID查询订单
        Orders order = this.getById(orderId);
        if (order == null) {
            throw new CustomException("订单不存在");
        }
        // 2.校验订单状态是否为待付款
        if (order.getStatus() != 1) {
            throw new CustomException("订单状态异常，无法支付");
        }
        // 3.更新订单状态为制作中、支付方式、支付时间
        order.setStatus(2);
        order.setPayMethod(payMethod);
        order.setCheckoutTime(LocalDateTime.now());
        // 4.执行更新
        boolean result = this.updateById(order);

        // 联动收银台：支付成功即记收入
        if (result && order.getAmount() != null) {
            cashRegisterContext.addIncome(order.getAmount());
            log.info("收银台联动：支付成功记收入, orderId={}, amount={}", orderId, order.getAmount());
        }
        return result;
    }

    // 获取订单分页展示
    @Override
    public Page<OrderDto> getPage(Long page, Long pageSize) {
        // 1.创建分页封装器
        Page<Orders> ordersPage = new Page<>(page, pageSize);
        // 2.创建OrderDto的分页封装器
        Page<OrderDto> dtoPage = new Page<>();

        // 3.创建Orders的查询条件封装器
        LambdaQueryWrapper<Orders> lqw = new LambdaQueryWrapper<>();
        // 3.1 添加查询条件：按下单时间降序排列
        lqw.orderByDesc(Orders::getOrderTime);
        // 3.2 条件查询条件：按当前用户ID查询
        Long userId = BaseContext.getCurrentUserId();
        lqw.eq(userId != null, Orders::getUserId, userId);
        // 4.Orders分页查询
        this.page(ordersPage, lqw);

        // 5.除了Record都复制
        BeanUtils.copyProperties(ordersPage, dtoPage, "records");

        // 6.获取当前页的order对象（C端）
        List<Orders> orders = ordersPage.getRecords();
        // 7.通过stream流逐一包装成OrderDto对象
        List<OrderDto> orderDtos = orders.stream().map(order -> {
            // 7.1 创建OrderDto对象
            OrderDto orderDto = new OrderDto();
            // 7.2 拷贝属性
            BeanUtils.copyProperties(order, orderDto);
            // 7.3 调用OrderDetail业务层获取订单明细集合
            LambdaQueryWrapper<OrderDetail> orderDetailLqw = new LambdaQueryWrapper<>();
            orderDetailLqw.eq(OrderDetail::getOrderId, order.getId());
            List<OrderDetail> orderDetails = orderDetailService.list(orderDetailLqw);
            // 7.4 设置orderDto的订单明细属性
            orderDto.setOrderDetails(orderDetails);
            // 7.5 返回orderDto
            return orderDto;
        }).collect(Collectors.toList());

        // 8.设置dtoPage的records属性
        dtoPage.setRecords(orderDtos);
        return dtoPage;
    }

    @Override
    public Page<OrderDto> getAllPage(Long page, Long pageSize, String number, String beginTime, String endTime) {
        // 1.创建分页封装器
        Page<Orders> ordersPage = new Page<>(page, pageSize);
        // 2.创建OrderDto的分页封装器
        Page<OrderDto> dtoPage = new Page<>();

        // 3.创建Orders的查询条件封装器
        LambdaQueryWrapper<Orders> lqw = new LambdaQueryWrapper<>();
        // 3.1 添加查询条件：按下单时间降序排列
        lqw.orderByDesc(Orders::getOrderTime);
        // 3.2 添加查询条件：按订单号查询
        lqw.like(number != null, Orders::getNumber, number);
        // 3.3 添加查询条件：  动态SQL-字符串使用StringUtils.isNotEmpty这个方法来判断
        lqw.gt(StringUtils.isNotEmpty(beginTime), Orders::getOrderTime, beginTime);
        lqw.lt(StringUtils.isNotEmpty(endTime), Orders::getOrderTime, endTime);
        // 4.Orders分页查询
        this.page(ordersPage, lqw);

        // 5.除了Record都复制
        BeanUtils.copyProperties(ordersPage, dtoPage, "records");

        // 6.获取当前页的order对象（后台管理端）
        List<Orders> orders = ordersPage.getRecords();
        // 7.通过stream流逐一包装成OrderDto对象
        List<OrderDto> orderDtos = orders.stream().map(order -> {
            // 7.1 创建OrderDto对象
            OrderDto orderDto = new OrderDto();
            // 7.2 拷贝属性
            BeanUtils.copyProperties(order, orderDto);
            // 7.3 调用OrderDetail业务层获取订单明细集合
            LambdaQueryWrapper<OrderDetail> orderDetailLqw = new LambdaQueryWrapper<>();
            orderDetailLqw.eq(OrderDetail::getOrderId, order.getId());
            List<OrderDetail> orderDetails = orderDetailService.list(orderDetailLqw);
            // 7.4 设置orderDto的订单明细属性
            orderDto.setOrderDetails(orderDetails);
            // 7.5 返回orderDto
            return orderDto;
        }).collect(Collectors.toList());

        // 8.设置dtoPage的records属性
        dtoPage.setRecords(orderDtos);
        return dtoPage;
    }

    // 修改订单状态
    @Override
    public Boolean update(Orders order) {
        log.info("修改订单状态: id={}, status={}", order.getId(), order.getStatus());
        boolean result = this.updateById(order);
        log.info("修改订单状态结果: id={}, status={}, result={}", order.getId(), order.getStatus(), result);
        return result;
    }

    // 获取待处理订单数量（status=2制作中）
    @Override
    public Integer getNewOrderCount() {
        LambdaQueryWrapper<Orders> lqw = new LambdaQueryWrapper<>();
        lqw.in(Orders::getStatus, 2);
        return Math.toIntExact(this.count(lqw));
    }

    // 退款
    @Override
    @Transactional
    public Boolean refund(Long orderId, String reason) {
        if (orderId == null) {
            throw new CustomException("订单ID不能为空");
        }
        Orders order = this.getById(orderId);
        if (order == null) {
            throw new CustomException("订单不存在");
        }
        Integer status = order.getStatus();
        if (status != 1 && status != 2) {
            throw new CustomException("仅待付款或制作中的订单可退款");
        }
        // 更新订单状态为已退款
        order.setStatus(6);
        boolean updated = this.updateById(order);

        // 退款联动0：收银台记录退款金额（仅制作中的订单已记收入，需扣减）
        if (status == 2 && order.getAmount() != null) {
            cashRegisterContext.addRefund(order.getAmount());
            log.info("收银台联动：退款扣减收入, orderId={}, amount={}", orderId, order.getAmount());
        }

        // 退款联动1：清理后厨任务（安全调用，未注册后厨的订单不会抛异常）
        try {
            kitchenOrderService.cancelOrder(orderId, true);
        } catch (Exception e) {
            log.warn("退款清理后厨任务失败（可能订单未注册后厨）: orderId={}, error={}", orderId, e.getMessage());
        }

        // 退款联动2：恢复订单中已估清的菜品
        try {
            LambdaQueryWrapper<OrderDetail> detailLqw = new LambdaQueryWrapper<>();
            detailLqw.eq(OrderDetail::getOrderId, orderId);
            List<OrderDetail> details = orderDetailService.list(detailLqw);
            if (details != null && !details.isEmpty()) {
                for (OrderDetail detail : details) {
                    Long dishId = detail.getDishId();
                    if (dishId != null) {
                        Dish dish = dishService.getById(dishId);
                        if (dish != null && dish.getStatus() == 2) {
                            dish.setStatus(1);
                            dishService.updateById(dish);
                            log.info("退款恢复估清菜品: dishId={}, dishName={}", dishId, dish.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("退款恢复估清菜品失败: orderId={}, error={}", orderId, e.getMessage());
        }

        log.info("订单退款成功: orderId={}, orderNumber={}, reason={}", orderId, order.getNumber(), reason);
        return updated;
    }

    // 加餐(追加菜品)
    @Override
    @Transactional
    public Boolean addItems(Long orderId) {
        if (orderId == null) {
            throw new CustomException("订单ID不能为空");
        }
        Orders order = this.getById(orderId);
        if (order == null) {
            throw new CustomException("订单不存在");
        }
        if (order.getStatus() != 2) {
            throw new CustomException("仅制作中的订单可加餐");
        }
        Long userId = BaseContext.getCurrentUserId();
        // 查询当前用户购物车
        LambdaQueryWrapper<ShoppingCart> cartLqw = new LambdaQueryWrapper<>();
        cartLqw.eq(ShoppingCart::getUserId, userId);
        List<ShoppingCart> cartItems = shoppingCartService.list(cartLqw);
        if (cartItems.isEmpty()) {
            throw new CustomException("购物车为空，无法加餐");
        }
        // 将购物车项目转为订单明细
        List<OrderDetail> orderDetails = cartItems.stream().map(item -> {
            OrderDetail detail = new OrderDetail();
            detail.setOrderId(orderId);
            detail.setNumber(item.getNumber());
            detail.setDishFlavor(item.getDishFlavor());
            detail.setDishId(item.getDishId());
            detail.setSetmealId(item.getSetmealId());
            detail.setName(item.getName());
            detail.setImage(item.getImage());
            detail.setAmount(item.getAmount());
            return detail;
        }).collect(Collectors.toList());
        // 批量新增订单明细
        orderDetailService.saveBatch(orderDetails);
        // 累加订单金额
        BigDecimal addAmount = cartItems.stream()
                .map(item -> item.getAmount().multiply(new BigDecimal(item.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setAmount(order.getAmount().add(addAmount));
        this.updateById(order);
        // 清空购物车
        shoppingCartService.remove(cartLqw);

        // 加餐联动：将新菜品加入后厨加工队列
        try {
            List<IKitchenOrderService.DishInfo> dishInfos = cartItems.stream()
                    .filter(item -> item.getDishId() != null)
                    .map(item -> {
                        Integer duration = 15; // 默认烹饪耗时（分钟）
                        Dish dish = dishService.getById(item.getDishId());
                        if (dish != null) {
                            return new IKitchenOrderService.DishInfo(
                                    item.getDishId(), dish.getName(), duration);
                        }
                        return new IKitchenOrderService.DishInfo(
                                item.getDishId(), item.getName(), duration);
                    })
                    .collect(Collectors.toList());
            if (!dishInfos.isEmpty()) {
                kitchenOrderService.addDishes(orderId, dishInfos);
            }
        } catch (Exception e) {
            log.warn("加餐同步后厨任务失败（可能订单未注册后厨）: orderId={}, error={}", orderId, e.getMessage());
        }

        log.info("加餐成功: orderId={}, 新增{}件商品, 追加金额={}", orderId, cartItems.size(), addAmount);
        return true;
    }

    // 批量删除订单及关联明细
    @Override
    @Transactional
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new CustomException("请选择要删除的订单");
        }
        // 1.先删除订单明细（子表）
        LambdaQueryWrapper<OrderDetail> detailLqw = new LambdaQueryWrapper<>();
        detailLqw.in(OrderDetail::getOrderId, ids);
        long detailDeleted = orderDetailService.count(detailLqw);
        orderDetailService.remove(detailLqw);
        // 2.再删除订单（主表）
        int orderDeleted = this.getBaseMapper().deleteBatchIds(ids);
        log.info("批量删除订单: 订单{}笔, 明细{}条", orderDeleted, detailDeleted);
        return orderDeleted;
    }

    // 删除单个订单及关联明细
    @Override
    @Transactional
    public int deleteById(Long id) {
        if (id == null) {
            throw new CustomException("订单ID不能为空");
        }
        // 1.先删除订单明细（子表）
        LambdaQueryWrapper<OrderDetail> detailLqw = new LambdaQueryWrapper<>();
        detailLqw.eq(OrderDetail::getOrderId, id);
        orderDetailService.remove(detailLqw);
        // 2.再删除订单（主表）
        int deleted = this.getBaseMapper().deleteById(id);
        log.info("删除单个订单: id={}, result={}", id, deleted);
        return deleted;
    }

    // 清理超过指定天数的历史订单及关联明细
    @Override
    @Transactional
    public int cleanOldOrders(int retentionDays) {
        // 计算截止时间：当前时间向前推retentionDays天
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
        log.info("清理历史订单: 保留{}天，截止时间={}", retentionDays, cutoffTime);
        // 1.查询符合条件的订单ID
        LambdaQueryWrapper<Orders> orderLqw = new LambdaQueryWrapper<>();
        orderLqw.lt(Orders::getOrderTime, cutoffTime);
        List<Orders> oldOrders = this.list(orderLqw);
        if (oldOrders.isEmpty()) {
            log.info("没有需要清理的历史订单");
            return 0;
        }
        List<Long> orderIds = oldOrders.stream().map(Orders::getId).collect(Collectors.toList());
        // 2.先删除订单明细（子表）
        LambdaQueryWrapper<OrderDetail> detailLqw = new LambdaQueryWrapper<>();
        detailLqw.in(OrderDetail::getOrderId, orderIds);
        long detailDeleted = orderDetailService.count(detailLqw);
        orderDetailService.remove(detailLqw);
        // 3.再删除订单（主表）
        int orderDeleted = this.getBaseMapper().deleteBatchIds(orderIds);
        log.info("历史订单清理完成: 订单{}笔, 明细{}条", orderDeleted, detailDeleted);
        return orderDeleted;
    }

    // 客户发起退款申请
    @Override
    @Transactional
    public RefundRequest requestRefund(RefundRequest request) {
        Long orderId = request.getOrderId();
        Long userId = BaseContext.getCurrentUserId();
        if (orderId == null) {
            throw new CustomException("订单ID不能为空");
        }
        if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException("退款金额必须大于0");
        }
        // 校验订单存在且属于当前用户
        Orders order = this.getById(orderId);
        if (order == null) {
            throw new CustomException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new CustomException("只能对自己的订单发起退款");
        }
        // 只有制作中(2)或已完成(3)的订单可退款
        if (order.getStatus() != 2 && order.getStatus() != 3) {
            throw new CustomException("当前订单状态不可退款");
        }
        // 退款金额不能超过订单金额
        if (request.getRefundAmount().compareTo(order.getAmount()) > 0) {
            throw new CustomException("退款金额不能超过订单金额");
        }
        // 同一订单不能重复申请（仅阻断有进行中请求的订单，被拒后可重新申请）
        RefundRequest existing = refundContext.getByOrderId(orderId);
        if (existing != null && existing.getStatus() == 0) {
            throw new CustomException("该订单已有退款申请在处理中");
        }
        // 构建退款申请
        RefundRequest refund = new RefundRequest();
        refund.setRefundId(IdWorker.getId());
        refund.setOrderId(orderId);
        refund.setUserId(userId);
        refund.setRefundAmount(request.getRefundAmount());
        refund.setRefundReason(request.getRefundReason());
        refund.setStatus(0);
        refund.setOriginalOrderStatus(order.getStatus());
        refund.setCreateTime(LocalDateTime.now());
        refund.setUpdateTime(LocalDateTime.now());
        // 存入内存
        refundContext.put(refund);
        // 更新订单状态为退款申请中
        order.setStatus(7);
        this.updateById(order);
        log.info("客户发起退款申请: refundId={}, orderId={}, amount={}", refund.getRefundId(), orderId, refund.getRefundAmount());
        return refund;
    }

    // 客户查询退款进度
    @Override
    public RefundRequest getRefundStatus(Long orderId) {
        if (orderId == null) {
            throw new CustomException("订单ID不能为空");
        }
        RefundRequest refund = refundContext.getByOrderId(orderId);
        if (refund == null) {
            throw new CustomException("未找到退款申请");
        }
        return refund;
    }

    // 商家查看退款申请列表
    @Override
    public List<RefundRequest> getRefundRequests() {
        return refundContext.getPendingList();
    }

    // 商家处理退款（同意/拒绝/部分退款）
    @Override
    @Transactional
    public RefundRequest handleRefund(RefundRequest request) {
        Long refundId = request.getRefundId();
        if (refundId == null) {
            throw new CustomException("退款申请ID不能为空");
        }
        RefundRequest refund = refundContext.getById(refundId);
        if (refund == null) {
            throw new CustomException("退款申请不存在");
        }
        if (refund.getStatus() != 0) {
            throw new CustomException("该退款申请已处理过");
        }
        Integer handleStatus = request.getStatus();
        if (handleStatus == null || (handleStatus != 1 && handleStatus != 2 && handleStatus != 3)) {
            throw new CustomException("处理状态无效，请选择同意/拒绝/部分退款");
        }
        // 同意
        if (handleStatus == 1) {
            refund.setStatus(1);
            refund.setActualRefund(refund.getRefundAmount());
            refund.setMerchantReply(request.getMerchantReply());
            refund.setUpdateTime(LocalDateTime.now());
            // 更新订单状态为已退款
            Orders order = this.getById(refund.getOrderId());
            if (order != null) {
                order.setStatus(6);
                this.updateById(order);
                // 恢复已估清菜品
                restoreSoldOutDishes(refund.getOrderId());
                // 联动收银台：累加退款金额
                cashRegisterContext.addRefund(refund.getRefundAmount());
            }
        }
        // 拒绝
        if (handleStatus == 2) {
            refund.setStatus(2);
            refund.setMerchantReply(request.getMerchantReply());
            refund.setUpdateTime(LocalDateTime.now());
            // 恢复订单原始状态
            Orders order = this.getById(refund.getOrderId());
            if (order != null && order.getStatus() == 7) {
                Integer originalStatus = refund.getOriginalOrderStatus();
                order.setStatus(originalStatus != null ? originalStatus : 2);
                this.updateById(order);
            }
        }
        // 部分退款
        if (handleStatus == 3) {
            if (request.getActualRefund() == null || request.getActualRefund().compareTo(BigDecimal.ZERO) <= 0) {
                throw new CustomException("部分退款金额必须大于0");
            }
            if (request.getActualRefund().compareTo(refund.getRefundAmount()) > 0) {
                throw new CustomException("实际退款金额不能超过申请金额");
            }
            refund.setStatus(3);
            refund.setActualRefund(request.getActualRefund());
            refund.setMerchantReply(request.getMerchantReply());
            refund.setUpdateTime(LocalDateTime.now());
            // 更新订单状态为部分退款
            Orders order = this.getById(refund.getOrderId());
            if (order != null) {
                order.setStatus(8);
                this.updateById(order);
                // 恢复已估清菜品
                restoreSoldOutDishes(refund.getOrderId());
                // 联动收银台：累加退款金额
                cashRegisterContext.addRefund(request.getActualRefund());
            }
        }
        refundContext.update(refund);
        log.info("商家处理退款: refundId={}, status={}, actualRefund={}", refundId, handleStatus, refund.getActualRefund());
        return refund;
    }

    // 恢复订单中已估清的菜品
    private void restoreSoldOutDishes(Long orderId) {
        try {
            LambdaQueryWrapper<OrderDetail> detailLqw = new LambdaQueryWrapper<>();
            detailLqw.eq(OrderDetail::getOrderId, orderId);
            List<OrderDetail> details = orderDetailService.list(detailLqw);
            if (details != null && !details.isEmpty()) {
                for (OrderDetail detail : details) {
                    Long dishId = detail.getDishId();
                    if (dishId != null) {
                        Dish dish = dishService.getById(dishId);
                        if (dish != null && dish.getStatus() == 2) {
                            dish.setStatus(1);
                            dishService.updateById(dish);
                            log.info("退款恢复估清菜品: dishId={}, dishName={}", dishId, dish.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("退款恢复估清菜品失败: orderId={}, error={}", orderId, e.getMessage());
        }
    }
}