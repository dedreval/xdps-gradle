package com.wiley.cms.cochrane.cmanager.export.data;

import java.util.List;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IExportStorage {
    int create(ExportVO vo);

    void setCompleted(int id, int state, String filePath);

    void remove(int id);

    List<ExportVO> getExportVOList(String dbName, Integer clDbId, String user, boolean isAdmin);

    List<ExportVO> getExportVOList(String dbName, Integer clDbId, String user, boolean isAdmin, int limit);

    int getExportVOListSize(String dbName, Integer clDbId, String user, boolean isAdmin);
}
