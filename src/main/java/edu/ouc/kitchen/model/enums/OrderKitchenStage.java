package edu.ouc.kitchen.model.enums;

/**
 * 订单后厨出餐阶段枚举
 */
@lombok.Getter
public enum OrderKitchenStage {

    /** 待出餐：所有菜品任务均未开始加工 */
    WAITING("待出餐"),

    /** 部分出餐：部分菜品已出餐，仍有菜品待加工或加工中 */
    PARTIAL("部分出餐"),

    /** 全部出餐：所有菜品任务均已完成出餐 */
    COMPLETE("全部出餐"),

    /** 已取消：订单已被取消 */
    CANCELLED("已取消");

    private final String description;

    OrderKitchenStage(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}