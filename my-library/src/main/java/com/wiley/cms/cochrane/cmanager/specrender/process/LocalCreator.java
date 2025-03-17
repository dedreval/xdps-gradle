package com.wiley.cms.cochrane.cmanager.specrender.process;

import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.record.CDSRVO;
import com.wiley.cms.cochrane.cmanager.data.record.IGroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordVO;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleEntity;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordVO;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.specrender.exception.SpecRendCreatorException;
import com.wiley.tes.util.XmlUtils;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class LocalCreator {
    private static final String DTD_STRING =
            "<!DOCTYPE component SYSTEM \"" + CochraneCMSProperties.getProperty("cms.resources.dtd.wileyml21") + "\">";

    private static final String SHOW_PRODUCT_SUBTITLE = "showProductSubtitle";
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String RECORD_LIST_OPEN_TAG = "<recordList>";
    private static final String RECORD_LIST_CLOSE_TAG = "</recordList>";
    private static final String RECORD_CLOSE_TAG = "</record>";
    private static final String GROUP_VRECORD_LIST_OPEN_TAG = "<groupVRecordList>";
    private static final String GROUP_VRECORD_LIST_CLOSE_TAG = "</groupVRecordList>";
    private static final String GROUP_CLOSE_TAG = "</group>";
    private static final String GROUP_NAME_OPEN_TAG = "<group name=\"";
    private static final String RECORD_OPEN_TAG = "<record ";
    private static final String PDF_EXT = ".pdf";
    private static final String PREVIEW_SERVLET = "PreviewServlet/";
    private static final String PDF = "pdf";
    private static final String LOCAL_DIR = "/local";

    private Creator creator;

    LocalCreator(Creator creator) {
        this.creator = creator;
    }

    void createLocalABC(List<IRecordVO> list, String filePath, boolean showProductSubtitle, String listType)
        throws SpecRendCreatorException {

        StringBuilder data = getLocalABCList(list);
        if (showProductSubtitle) {
            creator.articleListTransformer.setParameter(SHOW_PRODUCT_SUBTITLE, "yes");
        } else {
            creator.articleListTransformer.setParameter(SHOW_PRODUCT_SUBTITLE, "no");
        }
        creator.articleListTransformer.setParameter("listType", listType);
        creator.createArticleListOutput(data, filePath, creator.articleListTransformer);
    }

    private StringBuilder getLocalABCList(List<IRecordVO> list) {
        StringBuilder sb = new StringBuilder();
        sb.append(XML_HEADER);
        sb.append(DTD_STRING);
        sb.append(getLocalRecordListXML(list));
        return sb;
    }

    String getLocalRecordListXML(List<IRecordVO> recordList) {
        StringBuilder sb = new StringBuilder();
        sb.append(RECORD_LIST_OPEN_TAG);
        for (IRecordVO record : recordList) {
            try {
                String recordTag = "";

                if (creator.getDbVO().getTitle().equals("clsysrev")
                        || creator.getDbVO().getTitle().equals("clmethrev")) {
                    String htmlPath = FilePathCreator
                            .getRenderedDirPathEntire(record.getRecordPath(), RenderingPlan.HTML)
                            + "/" + record.getName() + "/"
                            + Creator.getDefaultFrameFileName(creator.getDbVO().getTitle());
                    htmlPath = getLocalRenderingPath(htmlPath, record.getName());

                    recordTag = buildRecordTag(htmlPath);

                    recordTag += addDoiAttribute(((CDSRVO) record).getDoi());

                    recordTag += addRecordPDFAttributes(record, "");
                    //recordTag += addRecordPDFAttributes(record, "_abstract");
                    //recordTag += addRecordPDFAttributes(record, "_standard");
                } else {
                    String recordName = record.getName();
                    String htmlPath = getLocalRenderingPath(creator.getHtmlLink(recordName, record.getRecordPath()),
                            recordName);
                    recordTag = buildRecordTag(htmlPath);
                }

                recordTag += creator.getStatusAndSubtitleParams(record)
                        + ">"
                        + record.getUnitTitle()
                        + RECORD_CLOSE_TAG;
                sb.append(recordTag);
            } catch (Exception e) {
                Creator.LOG.debug(e, e);
            }
        }
        sb.append(RECORD_LIST_CLOSE_TAG);
        return sb.toString();
    }

    private String buildRecordTag(String htmlPath) {
        String recordTag;
        recordTag = RECORD_OPEN_TAG
                + "html_path=\""
                + XmlUtils.escapeElementEntities(htmlPath)
                + "\" ";
        return recordTag;
    }

    private String addRecordPDFAttributes(IRecordVO record, String pdfVersion) throws Exception {

        String pdfPath = FilePathCreator
                .getRenderedDirPathEntire(record.getRecordPath(), RenderingPlan.PDF_TEX)
                + "/" + record.getName() + "/" + record.getName() + PDF_EXT;
        pdfPath = pdfPath.replace(PDF_EXT, pdfVersion + PDF_EXT);
        pdfPath = getLocalRenderingPath(pdfPath, record.getName());

        return PDF + pdfVersion + "_path=\""
                + XmlUtils.escapeElementEntities(pdfPath)
                + "\" "
                + PDF + pdfVersion + "_size=\"" + creator.getPdfSize(record, pdfVersion) + "\" ";
    }

    String getLocalRecordByGroupList(Map<IGroupVO, List<CDSRVO>> data) {
        StringBuilder sb = new StringBuilder();

        sb.append(GROUP_VRECORD_LIST_OPEN_TAG);
        for (IGroupVO group : data.keySet()) {
            sb.append(getLocalGroupRecords(group, data.get(group)));
        }
        sb.append(GROUP_VRECORD_LIST_CLOSE_TAG);

        return sb.toString();
    }

    String getAboutRecordByGroupList(Map<ProductSubtitleVO, List<RecordVO>> data, String filePath) {
        StringBuilder sb = new StringBuilder();

        sb.append(GROUP_VRECORD_LIST_OPEN_TAG);

        for (ProductSubtitleVO group : data.keySet()) {
            sb.append(GROUP_NAME_OPEN_TAG);
            sb.append(group.getName());
            sb.append("\">");

            if (group.getId() == ProductSubtitleEntity.ProductSubtitle.CRGS) {
//                String cdsrLink = SpecRenderingStorageFactory.getInstance()
//                        .getSpecRndFilePathLocalByDbIdAndFileName(
//                                creator.getSysrevDbId(), "crglist.html");                
//                //String cdsrLink = filePath.substring(0, filePath.lastIndexOf("/") + 1) + "crglist.html";
//                
//                cdsrLink = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.WEB_LOADING_URL)
//                    + "PreviewServlet/" + cdsrLink;
//                sb.append("<cdsrLink href=\"" + cdsrLink + "\" />");

                //sb.append("<cdsrLink href=\"/cochrane/clabout/titles/crglist.html\"/>");
                sb.append(
                    "<cdsrLink href=\"http://onlinelibrary.wiley.com/book/10.1002/14651858/homepage/crglist.html\"/>");
            }

            List<RecordVO> recordList = data.get(group);

            for (RecordVO record : recordList) {
                try {
                    String recName = record.getName();
                    String htmlPath = getLocalRenderingPath(creator.getHtmlLink(recName, record.getRecordPath()),
                        recName);
                    String recordTag = buildRecordTag(htmlPath)
                            + creator.getStatusAndSubtitleParams(record)
                            + ">"
                            + record.getUnitTitle()
                            + RECORD_CLOSE_TAG;
                    sb.append(recordTag);
                } catch (Exception e) {
                    Creator.LOG.debug(e, e);
                }
            }
            sb.append(GROUP_CLOSE_TAG);
        }
        sb.append(GROUP_VRECORD_LIST_CLOSE_TAG);

        return sb.toString();
    }

    String getLocalRenderingPath(String htmlLink, String recordName) {
        String link = CochraneCMSPropertyNames.getWebLoadingUrl() + PREVIEW_SERVLET + htmlLink;
//        int recordId = record.getId();
//        if(record instanceof CDSRVO)
//        {
//            CDSRVO cdsrVO = (CDSRVO) record;
//            recordId = cdsrVO.getRecordId();
//        }
//        int htmlRenderingId = Creator.getRenderingResultStorage().
//                getByRecordAndPlanDescription(recordId, renderingType).getId();

        return CochraneCMSProperties.getProperty("cochrane.cms.web.url")
                + "preview.jsp?link=" + link + "&recordName=" + recordName;
//                + "&record_id=" + recordId;
//                + "&rendering_id=" + htmlRenderingId;
    }

    void createLocalByGroup(Map<ProductSubtitleVO, List<RecordVO>> data, String filePath)
        throws SpecRendCreatorException {

        StringBuilder result = getAboutByGroupXml(data, filePath);
        creator.createArticleListOutput(result, filePath, creator.articleAboutListByGroupTransformer);
    }

    void createCDSRLocalByGroup(Map<IGroupVO, List<CDSRVO>> data, String filePath)
        throws SpecRendCreatorException {

        StringBuilder result = getLocalByGroupXml(data);
        creator.createGroupOutput(result, filePath, creator.articleCDSRListByGroupTransformer, "");
    }

    StringBuilder getAboutByGroupXml(Map<ProductSubtitleVO, List<RecordVO>> data, String filePath) {
        StringBuilder sb = new StringBuilder();
        sb.append(XML_HEADER);
        sb.append(DTD_STRING);
        sb.append(getAboutRecordByGroupList(data, filePath));
        return sb;
    }

    StringBuilder getLocalByGroupXml(Map<IGroupVO, List<CDSRVO>> data) {
        String recordList = getLocalRecordByGroupList(data);

        return creator.getGroupXml(data, recordList);
    }

    String createLinksMenu(Map<String, String> abcMenu) {
        String path = CochraneCMSPropertyNames.getWebLoadingUrl() + PREVIEW_SERVLET + creator.getFolder() + LOCAL_DIR;

        return creator.createLinksMenu(path, abcMenu);
    }


    String createLocalAddOnFile(String fileData, Map<String, String> abcMenu) {
        String localFileData = fileData.replaceAll("%path%",
                PREVIEW_SERVLET + creator.getFolder() + LOCAL_DIR);
        String localCompiledMenu = createLinksMenu(abcMenu);
        localFileData = localFileData.replaceAll("%links%", localCompiledMenu);
        return localFileData;
    }

    void createCDSRLocalGroup(IGroupVO group, String filePath, Map<IGroupVO, List<CDSRVO>> data)
        throws SpecRendCreatorException {

        StringBuilder result = new StringBuilder();
        result.append(DTD_STRING);
        result.append(GROUP_VRECORD_LIST_OPEN_TAG);

        for (IGroupVO curGroup : data.keySet()) {

            List<CDSRVO> recordList = data.get(curGroup);
            recordList = curGroup.getName().equals(group.getName()) ? recordList : null;
            result.append(getLocalGroupRecords(curGroup, recordList));
        }

        result.append(GROUP_VRECORD_LIST_CLOSE_TAG);

        creator.createGroupOutput(result, filePath, creator.articleCDSRListByGroupTransformer, group.getName());
    }

    private StringBuilder getLocalGroupRecords(IGroupVO group, List<CDSRVO> recordList) {
        StringBuilder sb = new StringBuilder();
        String groupHtmlPath = "cochrane/" + creator.getDbVO().getIssueId()
                + "/clabout/rendered-html_diamond/" + group.getName() + "/"
                + Creator.getDefaultFrameFileName(creator.getDbVO().getTitle());
        groupHtmlPath = getLocalRenderingPath(groupHtmlPath, group.getName());

        sb.append(GROUP_NAME_OPEN_TAG);
        sb.append(group.getName());
        sb.append("\" unitTitle=\"");
        sb.append(group.getUnitTitle());
        sb.append("\" html_path=\"");
        sb.append(XmlUtils.escapeElementEntities(groupHtmlPath));
        sb.append("\">");
        if (recordList != null && recordList.size() > 0) {
            sb.append(RECORD_LIST_OPEN_TAG);
            for (CDSRVO record : recordList) {
                String htmlPath =
                        FilePathCreator.getRenderedDirPathEntire(record.getRecordPath(), RenderingPlan.HTML)
                                + "/" + record.getName() + "/"
                                + Creator.getDefaultFrameFileName(creator.getDbVO().getTitle());
                htmlPath = getLocalRenderingPath(htmlPath, record.getName());
                try {
                    String recordTag = buildRecordTag(htmlPath)
                            + addDoiAttribute(record.getDoi())
                            + addRecordPDFAttributes(record, "")
                            + creator.getStatusAndSubtitleParams(record)
                            + " >"
                            + record.getUnitTitle()
                            + RECORD_CLOSE_TAG;
                    sb.append(recordTag);
                } catch (Exception ex) {
                    Creator.LOG.debug(ex, ex);
                }

            }
            sb.append(RECORD_LIST_CLOSE_TAG);
        }
        sb.append(GROUP_CLOSE_TAG);

        return sb;
    }

    private String addDoiAttribute(String doiAttribute) {
        return " doi=\"" + doiAttribute + "\" ";
    }

}
