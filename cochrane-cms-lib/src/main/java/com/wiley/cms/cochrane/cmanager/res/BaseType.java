package com.wiley.cms.cochrane.cmanager.res;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.LiteratumPriorityStrategy;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.BitValue;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.res.DataTable;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.03.13
 */
@XmlRootElement(name = BaseType.RES_NAME)
public class BaseType extends ResourceStrId {

    static final String RES_NAME = "database";

    private static final long serialVersionUID = 1L;

    private static final DataTable<String, BaseType> DT = new DataTable<>(RES_NAME);

    private boolean ds;
    private boolean hw;
    private boolean lit;

    @XmlAttribute(name = "dbid")
    private int dbId;

    @XmlAttribute(name = "snowflake")
    private boolean sf;

    @XmlAttribute(name = "shortname")
    private String shortname;

    @XmlAttribute(name = "fullname")
    private String fullname;

    @XmlElement(name = "pub")
    private List<PubInfo> pubTypes;

    @XmlAttribute(name = "pub-dates")
    private boolean pubDatesSupported;

    @XmlAttribute(name = "scheduled-pub-dates")
    private boolean scheduledPublicationSupported;

    @XmlAttribute(name = "pdf")
    private int pdf;

    @XmlAttribute(name = "html")
    private boolean html = true;

    @XmlAttribute(name = "ml3g")
    private int ml3gConverted = 1;

    @XmlAttribute(name = "min_approved")
    private int minApproved;

    @XmlAttribute(name = "unique-issue")
    private boolean uniqueInIssueDb;

    @XmlAttribute(name = "legal")
    private int legal = BitValue.BITS_0_1;

    /* default options holder **/
    private PubInfo mainWol;
    private PubInfo ml3g;

    @XmlElement(name = "literatum-priority")
    private LiteratumPriority litPriority;

    @XmlElement(name = "hw-frequency")
    private HWFrequency hwFrequency;

    @XmlAttribute(name = "doi-to-cdnumber")
    private String doiToRecordName;

    private ProductType productType;
    private final boolean[] keys = {false, false, false, false, false, false, false, false, false, false, false};

