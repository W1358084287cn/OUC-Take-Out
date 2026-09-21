package edu.ouc.kitchen.service;

import edu.ouc.kitchen.model.KitchenTask;
import edu.ouc.kitchen.model.OrderKitchenStatus;

import java.util.List;

/**
 * 后厨订单拆解服务接口
 * <p>
 * 负责：订单拆解为菜品加工任务、中途加菜、订单取消
 */
public interface IKitchenOrderService {

    /**
     * 订单拆解：将订单明细拆解为独立的菜品加工子任务并入队
     *
     * @param orderId        订单ID
     * @param orderNumber    订单号
     * @param orderTime      订单下单时间
     * @param dishInfos      菜品信息列表（每项包含 dishId、dishName、cookingDuration）
     * @return 生成的全部子任务
     */
    List<KitchenTask> decompose(Long orderId, String orderNumber, java.time.LocalDateTime orderTime,
                                List<DishInfo> dishInfos);

    /**
     * 中途加菜：追加菜品生成独立加工子任务，使用原订单下单时间
     *
     * @param orderId    订单ID
     * @param dishInfos  追加的菜品信息列表
     * @return 新增的子任务列表
     */
    List<KitchenTask> addDishes(Long orderId, List<DishInfo> dishInfos);

    /**
     * 取消订单：作废所有待加工任务，加工中任务按配置决定是否强制终止
     *
     * @param orderId             订单ID
     * @param forceStopProcessing 是否强制终止加工中任务
     * @return 取消后的订单后厨状态
     */
    OrderKitchenStatus cancelOrder(Long orderId, boolean forceStopProcessing);

    /**
     * 菜品信息（内部DTO，用于订单拆解和加菜传参）
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class DishInfo {
        private Long dishId;
        private String dishName;
        private Integer cookingDuration;
    }
}