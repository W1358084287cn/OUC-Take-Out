package edu.ouc.kitchen.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

/**
 * 后厨加工槽位（纯内存模型，不落库）
 */
@Data
public class KitchenSlot implements Serializable {

    private static final long serialVersionUID = 2002L;

    /** 槽位编号（0 ~ slotCount-1） */
    private Integer slotId;

    /** 是否被占用 */
    private Boolean occupied;

    /** 当前加工的任务ID（空闲时为null） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentTaskId;

    public KitchenSlot(Integer slotId) {
        this.slotId = slotId;
        this.occupied = false;
        this.currentTaskId = null;
    }
}