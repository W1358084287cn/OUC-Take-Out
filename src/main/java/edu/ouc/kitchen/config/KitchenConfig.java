package edu.ouc.kitchen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 后厨调度配置（对应 application.yml 中 kitchen.* 配置项）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "kitchen")
public class KitchenConfig {

    /** 并行加工槽位数量，默认4 */
    private Integer slotCount = 4;

    /** 取消订单时是否强制终止加工中任务，默认false（继续做完） */
    private Boolean forceStopOnCancel = false;
}