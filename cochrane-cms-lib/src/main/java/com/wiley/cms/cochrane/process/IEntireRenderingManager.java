package com.wiley.cms.cochrane.process;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.process.handler.RenderingRecordHandler;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10.09.13
 */
public interface IEntireRenderingManager extends IBaseRenderingManager {

    void startRendering(RenderingPlan plan, String dbName, boolean withPrevious);

    void startRendering(RenderingPlan plan, List<String> records, String dbName, boolean withPrevious);

    void startRendering(RenderingPlan plan, Integer[] recordIds, String dbName, boolean withPrevious);

    void startRendering(RenderingRecordHandler handler, ProcessVO pvo) throws ProcessException;

    void endRendering(RenderingRecordHandler handler, ProcessVO pvo);
}
