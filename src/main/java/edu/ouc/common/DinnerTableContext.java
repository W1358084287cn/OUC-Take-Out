package edu.ouc.common;

import edu.ouc.entity.DinnerTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class DinnerTableContext {

    private final Map<Long, DinnerTable> tableMap = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    @PostConstruct
    public void init() {
        for (char area : new char[]{'A', 'B', 'C'}) {
            String areaName = area == 'A' ? "大厅" : (area == 'B' ? "包间" : "露台");
            for (int i = 1; i <= 6; i++) {
                DinnerTable table = new DinnerTable();
                long id = idCounter.getAndIncrement();
                table.setId(id);
                table.setTableNo(area + "0" + i);
                table.setCapacity(area == 'B' ? 8 : 4);
                table.setArea(areaName);
                table.setStatus(0);
                table.setSort(i);
                tableMap.put(id, table);
            }
        }
        log.info("桌台数据初始化完成，共{}张桌台", tableMap.size());
    }

    public synchronized DinnerTable save(DinnerTable table) {
        if (table.getId() == null) {
            table.setId(idCounter.getAndIncrement());
        }
        tableMap.put(table.getId(), table);
        return table;
    }

    public DinnerTable getById(Long id) {
        return tableMap.get(id);
    }

    public List<DinnerTable> listAll() {
        return new ArrayList<>(tableMap.values());
    }

    public boolean updateById(DinnerTable table) {
        if (table.getId() == null || !tableMap.containsKey(table.getId())) {
            return false;
        }
        tableMap.put(table.getId(), table);
        return true;
    }

    public boolean removeById(Long id) {
        return tableMap.remove(id) != null;
    }

    public List<DinnerTable> listByArea(String area) {
        List<DinnerTable> result = new ArrayList<>();
        for (DinnerTable table : tableMap.values()) {
            if (area != null && area.equals(table.getArea())) {
                result.add(table);
            }
        }
        return result;
    }
}