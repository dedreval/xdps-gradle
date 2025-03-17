package com.wiley.cms.cochrane.cmanager.specrender.process;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.record.IRecordVO;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingVO;
import com.wiley.cms.cochrane.cmanager.specrender.exception.SpecRendCreatorException;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface ICreator {
    void run(SpecRenderingVO vo) throws SpecRendCreatorException;

    List<IRecordVO> getDbRecordVOByFirstCharList(int dbId, char ch, int productSubtitle);

    List<IRecordVO> getDbRecordVOByNumStartedList(int dbId, int productSubtitle);
}
