package edu.ouc.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已查看上下文（纯内存单例）
 * 追踪商家已查看过的订单ID和退款ID，用于红点提醒
 */
@Slf4j
@Component
public class ViewedContext {

    /** 商家已查看的订单ID集合 */
    private final Set<Long> viewedOrderIds = ConcurrentHashMap.newKeySet();

    /**
     * 标记订单为已查看
     */
    public void markOrderViewed(Long orderId) {
        if (viewedOrderIds.add(orderId)) {
            log.info("订单已标记为已查看: orderId={}", orderId);
        }
    }

    /**
     * 判断订单是否已被查看
     */
    public boolean isOrderViewed(Long orderId) {
        return viewedOrderIds.contains(orderId);
    }

    /**
     * 获取已查看订单数量
     */
    public int getViewedOrderCount() {
        return viewedOrderIds.size();
    }

    /**
     * 清空全部已查看记录（用于"全部已读"操作）
     */
    public void clearAll() {
        int size = viewedOrderIds.size();
        viewedOrderIds.clear();
        log.info("已查看记录已清空: 原来{}条", size);
    }
}