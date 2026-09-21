package edu.ouc.kitchen.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import edu.ouc.kitchen.common.KitchenDataContext;
import edu.ouc.kitchen.model.KitchenTask;
import edu.ouc.kitchen.model.OrderKitchenStatus;
import edu.ouc.kitchen.model.enums.OrderKitchenStage;
import edu.ouc.kitchen.model.enums.TaskStatus;
import edu.ouc.kitchen.service.IKitchenOrderService;
import edu.ouc.kitchen.service.IKitchenSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 后厨订单拆解服务实现
 * <p>
 * 负责：订单拆解为菜品加工任务、中途加菜、订单取消
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenOrderServiceImpl implements IKitchenOrderService {

    private final KitchenDataContext ctx;
    private final IKitchenSchedulerService schedulerService;

    /**
     * 订单拆解：将订单明细拆解为独立的菜品加工子任务并入队
     * <p>
     * 规则1：每个菜品生成独立子任务，携带 orderId、cookingDuration、orderTime
     * 规则3：插入优先级队列后自动按 耗时小优先→下单早优先 排序
     */
    @Override
    public List<KitchenTask> decompose(Long orderId, String orderNumber, LocalDateTime orderTime,
                                       List<DishInfo> dishInfos) {
        log.info("订单拆解开始: orderId={}, 菜品数={}", orderId, dishInfos.size());

        List<KitchenTask> tasks = new ArrayList<>();
        OrderKitchenStatus orderStatus = new OrderKitchenStatus();
        orderStatus.setOrderId(orderId);
        orderStatus.setOrderNumber(orderNumber);
        orderStatus.setTotalTasks(dishInfos.size());
        orderStatus.setPendingTasks(dishInfos.size());

        for (DishInfo dishInfo : dishInfos) {
            KitchenTask task = new KitchenTask();
            task.setTaskId(IdWorker.getId());
            task.setOrderId(orderId);
            task.setOrderNumber(orderNumber);
            task.setDishId(dishInfo.getDishId());
            task.setDishName(dishInfo.getDishName());
            task.setCookingDuration(dishInfo.getCookingDuration());
            task.setOrderTime(orderTime);
            task.setTaskStatus(TaskStatus.PENDING);

            orderStatus.getTasks().add(task);
            ctx.getPendingQueue().offer(task);
            tasks.add(task);
        }

        ctx.getOrderStatusMap().put(orderId, orderStatus);
        log.info("订单拆解完成: orderId={}, 任务数={}, 订单状态=待出餐", orderId, tasks.size());

        // 触发调度引擎尝试分配
        schedulerService.tryAssign();
        return tasks;
    }

    /**
     * 中途加菜：追加菜品生成独立加工子任务，使用原订单下单时间
     * <p>
     * 规则9：排序使用原订单下单时间 + 菜品自身时长，不额外提高优先级
     */
    @Override
    public List<KitchenTask> addDishes(Long orderId, List<DishInfo> dishInfos) {
        log.info("中途加菜: orderId={}, 追加菜品数={}", orderId, dishInfos.size());

        OrderKitchenStatus orderStatus = ctx.getOrderStatusMap().get(orderId);
        if (orderStatus == null) {
            throw new edu.ouc.common.CustomException("订单不存在，无法加菜");
        }
        if (orderStatus.getOrderStage() == OrderKitchenStage.COMPLETE) {
            throw new edu.ouc.common.CustomException("订单已全部出餐，无法加菜");
        }
        if (orderStatus.getOrderStage() == OrderKitchenStage.CANCELLED) {
            throw new edu.ouc.common.CustomException("订单已取消，无法加菜");
        }

        // 取原订单的下单时间（从已有任务或状态中获取）
        LocalDateTime orderTime = null;
        for (KitchenTask existingTask : orderStatus.getTasks()) {
            if (existingTask.getOrderTime() != null) {
                orderTime = existingTask.getOrderTime();
                break;
            }
        }
        if (orderTime == null) {
            throw new edu.ouc.common.CustomException("无法获取原订单下单时间");
        }

        List<KitchenTask> newTasks = new ArrayList<>();
        for (DishInfo dishInfo : dishInfos) {
            KitchenTask task = new KitchenTask();
            task.setTaskId(IdWorker.getId());
            task.setOrderId(orderId);
            task.setOrderNumber(orderStatus.getOrderNumber());
            task.setDishId(dishInfo.getDishId());
            task.setDishName(dishInfo.getDishName());
            task.setCookingDuration(dishInfo.getCookingDuration());
            task.setOrderTime(orderTime);
            task.setTaskStatus(TaskStatus.PENDING);

            orderStatus.getTasks().add(task);
            ctx.getPendingQueue().offer(task);
            newTasks.add(task);
        }

        // 更新订单状态计数
        orderStatus.setTotalTasks(orderStatus.getTotalTasks() + newTasks.size());
        orderStatus.setPendingTasks(orderStatus.getPendingTasks() + newTasks.size());
        recalcOrderStage(orderStatus);

        log.info("加菜完成: orderId={}, 新增任务数={}, 订单总任务数={}", orderId, newTasks.size(), orderStatus.getTotalTasks());

        // 触发调度引擎
        schedulerService.tryAssign();
        return newTasks;
    }

    /**
     * 取消订单
     * <p>
     * 规则11-①：未开始加工的子任务全部作废
     * 规则11-②：正在加工的菜品默认继续制作完成，可配置强制终止
     * 规则12：订单状态新增"已取消"
     */
    @Override
    public OrderKitchenStatus cancelOrder(Long orderId, boolean forceStopProcessing) {
        log.info("取消订单: orderId={}, forceStopProcessing={}", orderId, forceStopProcessing);

        OrderKitchenStatus orderStatus = ctx.getOrderStatusMap().get(orderId);
        if (orderStatus == null) {
            throw new edu.ouc.common.CustomException("订单不存在");
        }
        if (orderStatus.getOrderStage() == OrderKitchenStage.COMPLETE) {
            throw new edu.ouc.common.CustomException("订单已全部出餐，无法取消");
        }
        if (orderStatus.getOrderStage() == OrderKitchenStage.CANCELLED) {
            throw new edu.ouc.common.CustomException("订单已取消");
        }

        // 第一步：作废所有待加工任务
        Iterator<KitchenTask> iterator = ctx.getPendingQueue().iterator();
        while (iterator.hasNext()) {
            KitchenTask task = iterator.next();
            if (task.getOrderId().equals(orderId) && task.getTaskStatus() == TaskStatus.PENDING) {
                task.setTaskStatus(TaskStatus.CANCELLED);
                iterator.remove();
            }
        }

        // 同步更新 orderStatus 中的任务状态
        int cancelledCount = 0;
        for (KitchenTask task : orderStatus.getTasks()) {
            if (task.getTaskStatus() == TaskStatus.PENDING) {
                task.setTaskStatus(TaskStatus.CANCELLED);
                cancelledCount++;
            }
        }

        // 第二步：处理加工中任务
        int processingCount = cancelProcessingTasks(orderId, forceStopProcessing);

        // 第三步：重算订单状态
        orderStatus.setPendingTasks(0);
        orderStatus.setCancelledTasks(orderStatus.getCancelledTasks() + cancelledCount);
        recalcOrderStage(orderStatus);

        log.info("订单取消完成: orderId={}, 作废待加工={}, 加工中={}, 订单状态={}",
                orderId, cancelledCount, processingCount, orderStatus.getOrderStage());

        // 如果终止了加工中任务，有槽位释放，触发调度
        if (forceStopProcessing && processingCount > 0) {
            schedulerService.tryAssign();
        }

        return orderStatus;
    }

    /**
     * 处理取消订单时的加工中任务
     *
     * @return 被取消的加工中任务数
     */
    private int cancelProcessingTasks(Long orderId, boolean forceStopProcessing) {
        if (!forceStopProcessing) {
            return 0;
        }

        int count = 0;
        OrderKitchenStatus orderStatus = ctx.getOrderStatusMap().get(orderId);
        for (KitchenTask task : orderStatus.getTasks()) {
            if (task.getTaskStatus() == TaskStatus.PROCESSING) {
                task.setTaskStatus(TaskStatus.CANCELLED);
                task.setCompleteTime(LocalDateTime.now());
                ctx.getProcessingMap().remove(task.getTaskId());

                // 释放槽位
                Integer slotId = task.getSlotId();
                if (slotId != null && slotId >= 0 && slotId < ctx.getSlots().length) {
                    ctx.getSlots()[slotId].setOccupied(false);
                    ctx.getSlots()[slotId].setCurrentTaskId(null);
                    ctx.getSlotSemaphore().release();
                }
                count++;
            }
        }
        if (count > 0) {
            orderStatus.setProcessingTasks(orderStatus.getProcessingTasks() - count);
            orderStatus.setCancelledTasks(orderStatus.getCancelledTasks() + count);
        }
        return count;
    }

    /**
     * 重算订单后厨出餐阶段
     * <p>
     * 规则5+7+12：根据各状态任务数判定订单阶段
     */
    void recalcOrderStage(OrderKitchenStatus orderStatus) {
        int total = orderStatus.getTotalTasks();
        int completed = orderStatus.getCompletedTasks();
        int cancelled = orderStatus.getCancelledTasks();
        int processing = orderStatus.getProcessingTasks();
        int pending = orderStatus.getPendingTasks();

        if (cancelled > 0 && pending == 0 && processing == 0) {
            orderStatus.setOrderStage(OrderKitchenStage.CANCELLED);
        } else if (completed == total) {
            orderStatus.setOrderStage(OrderKitchenStage.COMPLETE);
        } else if (completed > 0) {
            orderStatus.setOrderStage(OrderKitchenStage.PARTIAL);
        } else {
            orderStatus.setOrderStage(OrderKitchenStage.WAITING);
        }
    }
}