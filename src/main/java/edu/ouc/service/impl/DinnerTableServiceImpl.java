package edu.ouc.service.impl;

import edu.ouc.common.DinnerTableContext;
import edu.ouc.entity.DinnerTable;
import edu.ouc.service.IDinnerTableService;
import edu.ouc.utils.QRCodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class DinnerTableServiceImpl implements IDinnerTableService {

    private final DinnerTableContext dinnerTableContext;
    private final String serverDomain;

    public DinnerTableServiceImpl(DinnerTableContext dinnerTableContext,
                                  @Value("${reggie.domain:http://localhost:8080}") String serverDomain) {
        this.dinnerTableContext = dinnerTableContext;
        this.serverDomain = serverDomain;
    }

    @Override
    public List<DinnerTable> listAll() {
        return dinnerTableContext.listAll();
    }

    @Override
    public List<DinnerTable> listByArea(String area) {
        if (area == null || area.isEmpty()) {
            return dinnerTableContext.listAll();
        }
        return dinnerTableContext.listByArea(area);
    }

    @Override
    public DinnerTable getById(Long id) {
        return dinnerTableContext.getById(id);
    }

    @Override
    public DinnerTable save(DinnerTable table) {
        log.info("新增桌台: tableNo={}, area={}, capacity={}", table.getTableNo(), table.getArea(), table.getCapacity());
        return dinnerTableContext.save(table);
    }

    @Override
    public Boolean update(DinnerTable table) {
        log.info("修改桌台: id={}, tableNo={}, area={}", table.getId(), table.getTableNo(), table.getArea());
        return dinnerTableContext.updateById(table);
    }

    @Override
    public Boolean delete(Long id) {
        log.info("删除桌台: id={}", id);
        return dinnerTableContext.removeById(id);
    }

    @Override
    public Boolean occupy(Long id) {
        log.info("桌台开台: id={}", id);
        DinnerTable table = dinnerTableContext.getById(id);
        if (table == null) {
            return false;
        }
        table.setStatus(1);
        dinnerTableContext.updateById(table);
        return true;
    }

    @Override
    public Boolean release(Long id) {
        log.info("桌台清台: id={}", id);
        DinnerTable table = dinnerTableContext.getById(id);
        if (table == null) {
            return false;
        }
        table.setStatus(0);
        dinnerTableContext.updateById(table);
        return true;
    }

    @Override
    public String generateQrCode(Long tableId) {
        DinnerTable table = dinnerTableContext.getById(tableId);
        if (table == null) {
            return null;
        }
        String url = serverDomain + "/front/index.html?tableId=" + tableId + "&tableNo=" + table.getTableNo();
        return QRCodeGenerator.generateBase64(url, 300, 300);
    }
}