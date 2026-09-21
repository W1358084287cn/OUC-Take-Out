package edu.ouc.kitchen.service;

import edu.ouc.kitchen.model.KitchenTask;
import edu.ouc.kitchen.model.OrderKitchenStatus;

import java.util.List;

/**
 * 后厨任务调度引擎服务接口
 * <p>
 * 负责：槽位分配、任务开始/完成状态流转、调度算法、估清处理
 */
public interface IKitchenSchedulerService {

    /**
     * 核心调度逻辑：检查空闲槽位 → 从待加工队列 poll 最优任务 → 分配槽位
     */
    void tryAssign();

    /**
     * 标记菜品开始加工
     *
     * @param taskId 任务ID
     * @param slotId 指定槽位（为null时自动分配）
     * @return 更新后的任务
     */
    KitchenTask startTask(Long taskId, Integer slotId);

    /**
     * 标记菜品加工完成出餐，释放槽位并触发下一轮调度
     *
     * @param taskId 任务ID
     * @return 所属订单的最新后厨状态
     */
    OrderKitchenStatus completeTask(Long taskId);

    /**
     * 预览下一批可分配任务（不实际分配，不消耗队列）
     *
     * @param count 预览数量
     * @return 当前最优的前N个待加工任务
     */
    List<KitchenTask> getNextPendingTasks(int count);

    /**
     * 菜品估清设置
     *
     * @param dishId  菜品ID
     * @param soldOut true=估清，false=恢复供应
     */
    void markDishSoldOut(Long dishId, boolean soldOut);
}