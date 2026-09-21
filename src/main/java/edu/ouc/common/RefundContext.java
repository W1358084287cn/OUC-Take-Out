package edu.ouc.common;

import edu.ouc.entity.RefundRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 退款申请内存上下文（单例Bean，纯内存存储）
 */
@Slf4j
@Component
public class RefundContext {

    private final ConcurrentHashMap<Long, RefundRequest> refundStore = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, Long> orderRefundIndex = new ConcurrentHashMap<>();

    /** 商家已读退款ID集合 */
    private final Set<Long> viewedRefundIds = ConcurrentHashMap.newKeySet();

    /**
     * 存入退款申请
     */
    public void put(RefundRequest refund) {
        refundStore.put(refund.getRefundId(), refund);
        orderRefundIndex.put(refund.getOrderId(), refund.getRefundId());
        log.info("退款申请已存入内存: refundId={}, orderId={}", refund.getRefundId(), refund.getOrderId());
    }

    /**
     * 按退款ID获取
     */
    public RefundRequest getById(Long refundId) {
        return refundStore.get(refundId);
    }

    /**
     * 按订单ID获取
     */
    public RefundRequest getByOrderId(Long orderId) {
        Long refundId = orderRefundIndex.get(orderId);
        if (refundId == null) {
            return null;
        }
        return refundStore.get(refundId);
    }

    /**
     * 获取所有待审核的退款申请（status=0）
     */
    public List<RefundRequest> getPendingList() {
        return refundStore.values().stream()
                .filter(r -> r.getStatus() == 0)
                .collect(Collectors.toList());
    }

    /**
     * 更新退款申请
     */
    public void update(RefundRequest refund) {
        refundStore.put(refund.getRefundId(), refund);
    }

    /**
     * 检查某订单是否已有退款申请
     */
    public boolean hasRefund(Long orderId) {
        return orderRefundIndex.containsKey(orderId);
    }

    /**
     * 获取总数
     */
    public int size() {
        return refundStore.size();
    }

    /**
     * 获取未读退款数（pending状态中未被商家查看过的）
     */
    public int getUnreadCount() {
        return (int) refundStore.values().stream()
                .filter(r -> r.getStatus() == 0 && !viewedRefundIds.contains(r.getRefundId()))
                .count();
    }

    /**
     * 标记所有待审核退款为已读
     */
    public void markAllViewed() {
        List<RefundRequest> pending = getPendingList();
        for (RefundRequest r : pending) {
            viewedRefundIds.add(r.getRefundId());
        }
        log.info("退款已读标记完成: 已标记{}条", pending.size());
    }
}