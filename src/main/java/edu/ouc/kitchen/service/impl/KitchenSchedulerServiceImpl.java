package edu.ouc.kitchen.service.impl;

import edu.ouc.kitchen.common.KitchenDataContext;
import edu.ouc.kitchen.model.KitchenSlot;
import edu.ouc.kitchen.model.KitchenTask;
import edu.ouc.kitchen.model.OrderKitchenStatus;
import edu.ouc.kitchen.model.enums.TaskStatus;
import edu.ouc.kitchen.service.IKitchenSchedulerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后厨任务调度引擎实现
 * <p>
 * 核心调度逻辑：基于优先级队列的贪心调度，快菜优先、慢菜不阻塞
 */
@Slf4j
@Service
public class KitchenSchedulerServiceImpl implements IKitchenSchedulerService {

    private final KitchenDataContext ctx;
    private final KitchenOrderServiceImpl orderService;

    /**
     * 构造器注入，@Lazy 解决 KitchenOrderServiceImpl ↔ KitchenSchedulerServiceImpl 循环依赖
     */
    public KitchenSchedulerServiceImpl(KitchenDataContext ctx,
                                        @Lazy KitchenOrderServiceImpl orderService) {
        this.ctx = ctx;
        this.orderService = orderService;
    }

    /**
     * 核心调度算法：贪心分配空闲槽位
     * <p>
     * 规则2：槽位被占用就不能分配新任务
     * 规则3：poll() 自动按 耗时小优先→下单早优先 返回最优任务
     * 规则4：一旦分配即标记为加工中，禁止抢占
     * 规则13：队列空时直接返回，不报错
     */
    @Override
    public void tryAssign() {
        int assignedCount = 0;

        while (ctx.getSlotSemaphore().tryAcquire()) {
            KitchenTask task = ctx.getPendingQueue().poll();

            // 队列空，释放槽位，结束调度
            if (task == null) {
                ctx.getSlotSemaphore().release();
                break;
            }

            // 已取消的任务直接丢弃，继续取下一个
            if (task.getTaskStatus() == TaskStatus.CANCELLED) {
                ctx.getSlotSemaphore().release();
                continue;
            }

            // 检查菜品是否已估清（防竞态：入队后、分配前被估清）
            if (ctx.getSoldOutDishIds().contains(task.getDishId())) {
                log.info("菜品已估清，作废任务: taskId={}, dishName={}", task.getTaskId(), task.getDishName());
                cancelTaskAndUpdateOrder(task);
                ctx.getSlotSemaphore().release();
                continue;
            }

            // 分配空闲槽位
            int slotId = findIdleSlot();
            if (slotId < 0) {
                // 无空闲槽位（信号量与槽位状态不一致的极端情况）
                ctx.getPendingQueue().offer(task);
                ctx.getSlotSemaphore().release();
                break;
            }

            // 绑定槽位与任务
            task.setSlotId(slotId);
            task.setTaskStatus(TaskStatus.PROCESSING);
            task.setStartTime(LocalDateTime.now());
            ctx.getProcessingMap().put(task.getTaskId(), task);

            ctx.getSlots()[slotId].setOccupied(true);
            ctx.getSlots()[slotId].setCurrentTaskId(task.getTaskId());

            // 更新订单状态
            updateOrderStatusOnStart(task.getOrderId());

            assignedCount++;
            log.info("调度分配: taskId={}, dishName={}, orderId={}, 耗时={}s, 槽位={}",
                    task.getTaskId(), task.getDishName(), task.getOrderId(), task.getCookingDuration(), slotId);
        }

        if (assignedCount > 0) {
            log.info("本轮调度完成: 分配任务数={}, 剩余空闲槽位={}",
                    assignedCount, ctx.getSlotSemaphore().availablePermits());
        }
    }

