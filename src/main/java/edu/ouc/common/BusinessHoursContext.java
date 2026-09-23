package edu.ouc.common;

import edu.ouc.entity.BusinessHours;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 餐厅营业状态内存上下文（Spring启动后保持全内存运行，不连数据库）
 *
 * 营业判断逻辑（优先级从高到低）：
 * 1. 手动开关 open=0 → 直接打烊
 * 2. 手动开关 open=1 + 当前时间在营业时间范围内 + 今天不是休息日 → 营业中
 * 3. 其他情况 → 打烊
 */
@Component
@Slf4j
public class BusinessHoursContext {

    /** 默认营业开始时间 */
    private static final String DEFAULT_START_TIME = "07:00";

    /** 默认营业结束时间 */
    private static final String DEFAULT_END_TIME = "21:00";

    @Getter
    private final BusinessHours hours = new BusinessHours();

    public BusinessHoursContext() {
        hours.setOpen(1);                      // 默认营业中
        hours.setStartTime(DEFAULT_START_TIME); // 默认07:00开始
        hours.setEndTime(DEFAULT_END_TIME);     // 默认21:00结束
        hours.setRestDays("");                  // 默认无休息日
    }

    /**
     * 更新营业时间配置
     */
    public void update(BusinessHours newHours) {
        if (newHours.getOpen() != null) {
            hours.setOpen(newHours.getOpen());
        }
        if (newHours.getStartTime() != null && !newHours.getStartTime().trim().isEmpty()) {
            hours.setStartTime(newHours.getStartTime().trim());
        }
        if (newHours.getEndTime() != null && !newHours.getEndTime().trim().isEmpty()) {
            hours.setEndTime(newHours.getEndTime().trim());
        }
        if (newHours.getRestDays() != null) {
            hours.setRestDays(newHours.getRestDays());
        }
        log.info("营业时间已更新: open={}, start={}, end={}, restDays={}",
                hours.getOpen(), hours.getStartTime(), hours.getEndTime(), hours.getRestDays());
    }

    /**
     * 判断当前是否营业中
     */
    public boolean isOpenNow() {
        // 规则1：手动打烊，直接返回 false
        if (hours.getOpen() == null || hours.getOpen() == 0) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // 规则2：检查今天是否为休息日
        if (isRestDay(now)) {
            return false;
        }

        // 规则3：检查当前时间是否在营业时间范围内
        try {
            LocalTime start = LocalTime.parse(hours.getStartTime());
            LocalTime end = LocalTime.parse(hours.getEndTime());
            LocalTime current = now.toLocalTime();
            return !current.isBefore(start) && !current.isAfter(end);
        } catch (Exception e) {
            log.warn("营业时间解析失败: start={}, end={}", hours.getStartTime(), hours.getEndTime());
            return false;
        }
    }

    /**
     * 判断指定日期是否为休息日
     */
    private boolean isRestDay(LocalDateTime dateTime) {
        String restDays = hours.getRestDays();
        if (restDays == null || restDays.trim().isEmpty()) {
            return false;
        }
        DayOfWeek dow = dateTime.getDayOfWeek();
        int dayValue = dow.getValue(); // 1=Mon...7=Sun
        Set<Integer> restSet = Arrays.stream(restDays.trim().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
        return restSet.contains(dayValue);
    }
}