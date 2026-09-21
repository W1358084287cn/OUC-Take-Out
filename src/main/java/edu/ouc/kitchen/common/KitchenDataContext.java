package edu.ouc.kitchen.common;

import edu.ouc.kitchen.config.KitchenConfig;
import edu.ouc.kitchen.model.KitchenSlot;
import edu.ouc.kitchen.model.KitchenTask;
import edu.ouc.kitchen.model.OrderKitchenStatus;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * 后厨调度核心数据上下文（全内存，Spring启动时初始化）
 * <p>
 * 持有所有调度所需的内存数据结构，不依赖数据库。
 */
@Getter
@Component
@RequiredArgsConstructor
public class KitchenDataContext {

    private final KitchenConfig kitchenConfig;

    /** 待加工任务优先级队列（排序规则：耗时小优先 → 下单时间早优先） */
    private PriorityBlockingQueue<KitchenTask> pendingQueue;

    /** 加工中任务表（key = taskId） */
    private final ConcurrentHashMap<Long, KitchenTask> processingMap = new ConcurrentHashMap<>();

    /** 已完成任务表（key = taskId） */
    private final ConcurrentHashMap<Long, KitchenTask> completedMap = new ConcurrentHashMap<>();

    /** 订单—后厨状态映射表（key = orderId） */
    private final ConcurrentHashMap<Long, OrderKitchenStatus> orderStatusMap = new ConcurrentHashMap<>();

    /** 槽位信号量（公平模式，控制并行加工数） */
    private Semaphore slotSemaphore;

    /** 槽位状态数组 */
    private KitchenSlot[] slots;

    /** 估清菜品ID集合（线程安全） */
    private final Set<Long> soldOutDishIds = ConcurrentHashMap.newKeySet();

    /**
     * Spring 容器初始化完成后，根据配置初始化槽位和队列
     */
    @PostConstruct
    public void init() {
        int slotCount = kitchenConfig.getSlotCount();
        this.pendingQueue = new PriorityBlockingQueue<>(64,
                java.util.Comparator.comparingInt(KitchenTask::getCookingDuration)
                        .thenComparing(KitchenTask::getOrderTime));
        this.slotSemaphore = new Semaphore(slotCount, true);
        this.slots = new KitchenSlot[slotCount];
        for (int i = 0; i < slotCount; i++) {
            this.slots[i] = new KitchenSlot(i);
        }
    }
}