package edu.ouc.controller;

import edu.ouc.common.R;
import edu.ouc.entity.DinnerTable;
import edu.ouc.service.IDinnerTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dinnerTable")
@Slf4j
@RequiredArgsConstructor
public class DinnerTableController {

    private final IDinnerTableService dinnerTableService;

    @GetMapping("/page")
    public R<Map<String, Object>> page(int page, int pageSize, String area) {
        List<DinnerTable> all = dinnerTableService.listByArea(area);
        // 简易内存分页
        int from = (page - 1) * pageSize;
        List<DinnerTable> records = from < all.size()
                ? all.stream().skip(from).limit(pageSize).collect(Collectors.toList())
                : java.util.Collections.emptyList();
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", all.size());
        result.put("size", pageSize);
        result.put("current", page);
        return R.success(result);
    }

    @GetMapping("/list")
    public R<List<DinnerTable>> list(String area) {
        return R.success(dinnerTableService.listByArea(area));
    }

    @PostMapping
    public R<String> save(@RequestBody DinnerTable table) {
        log.info("新增桌台: tableNo={}, area={}, capacity={}", table.getTableNo(), table.getArea(), table.getCapacity());
        dinnerTableService.save(table);
        return R.success("新增成功");
    }

    @PutMapping
    public R<String> update(@RequestBody DinnerTable table) {
        log.info("修改桌台: id={}, tableNo={}, area={}", table.getId(), table.getTableNo(), table.getArea());
        if (dinnerTableService.update(table)) {
            return R.success("修改成功");
        }
        return R.error("修改失败");
    }

    @DeleteMapping
    public R<String> delete(@RequestParam Long id) {
        log.info("删除桌台: id={}", id);
        if (dinnerTableService.delete(id)) {
            return R.success("删除成功");
        }
        return R.error("删除失败");
    }

    @GetMapping("/{id}")
    public R<DinnerTable> getById(@PathVariable Long id) {
        return R.success(dinnerTableService.getById(id));
    }

    @PostMapping("/occupy/{id}")
    public R<String> occupy(@PathVariable Long id) {
        log.info("桌台开台: id={}", id);
        if (dinnerTableService.occupy(id)) {
            return R.success("开台成功");
        }
        return R.error("开台失败");
    }

    @PostMapping("/release/{id}")
    public R<String> release(@PathVariable Long id) {
        log.info("桌台清台: id={}", id);
        if (dinnerTableService.release(id)) {
            return R.success("清台成功");
        }
        return R.error("清台失败");
    }

    @GetMapping("/qrCode/{id}")
    public R<String> qrCode(@PathVariable Long id) {
        log.info("生成桌台二维码: id={}", id);
        String base64 = dinnerTableService.generateQrCode(id);
        if (base64 != null) {
            return R.success(base64);
        }
        return R.error("生成二维码失败");
    }
}