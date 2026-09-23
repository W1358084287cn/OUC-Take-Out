package edu.ouc.controller;

import edu.ouc.common.AnnouncementContext;
import edu.ouc.common.BaseContext;
import edu.ouc.common.R;
import edu.ouc.entity.Announcement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 餐厅公告控制器（纯内存数据，不读写数据库）
 */
@RestController
@RequestMapping("/announcement")
@Slf4j
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementContext announcementContext;

    /**
     * C端获取所有生效公告（无需登录即可访问）
     */
    @GetMapping("/active")
    public R<List<Announcement>> getActive() {
        List<Announcement> list = announcementContext.getActive();
        log.info("C端查询公告: 生效中{}条", list.size());
        return R.success(list);
    }

    /**
     * B端获取所有公告（含已下架）
     */
    @GetMapping("/list")
    public R<List<Announcement>> listAll() {
        log.info("B端查询公告列表");
        return R.success(announcementContext.listAll());
    }

    /**
     * B端新增公告
     */
    @PostMapping
    public R<Announcement> add(@RequestBody Announcement announcement) {
        if (announcement.getTitle() == null || announcement.getTitle().trim().isEmpty()) {
            return R.error("公告标题不能为空");
        }
        if ((announcement.getContent() == null || announcement.getContent().trim().isEmpty())
                && (announcement.getImage() == null || announcement.getImage().trim().isEmpty())) {
            return R.error("公告内容和配图至少填写一项");
        }
        announcement.setCreateUser(BaseContext.getCurrentUserId());
        Announcement result = announcementContext.add(announcement);
        return R.success(result);
    }

    /**
     * B端编辑公告
     */
    @PutMapping
    public R<String> update(@RequestBody Announcement announcement) {
        if (announcement.getId() == null) {
            return R.error("公告ID不能为空");
        }
        if (announcementContext.update(announcement)) {
            return R.success("更新成功");
        }
        return R.error("公告不存在");
    }

    /**
     * B端下架公告
     */
    @DeleteMapping("/{id}")
    public R<String> remove(@PathVariable Long id) {
        if (announcementContext.remove(id)) {
            return R.success("下架成功");
        }
        return R.error("公告不存在");
    }
}