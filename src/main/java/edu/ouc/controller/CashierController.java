package edu.ouc.controller;

import edu.ouc.common.CashRegisterContext;
import edu.ouc.common.R;
import edu.ouc.config.CashRegisterConfig;
import edu.ouc.dto.CashierTodayDto;
import edu.ouc.entity.CashRegisterDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收银台控制器（纯内存数据，不读写数据库）
 */
@RestController
@RequestMapping("/cashier")
@Slf4j
@RequiredArgsConstructor
public class CashierController {

    private final CashRegisterContext cashRegisterContext;
    private final CashRegisterConfig cashRegisterConfig;

    /**
     * 获取今日收银统计
     */
    @GetMapping("/today")
    public R<CashierTodayDto> today() {
        CashRegisterDay today = cashRegisterContext.getToday();

        CashierTodayDto dto = new CashierTodayDto();
        dto.setTotalOrders(today.getOrderCount());
        dto.setTotalAmount(today.getTotalAmount());
        dto.setRefundOrders(0);
        dto.setRefundAmount(today.getRefundAmount());

        log.info("收银台今日统计: 订单数={}, 总收入={}, 退款={}, 净收入={}",
                today.getOrderCount(), today.getTotalAmount(), today.getRefundAmount(), today.getNetAmount());
        return R.success(dto);
    }

    /**
     * 获取历史记录（分页）
     */
    @GetMapping("/history")
    public R<Map<String, Object>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        List<CashRegisterDay> list = cashRegisterContext.getHistory(page, pageSize);
        int total = cashRegisterContext.getHistoryCount();

        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return R.success(result);
    }

    /**
     * 获取当前配置
     */
    @GetMapping("/config")
    public R<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("resetHour", cashRegisterConfig.getResetHour());
        config.put("retentionDays", cashRegisterConfig.getRetentionDays());
        return R.success(config);
    }

    /**
     * 更新配置（刷新时间 / 保留天数）
     */
    @PutMapping("/config")
    public R<String> updateConfig(@RequestBody Map<String, Integer> params) {
        Integer resetHour = params.get("resetHour");
        Integer retentionDays = params.get("retentionDays");

        if (resetHour != null) {
            if (resetHour < 0 || resetHour > 23) {
                return R.error("刷新时间必须在0-23之间");
            }
            cashRegisterConfig.setResetHour(resetHour);
        }
        if (retentionDays != null) {
            if (retentionDays < 1 || retentionDays > 3650) {
                return R.error("保留天数必须在1-3650之间（最高10年）");
            }
            cashRegisterConfig.setRetentionDays(retentionDays);
        }

        cashRegisterContext.reloadConfig();
        log.info("收银台配置已更新: resetHour={}, retentionDays={}", resetHour, retentionDays);
        return R.success("配置更新成功");
    }
}