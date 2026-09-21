package edu.ouc.kitchen.model.enums;

/**
 * 菜品加工子任务状态枚举
 */
@lombok.Getter
public enum TaskStatus {

    /** 待加工 */
    PENDING("待加工"),

    /** 加工中 */
    PROCESSING("加工中"),

    /** 已出餐 */
    COMPLETED("已出餐"),

    /** 已取消 */
    CANCELLED("已取消");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}