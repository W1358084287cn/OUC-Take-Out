package edu.ouc.kitchen.controller;

import edu.ouc.common.R;
import edu.ouc.kitchen.model.KitchenTask;
import edu.ouc.kitchen.model.OrderKitchenStatus;
import edu.ouc.kitchen.service.IKitchenOrderService;
import edu.ouc.kitchen.service.IKitchenQueryService;
import edu.ouc.kitchen.service.IKitchenSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 后厨出餐调度系统控制器
 * <p>
 * 小店模式：快菜优先出餐，慢菜不阻塞后面订单的快菜
 */
@Slf4j
@RestController
@RequestMapping("/kitchen")
@RequiredArgsConstructor
public class KitchenController {

    private final IKitchenOrderService orderService;
    private final IKitchenSchedulerService schedulerService;
    private final IKitchenQueryService queryService;

    // ==================== 订单拆解与调度控制 ====================

    /**
     * ① 订单拆解加工任务
     * <p>
     * 规则1：将订单拆解为每一道菜品独立的加工子任务
     */
    @PostMapping("/decompose")
    public R<List<KitchenTask>> decompose(@RequestBody DecomposeRequest request) {
        log.info("订单拆解: orderId={}, 菜品数={}", request.getOrderId(),
                request.getDishes() != null ? request.getDishes().size() : 0);

        if (request.getOrderId() == null) {
            return R.error("订单ID不能为空");
        }
        if (request.getDishes() == null || request.getDishes().isEmpty()) {
            return R.error("菜品列表不能为空");
        }

        List<IKitchenOrderService.DishInfo> dishInfos = request.getDishes().stream()
                .map(d -> new IKitchenOrderService.DishInfo(d.getDishId(), d.getDishName(), d.getCookingDuration()))
                .collect(Collectors.toList());

        List<KitchenTask> tasks = orderService.decompose(
                request.getOrderId(),
                request.getOrderNumber(),
                request.getOrderTime() != null ? request.getOrderTime() : LocalDateTime.now(),
                dishInfos);
        return R.success(tasks);
    }

    /**
     * ② 标记菜品开始加工
     * <p>
     * 规则4：菜品任务一旦开始加工，禁止抢占、禁止中断
     */
    @PostMapping("/start/{taskId}")
    public R<KitchenTask> startTask(@PathVariable Long taskId, @RequestBody(required = false) StartRequest request) {
        log.info("开始加工: taskId={}, slotId={}", taskId, request != null ? request.getSlotId() : null);

        if (taskId == null) {
            return R.error("任务ID不能为空");
        }

        try {
            KitchenTask task = schedulerService.startTask(taskId,
                    request != null ? request.getSlotId() : null);
            return R.success(task);
        } catch (edu.ouc.common.CustomException e) {
            return R.error(e.getMessage());
        }
    }

    /**
     * ③ 标记菜品加工完成出餐
     * <p>
     * 规则5：同一订单可以部分菜品先做好出餐
     * 规则6：单个菜品加工完成就标记出餐，释放加工槽位，重新调度
     */
    @PutMapping("/complete/{taskId}")
    public R<OrderKitchenStatus> completeTask(@PathVariable Long taskId) {
        log.info("出餐完成: taskId={}", taskId);

        if (taskId == null) {
            return R.error("任务ID不能为空");
        }

        try {
            OrderKitchenStatus status = schedulerService.completeTask(taskId);
            return R.success(status);
        } catch (edu.ouc.common.CustomException e) {
            return R.error(e.getMessage());
        }
    }

    // ==================== 查询 ====================

    /**
     * ④ 查询订单后厨出餐明细状态
     * <p>
     * 规则14：必须返回每一道菜品的独立状态：待加工 / 加工中 / 已出餐
     */
    @GetMapping("/order-status/{orderId}")
    public R<OrderKitchenStatus> getOrderStatus(@PathVariable Long orderId) {
        log.info("查询订单后厨状态: orderId={}", orderId);

        if (orderId == null) {
            return R.error("订单ID不能为空");
        }

        try {
            OrderKitchenStatus status = queryService.getOrderKitchenStatus(orderId);
            return R.success(status);
        } catch (edu.ouc.common.CustomException e) {
            return R.error(e.getMessage());
        }
    }

    /**
     * ⑤ 预览下一批可分配任务
     * <p>
     * 规则13：调度池为空时返回空数组，不报错
     */
    @GetMapping("/next-tasks")
    public R<List<KitchenTask>> getNextTasks(@RequestParam(defaultValue = "5") int count) {
        log.info("预览下一批任务: count={}", count);
        List<KitchenTask> tasks = schedulerService.getNextPendingTasks(count);
        return R.success(tasks);
    }

    /**
     * ⑥ 查询全局后厨状态
     */
    @GetMapping("/global-status")
    public R<IKitchenQueryService.GlobalStatus> getGlobalStatus() {
        log.info("查询全局后厨状态");
        IKitchenQueryService.GlobalStatus status = queryService.getGlobalStatus();
        return R.success(status);
    }

    // ==================== 追加 / 估清 / 取消 ====================

