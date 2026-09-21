package edu.ouc.service;

import edu.ouc.entity.DinnerTable;

import java.util.List;

public interface IDinnerTableService {

    List<DinnerTable> listAll();

    List<DinnerTable> listByArea(String area);

    DinnerTable getById(Long id);

    DinnerTable save(DinnerTable table);

    Boolean update(DinnerTable table);

    Boolean delete(Long id);

    Boolean occupy(Long id);

    Boolean release(Long id);

    String generateQrCode(Long tableId);
}