package edu.ouc.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款申请实体（纯内存存储，不映射数据库表）
 */
@Data
public class RefundRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long refundId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private BigDecimal refundAmount;

    private String refundReason;

    private Integer status;

    private BigDecimal actualRefund;

    private String merchantReply;

    private Integer originalOrderStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}