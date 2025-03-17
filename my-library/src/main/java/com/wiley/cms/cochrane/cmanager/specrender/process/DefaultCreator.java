package com.wiley.cms.cochrane.cmanager.specrender.process;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordVO;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingVO;
import com.wiley.cms.cochrane.cmanager.specrender.exception.SpecRendCreatorException;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class DefaultCreator implements ICreator {
    static final Logger LOG = Logger.getLogger(DefaultCreator.class);

    private Creator creator;

    DefaultCreator(Creator creator) {
        this.creator = creator;
    }

    public void run(SpecRenderingVO vo) throws SpecRendCreatorException {
        Map<String, String> abcMenu = creator.createFiles(vo);
        /*creator.createAlphaFS(vo, abcMenu);

        String fileName = "cochrane_" + creator.getDbVO().getTitle() + "_alphabar.html";
        createAlphaBar(vo, fileName, abcMenu);*/
    }

    public List<IRecordVO> getDbRecordVOByFirstCharList(int dbId, char ch, int productSubtitle) {
        List<RecordVO> voList = Creator.getRecordResultStorage().
                getDbRecordVOByFirstCharList(dbId, ch, productSubtitle);
        return new ArrayList<IRecordVO>(voList);
    }

    public List<IRecordVO> getDbRecordVOByNumStartedList(int dbId, int productSubtitle) {
        List<RecordVO> voList = Creator.getRecordResultStorage().
                getDbRecordVOByNumStartedList(dbId, productSubtitle);
        return new ArrayList<IRecordVO>(voList);
    }

    private void createAlphaBar(SpecRenderingVO vo, String fileName, Map<String, String> abcMenu) {
        String image = CochraneCMSProperties.getProperty("cms.cochrane.specrnd."
                + creator.getDbVO().getTitle() + ".image");
        String dbInclusionTemplate = image + "\n";

        creator.createAlphaBar(dbInclusionTemplate, fileName, vo, abcMenu);
    }
}
