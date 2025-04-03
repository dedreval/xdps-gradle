package com.wiley.cms.cochrane.cmanager.specrender.data;

import java.util.List;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface ISpecRenderingStorage {
    int create(SpecRenderingVO vo);

    SpecRenderingEntity findSpecRendering(int id);

    SpecRenderingVO findSpecRenderingVO(int id);

    String getSpecRndFilePathLocalByDbIdAndFileName(int dbId, String fileName);

    void merge(SpecRenderingEntity vo);

    void mergeVO(SpecRenderingVO vo);

    void setCompleted(int id, boolean completed, boolean successful);

    void remove(int id);

    List<SpecRenderingEntity> getSpecRenderingList(int dbId);

    List<SpecRenderingVO> getSpecRenderingVOList(int dbId);

}
