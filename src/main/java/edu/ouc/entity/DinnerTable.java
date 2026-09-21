package edu.ouc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 桌台管理实体
 */
@Data
public class DinnerTable implements Serializable {

    private static final long serialVersionUID = 2002L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 桌号，如 A01/B12 */
    private String tableNo;

    /** 容纳人数 */
    private Integer capacity;

    /** 区域：大厅/包间/露台 */
    private String area;

    /** 状态：0空闲 1使用中 */
    private Integer status;

    /** 二维码图片路径 */
    private String qrCodePath;

    /** 排序 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createUser;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateUser;
}