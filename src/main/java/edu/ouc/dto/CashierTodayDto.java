package edu.ouc.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 收银台今日统计DTO
 */
@Data
public class CashierTodayDto implements Serializable {

    private static final long serialVersionUID = 7001L;

    private Integer totalOrders;

    private BigDecimal totalAmount;

    private Integer completedOrders;

    private BigDecimal completedAmount;

    private Integer makingOrders;

    private Integer refundOrders;

    private BigDecimal refundAmount;
}