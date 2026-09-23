package edu.ouc.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * 餐厅营业时间实体（纯内存存储，不映射数据库表）
 */
@Data
public class BusinessHours implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否正在营业：1=营业中，0=已打烊（手动开关，优先级最高） */
    private Integer open;

    /** 每日营业开始时间（时:分），默认 07:00 */
    private String startTime;

    /** 每日营业结束时间（时:分），默认 21:00 */
    private String endTime;

    /** 休息日（周几，1=周一...7=周日，逗号分隔，如 "1,7" 表示周一和周日休息） */
    private String restDays;
}