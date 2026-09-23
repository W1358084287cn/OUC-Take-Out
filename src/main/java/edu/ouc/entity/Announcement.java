package edu.ouc.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 餐厅公告实体（纯内存存储，不映射数据库表）
 */
@Data
public class Announcement implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 公告标题 */
    private String title;

    /** 公告内容（文字） */
    private String content;

    /** 公告配图（文件路径，可选） */
    private String image;

    /** 状态：1=生效中，0=已下架 */
    private Integer status;

    /** 发布人ID */
    private Long createUser;

    /** 发布时间 */
    private LocalDateTime createTime;

    /** 下架时间 */
    private LocalDateTime updateTime;
}