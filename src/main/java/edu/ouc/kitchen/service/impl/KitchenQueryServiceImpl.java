package edu.ouc.kitchen.service.impl;

import edu.ouc.kitchen.common.KitchenDataContext;
import edu.ouc.kitchen.model.KitchenSlot;
import edu.ouc.kitchen.model.OrderKitchenStatus;
import edu.ouc.kitchen.service.IKitchenQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 后厨查询服务实现
 * <p>
 * 规则14：前台查询接口必须返回每一道菜品的独立状态：待加工 / 加工中 / 已出餐
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenQueryServiceImpl implements IKitchenQueryService {

    private final KitchenDataContext ctx;

    /**
     * 查询订单后厨出餐明细状态
     * <p>
     * 返回 OrderKitchenStatus，其中 tasks 字段包含每道菜品的独立状态
     */
    @Override
    public OrderKitchenStatus getOrderKitchenStatus(Long orderId) {
        OrderKitchenStatus status = ctx.getOrderStatusMap().get(orderId);
        if (status == null) {
            throw new edu.ouc.common.CustomException("订单不存在或无后厨任务");
        }
        return status;
    }

    /**
     * 查询全局后厨状态
     */
    @Override
    public GlobalStatus getGlobalStatus() {
        GlobalStatus gs = new GlobalStatus();

        // 槽位统计
        int slotCount = ctx.getSlots().length;
        int occupied = 0;
        for (KitchenSlot slot : ctx.getSlots()) {
            if (slot.getOccupied()) {
                occupied++;
            }
        }
        gs.setSlotCount(slotCount);
        gs.setOccupiedSlots(occupied);
        gs.setIdleSlots(slotCount - occupied);

        // 队列统计
        gs.setPendingTaskCount(ctx.getPendingQueue().size());
        gs.setProcessingTaskCount(ctx.getProcessingMap().size());
        gs.setCompletedTaskCount(ctx.getCompletedMap().size());

        // 估清菜品
        gs.setSoldOutDishIds(new ArrayList<>(ctx.getSoldOutDishIds()));

        return gs;
    }
}