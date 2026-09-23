package edu.ouc.common;

import edu.ouc.entity.Announcement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 餐厅公告内存上下文（Spring启动后保持全内存运行，不连数据库）
 */
@Component
@Slf4j
public class AnnouncementContext {

    /** 公告存储（ConcurrentLinkedDeque 保持插入顺序，线程安全） */
    private final ConcurrentLinkedDeque<Announcement> store = new ConcurrentLinkedDeque<>();

    /** ID自增器 */
    private final AtomicLong idCounter = new AtomicLong(1);

    /** 最多保留公告数（旧公告自动淘汰） */
    private static final int MAX_ANNOUNCEMENTS = 50;

    /**
     * 获取所有生效中的公告（按发布时间倒序）
     */
    public List<Announcement> getActive() {
        return store.stream()
                .filter(a -> a.getStatus() == 1)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有公告（含已下架，B端管理用）
     */
    public List<Announcement> listAll() {
        return new ArrayList<>(store);
    }

    /**
     * 新增公告
     */
    public Announcement add(Announcement announcement) {
        announcement.setId(idCounter.getAndIncrement());
        announcement.setStatus(1);
        announcement.setCreateTime(LocalDateTime.now());
        store.addFirst(announcement);
        // 超过最大保留数时淘汰最旧的
        while (store.size() > MAX_ANNOUNCEMENTS) {
            store.pollLast();
        }
        log.info("公告已发布: id={}, title={}", announcement.getId(), announcement.getTitle());
        return announcement;
    }

    /**
     * 下架公告
     */
    public boolean remove(Long id) {
        for (Announcement a : store) {
            if (a.getId().equals(id)) {
                a.setStatus(0);
                a.setUpdateTime(LocalDateTime.now());
                log.info("公告已下架: id={}, title={}", a.getId(), a.getTitle());
                return true;
            }
        }
        return false;
    }

    /**
     * 编辑公告
     */
    public boolean update(Announcement announcement) {
        for (Announcement a : store) {
            if (a.getId().equals(announcement.getId())) {
                if (announcement.getTitle() != null) {
                    a.setTitle(announcement.getTitle());
                }
                if (announcement.getContent() != null) {
                    a.setContent(announcement.getContent());
                }
                if (announcement.getImage() != null) {
                    a.setImage(announcement.getImage());
                }
                a.setUpdateTime(LocalDateTime.now());
                log.info("公告已更新: id={}, title={}", a.getId(), a.getTitle());
                return true;
            }
        }
        return false;
    }
}