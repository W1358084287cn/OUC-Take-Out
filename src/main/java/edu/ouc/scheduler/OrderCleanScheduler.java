package edu.ouc.scheduler;

import edu.ouc.service.impl.OrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 历史订单自动清理定时任务
 * 每天凌晨2:00自动清理超过保留天数的订单
 */
@Component
@Slf4j
public class OrderCleanScheduler {

    private final OrderServiceImpl orderService;

    @Value("${reggie.order-retention-days:90}")
    private int retentionDays;

    public OrderCleanScheduler(OrderServiceImpl orderService) {
        this.orderService = orderService;
    }

    @PostConstruct
    public void init() {
        log.info("订单自动清理任务已注册: 保留{}天, 每天凌晨2:00执行", retentionDays);
    }

    /**
     * 每天凌晨2:00执行历史订单清理
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanOldOrders() {
        log.info("===== 历史订单自动清理开始 =====");
        try {
            int count = orderService.cleanOldOrders(retentionDays);
            log.info("===== 历史订单自动清理完成: 清理{}笔 =====", count);
        } catch (Exception e) {
            log.error("历史订单自动清理异常: {}", e.getMessage(), e);
        }
    }
}