    /**
     * 标记菜品开始加工
     * <p>
     * 规则1：任务携带订单ID、菜品耗时、下单时间
     * 规则4：一旦开始加工，禁止抢占、禁止中断
     */
    @Override
    public KitchenTask startTask(Long taskId, Integer slotId) {
        // 从待加工队列中查找任务
        KitchenTask task = findPendingTask(taskId);
        if (task == null) {
            throw new edu.ouc.common.CustomException("任务不存在或状态不是待加工");
        }

        // 指定槽位或自动分配
        int assignedSlot;
        if (slotId != null) {
            if (slotId < 0 || slotId >= ctx.getSlots().length) {
                throw new edu.ouc.common.CustomException("无效的槽位编号");
            }
            if (ctx.getSlots()[slotId].getOccupied()) {
                throw new edu.ouc.common.CustomException("该槽位已被占用");
            }
            assignedSlot = slotId;
        } else {
            assignedSlot = findIdleSlot();
            if (assignedSlot < 0) {
                throw new edu.ouc.common.CustomException("所有加工槽位已满，请等待");
            }
        }

        // 占用槽位
        if (!ctx.getSlotSemaphore().tryAcquire()) {
            throw new edu.ouc.common.CustomException("所有加工槽位已满，请等待");
        }

        // 从待加工队列移除
        ctx.getPendingQueue().remove(task);

        // 更新任务状态
        task.setTaskStatus(TaskStatus.PROCESSING);
        task.setSlotId(assignedSlot);
        task.setStartTime(LocalDateTime.now());
        ctx.getProcessingMap().put(taskId, task);

        // 更新槽位状态
        ctx.getSlots()[assignedSlot].setOccupied(true);
        ctx.getSlots()[assignedSlot].setCurrentTaskId(taskId);

        // 更新订单状态
        updateOrderStatusOnStart(task.getOrderId());

        log.info("开始加工: taskId={}, dishName={}, 槽位={}", taskId, task.getDishName(), assignedSlot);
        return task;
    }

    /**
     * 标记菜品加工完成出餐
     * <p>
     * 规则6：单个菜品加工完成就标记出餐，释放加工槽位，重新执行调度
     * 规则5：同一订单可以部分菜品先做好出餐
     */
    @Override
    public OrderKitchenStatus completeTask(Long taskId) {
        KitchenTask task = ctx.getProcessingMap().get(taskId);
        if (task == null) {
            throw new edu.ouc.common.CustomException("任务不存在或不在加工中");
        }

        // 更新任务状态
        task.setTaskStatus(TaskStatus.COMPLETED);
        task.setCompleteTime(LocalDateTime.now());
        ctx.getProcessingMap().remove(taskId);
        ctx.getCompletedMap().put(taskId, task);

        // 释放槽位
        Integer slotId = task.getSlotId();
        if (slotId != null && slotId >= 0 && slotId < ctx.getSlots().length) {
            ctx.getSlots()[slotId].setOccupied(false);
            ctx.getSlots()[slotId].setCurrentTaskId(null);
        }
        ctx.getSlotSemaphore().release();

        // 更新订单状态
        OrderKitchenStatus orderStatus = updateOrderStatusOnComplete(task.getOrderId());

        log.info("出餐完成: taskId={}, dishName={}, orderId={}, 已完成{}/{}",
                taskId, task.getDishName(), task.getOrderId(),
                orderStatus.getCompletedTasks(), orderStatus.getTotalTasks());

        // 规则6：槽位释放之后，重新执行调度逻辑挑选下一个待加工菜品
        tryAssign();

        return orderStatus;
    }

