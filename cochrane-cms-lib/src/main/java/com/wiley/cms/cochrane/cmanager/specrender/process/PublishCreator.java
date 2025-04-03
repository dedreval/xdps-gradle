package com.wiley.cms.cochrane.cmanager.specrender.process;

import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.data.record.CDSRVO;
import com.wiley.cms.cochrane.cmanager.data.record.IGroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordVO;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleEntity;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordVO;
import com.wiley.cms.cochrane.cmanager.specrender.exception.SpecRendCreatorException;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.XmlUtils;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class PublishCreator {
    private static final String DTD_STRING =
            "<!DOCTYPE component SYSTEM \"" + CochraneCMSProperties.getProperty("cms.resources.dtd.wileyml21") + "\">";

    private static final Logger LOG = Logger.getLogger(PublishCreator.class);
    private static final String SHOW_PRODUCT_SUBTITLE = "showProductSubtitle";
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String RECORD_LIST_OPEN_TAG = "<recordList>";
    private static final String RECORD_LIST_CLOSE_TAG = "</recordList>";
    private static final String PDF = "pdf";
    private static final String GROUP_VRECORD_LIST_OPEN_TAG = "<groupVRecordList>";
    private static final String GROUP_VRECORD_LIST_CLOSE_TAG = "</groupVRecordList>";
    private static final String GROUP_CLOSE_TAG = "</group>";
    private static final String RECORD_CLOSE_TAG = "</record>";
    private static final String GROUP_NAME_OPEN_TAG = "<group name=\"";
    private static final String RECORD_OPEN_TAG = "<record ";
    private static final String ARTICLES_DIR = "/articles/";
    private static final String DOI_ATTR = " doi=\"";
    private static final String HTML_PATH_COCHRANE_ATTR = "html_path=\"/cochrane/";

    private Creator creator;

    PublishCreator(Creator creator) {
        this.creator = creator;
    }

    void createPublishABC(List<IRecordVO> list, String filePath, boolean showProductSubtitle, String listType)
        throws SpecRendCreatorException {

        StringBuilder data = getPublishABCList(list);
        if (showProductSubtitle) {
            creator.articleListTransformer.setParameter(SHOW_PRODUCT_SUBTITLE, "yes");
        } else {
            creator.articleListTransformer.setParameter(SHOW_PRODUCT_SUBTITLE, "no");
        }
        creator.articleListTransformer.setParameter("listType", listType);
        creator.createArticleListOutput(data, filePath, creator.articleListTransformer);
    }

    private StringBuilder getPublishABCList(List<IRecordVO> list) {
        StringBuilder sb = new StringBuilder();
        sb.append(XML_HEADER);
        sb.append(DTD_STRING);
        sb.append(RECORD_LIST_OPEN_TAG);

        boolean isCDSR = creator.getDbVO().getTitle().equals("clsysrev");
        boolean isMeth = creator.getDbVO().getTitle().equals("clmethrev");

        for (IRecordVO record : list) {
            String recordTag = RECORD_OPEN_TAG
                    + HTML_PATH_COCHRANE_ATTR + creator.getDbVO().getTitle() + ARTICLES_DIR + record.getName()
                    + "/" + Creator.getDefaultFrameFileName(creator.getDbVO().getTitle()) + "\" ";

            if (isCDSR || isMeth) {
                recordTag += addRecordPDFAttributes(record, "");
                recordTag += DOI_ATTR + ((CDSRVO) record).getDoi() + "\" ";
            }

            recordTag += creator.getStatusAndSubtitleParams(record)
                    + ">"
                    + record.getUnitTitle()
                    + RECORD_CLOSE_TAG;
            sb.append(recordTag);
        }
        sb.append(RECORD_LIST_CLOSE_TAG);
        return sb;
    }

    private String addRecordPDFAttributes(IRecordVO record, String pdfVersion) {
        String frameFS = Creator.DEFAULT_PDF_FRAME_FILE_NAME;

        if (pdfVersion != null && pdfVersion.length() > 0) {
            frameFS = frameFS.replace(PDF, PDF + pdfVersion);
        }

        return PDF + pdfVersion + "_path=\"/cochrane/" + creator.getDbVO().getTitle()
                + ARTICLES_DIR + record.getName() + "/" + frameFS + "\" "
                + PDF + pdfVersion + "_size=\""
                + creator.getPdfSize(record, pdfVersion) + "\" ";
    }

    void createAboutByGroup(Map<ProductSubtitleVO, List<RecordVO>> data, String filePath)
        throws SpecRendCreatorException {

        StringBuilder result = getAboutByGroupXml(data);
        creator.createArticleListOutput(result, filePath, creator.articleAboutListByGroupTransformer);
    }

    StringBuilder getAboutByGroupXml(Map<ProductSubtitleVO, List<RecordVO>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append(XML_HEADER);
        sb.append(DTD_STRING);
        sb.append(getAboutRecordByGroupList(data));

        return sb;
    }

    void createCDSRPublishByGroup(Map<IGroupVO, List<CDSRVO>> data, String filePath)
        throws SpecRendCreatorException {

        StringBuilder result = getPublishByGroupXml(data);
        creator.createGroupOutput(result, filePath, creator.articleCDSRListByGroupTransformer, "");
    }

    String createPublishAddOnFile(String fileData, Map<String, String> abcMenu) {
        String publishFileData = fileData.replaceAll("%path%", creator.replacePath);
        String publishCompiledMenu = createLinksMenu(abcMenu);
        publishFileData = publishFileData.replaceAll("%links%", publishCompiledMenu);

        return publishFileData;
    }

    private StringBuilder getPublishByGroupXml(Map<IGroupVO, List<CDSRVO>> data) {
        String recordList = getPublishRecordByGroupList(data);

        return creator.getGroupXml(data, recordList);
    }

    private String getPublishRecordByGroupList(Map<IGroupVO, List<CDSRVO>> data) {
        StringBuilder sb = new StringBuilder();

        sb.append(GROUP_VRECORD_LIST_OPEN_TAG);
        for (IGroupVO group : data.keySet()) {
            sb.append(getPublishGroupRecords(group, data.get(group)));
        }
        sb.append(GROUP_VRECORD_LIST_CLOSE_TAG);

        return sb.toString();
    }

    private String getAboutRecordByGroupList(Map<ProductSubtitleVO, List<RecordVO>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append(GROUP_VRECORD_LIST_OPEN_TAG);

        for (ProductSubtitleVO group : data.keySet()) {
            sb.append(GROUP_NAME_OPEN_TAG);
            sb.append(group.getName());
            sb.append("\">");
            if (group.getId() == ProductSubtitleEntity.ProductSubtitle.CRGS) {
                //sb.append("<cdsrLink href=\"/cochrane/clabout/titles/crglist.html\"/>");
                sb.append(
                    "<cdsrLink href=\"http://onlinelibrary.wiley.com/book/10.1002/14651858/homepage/crglist.html\"/>");
            }
            sb.append(RECORD_LIST_OPEN_TAG);
            List<RecordVO> recordList = data.get(group);
            for (RecordVO record : recordList) {
                String recordStartTag = RECORD_OPEN_TAG
                        + HTML_PATH_COCHRANE_ATTR + creator.getDbVO().getTitle()
                        + ARTICLES_DIR + record.getName()
                        + "/" + Creator.getDefaultFrameFileName(creator.getDbVO().getTitle()) + "\" "
                        + creator.getStatusAndSubtitleParams(record)
                        + ">";
                sb.append(recordStartTag);
                sb.append(record.getUnitTitle());
                sb.append(RECORD_CLOSE_TAG);
            }
            sb.append(RECORD_LIST_CLOSE_TAG);
            sb.append(GROUP_CLOSE_TAG);
        }
        sb.append(GROUP_VRECORD_LIST_CLOSE_TAG);

        return sb.toString();
    }

    String createLinksMenu(Map<String, String> abcMenu) {
        String path = creator.replacePath;

        return creator.createLinksMenu(path, abcMenu);
    }


    void createCDSRPublishGroup(IGroupVO group, String filePath, Map<IGroupVO, List<CDSRVO>> data)
        throws SpecRendCreatorException {

        StringBuilder result = new StringBuilder();
        result.append(DTD_STRING);
        result.append(GROUP_VRECORD_LIST_OPEN_TAG);

        for (IGroupVO curGroup : data.keySet()) {
            List<CDSRVO> recordList = data.get(curGroup);
            recordList = curGroup.getName().equals(group.getName()) ? recordList : null;
            result.append(getPublishGroupRecords(curGroup, recordList));
        }

        result.append(GROUP_VRECORD_LIST_CLOSE_TAG);

        creator.createGroupOutput(result, filePath, creator.articleCDSRListByGroupTransformer, group.getName());
    }

    private StringBuilder getPublishGroupRecords(IGroupVO group, List<CDSRVO> recordList) {
        StringBuilder sb = new StringBuilder();
        sb.append(GROUP_NAME_OPEN_TAG);
        sb.append(XmlUtils.escapeElementEntities(group.getName()));
        sb.append("\" unitTitle=\"");
        sb.append(XmlUtils.escapeElementEntities(group.getUnitTitle()));
        sb.append("\" html_path=\"/cochrane/clabout");
        sb.append(ARTICLES_DIR);
        sb.append(group.getName());
        sb.append("/");
        sb.append(Creator.getDefaultFrameFileName(creator.getDbVO().getTitle()));
        sb.append("\">");
        if (recordList != null && recordList.size() > 0) {
            sb.append(RECORD_LIST_OPEN_TAG);
            for (CDSRVO record : recordList) {
                String recordStartTag = RECORD_OPEN_TAG
                        + "html_path=\"/cochrane/clsysrev"
                        + ARTICLES_DIR + record.getName()
                        + "/" + Creator.getDefaultFrameFileName(creator.getDbVO().getTitle()) + "\" "
                        + DOI_ATTR + record.getDoi() + "\" "
                        + addRecordPDFAttributes(record, "")
                        + creator.getStatusAndSubtitleParams(record)
                        + ">";
                sb.append(recordStartTag);
                sb.append(record.getUnitTitle());
                sb.append(RECORD_CLOSE_TAG);
            }
            sb.append(RECORD_LIST_CLOSE_TAG);
        }
        sb.append(GROUP_CLOSE_TAG);

        return sb;
    }

}
