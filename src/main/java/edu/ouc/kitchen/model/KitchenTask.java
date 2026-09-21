package edu.ouc.kitchen.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import edu.ouc.kitchen.model.enums.TaskStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 菜品加工子任务（纯内存模型，不落库）
 */
@Data
public class KitchenTask implements Serializable {

    private static final long serialVersionUID = 2001L;

    /** 任务唯一标识（雪花ID） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 所属订单ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 订单号（前台展示用） */
    private String orderNumber;

    /** 菜品ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dishId;

    /** 菜品名称 */
    private String dishName;

    /** 菜品制作耗时（秒） */
    private Integer cookingDuration;

    /** 订单下单时间（排序关键字段） */
    private LocalDateTime orderTime;

    /** 任务状态 */
    private TaskStatus taskStatus;

    /** 占用槽位编号（加工中时有值，否则为null） */
    private Integer slotId;

    /** 开始加工时间 */
    private LocalDateTime startTime;

    /** 完成出餐时间 */
    private LocalDateTime completeTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KitchenTask that = (KitchenTask) o;
        return Objects.equals(taskId, that.taskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId);
    }
}