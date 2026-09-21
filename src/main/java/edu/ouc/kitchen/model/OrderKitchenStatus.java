package edu.ouc.kitchen.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import edu.ouc.kitchen.model.enums.OrderKitchenStage;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单后厨出餐状态（纯内存模型，不落库）
 * <p>
 * 前台查询接口必须返回每一道菜品的独立状态（规则14）
 */
@Data
public class OrderKitchenStatus implements Serializable {

    private static final long serialVersionUID = 2003L;

    /** 订单ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 订单号 */
    private String orderNumber;

    /** 该订单总菜品任务数 */
    private Integer totalTasks;

    /** 已完成出餐数 */
    private Integer completedTasks;

    /** 加工中数 */
    private Integer processingTasks;

    /** 待加工数 */
    private Integer pendingTasks;

    /** 已取消数 */
    private Integer cancelledTasks;

    /** 订单后厨出餐阶段 */
    private OrderKitchenStage orderStage;

    /** 该订单下所有菜品任务的独立状态明细 */
    private List<KitchenTask> tasks;

    public OrderKitchenStatus() {
        this.totalTasks = 0;
        this.completedTasks = 0;
        this.processingTasks = 0;
        this.pendingTasks = 0;
        this.cancelledTasks = 0;
        this.orderStage = OrderKitchenStage.WAITING;
        this.tasks = new ArrayList<>();
    }
}