    /**
     * 预览下一批可分配任务（不分配、不消耗队列）
     * <p>
     * 规则13：调度池为空时返回空数组，不报错
     */
    @Override
    public List<KitchenTask> getNextPendingTasks(int count) {
        Set<Long> soldOutIds = ctx.getSoldOutDishIds();
        return ctx.getPendingQueue().stream()
                .filter(task -> task.getTaskStatus() == TaskStatus.PENDING)
                .filter(task -> !soldOutIds.contains(task.getDishId()))
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * 菜品估清设置
     * <p>
     * 规则10：标记估清后，仅作废【待加工】状态的该菜品任务；已经开始加工的任务继续做完，不可中断
     */
    @Override
    public void markDishSoldOut(Long dishId, boolean soldOut) {
        if (soldOut) {
            ctx.getSoldOutDishIds().add(dishId);
            log.info("菜品估清: dishId={}", dishId);

            // 遍历待加工队列，作废该菜品的PENDING任务
            int cancelledCount = 0;
            java.util.Iterator<KitchenTask> iterator = ctx.getPendingQueue().iterator();
            while (iterator.hasNext()) {
                KitchenTask task = iterator.next();
                if (task.getDishId().equals(dishId) && task.getTaskStatus() == TaskStatus.PENDING) {
                    cancelTaskAndUpdateOrder(task);
                    iterator.remove();
                    cancelledCount++;
                }
            }
            log.info("估清作废待加工任务: dishId={}, 作废数={}", dishId, cancelledCount);

            // 加工中任务不受影响，不干预
            long processingCount = ctx.getProcessingMap().values().stream()
                    .filter(t -> t.getDishId().equals(dishId))
                    .count();
            if (processingCount > 0) {
                log.info("估清时仍有加工中任务: dishId={}, 加工中数={}，这些任务继续做完", dishId, processingCount);
            }
        } else {
            ctx.getSoldOutDishIds().remove(dishId);
            log.info("取消估清: dishId={}", dishId);
        }
    }

    // ====================== 内部辅助方法 ======================

    /**
     * 查找空闲槽位
     */
    private int findIdleSlot() {
        for (KitchenSlot slot : ctx.getSlots()) {
            if (!slot.getOccupied()) {
                return slot.getSlotId();
            }
        }
        return -1;
    }

    /**
     * 从待加工队列中查找指定taskId的任务
     */
    private KitchenTask findPendingTask(Long taskId) {
        for (KitchenTask task : ctx.getPendingQueue()) {
            if (task.getTaskId().equals(taskId) && task.getTaskStatus() == TaskStatus.PENDING) {
                return task;
            }
        }
        return null;
    }

    /**
     * 作废任务并更新对应订单状态
     */
    private void cancelTaskAndUpdateOrder(KitchenTask task) {
        task.setTaskStatus(TaskStatus.CANCELLED);
        OrderKitchenStatus orderStatus = ctx.getOrderStatusMap().get(task.getOrderId());
        if (orderStatus != null) {
            orderStatus.setPendingTasks(orderStatus.getPendingTasks() - 1);
            orderStatus.setCancelledTasks(orderStatus.getCancelledTasks() + 1);
            orderService.recalcOrderStage(orderStatus);
        }
    }

    /**
     * 任务开始加工时，更新订单状态
     */
    private void updateOrderStatusOnStart(Long orderId) {
        OrderKitchenStatus orderStatus = ctx.getOrderStatusMap().get(orderId);
        if (orderStatus != null) {
            orderStatus.setPendingTasks(orderStatus.getPendingTasks() - 1);
            orderStatus.setProcessingTasks(orderStatus.getProcessingTasks() + 1);
            orderService.recalcOrderStage(orderStatus);
        }
    }

    /**
     * 任务完成出餐时，更新订单状态
     */
    private OrderKitchenStatus updateOrderStatusOnComplete(Long orderId) {
        OrderKitchenStatus orderStatus = ctx.getOrderStatusMap().get(orderId);
        if (orderStatus != null) {
            orderStatus.setProcessingTasks(orderStatus.getProcessingTasks() - 1);
            orderStatus.setCompletedTasks(orderStatus.getCompletedTasks() + 1);
            orderService.recalcOrderStage(orderStatus);

            if (orderStatus.getOrderStage() == edu.ouc.kitchen.model.enums.OrderKitchenStage.COMPLETE) {
                log.info("订单全部出餐完成: orderId={}", orderId);
            }
        }
        return orderStatus;
    }
}