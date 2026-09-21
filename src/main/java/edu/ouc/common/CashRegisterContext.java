package edu.ouc.common;

import edu.ouc.config.CashRegisterConfig;
import edu.ouc.entity.CashRegisterDay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 收银台内存上下文（单例Bean，纯内存存储）
 */
@Slf4j
@Component
public class CashRegisterContext {

    @Autowired
    private CashRegisterConfig config;

    /** 当前营业日 */
    private LocalDate currentDate;

    /** 今日实时记录 */
    private CashRegisterDay currentRecord;

    /** 历史记录：日期 → 日记录 */
    private final ConcurrentSkipListMap<LocalDate, CashRegisterDay> history = new ConcurrentSkipListMap<>();

    /**
     * Spring 启动后初始化
     */
    @PostConstruct
    public void init() {
        this.currentDate = LocalDate.now();
        this.currentRecord = new CashRegisterDay();
        this.currentRecord.setDate(currentDate);
        this.currentRecord.setCreateTime(LocalDateTime.now());
        log.info("收银台内存上下文已初始化: 当前营业日={}, 刷新时间=凌晨{}点, 保留天数={}",
                currentDate, config.getResetHour(), config.getRetentionDays());
    }

    /**
     * 累加收入（订单完成时调用）
     */
    public void addIncome(BigDecimal amount) {
        checkAndRefresh();
        currentRecord.setTotalAmount(currentRecord.getTotalAmount().add(amount));
        currentRecord.setNetAmount(currentRecord.getTotalAmount().subtract(currentRecord.getRefundAmount()));
        currentRecord.setOrderCount(currentRecord.getOrderCount() + 1);
    }

    /**
     * 累加退款（退款完成时调用）
     */
    public void addRefund(BigDecimal amount) {
        checkAndRefresh();
        currentRecord.setRefundAmount(currentRecord.getRefundAmount().add(amount));
        currentRecord.setNetAmount(currentRecord.getTotalAmount().subtract(currentRecord.getRefundAmount()));
    }

    /**
     * 获取今日记录
     */
    public CashRegisterDay getToday() {
        checkAndRefresh();
        return currentRecord;
    }

    /**
     * 获取历史记录列表（分页，日期降序）
     */
    public List<CashRegisterDay> getHistory(int page, int pageSize) {
        List<CashRegisterDay> all = new ArrayList<>(history.values());
        java.util.Collections.reverse(all);
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= all.size()) {
            return new ArrayList<>();
        }
        int toIndex = Math.min(fromIndex + pageSize, all.size());
        return all.subList(fromIndex, toIndex);
    }

    /**
     * 获取历史记录总数
     */
    public int getHistoryCount() {
        return history.size();
    }

    /**
     * 检查是否需要跨天刷新
     */
    private synchronized void checkAndRefresh() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalTime resetTime = LocalTime.of(config.getResetHour(), 0);

        if (currentDate.isBefore(today) && !now.isBefore(resetTime)) {
            // 把今日记录存入历史
            if (currentRecord != null) {
                history.put(currentDate, currentRecord);
                log.info("收银台跨天刷新: 日期={} 已存入历史, 总收入={}", currentDate, currentRecord.getTotalAmount());
            }
            // 新建今日记录
            currentDate = today;
            currentRecord = new CashRegisterDay();
            currentRecord.setDate(currentDate);
            currentRecord.setCreateTime(LocalDateTime.now());
            log.info("收银台新营业日开始: 日期={}", currentDate);
        }
    }

    /**
     * 定时任务：每分钟检查是否需要跨天刷新（兜底机制）
     */
    @Scheduled(fixedDelay = 60000)
    public void scheduledRefresh() {
        checkAndRefresh();
    }

    /**
     * 定时任务：每天凌晨清理超过保留天数的历史记录
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void scheduledCleanup() {
        LocalDate cutoffDate = LocalDate.now().minusDays(config.getRetentionDays());
        int cleaned = 0;
        for (LocalDate date : history.keySet()) {
            if (date.isBefore(cutoffDate)) {
                history.remove(date);
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.info("收银台历史记录清理完成: 删除{}条, 保留{}天, 截止日期={}", cleaned, config.getRetentionDays(), cutoffDate);
        }
    }

    /**
     * 更新配置后重新初始化
     */
    public void reloadConfig() {
        log.info("收银台配置已更新: 刷新时间=凌晨{}点, 保留天数={}", config.getResetHour(), config.getRetentionDays());
    }
}