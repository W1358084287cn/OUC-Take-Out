package edu.ouc.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收银台日记录实体（纯内存存储，不映射数据库表）
 */
@Data
public class CashRegisterDay implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日期 */
    private LocalDate date;

    /** 总收入 */
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 退款金额 */
    private BigDecimal refundAmount = BigDecimal.ZERO;

    /** 净收入 = totalAmount - refundAmount */
    private BigDecimal netAmount = BigDecimal.ZERO;

    /** 订单总数 */
    private Integer orderCount = 0;

    /** 记录创建时间 */
    private LocalDateTime createTime;
}