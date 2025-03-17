package com.wiley.cms.cochrane.cmanager.specrender.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordVO;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordVO;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingVO;
import com.wiley.cms.cochrane.cmanager.specrender.exception.SpecRendCreatorException;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class EEDCreator implements ICreator {
    private static final String COCHRANE_PREFIX = "cochrane_";
    private Creator creator;

    EEDCreator(Creator creator) {
        this.creator = creator;
    }

    public void run(SpecRenderingVO vo) throws SpecRendCreatorException {
        Map<String, String> criticallyABCMenu = creator.createFiles(vo, "critically/",
                ProductSubtitleEntity.ProductSubtitle.ECONOMIC_EVALUATIONS);
        Map<String, String> otherABCMenu = creator.createFiles(vo, "other/",
                ProductSubtitleEntity.ProductSubtitle.OTHER_ECONOMIC_STUDIES);
        Map<String, String> allMenu = creator.createFiles(vo);

        /*creator.createAlphaFS(vo, criticallyABCMenu, "_critically");
        creator.createAlphaFS(vo, otherABCMenu, "_other");
        creator.createAlphaFS(vo, allMenu);

        createSpecFiles(vo, criticallyABCMenu, otherABCMenu, allMenu);*/
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

    private void createSpecFiles(SpecRenderingVO vo,
                                 Map<String, String> criticallyABCMenu,
                                 Map<String, String> otherABCMenu,
                                 Map<String, String> allMenu) throws SpecRendCreatorException {

        String criticallyFileName = COCHRANE_PREFIX + creator.getDbVO().getTitle() + "_alphabar_critically.html";
        createAlphaBar(vo, criticallyFileName, criticallyABCMenu);

        String otherFileName = COCHRANE_PREFIX + creator.getDbVO().getTitle() + "_alphabar_other.html";
        createAlphaBar(vo, otherFileName, otherABCMenu);

        String allFileName = COCHRANE_PREFIX + creator.getDbVO().getTitle() + "_alphabar.html";
        createAlphaBar(vo, allFileName, allMenu);
    }

    private void createAlphaBar(SpecRenderingVO vo, String fileName, Map<String, String> abcMenu) {
        String image = CochraneCMSProperties.getProperty("cms.cochrane.specrnd."
                + creator.getDbVO().getTitle() + ".image");

//        String dbInclusionTemplate = image + "\n <nobr><a href=\"%cochrane%cochrane_cleed_alpha_critically_fs.html\" "
//                + "alt=\"Critically Appraised Economic Evaluations\" target=\"_top\">"
//                + "Critically Appraised Economic Evaluations</a>"
//                + "&nbsp;&nbsp;<a href=\"%cochrane%cochrane_cleed_alpha_other_fs.html\" "
//                + "alt=\"Other Economic Studies: Bibliographic Details\" target=\"_top\">Other Economic Studies: "
//                + "Bibliographic Details</a>&nbsp;&nbsp;&nbsp;<a href=\"%cochrane%cochrane_cleed_alpha_fs.html\" "
//                + "alt=\"All Studies\" target=\"_top\">All Studies</a></nobr>";

        Map<String, String> map = new HashMap<String, String>();
        map.put("cochrane", "");
        String dbInclusionTemplate = image
                + CochraneCMSProperties.getProperty("spec_rendering.eed.createAlphaBar", map);

        creator.createAlphaBar(dbInclusionTemplate, fileName, vo, abcMenu);
    }
}