    /**
     * ⑦ 追加菜品加工任务（加菜）
     * <p>
     * 规则9：排序使用原订单下单时间 + 菜品自身时长，不额外提高优先级
     */
    @PostMapping("/add-dish")
    public R<List<KitchenTask>> addDish(@RequestBody AddDishRequest request) {
        log.info("中途加菜: orderId={}, 追加菜品数={}", request.getOrderId(),
                request.getDishes() != null ? request.getDishes().size() : 0);

        if (request.getOrderId() == null) {
            return R.error("订单ID不能为空");
        }
        if (request.getDishes() == null || request.getDishes().isEmpty()) {
            return R.error("追加菜品列表不能为空");
        }

        List<IKitchenOrderService.DishInfo> dishInfos = request.getDishes().stream()
                .map(d -> new IKitchenOrderService.DishInfo(d.getDishId(), d.getDishName(), d.getCookingDuration()))
                .collect(Collectors.toList());

        try {
            List<KitchenTask> tasks = orderService.addDishes(request.getOrderId(), dishInfos);
            return R.success(tasks);
        } catch (edu.ouc.common.CustomException e) {
            return R.error(e.getMessage());
        }
    }

    /**
     * ⑧ 菜品估清设置
     * <p>
     * 规则10：标记估清后，仅作废【待加工】状态的该菜品任务；已经开始加工的任务继续做完，不可中断
     */
    @PutMapping("/sold-out/{dishId}")
    public R<String> soldOut(@PathVariable Long dishId, @RequestBody SoldOutRequest request) {
        log.info("菜品估清设置: dishId={}, soldOut={}", dishId, request.isSoldOut());

        if (dishId == null) {
            return R.error("菜品ID不能为空");
        }

        schedulerService.markDishSoldOut(dishId, request.isSoldOut());
        String msg = request.isSoldOut() ? "菜品" + dishId + "已估清" : "菜品" + dishId + "已恢复供应";
        return R.success(msg);
    }

    /**
     * ⑨ 订单取消
     * <p>
     * 规则11-①：未开始加工的子任务全部作废
     * 规则11-②：正在加工的菜品默认继续制作完成，可配置强制终止
     * 规则12：订单状态新增"已取消"
     */
    @PutMapping("/cancel-order/{orderId}")
    public R<OrderKitchenStatus> cancelOrder(@PathVariable Long orderId,
                                             @RequestBody(required = false) CancelOrderRequest request) {
        boolean forceStop = request != null && request.isForceStopProcessing();
        log.info("取消订单: orderId={}, forceStopProcessing={}", orderId, forceStop);

        if (orderId == null) {
            return R.error("订单ID不能为空");
        }

        try {
            OrderKitchenStatus status = orderService.cancelOrder(orderId, forceStop);
            return R.success(status);
        } catch (edu.ouc.common.CustomException e) {
            return R.error(e.getMessage());
        }
    }

    // ==================== 请求体 DTO（内部类，遵循项目 DTO 规范） ====================

    /**
     * 订单拆解请求体
     */
    public static class DecomposeRequest {
        private Long orderId;
        private String orderNumber;
        private LocalDateTime orderTime;
        private List<DishItem> dishes;

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public String getOrderNumber() {
            return orderNumber;
        }

        public void setOrderNumber(String orderNumber) {
            this.orderNumber = orderNumber;
        }

        public LocalDateTime getOrderTime() {
            return orderTime;
        }

        public void setOrderTime(LocalDateTime orderTime) {
            this.orderTime = orderTime;
        }

        public List<DishItem> getDishes() {
            return dishes;
        }

        public void setDishes(List<DishItem> dishes) {
            this.dishes = dishes;
        }
    }

    /**
     * 菜品项（用于拆解和加菜请求）
     */
    public static class DishItem {
        private Long dishId;
        private String dishName;
        private Integer cookingDuration;

        public Long getDishId() {
            return dishId;
        }

        public void setDishId(Long dishId) {
            this.dishId = dishId;
        }

        public String getDishName() {
            return dishName;
        }

        public void setDishName(String dishName) {
            this.dishName = dishName;
        }

        public Integer getCookingDuration() {
            return cookingDuration;
        }

        public void setCookingDuration(Integer cookingDuration) {
            this.cookingDuration = cookingDuration;
        }
    }

    /**
     * 开始加工请求体
     */
    public static class StartRequest {
        private Integer slotId;

        public Integer getSlotId() {
            return slotId;
        }

        public void setSlotId(Integer slotId) {
            this.slotId = slotId;
        }
    }

    /**
     * 加菜请求体
     */
    public static class AddDishRequest {
        private Long orderId;
        private List<DishItem> dishes;

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public List<DishItem> getDishes() {
            return dishes;
        }

        public void setDishes(List<DishItem> dishes) {
            this.dishes = dishes;
        }
    }

    /**
     * 估清请求体
     */
    public static class SoldOutRequest {
        private boolean soldOut;

        public boolean isSoldOut() {
            return soldOut;
        }

        public void setSoldOut(boolean soldOut) {
            this.soldOut = soldOut;
        }
    }

    /**
     * 取消订单请求体
     */
    public static class CancelOrderRequest {
        private boolean forceStopProcessing;

        public boolean isForceStopProcessing() {
            return forceStopProcessing;
        }

        public void setForceStopProcessing(boolean forceStopProcessing) {
            this.forceStopProcessing = forceStopProcessing;
        }
    }
}