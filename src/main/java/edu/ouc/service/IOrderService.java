package edu.ouc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import edu.ouc.dto.OrderDto;
import edu.ouc.entity.Orders;

/**
 * @Author: Sihang Xie
 * @Description: 订单服务层接口
 * @Date: 2022/10/27 10:29
 * @Version: 0.0.1
 * @Modified By:
 */
public interface IOrderService extends IService<Orders> {

    // 提交(添加)订单，返回订单对象(含订单号)
    Orders submit(Orders orders);

    // 用户支付，更新订单状态为待派送
    Boolean pay(Long orderId, Integer payMethod);

    // 根据ID查询单个订单
    Orders getOrderById(Long id);

    // 获取订单分页展示
    Page<OrderDto> getPage(Long page, Long pageSize);

    // 后台管理端获取订单分页展示
    Page<OrderDto> getAllPage(Long page, Long pageSize, String number, String beginTime, String endTime);

    // 修改订单状态
    Boolean update(Orders order);

    // 获取待处理订单数量（status=2待派送 + status=3已派送）
    Integer getNewOrderCount();
}