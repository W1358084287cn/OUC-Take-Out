package edu.ouc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import edu.ouc.dto.OrderDto;
import edu.ouc.entity.Orders;
import edu.ouc.entity.RefundRequest;

import java.util.List;

/**
 * Author: Sihang Xie
 * Description: 订单服务层接口
 * Date: 2022/10/27 10:29
 * Version: 0.0.1
 * Modified By:
 */
public interface IOrderService extends IService<Orders> {

    // 提交(添加)订单，返回订单对象(含订单号)
    Orders submit(Orders orders);

    // 用户支付，更新订单状态为等待商家接单
    Boolean pay(Long orderId, Integer payMethod);

    // 商家接单，状态 2→3 制作中
    Boolean acceptOrder(Long orderId);

    // 根据ID查询单个订单
    Orders getOrderById(Long id);

    // 获取订单分页展示
    Page<OrderDto> getPage(Long page, Long pageSize);

    // 后台管理端获取订单分页展示
    Page<OrderDto> getAllPage(Long page, Long pageSize, String number, String beginTime, String endTime);

    // 修改订单状态
    Boolean update(Orders order);

    // 获取待处理订单数量（等待商家接单+制作中）
    Integer getNewOrderCount();

    // 退款
    Boolean refund(Long orderId, String reason);

    // 客户发起退款申请
    RefundRequest requestRefund(RefundRequest request);

    // 客户查询退款进度
    RefundRequest getRefundStatus(Long orderId);

    // 商家查看退款申请列表
    List<RefundRequest> getRefundRequests();

    // 商家处理退款（同意/拒绝/部分退款）
    RefundRequest handleRefund(RefundRequest request);

    // 加餐(追加菜品)
    Boolean addItems(Long orderId);

    // 批量删除订单（同时删除订单明细）
    int batchDelete(List<Long> ids);

    // 根据ID删除单个订单（同时删除订单明细）
    int deleteById(Long id);

    // 清理超过指定天数的历史订单（同时删除订单明细）
    int cleanOldOrders(int retentionDays);

    // 删除全部订单及关联明细
    int deleteAll();

    // 再来一单：将历史订单菜品重新加入购物车
    void again(Long orderId);
}