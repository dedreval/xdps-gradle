package com.wiley.cms.cochrane.cmanager.specrender.process;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.CDSRVO;
import com.wiley.cms.cochrane.cmanager.data.record.CDSRVO4Entire;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.IGroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordVO;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingVO;
import com.wiley.cms.cochrane.cmanager.specrender.exception.SpecRendCreatorException;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class CDSRCreator implements ICreator {
    private static final Logger LOG = Logger.getLogger(CDSRCreator.class);

    private static final String DTD_STRING =
            "<!DOCTYPE component SYSTEM \"" + CochraneCMSProperties.getProperty("cms.resources.dtd.wileyml21") + "\">";

    private static final String PUBLISH_DIR = "/publish/";
    private static final String LOCAL_DIR = "/local/";
    private static final String TOPICS_XML = "topics.xml";
    private static final String HTML_EXT = ".html";

    private Creator creator;
    private int currentIssue;
    private int prevIssue;

    CDSRCreator(Creator creator) {
        this.creator = creator;
    }

    public void run(SpecRenderingVO vo) throws SpecRendCreatorException {

        IssueVO issue = creator.getDbVO().getIssue();
        currentIssue = CmsUtils.getIssueNumber(issue.getYear(), issue.getNumber());
        prevIssue = creator.isClosed() ? 0 : CmsUtils.getPreviousIssueNumber(issue.getYear(), issue.getNumber());

        creator.showProductSubtitle = false;

        createSpecFiles(vo);

        createTopicsXml(vo);
    }

    public List<IRecordVO> getDbRecordVOByFirstCharList(int dbId, char ch, int productSubtitle) {
        List<CDSRVO> voList = Creator.getRecordResultStorage()
                .getCDSREntityByFirstCharList4Entire(ch, productSubtitle);
        return new ArrayList<IRecordVO>(voList);
    }

    public List<IRecordVO> getDbRecordVOByNumStartedList(int dbId,
                                                         int productSubtitle) {
        List<CDSRVO> voList = Creator.getRecordResultStorage()
                .getCDSRVOByNumStartedList4Entire(productSubtitle);
        return new ArrayList<IRecordVO>(voList);
    }

    void createSpecFiles(SpecRenderingVO vo) throws SpecRendCreatorException {
        creator.showProductSubtitle = true;

        List<CDSRVO4Entire> voList = Creator.getRecordResultStorage().getRecordsByUnitStatusesFromEntire(
            null, currentIssue, prevIssue);

        Set<Integer> newStatus = new HashSet<Integer>();

        newStatus.add(UnitStatusEntity.UnitStatus.NEW); //1
        newStatus.add(UnitStatusEntity.UnitStatus.NEW1); //7
        newStatus.add(UnitStatusEntity.UnitStatus.NEW_COMMENTED); //17

        createReviewFiles(vo, "new-sysrev.html", newStatus, "new", voList);

        Set<Integer> updateStatus = new HashSet<Integer>();
        updateStatus.add(UnitStatusEntity.UnitStatus.UPDATED); // 3
        updateStatus.add(UnitStatusEntity.UnitStatus.MAJOR_CHANGE); // 9
        updateStatus.add(UnitStatusEntity.UnitStatus.EDITED_CON); // 10
        updateStatus.add(UnitStatusEntity.UnitStatus.UPDATED_NOT_CON); // 11
        updateStatus.add(UnitStatusEntity.UnitStatus.UPDATED_CON); // 12
        updateStatus.add(UnitStatusEntity.UnitStatus.MAJOR_CHANGE_COMMENTED); // 19
        updateStatus.add(UnitStatusEntity.UnitStatus.EDITED_CON_COMMENTED); // 20
        updateStatus.add(UnitStatusEntity.UnitStatus.UPDATED_NOT_CON_COMMENTED); // 21
        updateStatus.add(UnitStatusEntity.UnitStatus.UPDATED_CON_COMMENTED); // 22

        createReviewFiles(vo, "updated-sysrev.html", updateStatus, "updated", voList);
        creator.showProductSubtitle = false;

        createByReviewGroupFileList(vo, "crglist.html");
    }

    private int createReviewFiles(SpecRenderingVO vo, String fileName, Set<Integer> unitStatuses, String listType,
                                  List<CDSRVO4Entire> voList) throws SpecRendCreatorException {

        List<IRecordVO> list = new ArrayList<IRecordVO>();
        Map<String, CDSRVO4Entire> names = new HashMap<String, CDSRVO4Entire>();

        Iterator<CDSRVO4Entire> it = voList.iterator();
        while (it.hasNext()) {

            CDSRVO4Entire rec = it.next();
            if (!unitStatuses.contains(rec.getUnitStatusId())) {
                continue;
            }

            String name = rec.getRecordName();
            int year = rec.getYear();
            int number = rec.getNumber();

            if (!creator.isForbidden(name, year, number)) {

                CDSRVO4Entire dup = names.get(name);
                if (dup != null && ((dup.getYear() == year && dup.getNumber() > number) || dup.getYear() > year)) {
                    continue;
                }

                if (dup != null) {
                    LOG.warn(String.format("***** %s year=%d, n=%d, year2=%d, n2=%d", name, year, number,
                            dup.getYear(), dup.getNumber()));
                }

                if (creator.isOpenAccess(name, year, number)) {
                    rec.setOpenAccess(true);
                }

                names.put(name, rec);
                list.add(rec);
            }
            it.remove();
        }

        creator.handleRecordList(list, fileName, vo, listType);
        return list.size();
    }

//    private void createByReviewGroupFileList(SpecRenderingVO vo, String fileName) throws SpecRendCreatorException
//    {
//        int groupDbId = creator.getGroupDbId();
//        int dbSize = 0;
//        List<RecordVO> groupList = Creator.getRecordResultStorage().getDbRecordVOList(groupDbId);
//        HashMap<RecordVO, List<CDSRVO>> data = new LinkedHashMap<RecordVO, List<CDSRVO>>();
//
//        for(RecordVO group : groupList)
//        {
//            List<CDSRVO> recordList = Creator.getRecordResultStorage().getDBCDSRVOListByGroup(creator.dbId, group);
//            if(recordList!=null && recordList.size()>0)
//            {
//                data.put(group, recordList);
//                dbSize += recordList.size();
//            }
//        }


//        String filePathPublish = creator.getFolder() + "/publish/" + fileName;
//        String filePathLocal = creator.getFolder() + "/local/" + fileName;
//
//        creator.publishCreator.createCDSRPublishByGroup(data, filePathPublish);
//        creator.localCreator.createCDSRLocalByGroup(data, filePathLocal);
//
//        creator.addFileVO2SpecRenderingVO(filePathPublish, filePathLocal, dbSize, vo);
//    }

    void createNewTitleFs(SpecRenderingVO vo) {
        String fileName = "cochrane_clsysrev_new_fs.html";
        creator.createFile(fileName, fileName, vo);
    }

    void createNewTitleBar(SpecRenderingVO vo) {
        String fileName = "cochrane_clsysrev_newtitlebar.html";
        creator.createFile(fileName, fileName, vo);
    }

    void createUpdateTitleFs(SpecRenderingVO vo) {
        String fileName = "cochrane_clsysrev_update_fs.html";
        creator.createFile(fileName, fileName, vo);
    }

    void creatUpdateTitleBar(SpecRenderingVO vo) {
        String fileName = "cochrane_clsysrev_updatetitlebar.html";
        creator.createFile(fileName, fileName, vo);
    }

    void createAlphaBar(SpecRenderingVO vo, String fileName,
                        Map<String, String> abcMenu) {
        String image = CochraneCMSProperties
                .getProperty("cms.cochrane.specrnd."
                        + creator.getDbVO().getTitle() + ".image");

        // String dbInclusionTemplate = image
        // +
        // "<nobr><a href=\"%cochrane%cochrane_clsysrev_alpha_protocols_fs.html\" target=\"_top\" "
        // + "alt=\"Protocols\">Protocol</a>&nbsp;&nbsp;"
        // +
        // "<a href=\"%cochrane%cochrane_clsysrev_alpha_reviews_fs.html\" target=\"_top\" alt=\"Reviews\">"
        // + "Review</a>&nbsp;&nbsp;&nbsp;"
        // +
        // "<a href=\"%cochrane%cochrane_clsysrev_alpha_fs.html\" target=\"_top\" "
        // + "alt=\"Protocols and Reviews\">Protocols and Reviews</a></nobr>";

        Map<String, String> map = new HashMap<String, String>();
        map.put("cochrane", "");
        String dbInclusionTemplate =
                image + CochraneCMSProperties.getProperty("spec_rendering.cdsr.createAlphaBar", map);

        creator.createAlphaBar(dbInclusionTemplate, fileName, vo, abcMenu);
    }

    private void createTopicsXml(SpecRenderingVO vo) throws SpecRendCreatorException {
        StringBuilder xml = new StringBuilder(mergeInputTopicsXmls());

        //output file
        String filePathPublish = creator.getFolder() + PUBLISH_DIR + TOPICS_XML;
        String filePathLocal = creator.getFolder() + LOCAL_DIR + TOPICS_XML;

        creator.createArticleListOutput(xml, filePathPublish, creator.topicsTransformer);
        creator.createArticleListOutput(xml, filePathLocal, creator.topicsTransformer);

        creator.addFileVO2SpecRenderingVO(filePathPublish, filePathLocal, 0, vo);
    }

    /* Takes content of topics.xml files and merge it under <ROOT>. */
    private String mergeInputTopicsXmls() throws SpecRendCreatorException {
        IRepository rps = creator.getRepository();
        String pathToInput = CochraneCMSProperties
                .getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                + "/" + creator.getDbVO().getIssueId()
                + "/" + creator.getDbVO().getTitle() + "/input";

        StringBuilder sb;
        try {
            if (!rps.isFileExists(pathToInput)) {
                throw new SpecRendCreatorException(
                        "Cannot find \"input\" directory with topics.xml in "
                                + pathToInput);
            }

            File[] dirs = rps.getFilesFromDir(pathToInput);

            sb = new StringBuilder();
            sb.append(DTD_STRING);
            sb.append("<ROOT>");

            for (File dir : dirs) {
                if (dir.isDirectory()) {
                    sb.append(readTopicsXml(rps, pathToInput + "/" + dir.getName()));
                }
            }
            sb.append("</ROOT>");
        } catch (IOException e) {
            throw new SpecRendCreatorException(e);
        }

        return sb.toString();
    }

    private String readTopicsXml(IRepository rps, String uri) throws SpecRendCreatorException {
        String xml = "";
        String topicsXmlUri = uri + "/topics.xml";
        try {
            if (rps.isFileExists(topicsXmlUri)) {
                try {
                    xml = InputUtils.readStreamToString(rps.getFile(topicsXmlUri));
                } catch (IOException e) {
                    throw new SpecRendCreatorException("Cannot read " + topicsXmlUri);
                }

                // remove XML declaration
                Pattern p = Pattern.compile("<\\?xml(.)+\\?>");
                Matcher m = p.matcher(xml);

                if (m.find()) {
                    int ind = m.end();
                    xml = xml.substring(ind);
                }
            }
        } catch (IOException e) {
            throw new SpecRendCreatorException(e);
        }

        return xml;
    }

    private void createByReviewGroupFileList(SpecRenderingVO vo, String fileName) throws SpecRendCreatorException {
        IRecordCache cache;
        try {
            cache = CochraneCMSPropertyNames.lookupRecordCache();
        } catch (Exception e) {
            throw new SpecRendCreatorException(e.getMessage());
        }

        Iterator<String> groupCodes = cache.getCRGGroupCodes();
        Map<String, IGroupVO> groupMap = new HashMap<>();
        Map<IGroupVO, List<CDSRVO>> data = new LinkedHashMap<>();
        while (groupCodes.hasNext()) {
            String sid = groupCodes.next();
            GroupVO gvo = cache.getCRGGroup(sid);
            groupMap.put(sid, gvo);
            data.put(gvo, null);
        }

        List<CDSRVO> fullList = Creator.getRecordResultStorage().getCDSRVOListByGroup4Entire(null);
        LOG.debug("full recordList size=" + fullList.size());
        int dbSize = fullList.size();

        for (CDSRVO recVo: fullList) {

            String groupSid = recVo.getGroupSid();
            IGroupVO group = groupMap.get(groupSid);
            if (group == null) {
                LOG.error(String.format("cannot find a group %s for %s", groupSid, recVo.getDoi()));
                dbSize--;
                continue;
            }

            List<CDSRVO> list = data.get(group);
            if (list == null) {
                list = new ArrayList<CDSRVO>();
                data.put(group, list);
            }
            list.add(recVo);
        }


        String filePathPublish = creator.getFolder() + PUBLISH_DIR + fileName;
        String filePathLocal = creator.getFolder() + LOCAL_DIR + fileName;

        creator.publishCreator.createCDSRPublishByGroup(data, filePathPublish);
        creator.localCreator.createCDSRLocalByGroup(data, filePathLocal);

        creator.addFileVO2SpecRenderingVO(filePathPublish, filePathLocal, dbSize, vo);

        for (IGroupVO group : data.keySet()) {

            List<CDSRVO> recordList = data.get(group);
            if (recordList == null) {
                continue;
            }
            String groupPublishFilePath = creator.getFolder() + "/publish/crg_" + group.getName() + HTML_EXT;
            creator.publishCreator.createCDSRPublishGroup(group, groupPublishFilePath, data);

            String groupLocalFilePath = creator.getFolder() + "/local/crg_" + group.getName() + HTML_EXT;
            creator.localCreator.createCDSRLocalGroup(group, groupLocalFilePath, data);

            creator.addFileVO2SpecRenderingVO(groupPublishFilePath, groupLocalFilePath, recordList.size(), vo);
        }
    }
}