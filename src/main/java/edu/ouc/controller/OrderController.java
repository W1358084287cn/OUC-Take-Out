package edu.ouc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.ouc.common.R;
import edu.ouc.common.RefundContext;
import edu.ouc.dto.OrderDto;
import edu.ouc.entity.Orders;
import edu.ouc.entity.RefundRequest;
import edu.ouc.service.impl.OrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Author: Sihang Xie
 * Description: 订单控制层
 * Date: 2022/10/27 10:45
 * Version: 0.0.1
 * Modified By:
 */
@Slf4j
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {
    private final OrderServiceImpl orderService;
    private final RefundContext refundContext;

    @Value("${reggie.order-retention-days:90}")
    private int retentionDays;

    // 提交(添加)订单，返回订单对象(含订单号、金额)
    @PostMapping("/submit")
    public R<Orders> submit(@RequestBody Orders orders) {
        log.info("下单请求: tableNo={}, payMethod={}, remark={}", orders.getTableNo(), orders.getPayMethod(), orders.getRemark());
        Orders order = orderService.submit(orders);
        if (order != null) {
            return R.success(order);
        }
        return R.error("下单失败");
    }

    // 用户支付：更新订单状态为等待商家接单，记录支付方式和支付时间
    @PostMapping("/pay")
    public R<String> pay(@RequestBody Orders orders) {
        log.info("支付请求: orderId={}, payMethod={}", orders.getId(), orders.getPayMethod());
        if (orderService.pay(orders.getId(), orders.getPayMethod())) {
            return R.success("支付成功");
        }
        return R.error("支付失败");
    }

    // 商家接单：状态 2→3 制作中
    @PostMapping("/accept")
    public R<String> accept(@RequestBody Map<String, Long> params) {
        Long orderId = params.get("orderId");
        log.info("商家接单请求: orderId={}", orderId);
        if (orderService.acceptOrder(orderId)) {
            return R.success("接单成功");
        }
        return R.error("接单失败");
    }

    // 用户端获取订单分页展示
    @GetMapping("/userPage")
    public R<Page<OrderDto>> getPage(Long page, Long pageSize) {
        return R.success(orderService.getPage(page, pageSize));
    }

    // 后台管理端获取待处理订单数量
    @GetMapping("/newCount")
    public R<Integer> getNewCount() {
        return R.success(orderService.getNewOrderCount());
    }

    // 后台管理端获取订单分页展示
    @GetMapping("/page")
    public R<Page<OrderDto>> page(Long page, Long pageSize, String number, String beginTime, String endTime) {
        return R.success(orderService.getAllPage(page, pageSize, number, beginTime, endTime));
    }

    // 根据ID查询单个订单详情
    @GetMapping("/detail/{id}")
    public R<Orders> getById(@PathVariable Long id) {
        log.info("查询订单详情: id={}", id);
        Orders order = orderService.getOrderById(id);
        if (order != null) {
            return R.success(order);
        }
        return R.error("订单不存在");
    }

    // 修改订单状态
    @PutMapping
    public R<String> update(@RequestBody Orders order) {
        log.info("修改订单状态请求: id={}, status={}", order.getId(), order.getStatus());
        if (orderService.update(order)) {
            return R.success("修改成功");
        }
        return R.error("修改失败");
    }

    // 退款
    @PostMapping("/refund")
    public R<String> refund(@RequestBody Orders orders) {
        log.info("退款请求: orderId={}, reason={}", orders.getId(), orders.getRemark());
        if (orderService.refund(orders.getId(), orders.getRemark())) {
            return R.success("退款成功");
        }
        return R.error("退款失败");
    }

    // 加餐
    @PostMapping("/addItems")
    public R<String> addItems(@RequestBody Orders orders) {
        log.info("加餐请求: orderId={}", orders.getId());
        if (orderService.addItems(orders.getId())) {
            return R.success("加餐成功");
        }
        return R.error("加餐失败");
    }

    // 批量删除订单
    @DeleteMapping("/batch")
    public R<String> batchDelete(@RequestBody Map<String, List<Long>> params) {
        List<Long> ids = params.get("ids");
        log.info("批量删除订单: ids={}", ids);
        int count = orderService.batchDelete(ids);
        return R.success("成功删除" + count + "笔订单");
    }

    // 删除单个订单
    @DeleteMapping("/{id}")
    public R<String> deleteById(@PathVariable Long id) {
        log.info("删除单个订单: id={}", id);
        int count = orderService.deleteById(id);
        return R.success("删除成功");
    }

    // 手动清理历史订单（参数：保留天数）
    @PostMapping("/clean")
    public R<String> cleanOldOrders(@RequestBody Map<String, Integer> params) {
        Integer days = params.get("days");
        log.info("清理历史订单请求: days={}", days);
        if (days == null || days <= 0) {
            return R.error("保留天数必须大于0");
        }
        int count = orderService.cleanOldOrders(days);
        return R.success("成功清理" + count + "笔历史订单");
    }

    // 删除全部订单及关联明细
    @DeleteMapping("/deleteAll")
    public R<String> deleteAll() {
        log.info("删除全部订单请求");
        int count = orderService.deleteAll();
        return R.success("成功删除全部" + count + "笔订单");
    }

    // 再来一单
    @PostMapping("/again")
    public R<String> again(@RequestBody Map<String, Long> params) {
        Long orderId = params.get("id");
        log.info("再来一单请求: orderId={}", orderId);
        if (orderId == null) {
            return R.error("订单ID不能为空");
        }
        orderService.again(orderId);
        return R.success("已加入购物车");
    }

    // 获取订单保留天数配置
    @GetMapping("/retention-days")
    public R<Integer> getRetentionDays() {
        return R.success(retentionDays);
    }

    // 客户发起退款申请
    @PostMapping("/requestRefund")
    public R<RefundRequest> requestRefund(@RequestBody RefundRequest refundRequest) {
        log.info("客户发起退款: orderId={}, amount={}", refundRequest.getOrderId(), refundRequest.getRefundAmount());
        RefundRequest result = orderService.requestRefund(refundRequest);
        return R.success(result);
    }

    // 客户查询退款进度
    @GetMapping("/refundStatus/{orderId}")
    public R<RefundRequest> getRefundStatus(@PathVariable Long orderId) {
        log.info("查询退款进度: orderId={}", orderId);
        RefundRequest refund = orderService.getRefundStatus(orderId);
        return R.success(refund);
    }

    // 商家查看退款申请列表
    @GetMapping("/refundRequests")
    public R<List<RefundRequest>> getRefundRequests() {
        log.info("商家查看退款申请列表");
        List<RefundRequest> list = orderService.getRefundRequests();
        return R.success(list);
    }

    // 商家处理退款（同意/拒绝/部分退款）
    @PostMapping("/handleRefund")
    public R<RefundRequest> handleRefund(@RequestBody RefundRequest refundRequest) {
        log.info("商家处理退款: refundId={}, status={}", refundRequest.getRefundId(), refundRequest.getStatus());
        RefundRequest result = orderService.handleRefund(refundRequest);
        return R.success(result);
    }

    // 获取未读退款数量（用于红点展示）
    @GetMapping("/refundUnreadCount")
    public R<Integer> getRefundUnreadCount() {
        int count = refundContext.getUnreadCount();
        log.info("未读退款数: {}", count);
        return R.success(count);
    }

    // 标记所有退款为已读（打开弹窗后调用）
    @PostMapping("/markRefundViewed")
    public R<String> markRefundViewed() {
        refundContext.markAllViewed();
        log.info("退款已标记为已读");
        return R.success("已标记");
    }
}