package edu.ouc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 收银台配置（刷新时间 / 历史保留天数）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cash-register")
public class CashRegisterConfig {

    /** 每日刷新小时（0-23），默认凌晨4点 */
    private int resetHour = 4;

    /** 历史记录保留天数（1-3650，即最高10年），默认90天 */
    private int retentionDays = 90;
}