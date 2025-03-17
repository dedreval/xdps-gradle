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
public class AboutCreator implements ICreator {
    private Creator creator;


    private final int[] groupArray = {
        ProductSubtitleEntity.ProductSubtitle.COLLABORATION,
        ProductSubtitleEntity.ProductSubtitle.CRGS,
        ProductSubtitleEntity.ProductSubtitle.FIELDS,
        ProductSubtitleEntity.ProductSubtitle.METHODS_GROUPS,
        // ProductSubtitleEntity.ProductSubtitle.CENTRES,
        // ProductSubtitleEntity.ProductSubtitle.POSSIBLE_ENTITIES
    };

    AboutCreator(Creator creator) {
        this.creator = creator;
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

    public void run(SpecRenderingVO vo) throws SpecRendCreatorException {
        String firstPage = "A-Z.html";

        //createByGroupFileList(vo, firstPage);

        //String fileName = "cochrane_" + creator.getDbVO().getTitle() + "_alphabar.html";
        //createAlphaBar(vo, fileName, null);
        //createAlphaFS(vo, firstPage);
    }

    /*private void createByGroupFileList(SpecRenderingVO vo, String fileName) throws SpecRendCreatorException {
        List<ProductSubtitleVO> groupList = Creator.getRecordResultStorage().getProductSubtitles(groupArray);

        int dbSize = 0;

        Map<ProductSubtitleVO, List<RecordVO>> data = new LinkedHashMap<ProductSubtitleVO, List<RecordVO>>();

        for (ProductSubtitleVO group : groupList) {
            fillRecordList(data, group);
            if (data.containsKey(group)) {
                dbSize += data.get(group).size();
            }
        }

        String filePathPublish = creator.getFolder() + "/publish/" + fileName;
        String filePathLocal = creator.getFolder() + "/local/" + fileName;

        creator.publishCreator.createAboutByGroup(data, filePathPublish);
        creator.localCreator.createLocalByGroup(data, filePathLocal);

        creator.addFileVO2SpecRenderingVO(filePathPublish, filePathLocal, dbSize, vo);
    }

    private void fillRecordList(Map<ProductSubtitleVO, List<RecordVO>> data, ProductSubtitleVO group) {
        List<RecordVO> recordList = Creator.getRecordResultStorage()
                .getDbRecordVOListByProductSubtitle(creator.dbId, group.getId());
        if (recordList != null && recordList.size() > 0) {
            data.put(group, recordList);
        }
    }*/

    void createAlphaFS(SpecRenderingVO vo, String firstPage) {
        String fileName = "cochrane_clabout_contentsbar_fs.html";
        String fileTemplate = "cochrane_narrow_title_alpha_fs.html";

        HashMap<String, String> replace = new HashMap<String, String>();
        replace.put("%postfix%", "");
        replace.put("%firstPage%", firstPage);

        creator.createFile(fileName, fileTemplate, replace, replace, vo);
    }

    private void createAlphaBar(SpecRenderingVO vo, String fileName, Map<String, String> abcMenu) {
        String image = CochraneCMSProperties.getProperty("cms.cochrane.specrnd."
                + creator.getDbVO().getTitle() + ".image");
        String dbInclusionTemplate = image + "\n";

        creator.createAlphaBar(dbInclusionTemplate, fileName, vo, abcMenu);
    }
}