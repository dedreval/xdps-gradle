package com.wiley.cms.cochrane.meshtermmanager;

import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.meshterm.IMeshtermStorage;
import com.wiley.cms.cochrane.cmanager.data.meshterm.MeshtermRecord4CheckEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.entitywrapper.AbstractWrapper;
import com.wiley.cms.cochrane.process.BaseManager;

import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.process.handler.DbHandler;
import com.wiley.cms.cochrane.process.handler.Wml3gValidationHandler;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.process.AbstractBeanFactory;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.handler.NamedHandler;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.CharEncoding;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.collections.CollectionUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 16.12.2015
 */
@Stateless
@Local(IMeshtermCodesUpdaterManager.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class MeshtermCodesUpdaterManager extends BaseManager implements IMeshtermCodesUpdaterManager {

    private static final Logger LOG = Logger.getLogger(MeshtermCodesUpdaterManager.class);
    private static final Pattern CENTRAL_DESC_PTRN = Pattern.compile("<(D|MCW).*?>([^</>]*)</(D|MCW)>");
    private static final Pattern CDSR_DESC_PTRN =
            Pattern.compile("<(MeSHdescriptor|MeSHcheckWord).*>([^</>]*)</(MeSHdescriptor|MeSHcheckWord>)");
    private static final int MAX_DESC_LENGTH = 256;

    private final IRepository rps = AbstractManager.getRepository();

    @EJB
    private IMeshtermStorage mtStorage;

    @EJB
    private IEntireDBStorage entStorage;

    @EJB
    private IVersionManager vm;

    @Deprecated
    public void updateMeshtermCodes() {

        if (existProcess(ICMSProcessManager.LABEL_TERM2NUM)) {
            return;
        }

        startProcess(new NamedHandler(ICMSProcessManager.LABEL_TERM2NUM), IProcessManager.USUAL_PRIORITY);
    }

    private boolean isChangedDescriptorsExist() {
        return (mtStorage.getChangedDescriptorsCount() != 0);
    }


    public void prepareMeshtermCodes(String user) throws Exception {
        LOG.debug("%s process is started.", ICMSProcessManager.LABEL_TERM2NUM);
        clearDescriptors();
        initChangedDescriptors();

        if (!isChangedDescriptorsExist()) {
            throw new Exception("Descriptors didn't change. Mesh update aborted. ");
        }

        List<DBData> dbDataLst = getDBData();
        for (DBData dbData : dbDataLst) {
            prepareData(dbData);
            createUpdateProcess(dbData, user);
        }
        LOG.debug("%s process is finished.", ICMSProcessManager.LABEL_TERM2NUM);
    }


    @Override
    protected void onStart(ProcessHandler handler, ProcessVO pvo) throws ProcessException {
        initChangedDescriptors();
        if (!isChangedDescriptorsExist()) {
            LOG.debug("Descriptors didn't change. Mesh update aborted.");
            endProcess(pvo, ProcessState.SUCCESSFUL);
            return;
        }

        List<DBData> dbDataLst = getDBData();
        for (DBData dbData : dbDataLst) {
            prepareData(dbData);
            createUpdateProcess(dbData, pvo.getOwner());
        }
    }

    private void initChangedDescriptors() throws ProcessException {
        LOG.debug("Search changed descriptors started");
        try {
            if (!isChangedDescriptorsExist()) {
                List<String> descs = getChangedDescriptors();
                mtStorage.addChangedDescriptors(descs);
            }
            LOG.debug("Search changed descriptors completed");
        } catch (Exception e) {
            String err = "Search changed descriptors failed, " + e;
            LOG.error(err, e);
            throw new ProcessException(err);
        }
    }

    private List<String> getChangedDescriptors() throws Exception {
        String term2NumFilePath = FilePathCreator.getTerm2NumFilePath("term2num.xml");
        String term2NumOldFilePath = FilePathCreator.getTerm2NumFilePath("term2num_old.xml");

        checkTerm2NumExisting(term2NumFilePath);
        checkTerm2NumExisting(term2NumOldFilePath);

        Document term2NumDoc = parseDocument(term2NumFilePath);
        Document term2NumOldDoc = parseDocument(term2NumOldFilePath);

        return getChangedDescriptors(term2NumDoc, term2NumOldDoc);
    }

    private void checkTerm2NumExisting(String fileName) throws IOException {
        File f = new File(fileName);
        if (!f.exists()) {
            LOG.error(f.getName() + " does not exist. Please put " + f.getName() + " to "
                    + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.RESOURCES_TERM2NUM_OUTPUT)
                    + " catalog.");

            throw new IOException(f.getName() + " doesn't exist");
        }
    }

    private Document parseDocument(String filePath) throws Exception {
        String term2Num = InputUtils.readStreamToString(new FileInputStream(new File(filePath)));

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setCoalescing(true);
        dbf.setIgnoringElementContentWhitespace(true);
        dbf.setIgnoringComments(true);
        DocumentBuilder db = dbf.newDocumentBuilder();

        Document doc = db.parse(new ByteArrayInputStream(term2Num.getBytes()));
        doc.normalizeDocument();

        return doc;
    }

    private List<String> getChangedDescriptors(Document term2NumDoc, Document term2NumOldDoc) {
        List<String> descriptors = new ArrayList<>();
        List<Descriptor> term2NumDescriptorNodes = getDescriptorNodes(term2NumDoc);
        List<Descriptor> term2NumOldDescriptorNodes = getDescriptorNodes(term2NumOldDoc);
        List<Descriptor> backup = new ArrayList<>(term2NumDescriptorNodes);

        term2NumDescriptorNodes.removeAll(term2NumOldDescriptorNodes);
        term2NumOldDescriptorNodes.removeAll(backup);

        term2NumDescriptorNodes.addAll(term2NumOldDescriptorNodes);

        for (Descriptor qNode : term2NumDescriptorNodes) {
            if (!descriptors.contains(qNode.term)) {
                descriptors.add(qNode.term);
            }
        }

        return descriptors;
    }

    private List<Descriptor> getDescriptorNodes(Document doc) {
        List<Descriptor> result = new ArrayList<>();
        NodeList descriptorNodes = doc.getElementsByTagName("hSet").item(0).getChildNodes();
        String descriptorName;
        for (int i = 0; i < descriptorNodes.getLength(); i++) {
            NodeList numNodes = descriptorNodes.item(i).getChildNodes();
            List<String> nums = new ArrayList<>();
            for (int j = 0; j < numNodes.getLength(); j++) {
                nums.add(numNodes.item(j).getAttributes().getNamedItem("num").getTextContent());
            }

            descriptorName = descriptorNodes.item(i).getAttributes().getNamedItem("term").getTextContent();
            // cut "b" prefix for name
            if (!descriptorName.startsWith("a{") && !descriptorName.endsWith("}")) {
                result.add(new Descriptor(descriptorName.substring(1), nums));
            }
        }
        return result;
    }

    private List<DBData> getDBData() {
        List<DBData> dbDataLst = new ArrayList<>();
        String dbs = CochraneCMSPropertyNames.getMeshtermRecordUpdateDbs();
        if (dbs.contains(CochraneCMSPropertyNames.getCentralDbName())) {
            dbDataLst.add(new DBData(CochraneCMSPropertyNames.getCentralDbName(), CENTRAL_DESC_PTRN));
        }
        if (dbs.contains(CochraneCMSPropertyNames.getCDSRDbName())) {
            dbDataLst.add(new DBData(CochraneCMSPropertyNames.getCDSRDbName(), CDSR_DESC_PTRN));
        }

        return dbDataLst;
    }

    private void prepareData(DBData dbData) {
        LOG.debug(String.format("Parsing descriptors of [%s] DB records started", dbData.getDbName()));
        initRecords4Check(dbData);
        initRecordDescriptors(dbData);
        LOG.debug(String.format("Parsing descriptors of [%s] DB records completed", dbData.getDbName()));
    }

    private void initRecords4Check(DBData dbData) {
        if (isRecords4CheckInitiated(dbData)) {
            return;
        }

        int qSize = CochraneCMSPropertyNames.getMeshtermRecordUpdateQueryMaxSize();
        boolean created;
        int createdCnt = 0;
        do {
            created = createRecords4CheckDbEntries(dbData.getDbName(), createdCnt, qSize);
            createdCnt += qSize;
        } while (created);
    }

    private boolean isRecords4CheckInitiated(DBData dbData) {
        boolean initiated;
        int rec4ChkCnt = mtStorage.getRecords4CheckCountByDbId(dbData.getDbId());
        int recCnt = entStorage.getRecordListCount(dbData.getDbId());

        if (rec4ChkCnt == 0) {
            initiated = false;
        } else {
            if (rec4ChkCnt != recCnt) {
                mtStorage.deleteRecordDescriptors(dbData.getDbId());
                mtStorage.deleteRecords4Check(dbData.getDbId());

                initiated = false;
            } else {
                initiated = true;
            }
        }
        return initiated;
    }

    private boolean createRecords4CheckDbEntries(String dbName, int offset, int limit) {
        Map<Integer, String> idsAndNames =
                entStorage.getRecordIdsAndNames(dbName, offset, limit, null, null, 0, false, null);
        if (idsAndNames.isEmpty()) {
            return false;
        }

        List<MeshtermRecord4CheckEntity> record4CheckEntities = new ArrayList<>(idsAndNames.size());
        boolean cdsr = dbName.equals(CochraneCMSPropertyNames.getCDSRDbName());
        for (int recordId : idsAndNames.keySet()) {
            if (!cdsr) {
                record4CheckEntities.add(
                        new MeshtermRecord4CheckEntity(idsAndNames.get(recordId), recordId, null, null, true));
                continue;
            }
            List<PrevVO> versions = vm.getVersions(idsAndNames.get(recordId));
            if (!versions.isEmpty()) {
                PrevVO last = versions.remove(0);
                record4CheckEntities.add(
                        new MeshtermRecord4CheckEntity(last.name, recordId, last.buildDoi(), last.version, true));
                versions.forEach(v -> record4CheckEntities.add(
                        new MeshtermRecord4CheckEntity(last.name, recordId, v.buildDoi(), v.version, false)));
            }
        }
        mtStorage.saveRecords4Check(record4CheckEntities, dbName);
        return true;
    }

    private void initRecordDescriptors(DBData dbData) {
        int batchSize = CochraneCMSPropertyNames.getMeshtermRecordUpdateBatchSize();

        List<MeshtermRecord4CheckEntity> entities =
                mtStorage.getRecords4CheckNotCheckedByDbId(dbData.getDbId(), batchSize);
        while (!entities.isEmpty()) {
            parseRecordDescriptors(dbData, entities);
            mtStorage.updateRecords4CheckStatus(getRecord4CheckIds(entities), true);

            entities = mtStorage.getRecords4CheckNotCheckedByDbId(dbData.getDbId(), batchSize);
        }
    }

    private void parseRecordDescriptors(DBData dbData, List<MeshtermRecord4CheckEntity> entities) {
        for (MeshtermRecord4CheckEntity entity : entities) {
            if (mtStorage.getRecordDescriptorsCountByRecordId(entity.getId()) > 0) {
                continue;
            }

            String source = getSource(dbData.getDbName(), entity);
            List<String> descriptors = getDescriptorsFromSource(dbData.getDescPattern(), source, entity.getName());
            if (CollectionUtils.isNotEmpty(descriptors)) {
                mtStorage.createRecordDescriptors(descriptors, entity);
            }
        }
    }

    private String getSource(String dbName, MeshtermRecord4CheckEntity entity) {
        String sourceFilePath;
        if (entity.isLatestVersion()) {
            sourceFilePath = dbName.equals(CochraneCMSPropertyNames.getCDSRDbName())
                                     ? FilePathCreator.getFilePathForEntireMl3gXml(dbName, entity.getName())
                                     : FilePathCreator.getFilePathToSourceEntire(dbName, entity.getName());
        } else {
            sourceFilePath = FilePathCreator.getPreviousMl3gXmlPath(entity.getName(), entity.getVersion());
        }
        String source = "";
        try {
            source = InputUtils.readStreamToString(rps.getFile(sourceFilePath));
        } catch (IOException e) {
            LOG.error("Could not read source file from " + sourceFilePath);
        }

        return source;
    }

    private List<String> getDescriptorsFromSource(Pattern descPattern, String source, String recName) {
        List<String> descriptors = new ArrayList<>();
        Matcher m = descPattern.matcher(source);
        while (m.find()) {
            String tmp = m.group(m.groupCount() - 1).toLowerCase();
            if (isValidDescriptor(tmp)) {
                String desc = replaceSymbols(tmp);
                descriptors.add(desc);
            } else {
                LOG.error(String.format("Record [%s] contains invalid descriptor {%s}", recName, tmp));
            }
        }

        return descriptors;
    }

    private boolean isValidDescriptor(String desc) {
        return (desc.length() <= MAX_DESC_LENGTH && !desc.contains("\n"));
    }

    private String replaceSymbols(String str) {
        String result = str;
        result = result.replaceAll("&amp;", "&");
        result = result.replaceAll("&#8208;", "-");
        result = result.replaceAll("[-'();&+,‐]", "");
        result = result.replaceAll("\\s", "_");

        return result;
    }

    private List<Integer> getRecord4CheckIds(List<MeshtermRecord4CheckEntity> entities) {
        List<Integer> ids = new ArrayList<>(entities.size());
        for (MeshtermRecord4CheckEntity entity : entities) {
            ids.add(entity.getId());
        }

        return ids;
    }

    private void createUpdateProcess(DBData dbData, String user) {
        List<OutdatedRecord> outdatedRecords = mtStorage.getOutdatedRecords4Check(dbData.getDbId()).stream()
                .map(OutdatedRecord::new).distinct().collect(Collectors.toList());
        if (outdatedRecords.isEmpty()) {
            LOG.debug(dbData.getDbName()
                    + " records with outdated mesh terms were not found, further processing has been terminated");
            return;
        }

        saveOutdatedRecords(dbData, outdatedRecords);

        List<Integer> recIds = outdatedRecords.stream().map(r -> r.id).collect(Collectors.toList());
        if (CochraneCMSPropertyNames.getMeshUpdateMl3gToMl3gConversionEnabled()
                    && dbData.getDbName().equals(CochraneCMSPropertyNames.getCDSRDbName())) {
            startWML3GToWML3GConversionProcess(dbData, recIds, user);
        }
    }

    private void startWML3GToWML3GConversionProcess(DBData dbData, List<Integer> recIds, String user) {
        DbHandler dbHandler = new DbHandler(0, dbData.getDbName(), dbData.getDbId(), true);
        Wml3gValidationHandler ml3gh = new Wml3gValidationHandler(dbHandler);
        ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_ML3G_MESH_UPDATE_SELECTIVE).get();
        try {
            ProcessVO pvo = ProcessHelper.createIdPartsProcess(ml3gh, pt, pt.getPriority(), user,
                                                               recIds.toArray(new Integer[0]),
                                                               DbEntity.NOT_EXIST_ID, pt.batch());
            CochraneCMSBeans.getCMSProcessManager().startProcess(pvo);
        } catch (Exception e) {
            String err = String.format("Failed to create process %s, %s",
                    ICMSProcessManager.LABEL_WML3G_MESH_UPDATE, e);
            LOG.error(err, e);
        }
    }

    private void saveOutdatedRecords(DBData dbData, List<OutdatedRecord> outdatedRecords) {
        List<String> lines = outdatedRecords.stream().map(OutdatedRecord::toString).collect(Collectors.toList());
        File f = new File(rps.getRealFilePath(FilePathCreator.getMeshtermRecordUpdatedFilePath(dbData.getDbName())));
        try {
            FileUtils.writeLines(f, CharEncoding.UTF_8, lines);
            LOG.debug("Outdated record names saved to file [" + f + "]");
        } catch (Exception e) {
            LOG.error("Failed to save outdated record names to file [" + f + "]", e);
        }
    }

    @Override
    protected void onEnd(ProcessVO pvo) throws ProcessException {
        super.onEnd(pvo);

        clearDescriptors();
    }

    private void clearDescriptors() {
        mtStorage.deleteChangedDescriptors();
        mtStorage.deleteRecordDescriptors();
        mtStorage.deleteRecords4Check();
    }

    /**
     *
     */
    private static class DBData {

        private final int dbId;
        private final String dbName;
        private final Pattern descPattern;

        public DBData(String dbName, Pattern descPattern) {
            this.dbName = dbName;
            this.dbId = AbstractWrapper.getResultStorage().getDatabaseEntity(dbName).getId();
            this.descPattern = descPattern;
        }

        public int getDbId() {
            return dbId;
        }

        public String getDbName() {
            return dbName;
        }

        public Pattern getDescPattern() {
            return descPattern;
        }
    }

    /**
     *
     */
    private static class Descriptor {

        private static final int DIFF = 31;

        private final String term;
        private final List<String> nums;

        private Descriptor(String term, List<String> nums) {
            this.term = term;
            this.nums = nums;
            Collections.sort(this.nums);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Descriptor that = (Descriptor) o;

            return nums.equals(that.nums) && term.equals(that.term);

        }

        @Override
        public int hashCode() {
            int result = term.hashCode();
            result = DIFF * result + nums.hashCode();
            return result;
        }
    }

    /**
     *
     */
    public static class OutdatedRecord {

        private final int id;
        private final String name;
        private final String doi;

        OutdatedRecord(MeshtermRecord4CheckEntity record4Check) {
            id = record4Check.getRecordId();
            name = record4Check.getName();
            doi = record4Check.getDoi();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            OutdatedRecord that = (OutdatedRecord) o;
            return this == o
                    || Objects.equals(name, that.name) && Objects.equals(doi, that.doi);
        }

        @Override
        public int hashCode() {
            return Objects.hash(toString());
        }

        @Override
        public String toString() {
            return StringUtils.isNotEmpty(doi) ? doi : name;
        }
    }

    /**
     *
     */
    public static class Factory extends AbstractBeanFactory<IMeshtermCodesUpdaterManager> {

        public static final String LOOKUP_NAME = CochraneCMSPropertyNames.buildLookupName(
            "MeshtermCodesUpdaterManager", IMeshtermCodesUpdaterManager.class);
        private static final Factory FACTORY_INSTANCE = new Factory();

        private Factory() {
            super(LOOKUP_NAME);
        }

        public static IMeshtermCodesUpdaterManager getBeanInstance() {
            return FACTORY_INSTANCE.getInstance();
        }
    }
}