    /** resource file name -> cached resource content */
    private Map<String, String> resourceCache;

    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, JaxbResourceFactory.create(BaseType.class));
    }

    public static Res<BaseType> find(String sid) {
        return DT.findResource(sid);
    }

    public static Collection<Res<BaseType>> getAll() {
        return DT.values();
    }

    public static List<String> getDbNames4Create() {
        Collection<Res<BaseType>> all = getAll();
        List<String> ret = new ArrayList<>();
        for (Res<BaseType> bt: all) {
            if (bt.exist() && bt.get().shouldCreate()) {
                ret.add(bt.get().getId());
            }
        }
        return ret;
    }

    public static String getDbName(int baseType) {
        String ret;
        switch (baseType) {
            case DatabaseEntity.CENTRAL_KEY:
                ret = CochraneCMSPropertyNames.getCentralDbName();
                break;
            case DatabaseEntity.CDSR_KEY:
                ret =  CochraneCMSPropertyNames.getCDSRDbName();
                break;

            case DatabaseEntity.CDSR_TA_KEY:
                ret = "clsysrevta";
                break;

            case DatabaseEntity.EDITORIAL_KEY:
                ret =  CochraneCMSPropertyNames.getEditorialDbName();
                break;
            case DatabaseEntity.CCA_KEY:
                ret =  CochraneCMSPropertyNames.getCcaDbName();
                break;
            default:
                ret = null;
        }
        return ret;
    }

    static Res<BaseType> get(String sid) {
        return DT.get(sid);
    }

    public static boolean isWRSupported(Res<BaseType> baseType) {
        return Res.valid(baseType) && isWRSupported(baseType.get());
    }

    public static boolean isWRSupported(BaseType bt) {
        return bt.isCDSR() || bt.isCCA() || bt.isEditorial();
    }

    public static Res<BaseType> getCentral() {
        return find(CochraneCMSPropertyNames.getCentralDbName());
    }

    public static Res<BaseType> getCDSR() {
        return find(CochraneCMSPropertyNames.getCDSRDbName());
    }

    public static Res<BaseType> getCDSRTA() {
        return find(CochraneCMSPropertyNames.getCDSRDbName() + "ta");
    }

    public static Res<BaseType> getEditorial() {
        return find(CochraneCMSPropertyNames.getEditorialDbName());
    }

    public static Res<BaseType> getCCA() {
        return find(CochraneCMSPropertyNames.getCcaDbName());
    }

    public final boolean canDS() {
        return ds;
    }

    public final boolean canLiteratum() {
        return lit;
    }

    public int getDbId() {
        return dbId;
    }

    public final String getShortName() {
        return shortname;
    }

    public final String getFullName() {
        return fullname;
    }

    public final int getMinApproved() {
        return minApproved;
    }

    public final boolean canUnpack() {
        return false;
    }

    public final boolean hasSFLogging() {
        return sf;
    }

    public final boolean canPdfFopConvert() {
        return pdf > 1;
    }

    public final boolean canMl3gConvert() {
        return ml3gConverted > 0;
    }

    public final boolean canHtmlConvert() {
        return html;
    }

    public final boolean isActualPublicationDateSupported() {
        return pubDatesSupported;
    }

    public final boolean isScheduledPublicationSupported() {
        return scheduledPublicationSupported;
    }

    public final boolean hasStandaloneWml3g() {
        return ml3gConverted == 2;
    }

    public final boolean hasTranslationMl3g() {
        return canMl3gConvert() && ml3g != null && ml3g.hasTranslation();
    }

    public final int getTranslationModeMl3g() {
        return ml3g.getTranslationMode();
    }

    public final boolean hasTranslationHtml() {
        return canHtmlConvert() && mainWol != null && mainWol.hasTranslation();
    }

    public final int getTranslationModeHtml() {
        return mainWol != null ? mainWol.getTranslationMode() : 0;
    }

    public boolean isUniqueInIssueDb() {
        return uniqueInIssueDb;
    }

    public boolean legal() {
        return BitValue.getBit(0, legal);
    }

    private boolean shouldCreate() {
        return BitValue.getBit(1, legal);
    }

    private LiteratumPriorityStrategy getDefaultLiteratumPriority() {
        return litPriority == null ? LiteratumPriorityStrategy.LOW : litPriority.def;
    }

    public final LiteratumPriorityStrategy getPackageLiteratumPriority() {
        return litPriority == null ? getDefaultLiteratumPriority() : litPriority.file;
    }

    public final LiteratumPriorityStrategy getIssueLiteratumPriority() {
        return litPriority == null ? getDefaultLiteratumPriority() : litPriority.issue;
    }

    public final LiteratumPriorityStrategy getEntireLiteratumPriority() {
        return litPriority == null ? getDefaultLiteratumPriority() : litPriority.entire;
    }

    public final LiteratumPriorityStrategy getWhenReadyLiteratumPriority() {
        return litPriority == null ? getDefaultLiteratumPriority() : litPriority.whenready;
    }

    public final LiteratumPriorityStrategy getSelectiveLiteratumPriority() {
        return litPriority == null ? getDefaultLiteratumPriority() : litPriority.selective;
    }

    public final HWFreq getIssueHWFrequency() {
        return hwFrequency == null ? getDefaultHWFrequency() : hwFrequency.issue;
    }

    public final HWFreq getEntireHWFrequency() {
        return hwFrequency == null ? getDefaultHWFrequency() : hwFrequency.entire;
    }

    public final HWFreq getWhenReadyHWFrequency() {
        return hwFrequency == null ? getDefaultHWFrequency() : hwFrequency.whenready;
    }

    public final HWFreq getSelectiveHWFrequency() {
        return hwFrequency == null ? getDefaultHWFrequency() : hwFrequency.selective;
    }

    private static HWFreq getDefaultHWFrequency() {
        return HWFreq.BULK;
    }

    public String getCdNumberByDoi(String doi) {
        return doi.replaceAll(doiToRecordName, "");
    }

    public ProductType getProductType() {
        return productType;
    }

    public boolean isCentral() {
        return keys[DatabaseEntity.CENTRAL_KEY - 1];
    }

    public boolean isCDSR() {
        return keys[DatabaseEntity.CDSR_KEY - 1];
    }

    public boolean isCmr() {
        return keys[DatabaseEntity.CMR_KEY - 1];
    }

    public boolean isMethrev() {
        return keys[DatabaseEntity.METHREV_KEY - 1];
    }

    public boolean isEditorial() {
        return keys[DatabaseEntity.EDITORIAL_KEY - 1];
    }

    public boolean isCCA() {
        return keys[DatabaseEntity.CCA_KEY - 1];
    }

    @Override
    protected void resolve() {
        resolveProductType(getDbId());
        
        if (canPublish()) {
            resolvePubTypes();
        }
    }

    private void resolveProductType(int dbType) {

        switch (dbType) {
            case DatabaseEntity.CENTRAL_KEY:
                productType = ProductType.CENTRAL;
                break;
            case DatabaseEntity.CDSR_KEY:
                productType = ProductType.CDSR;
                break;
            case DatabaseEntity.CDSR_TA_KEY:
                productType = ProductType.CDSR;
                keys[DatabaseEntity.CDSR_KEY - 1] = true;
                break;
            case DatabaseEntity.EDITORIAL_KEY:
                productType = ProductType.EDITORIAL;
                break;
            case DatabaseEntity.CCA_KEY:
                productType = ProductType.CCA;
                break;
            case DatabaseEntity.CMR_KEY:
            case DatabaseEntity.EED_KEY:
            case DatabaseEntity.METHREV_KEY:
            case DatabaseEntity.ABOUT_KEY:
                break;
            default:
                return;
        }
        keys[dbType - 1] = true;
    }

    private void resolvePubTypes() {
        for (PubInfo pi: pubTypes) {
            PubType type = pi.getType();
            if (!lit && PubType.isLiteratum(type.getMajorType())) {
                lit = true;
            } else if (!ds && PubType.isDS(type.getMajorType())) {
                ds = true;
            } else if (!hw && PubType.isSemantico(type.getMajorType())) {
                hw = true;
            }
            updateSourceOptions(pi);
        }
    }

    @Override
    protected void populate() {
        DT.publish(this);
    }

    private void updateSourceOptions(PubInfo pi) {

        if (pi.cacheResource) {
            updateResourceCache(pi);
        }

        if (pi.getType().isMl3g()) {
            updateMl3gSourceOptions(pi);

        } else if (pi.getType().isHtml()) {
            updateHtmlSourceOptions(pi);
        }
    }

    private void updateResourceCache(PubInfo pi) {

        if (resourceCache == null) {
            resourceCache = new HashMap<>();
        }

        String pubType = pi.getType().getId();
        File[] resources = PublishHelper.getControlFileContent(getId(), pubType);
        for (File fl: resources) {
            try {
                String content = FileUtils.readStream(fl.toURI(), "\n");
                String key = pubType + "_" + fl.getName();
                if (resourceCache.containsKey(key)) {
                    LOG.warn(String.format("%s already contains resource by key %s", getId(), key));
                }
                resourceCache.put(key, content);

            } catch (Exception e) {
                LOG.error(e.getMessage());
            }
        }
    }

    public String getResourceContent(String pubType, String fileName) {
        return resourceCache != null ? resourceCache.get(pubType + "_" + fileName) : null;
    }

    private void updateMl3gSourceOptions(PubInfo pi) {

        if (ml3g == null || ml3g.sourcePostfix != null) {
            // the default is that which have no sourcePostfix among others
            ml3g = pi;
        }
    }

    private void updateHtmlSourceOptions(PubInfo pi) {

        if (mainWol == null || mainWol.sourcePostfix != null) {
            // the default is that which have no sourcePostfix among others
            mainWol = pi;
        }
    }

    public final Collection<PubInfo> getPubInfo() {
        return pubTypes;
    }

    public final PubInfo getPubInfo(String pubId) {

        for (PubInfo pi: pubTypes) {
            if (pubId.equals(pi.getType().getId())) {
                return pi;
            }
        }
        return null;
    }

    public final boolean canPublish() {
        return pubTypes != null && !pubTypes.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("%s.%s", getId(), shortname);
    }

    /**
     * Publication info
     */
    public static class PubInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        @XmlAttribute(name = "tag")
        private String tag = "MAIN";

        @XmlAttribute(name = "track")
        private int trackRecord;

        @XmlAttribute(name = "entire")
        private boolean entire;

        @XmlAttribute(name = "issue")
        private boolean issue = true;

        @XmlAttribute(name = "archive")
        private String archiveName;

        private Res<PubType> type;

        private int ta;

        private String sourcePostfix;

        private String archiveRootDir = "";

        private int batch;
        private int outBatch;

        @XmlAttribute(name = "cache-resource")
        private boolean cacheResource;

        @XmlAttribute(name = "pubtype-ref")
        public void setTypeXml(String type) {
            this.type = PubType.get(type);
        }

        public PubType getType() {
            return type.get();
        }

        public String getTag() {
            return tag;
        }

        public boolean canEntire() {
            return entire;
        }

        public boolean canIssue() {
            return issue;
        }

        final String getArchiveName() {
            return archiveName;
        }

        public final int getTranslationMode() {
            return ta;
        }

        public final boolean hasTranslation() {
            return ta > 0;
        }

        public int getTrackRecord() {
            return trackRecord;
        }

        public void setTrackRecordForTest(int value) {
            trackRecord = value;
        }

        @XmlAttribute(name = "ta")
        public final void setTranslationMode(int value) {
            ta = value;
        }

        @XmlAttribute(name = "source-ref-postfix")
        public final String getSourcePostfix() {
            return sourcePostfix;
        }

        public final void setSourcePostfix(String value) {
            sourcePostfix = value;
        }

        public String getSBNDir() {
            return archiveRootDir;
        }

        public String getSBN() {
            return archiveRootDir == null || archiveRootDir.isEmpty() ? Constants.SBN
                    : (archiveRootDir.startsWith("/") ? archiveRootDir.substring(1) : archiveRootDir);
        }

        @XmlAttribute(name = "sbn")
        public void setSBNDir(String archiveRootDir) {
            this.archiveRootDir = archiveRootDir;
        }

        /**
         * It'supposed to be a batch for publishing scope processing
         * @return a batch for processing of content 
         */
        @XmlAttribute(name = "batch")
        public int getBatch() {
            return batch;
        }

        public void setBatch(int batch) {
            this.batch = batch;
        }

        /**
         * It's supposed to be a batch which splits a resulting package to send
         * @return a batch of final publishing package
         */
        @XmlAttribute(name = "out-batch")
        public int getOutBatch() {
            return outBatch;
        }

        public void setOutBatch(int outBatch) {
            this.outBatch = outBatch;
        }

        @Override
        public String toString() {
            return sourcePostfix == null ? getType().toString()
                    : String.format("%s is copied in %s, ta=%s", getType(), sourcePostfix, ta);
        }
    }

    /**
     * Define Literatum priority strategy for various publishing cases
     */
    public static class LiteratumPriority implements Serializable {
        private static final long serialVersionUID = 1L;

        private LiteratumPriorityStrategy issue     = LiteratumPriorityStrategy.HIGH4NEW_LOW4UPDATE;
        private LiteratumPriorityStrategy entire    = LiteratumPriorityStrategy.HIGH4NEW_LOW4UPDATE;
        private LiteratumPriorityStrategy selective = LiteratumPriorityStrategy.HIGH4NEW_LOW4UPDATE;
        private LiteratumPriorityStrategy whenready = LiteratumPriorityStrategy.HIGH4NEW_LOW4UPDATE;
        private LiteratumPriorityStrategy file      = LiteratumPriorityStrategy.HIGH4NEW_LOW4UPDATE;

        @XmlAttribute(name = "all")
        private final LiteratumPriorityStrategy def = LiteratumPriorityStrategy.HIGH4NEW_LOW4UPDATE;

        @XmlAttribute(name = Constants.ISSUE)
        public void setIssuePriority(String name) {
            issue = LiteratumPriorityStrategy.valueOf(name);
        }

        @XmlAttribute(name = Constants.ENTIRE)
        public void setEntirePriority(String name) {
            entire = LiteratumPriorityStrategy.valueOf(name);
        }

        @XmlAttribute(name = Constants.SELECTIVE)
        public void setSelectivePriority(String name) {
            selective = LiteratumPriorityStrategy.valueOf(name);
        }

        @XmlAttribute(name = Constants.WHEN_READY)
        public void setWhenReadyPriority(String name) {
            whenready = LiteratumPriorityStrategy.valueOf(name);
        }

        @XmlAttribute(name = Constants.PACKAGE)
        public void setPackagePriority(String name) {
            file = LiteratumPriorityStrategy.valueOf(name);
        }
    }

    /**
     * Define HW frequency
     */
    public static class HWFrequency implements Serializable {
        private static final long serialVersionUID = 1L;

        private HWFreq issue     = HWFreq.BULK;
        private HWFreq entire    = HWFreq.BULK;
        private HWFreq selective = HWFreq.BULK;
        private HWFreq whenready = HWFreq.REAL_TIME;

        @XmlAttribute(name = Constants.ISSUE)
        public void setIssueFrequency(String name) {
            issue = HWFreq.valueOf(name);
        }

        @XmlAttribute(name = Constants.ENTIRE)
        public void setEntireFrequency(String name) {
            entire = HWFreq.valueOf(name);
        }

        @XmlAttribute(name = Constants.SELECTIVE)
        public void setSelectiveFrequency(String name) {
            selective = HWFreq.valueOf(name);
        }

        @XmlAttribute(name = Constants.WHEN_READY)
        public void setWhenReadyFrequency(String name) {
            whenready = HWFreq.valueOf(name);
        }
    }

    /**
     *  Database constants grouped by database type
     */
    public enum ProductType {
        CDSR {
            @Override
            public String getDOIPrefix() {
                return Constants.DOI_PREFIX_CDSR;
            }

            @Override
            public String getParentDOI() {
                return Constants.DOI_CDSR;
            }

            @Override
            public int buildRecordNumber(String cdNumber) {
                return RecordHelper.buildRecordNumberCdsr(cdNumber);
            }

            @Override
            public String buildDoi(String cdNumber, int pubNumber) {
                return RevmanMetadataHelper.buildDoi(cdNumber, pubNumber);
            }

            @Override
            public String buildDoi(String pubName) {
                return getDOIPrefix() + pubName;
            }

            @Override
            public String buildPublisherId(String cdNumber, int pubNumber) {
                return RevmanMetadataHelper.buildPubName(cdNumber, pubNumber);
            }

            @Override
            public String parsePubName(String doi) {
                return RevmanMetadataHelper.parsePubName(doi);
            }

            @Override
            public String parseCdNumber(String pubName)  {
                return RevmanMetadataHelper.parseCdNumber(pubName);
            }

            @Override
            public int parsePubNumber(String publisherIdOrDoi) {
                return RevmanMetadataHelper.parsePubNumber(publisherIdOrDoi);
            }

            @Override
            public int getProcessTypeOnUpload() {
                return ICMSProcessManager.PROC_TYPE_UPLOAD_CDSR;
            }

            @Override
            public Integer getSPDDbId() {
                return Constants.SPD_DB_CDSR_ID;
            }
        },

        EDITORIAL {
            @Override
            public String getDOIPrefix() {
                return Constants.DOI_PREFIX_CDSR;
            }

            @Override
            public String getParentDOI() {
                return Constants.DOI_CDSR;
            }

            @Override
            public String getType(BaseType bt) {
                return super.getType(bt).toUpperCase();
            }

            @Override
            public int buildRecordNumber(String cdNumber) {
                return RecordHelper.buildRecordNumberCdsr(cdNumber);
            }

            @Override
            public String buildDoi(String cdNumber, int pubNumber) {
                return RevmanMetadataHelper.buildDoi(cdNumber, pubNumber);
            }

            @Override
            public String buildDoi(String pubName) {
                return getDOIPrefix() + pubName;
            }

            @Override
            public String parsePubName(String doi) {
                return RevmanMetadataHelper.parsePubName(doi);
            }

            @Override
            public int getProcessTypeOnUpload() {
                return ICMSProcessManager.PROC_TYPE_UPLOAD_EDI;
            }

            @Override
            public Integer getSPDDbId() {
                return Constants.SPD_DB_EDITORIAL_ID;
            }
        },

        CENTRAL {
            @Override
            public String getDOIPrefix() {
                return Constants.DOI_PREFIX_CENTRAL;
            }

            @Override
            public String getParentDOI() {
                return Constants.DOI_CENTRAL;
            }

            @Override
            public int buildRecordNumber(String cdNumber) {
                return RecordHelper.buildRecordNumberCentral(cdNumber);
            }

            @Override
            public String buildDoi(String cdNumber, int pubNumber) {
                return buildDoi(cdNumber);
            }

            @Override
            public String buildDoi(String pubName) {
                return RecordHelper.buildDoiCentral(pubName);
            }


            @Override
            public Integer getSPDDbId() {
                return Constants.SPD_DB_CENTRAL_ID;
            }

            @Override
            public int getProcessTypeOnUpload() {
                return ICMSProcessManager.PROC_TYPE_UPLOAD_CENTRAL;
            }
        },

        CCA {
            @Override
            public String getDOIPrefix() {
                return Constants.DOI_PREFIX;
            }

            @Override
            public String getParentDOI() {
                return Constants.DOI_CCA;
            }

            @Override
            public int buildRecordNumber(String cdNumber) {
                return RecordHelper.buildRecordNumberCca(cdNumber);
            }

            @Override
            public String buildDoi(String cdNumber, int pubNumber) {
                return buildDoi(cdNumber);
            }

            @Override
            public String buildDoi(String pubName) {
                return RecordHelper.buildDoiCCA(pubName);
            }

            @Override
            public String parseCdNumber(String pubName)  {
                return pubName.replace(".", "");
            }

            @Override
            public int getProcessTypeOnUpload() {
                return ICMSProcessManager.PROC_TYPE_UPLOAD_CCA;
            }

            @Override
            public Integer getSPDDbId() {
                return Constants.SPD_DB_CCA_ID;
            }
        };

        public abstract String getDOIPrefix();

        public abstract String getParentDOI();

        public String getType(BaseType bt) {
            return bt.getShortName();
        }

        public int buildRecordNumber(String cdNumber) {
            return RecordHelper.buildRecordNumber(cdNumber);
        }

        public String buildCdNumber(int recordNumber) {
            return RecordHelper.buildCdNumber(recordNumber);
        }

        public String buildDoi(String cdNumber, int pubNumber) {
            return null;
        }

        public String buildDoi(String pubName) {
            return null;
        }

        public String buildPublisherId(String cdNumber, int pubNumber) {
            return cdNumber;
        }

        public String parsePubName(String doi) {
            return doi.substring(Constants.DOI_PREFIX.length());
        }

        public String parseCdNumber(String pubName)  {
            return pubName;
        }

        public int parsePubNumber(String publisherIdOrDoi) {
            return Constants.FIRST_PUB;
        }

        public int getProcessTypeOnUpload() {
            return -1;
        }

        public Integer getSPDDbId() {
            return null;
        }
    }
}

