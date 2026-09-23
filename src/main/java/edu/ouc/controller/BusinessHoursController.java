package edu.ouc.controller;

import edu.ouc.common.BusinessHoursContext;
import edu.ouc.common.R;
import edu.ouc.entity.BusinessHours;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 餐厅营业状态控制器（纯内存数据，不读写数据库）
 */
@RestController
@RequestMapping("/business")
@Slf4j
@RequiredArgsConstructor
public class BusinessHoursController {

    private final BusinessHoursContext businessHoursContext;

    /**
     * C端查询是否营业（无需登录）
     * 返回 { open: true/false, startTime: "07:00", endTime: "21:00", restDays: "" }
     */
    @GetMapping("/status")
    public R<Map<String, Object>> getStatus() {
        BusinessHours hours = businessHoursContext.getHours();
        boolean open = businessHoursContext.isOpenNow();

        Map<String, Object> result = new HashMap<>();
        result.put("open", open);
        result.put("manualOpen", hours.getOpen());  // 手动开关状态（1=营业 0=打烊）
        result.put("startTime", hours.getStartTime());
        result.put("endTime", hours.getEndTime());
        result.put("restDays", hours.getRestDays());

        log.info("C端查询营业状态: open={}", open);
        return R.success(result);
    }

    /**
     * B端获取营业时间配置
     */
    @GetMapping("/config")
    public R<BusinessHours> getConfig() {
        return R.success(businessHoursContext.getHours());
    }

    /**
     * B端更新营业时间配置
     */
    @PutMapping("/config")
    public R<BusinessHours> updateConfig(@RequestBody BusinessHours hours) {
        log.info("更新营业配置: open={}, start={}, end={}, restDays={}",
                hours.getOpen(), hours.getStartTime(), hours.getEndTime(), hours.getRestDays());
        businessHoursContext.update(hours);
        return R.success(businessHoursContext.getHours());
    }

    /**
     * B端开关门店（快捷操作：直接切换 open 字段）
     */
    @PutMapping("/toggle")
    public R<Map<String, Object>> toggle(@RequestBody Map<String, Integer> body) {
        Integer open = body.get("open");
        if (open == null || (open != 0 && open != 1)) {
            return R.error("参数 open 必须为 0（打烊）或 1（营业）");
        }
        BusinessHours update = new BusinessHours();
        update.setOpen(open);
        businessHoursContext.update(update);

        Map<String, Object> result = new HashMap<>();
        result.put("open", businessHoursContext.isOpenNow());
        result.put("manualOpen", open);
        log.info("门店状态切换: open={}", open);
        return R.success(result);
    }
}