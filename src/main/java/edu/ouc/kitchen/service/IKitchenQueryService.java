package edu.ouc.kitchen.service;

import edu.ouc.kitchen.model.OrderKitchenStatus;

import java.util.List;

/**
 * 后厨查询服务接口
 * <p>
 * 负责：订单后厨状态查询、全局状态查询
 */
public interface IKitchenQueryService {

    /**
     * 查询订单后厨出餐明细状态（包含每道菜品独立状态）
     *
     * @param orderId 订单ID
     * @return 订单后厨状态
     */
    OrderKitchenStatus getOrderKitchenStatus(Long orderId);

    /**
     * 查询全局后厨状态（槽位、队列、估清等汇总信息）
     *
     * @return 全局状态DTO
     */
    GlobalStatus getGlobalStatus();

    /**
     * 全局状态DTO
     */
    @lombok.Data
    class GlobalStatus {
        private Integer slotCount;
        private Integer occupiedSlots;
        private Integer idleSlots;
        private Integer pendingTaskCount;
        private Integer processingTaskCount;
        private Integer completedTaskCount;
        private List<Long> soldOutDishIds;
    }
}