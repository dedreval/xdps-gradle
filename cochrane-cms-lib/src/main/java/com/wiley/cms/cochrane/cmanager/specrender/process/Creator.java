package com.wiley.cms.cochrane.cmanager.specrender.process;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.wiley.cms.cochrane.cmanager.data.record.RecordManifest;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.utils.Constants;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.db.DbVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.CDSRVO;
import com.wiley.cms.cochrane.cmanager.data.record.CDSRVO4Entire;
import com.wiley.cms.cochrane.cmanager.data.record.IGroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordVO;

import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.rendering.IRenderingStorage;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.specrender.data.ISpecRenderingStorage;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingFileVO;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingStorageFactory;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingVO;
import com.wiley.cms.cochrane.cmanager.specrender.exception.SpecRendCreatorException;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.zip.IZipOutput;
import com.wiley.cms.cochrane.utils.zip.ZipOutput;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.XmlUtils;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class Creator implements Runnable {
    static final Logger LOG = Logger.getLogger(Creator.class);
    static final String DEFAULT_PDF_FRAME_FILE_NAME = "pdf_fs.html";

    private static final String DTD_STRING =
            "<!DOCTYPE component SYSTEM \"" + CochraneCMSProperties.getProperty("cms.resources.dtd.wileyml21") + "\">";

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String NUM_FILE_NAME = "num.html";
    private static final String NUM_MENU_NAME = "0-9";
    private static final String DEFAULT_FILE_EXTENSION = "html";
    private static final long KILO = 1024;

    private static final String POSTFIX = "%postfix%";
    private static final String COCHRANE_PATH = "/cochrane/";
    private static final String COCHRANE_PREFIX = "cochrane_";
    private static final String PUBLISH_PATH = "/publish/";
    private static final String LOCAL_PATH = "/local/";

    private static final String DB_INCLUSION = "%db_inclusion%";
    private static final String LINKS = "%links%";
    private static final String INTERSCIENCE = "%interscience%";
    private static final String COCHRANE = "%cochrane%";
    private static final String DB_TITLE = "%db_title%";
    private static final String TITLES_PATH = "%titles_path%";
    private static final String FILE_NAME = "%file_name%";
    private static final String CH = "%ch%";
    private static final String PATH = "%path%";
    private static final String SPEC_RENDERING_FILE = "Spec rendering file ";
    private static final String PDF_EXT = ".pdf";
    private static final String CREATED_SUCCESSFULLY = " created successfully";

    protected Map<Integer, Set<String>> openAccessNames = new HashMap<Integer, Set<String>>();

    boolean showProductSubtitle = false;

    int dbId;

    Transformer articleListTransformer;
    Transformer articleCDSRListByGroupTransformer;
    Transformer articleAboutListByGroupTransformer;
    Transformer topicsTransformer;
    Transformer headerTransformer;
    Transformer titlesTransformer;

    LocalCreator localCreator;
    PublishCreator publishCreator;

    final String assetsPath;
    final String replacePath;
    final String replacePrefix;

    private boolean createZip;
    private IZipOutput output;

    private ICreator concreteCreator;

    private DbVO dbVO;

    private Date date = new Date();

    private String folder;

    public Creator(int dbId) throws SpecRendCreatorException {

        //System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
        this.dbId = dbId;
        this.dbVO = getDbVO();

        assetsPath = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.COCHRANE_RESOURCES)
            + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SPECRND_ASSETS_PATH_PREFIX
                + dbVO.getTitle());

        replacePath = COCHRANE_PATH + dbVO.getTitle();
        replacePrefix = COCHRANE_PREFIX + dbVO.getTitle() + "_";
        createZip = false;

        try {
            initALTransformer();
            LOG.debug("Spec rendering creator initialized successfully");
        } catch (Exception e) {
            LOG.debug("Spec rendering creator initialization failed");
            throw new SpecRendCreatorException(e);
        }

        if (createZip) {
            try {
                output = new ZipOutput(getZipFilePath());
            } catch (FileNotFoundException e) {
                throw new SpecRendCreatorException(e);
            }
        }

        localCreator = new LocalCreator(this);
        publishCreator = new PublishCreator(this);
    }

    protected boolean isForbidden(String recordName, int year, int number) {
        return false;
    }

    protected boolean isOpenAccess(String recordName, int year, int number) {


        int issueNumber = CmsUtils.getIssueNumber(year, number);

        if (!openAccessNames.containsKey(issueNumber)) {

            IResultsStorage rs = Creator.getResultStorage();
            Set<String> set = new HashSet<String>();
            setOpenAccessNames(issueNumber, set, rs);
            openAccessNames.put(issueNumber, set);
        }

        return openAccessNames.get(issueNumber).contains(recordName);
    }

    protected boolean isClosed() {
        return true;
    }

    private void setOpenAccessNames(int issueNumber, Set<String> set, IResultsStorage rs) {
        List<String> list = rs.getOpenAccessCDSRNames(issueNumber);
        for (String name: list) {
            set.add(name);
        }
    }

    private String getZipFilePath() {
        return getFolder() + "/" + date.getTime() + ".zip";
    }

    private void initALTransformer() throws URISyntaxException, TransformerConfigurationException, IOException {

        TransformerFactory factory = null; // TransformerFactory.newInstance();
        try {
            factory = (TransformerFactory) Class.forName("net.sf.saxon.TransformerFactoryImpl").newInstance();
            factory.setURIResolver(new MyResolver());
        } catch (Exception e) {
            LOG.error(e, e);
            throw new TransformerConfigurationException(e.getMessage());
        }

        URL articleListXsl = new URI(assetsPath
                + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SPECRND_ARTICLE_LIST_XSL)).toURL();

        articleListTransformer = factory.newTransformer(
                new StreamSource(articleListXsl.openStream())
        );

        URL articleCDSRListByGroupXsl = new URI(assetsPath
                + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SPECRND_ARTICLE_CDSR_LIST_BY_GROUP_XSL))
                .toURL();


        articleCDSRListByGroupTransformer = factory.newTransformer(
                new StreamSource(articleCDSRListByGroupXsl.openStream())
        );

        URL articleAboutListByGroupXsl = new URI(assetsPath
                + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SPECRND_ARTICLE_ABOUT_LIST_BY_GROUP_XSL))
                .toURL();

        articleAboutListByGroupTransformer = factory.newTransformer(
                new StreamSource(articleAboutListByGroupXsl.openStream())
        );

        /*  new transformer for topics.xml generation  */
        URL topicsXsl = new URI(assetsPath
                + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SPECRND_EMRW_TRANSFORM_XSL)).toURL();

        topicsTransformer = factory.newTransformer(new StreamSource(topicsXsl
                .openStream()));

        /*   new transformers for titles.xml generation   */
        URL makeHeader = new URI(assetsPath
                + "make-header.xsl").toURL();

        headerTransformer = factory.newTransformer(new StreamSource(makeHeader
                .openStream()));

        URL makeTitles = new URI(assetsPath
                + "make-titles.xsl").toURL();

        titlesTransformer = factory.newTransformer(new StreamSource(makeTitles
                .openStream()));
    }

    String getFolder() {
        if (folder == null) {
            setFolder(generateFolderPath());
        }
        return folder;
    }

    private void setFolder(String folder) {
        this.folder = folder;
    }

    private String generateFolderPath() {
        DbVO db = getDbVO();
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                + "/" + db.getIssueId()
                + "/" + db.getTitle()
                + "/spec_rendering"
                + "/" + date.getTime();
    }

    DbVO getDbVO() {
        if (dbVO == null) {
            dbVO = DbStorageFactory.getFactory().getInstance().getDbVO(dbId);
        }
        return dbVO;
    }

    public void createSpecRendering() {

        LOG.debug("Creation of spec rendering files started...");

        SpecRenderingVO vo = new SpecRenderingVO();
        vo.setDate(date);
        vo.setDbId(dbId);
        vo.setCompleted(false);
        vo.setSuccessful(false);

        int voId = getSpecRenderingResultStorage().create(vo);
        vo.setId(voId);

        try {
            getConcreteCreator().run(vo);
            vo.setSuccessful(true);
        } catch (SpecRendCreatorException e) {
            LOG.error(e, e);
        }
        vo.setCompleted(true);
        getSpecRenderingResultStorage().mergeVO(vo);

        tryCloseCreatedZip();

        LOG.debug("Creation of spec rendering files finished successfully");
    }

    public void run() {
        createSpecRendering();
    }

    private ICreator getConcreteCreator() throws SpecRendCreatorException {

        if (concreteCreator == null) {
            if (dbVO.getTitle().equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV))
                    || dbVO.getTitle().equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLMETHREV))) {
                this.concreteCreator = new CDSRCreator(this);
            } else if (dbVO.getTitle().equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLEED))) {
                this.concreteCreator = new EEDCreator(this);
            } else if (dbVO.getTitle().equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLABOUT))) {
                this.concreteCreator = new AboutCreator(this);
            } else {
                this.concreteCreator = new DefaultCreator(this);
            }
        }
        return concreteCreator;
    }

    void createAlphaFS(SpecRenderingVO vo, Map<String, String> abcMenu) {
        createAlphaFS(vo, abcMenu, "");
    }

    void createAlphaFS(SpecRenderingVO vo, Map<String, String> abcMenu, String postfix) {
        String firstPage = "_BLANK.html";

        if (abcMenu.size() > 0) {
            Iterator it = abcMenu.values().iterator();
            firstPage = (String) it.next();
            firstPage = (String) it.next();
        } else {
            createFile(firstPage, firstPage, vo);
        }

        String fileName = COCHRANE_PREFIX + dbVO.getTitle() + "_alpha" + postfix + "_fs.html";
        String fileTemplate = "cochrane_alpha_fs.html";
        HashMap<String, String> replace = new HashMap<String, String>();
        replace.put(POSTFIX, postfix);
        replace.put("%firstPage%", firstPage);
        createFile(fileName, fileTemplate, replace, replace, vo);
    }

    private void tryCloseCreatedZip() {
        if (createZip) {
            try {
                output.close();
            } catch (IOException e) {
                LOG.error(e, e);
            }
        }
    }

    //int getGroupDbId() {
    //    return getResultStorage().findDb(dbVO.getIssueId(),
    //            CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLABOUT));
    //}

    //int getSysrevDbId() {
    //    return getResultStorage().findDb(dbVO.getIssueId(),
    //            CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV));
    //}

    Map<String, String> createFiles(SpecRenderingVO vo) throws SpecRendCreatorException {
        return createFiles(vo, "", 0);
    }

    Map<String, String> createFiles(SpecRenderingVO vo, String filePath, int productSubtitle)
        throws SpecRendCreatorException {

        Map<String, String> abcMenu = new TreeMap<String, String>();

        createAlphabetFiles(vo, abcMenu, filePath, productSubtitle);
        createNumFile(vo, abcMenu, filePath, productSubtitle);

        return abcMenu;
    }

    private void createAlphabetFiles(SpecRenderingVO vo, Map<String, String> abcMenu,
                                     String filePath, int productSubtitle)
        throws SpecRendCreatorException {

        for (char ch : ALPHABET.toCharArray()) {
            List<IRecordVO> list = this.concreteCreator.getDbRecordVOByFirstCharList(dbId, ch, productSubtitle);
//            if (list.size() > 0)
//            {
            String fileName = String.valueOf(ch) + "." + DEFAULT_FILE_EXTENSION;
            abcMenu.put(String.valueOf(ch), filePath + fileName);
            handleRecordList(list, filePath + fileName, vo, "");
//            }
        }
    }

    private void createNumFile(SpecRenderingVO vo, Map<String, String> abcMenu, final String filePath,
                               int productSubtitle)
        throws SpecRendCreatorException {

        List<IRecordVO> list = this.concreteCreator.getDbRecordVOByNumStartedList(dbId, productSubtitle);

//        if (list.size() > 0)
//        {
        abcMenu.put(NUM_MENU_NAME, filePath + NUM_FILE_NAME);
        handleRecordList(list, filePath + NUM_FILE_NAME, vo, "");
//        }
    }

    void handleRecordList(List<IRecordVO> list, String subFilePath, SpecRenderingVO vo, String listType)
        throws SpecRendCreatorException {

        String filePathPublish = getFolder() + PUBLISH_PATH + subFilePath;
        String filePathLocal = getFolder() + LOCAL_PATH + subFilePath;

        publishCreator.createPublishABC(list, filePathPublish, showProductSubtitle, listType);
        localCreator.createLocalABC(list, filePathLocal, showProductSubtitle, listType);

        SpecRenderingFileVO fileVO = new SpecRenderingFileVO();
        fileVO.setCompleted(true);
        fileVO.setDate(new Date());
        fileVO.setFilePathPublish(filePathPublish);
        fileVO.setFilePathLocal(filePathLocal);
        fileVO.setItemAmount(list.size());
        fileVO.setSpecRenderingId(vo.getId());
        fileVO.setSuccessful(true);

        vo.getFiles().add(fileVO);
    }

    void createArticleListOutput(StringBuilder data, String filePath, Transformer transformer)
        throws SpecRendCreatorException {

        String xml = data.toString(); //.replaceAll(">", "&gt;").replaceAll("<", "&lt;");

        ByteArrayOutputStream baosPublish = new ByteArrayOutputStream();

        try {
            transformer.transform(new StreamSource(new ByteArrayInputStream(xml.getBytes())),
                    new StreamResult(baosPublish)
            );
        } catch (TransformerException e) {
            throw new SpecRendCreatorException(e);
        }

        saveResultFile(filePath, baosPublish.toByteArray());
    }

    byte[] makeHeader(InputStream is, String currentIssue) throws SpecRendCreatorException {

        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            headerTransformer.setParameter("currentIssue", currentIssue);
            headerTransformer.transform(new StreamSource(is), new StreamResult(result));
            return result.toByteArray();
        } catch (TransformerException e) {
            throw new SpecRendCreatorException(e);
        }
    }

    void makeTitle(InputStream is, OutputStream os, String pdfSize) throws SpecRendCreatorException {

        try {
            titlesTransformer.setParameter("pdfSize", pdfSize);
            titlesTransformer.transform(new StreamSource(is), new StreamResult(os));
        } catch (TransformerException e) {
            throw new SpecRendCreatorException(e);
        }
    }

    String getStatusAndSubtitleParams(IRecordVO record) {
        String statusAndSubtitleParams = "";

        if (record instanceof CDSRVO4Entire) {
            statusAndSubtitleParams = getStatusAndSubtitleParams4Entire(record);
        } else if (record.getUnitStatus() != null) {
            statusAndSubtitleParams = getStatusParamValue(record);
        }

        if (record.getProductSubtitle() != null) {
            String updatedProductSubtitleName = record.getProductSubtitle().getName().toLowerCase()
                    .substring(0, record.getProductSubtitle().getName().length() - 1);
            statusAndSubtitleParams += "productSubtitleId=\"" + record.getProductSubtitle().getId() + "\" "
                    + "productSubtitleName=\"" + record.getProductSubtitle().getName() + "\" "
                    + "updatedProductSubtitleName=\"" + updatedProductSubtitleName + "\" ";
        }

        return statusAndSubtitleParams;
    }

    protected String getStatusAndSubtitleParams4Entire(IRecordVO record) {
        StringBuilder params = new StringBuilder();
        CDSRVO4Entire rec = (CDSRVO4Entire) record;
        //IssueVO issue = IssueStorageFactory.getInstance().getIssueVO(dbVO.getIssueId());
        IssueVO issue = dbVO.getIssue();

        if ((rec.getUnitStatus() != null) && (rec.isCurrent(issue.getNumber(), issue.getYear())
                || rec.getUnitStatus().isWithdrawn())) {
            params.append(getStatusParamValue(rec));
        }

        return params.append(getReviewType(rec)).append(getAccessType(rec)).toString();
    }

    protected String getReviewType(CDSRVO4Entire rec) {

        String rt = rec.getReviewType();
        String ret = "";
        if ("MR".equals(rt)) {
            ret = "isMethodology=\"true\" ";
        } else if ("DR".equals(rt)) {
            ret = "isDiagnostic=\"true\" ";
        } else if ("UR".equals(rt)) {
            ret = "isOverview=\"true\" ";
        }

        return ret;
    }

    protected String getAccessType(CDSRVO4Entire rec) {

        if (rec.getOpenAccess())  {
            return "isOpenAccess=\"true\" ";
        }
        return "";
    }

    protected String getStatusParamValue(IRecordVO record) {
        String params = "unitStatusId=\"" + record.getUnitStatus().getId() + "\" "
                + "unitStatusName=\"" + record.getUnitStatus().getName() + "\" ";

        if (record.getUnitStatus().getImages() != null
                && !record.getUnitStatus().getImages().equals("")) {
            params += "images=\"" + record.getUnitStatus().getImages() + "\" ";
        }
        return params;
    }

    StringBuilder getGroupXml(Map<IGroupVO, List<CDSRVO>> data, String recordList) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append(DTD_STRING);
        sb.append("<content>");
        sb.append("<groups>");

        for (IGroupVO group : data.keySet()) {
            sb.append("<group");
            sb.append(" name=\"");
            sb.append(XmlUtils.escapeElementEntities(group.getUnitTitle()));
            sb.append("\">");
            sb.append(group.getName());
            sb.append("</group>");
        }

        sb.append("</groups>");

        sb.append(recordList);

        sb.append("</content>");
        return sb;
    }

    private String getFileLink(String recName, String recPath, String groupName, String fileName) throws Exception {

        RecordManifest rm = new RecordManifest(dbVO.getIssueId(), dbVO.getTitle(), recName, recPath);
        List<String> uris = rm.getUris(groupName);
        for (String uri : uris) {
            if (uri.endsWith(fileName)) {
                return uri;
            }
        }
        throw new FileNotFoundException(String.format(
                "Record %s hasn't resource %s in group %s", recPath, fileName, groupName));
    }

    String getPdfSize(IRecordVO record) {
        return getPdfSize(record, "");
    }

    String getPdfSize(IRecordVO record, String type) {
        try {
            String rep = RepositoryFactory.getRepository().getRepositoryPlace();
            File dirPdf = new File(new URI(rep + "/" + FilePathCreator
                    .getRenderedDirPathEntire(record.getRecordPath(), RenderingPlan.PDF_TEX) + "/"
                    + record.getName()));
            long size = new File(dirPdf + "/" + record.getName() + type + PDF_EXT).length() / KILO;
            return Long.toString(size);
        } catch (URISyntaxException e) {
            return CochraneCMSPropertyNames.getNotAvailableMsg();
        }
    }

    void createAlphaBar(String dbInclusionTemplate, String fileName, SpecRenderingVO vo, Map<String, String> abcMenu) {
        String path = "PreviewServlet/" + getFolder() + LOCAL_PATH;

        HashMap<String, String> localReplace = new HashMap<String, String>();
        String dbLocalInclusion = dbInclusionTemplate.replaceAll(INTERSCIENCE,
                "http://www.mrw.interscience.wiley.com");
        dbLocalInclusion = dbLocalInclusion.replaceAll(COCHRANE, path);
        localReplace.put(DB_INCLUSION, dbLocalInclusion);

        if (abcMenu != null) {
            localReplace.put(LINKS, localCreator.createLinksMenu(abcMenu));
        } else {
            localReplace.put(LINKS, "");
        }

        HashMap<String, String> publishReplace = new HashMap<String, String>();
        String dbPublishInclusion = dbInclusionTemplate.replaceAll(INTERSCIENCE, "");
        dbPublishInclusion = dbPublishInclusion.replaceAll(COCHRANE, COCHRANE_PATH);
        publishReplace.put(DB_INCLUSION, dbPublishInclusion);

        if (abcMenu != null) {
            publishReplace.put(LINKS, publishCreator.createLinksMenu(abcMenu));
        } else {
            publishReplace.put(LINKS, "");
        }

        String fileTemplate = "cochrane_alphabar.html";

        createFile(fileName, fileTemplate, localReplace, publishReplace, vo);
    }

    void createFile(String fileName, String fileTemplate, SpecRenderingVO vo) {
        createFile(fileName, fileTemplate, null, null, vo);
    }

    void createFile(String fileName, String fileTemplate,
                    HashMap<String, String> localReplace, HashMap<String, String> publishReplace,
                    SpecRenderingVO vo) {
        try {
            URI uri = new URI(assetsPath + fileTemplate);

            String fileData = FileUtils.readStream(uri, "\n");

            String localFileData = fileData.replaceAll(DB_TITLE, dbVO.getTitle());
            localFileData = localFileData.replaceAll(TITLES_PATH, "");

            if (localReplace != null) {
                for (String key : localReplace.keySet()) {
                    localFileData = localFileData.replaceAll(key, localReplace.get(key));
                }
            }

            String publishFileData = fileData.replaceAll(DB_TITLE, dbVO.getTitle());
            publishFileData = publishFileData.replaceAll(TITLES_PATH, COCHRANE_PATH + dbVO.getTitle() + "/titles/");

            if (publishReplace != null) {
                for (String key : publishReplace.keySet()) {
                    publishFileData = publishFileData.replaceAll(key, publishReplace.get(key));
                }
            }

            saveFile(fileName, localFileData, publishFileData, vo);
        } catch (Exception e) {
            LOG.debug(e, e);
        }
    }

    void saveFile(String fileName, String localFileData, String publishFileData, SpecRenderingVO vo)
        throws IOException {

        ByteArrayInputStream localBais = new ByteArrayInputStream(localFileData.getBytes());
        String filePathLocal = getFolder() + LOCAL_PATH + fileName;
        Creator.getRepository().putFile(filePathLocal, localBais);

        LOG.debug(SPEC_RENDERING_FILE + filePathLocal + CREATED_SUCCESSFULLY);

        ByteArrayInputStream publishBAIS = new ByteArrayInputStream(publishFileData.getBytes());
        String filePathPublish = getFolder() + PUBLISH_PATH + fileName;
        Creator.getRepository().putFile(filePathPublish, publishBAIS);

        LOG.debug(SPEC_RENDERING_FILE + filePathPublish + CREATED_SUCCESSFULLY);

        addFileVO2SpecRenderingVO(filePathPublish, filePathLocal, 0, vo);
    }

    String createLinksMenu(String path, Map<String, String> abcMenu) {
        String linkTemplate = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SPECRND_ABC_LINK_TEMPLATE);
        linkTemplate = linkTemplate.replaceFirst(PATH, path);
        StringBuilder linksBuilder = new StringBuilder();

        for (char ch : ALPHABET.toCharArray()) {
            String chs = Character.toString(ch);

            if (abcMenu.containsKey(chs)) {
                String link = linkTemplate.replaceFirst(FILE_NAME, abcMenu.get(chs));
                link = link.replaceFirst(CH, chs) + "\n";
                linksBuilder.append(link);
            } else {
                String link = chs + " ";
                linksBuilder.append(link);
            }
        }
        if (abcMenu.containsKey(NUM_MENU_NAME)) {
            String link = linkTemplate.replaceFirst(FILE_NAME, abcMenu.get(NUM_MENU_NAME));
            link = link.replaceFirst(CH, NUM_MENU_NAME) + "\n";
            linksBuilder.append(link);
        } else {
            String link = NUM_MENU_NAME + " ";
            linksBuilder.append(link);
        }

        return linksBuilder.toString();
    }

    void addFileVO2SpecRenderingVO(String filePathPublish, String filePathLocal, int dbSize, SpecRenderingVO vo) {
        SpecRenderingFileVO fileVO = new SpecRenderingFileVO();
        fileVO.setCompleted(true);
        fileVO.setDate(new Date());
        fileVO.setFilePathPublish(filePathPublish);
        fileVO.setFilePathLocal(filePathLocal);
        fileVO.setItemAmount(dbSize);
        fileVO.setSpecRenderingId(vo.getId());
        fileVO.setSuccessful(true);

        vo.getFiles().add(fileVO);
    }

    String getHtmlLink(String recordName, String recordPath) throws Exception {
        return getFileLink(recordName, recordPath, Constants.RENDERED_HTML_DIAMOND,
                CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SPECRND_DEFAULT_FRAME_FILE_NAME_PREFIX
                        + dbVO.getTitle()));
    }

    String getPdfLink(String recordName, String recordPath) throws Exception {
        return getFileLink(recordName, recordPath, Constants.RENDERED_PDF_TEX, recordName + PDF_EXT);
    }

    static IRecordStorage getRecordResultStorage() {
        return RecordStorageFactory.getFactory().getInstance();
    }

    static IResultsStorage getResultStorage() {
        return ResultStorageFactory.getFactory().getInstance();
    }

    static IRenderingStorage getRenderingResultStorage() {
        return RenderingStorageFactory.getFactory().getInstance();
    }

    private static ISpecRenderingStorage getSpecRenderingResultStorage() {
        return SpecRenderingStorageFactory.getFactory().getInstance();
    }

    static IRepository getRepository() {
        return RepositoryFactory.getRepository();
    }

    /*
     *  Generates a subject hierarchy suitable for display of topics list.
     *  Takes as input merged topics.xml files from revmanxml.zip     *  
     */
    void createTopicsXml(String data, String path) throws SpecRendCreatorException {

        ByteArrayOutputStream baosPublish = new ByteArrayOutputStream();

        try {
            topicsTransformer.transform(new StreamSource(
                    new ByteArrayInputStream(data.getBytes())),
                    new StreamResult(baosPublish));

            getRepository().putFile(path, new ByteArrayInputStream(baosPublish.toByteArray()));
            // LOG.debug("Spec rendering file " + filePath + " created");
        } catch (TransformerException e) {
            throw new SpecRendCreatorException(e);
        } catch (IOException e) {
            throw new SpecRendCreatorException(e);
        }
    }

    void createGroupOutput(StringBuilder data, String filePath, Transformer transformer, String groupName)
        throws SpecRendCreatorException {

        String xml = data.toString();
        byte[] result = transform(transformer, xml, groupName);
        saveResultFile(filePath, result);
    }

    private byte[] transform(Transformer transformer, String xml, String groupName) throws SpecRendCreatorException {

        ByteArrayOutputStream baosPublish = new ByteArrayOutputStream();
        try {
            transformer.setParameter("groupName", groupName);
            transformer.transform(new StreamSource(new ByteArrayInputStream(xml.getBytes())),
                    new StreamResult(baosPublish)
            );
        } catch (TransformerException e) {
            throw new SpecRendCreatorException(e);
        }
        return baosPublish.toByteArray();
    }

    private void saveResultFile(String filePath, byte[] data) throws SpecRendCreatorException {

        try {
            getRepository().putFile(filePath, new ByteArrayInputStream(data));
            LOG.debug(SPEC_RENDERING_FILE + filePath + " created");
        } catch (IOException e) {
            throw new SpecRendCreatorException(e);
        }
    }

    public static String getDefaultFrameFileName(String dbName) {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SPECRND_DEFAULT_FRAME_FILE_NAME_PREFIX
                + dbName);
    }

    /*
    * This class is used to resolve URI for xsl-stylesheet.
    *
    * When <xsl:import> is used in xsl-stylesheet then custom
    * URIResolver must be added to TransformerFactory to locate
    * files, specified in the import statement.
    */
    class MyResolver implements URIResolver {
        public Source resolve(String href, String base) {
            File file;

            try {
                URI uri = new URI(assetsPath + href);
                file = new File(uri);

                if (file.exists()) {
                    return new StreamSource(file);
                }
            } catch (URISyntaxException e) {
                LOG.debug("Spec rendering creator initialization failed:"
                        + " cannot resolve xsl:import", e);
            }

            return null;
        }
    }

}