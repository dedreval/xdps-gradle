package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.InputStream;
import java.time.LocalDate;

import org.jdom.Document;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.CmsResourceInitializer;
import com.wiley.cms.cochrane.utils.xml.JDOMHelper;
import com.wiley.tes.util.jdom.DocumentLoader;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.Settings;

/**
 * @author <a href='mailto:atolkachev@wiley.ru'>Andrey Tolkachev</a>
 * Date: 01.02.21
 */
public class AriesImportFile {
    public static final String SUBMISSION_PATH = "//submission";
    public static final String PRODUCTION_TASK_DESCRIPTION = "production-task-description";
    public static final String PRODUCTION_TASK_DESCRIPTION_PATH = SUBMISSION_PATH + "/" + PRODUCTION_TASK_DESCRIPTION;
    public static final String TARGET_ONLINE_PUB_DATE_PATH = SUBMISSION_PATH + "/target-online-pub-date";
    public static final String ACTUAL_ONLINE_PUB_DATE_PATH = SUBMISSION_PATH + "/actual-online-pub-date";
    public static final String MANUSCRIPT_NUMBER_PATH = SUBMISSION_PATH + "/manuscript-number";
    public static final String ARTICLE_TYPE_PATH = SUBMISSION_PATH + "/article-type";
    public static final String TASK_ID_PATH = SUBMISSION_PATH + "/@guid";

    public static final String DELIVER_TO_WILEY = "Deliver to Wiley";
    public static final String PUBLISH_ON_CLIB = "Publish on CLib";
    public static final String CANCEL_PUBLISH_ON_CLIB = "Cancel Scheduled Publication";

    private static final Res<Settings> ARTICLE_TYPE_DB_MAP = CmsResourceInitializer.getAriesArticleTypeDbMapping();

    private final String fileName;
    private String productionTaskDescription;
    private String targetOnlinePubDateSrc;
    private String actualOnlinePubDate;
    private String doi;
    private String cdNumber;
    private int pub;
    private String manuscriptNumber;
    private String articleType;
    private Boolean deliver;
    private boolean cancel;
    private String taskId;
    private String err;

    public AriesImportFile(String fileName) {
        this.fileName = fileName;
    }

    AriesImportFile parseImportFile(InputStream is, boolean ack) {
        return parseImportFile(is, new DocumentLoader(), ack);
    }

    AriesImportFile parseImportFile(InputStream is, DocumentLoader dl, boolean ack) {
        try {
            Document doc = dl.load(is);

            setImportElementValue(doc, PRODUCTION_TASK_DESCRIPTION_PATH, PRODUCTION_TASK_DESCRIPTION_PATH);
            setImportElementValue(doc, MANUSCRIPT_NUMBER_PATH, "manuscript-number");
            setImportElementValue(doc, ARTICLE_TYPE_PATH, "article-type");

            if (!AriesHelper.getManuscriptNumberByImportName(fileName).equals(getManuscriptNumber())) {
                throw new CmsException(String.format("'%s' %s does not relevant to file name %s",
                        MANUSCRIPT_NUMBER_PATH, getManuscriptNumber(), getFileName()));
            }
            taskId = JDOMHelper.getAttributeValue(doc, TASK_ID_PATH, null);
            if (taskId == null || taskId.trim().isEmpty()) {
                throw new CmsException(String.format("no value found by '%s' in '%s'", SUBMISSION_PATH, getFileName()));
            }
            if (isPublish() || ack) {
                setImportElementValue(doc, ACTUAL_ONLINE_PUB_DATE_PATH, "actual-online-pub-date", ack);
            }
            if (isDeliver() || ack) {
                setImportElementValue(doc, TARGET_ONLINE_PUB_DATE_PATH, "target-online-pub-date", ack);
            }
        } catch (Throwable e) {
            err = e.getMessage();
        }
        return this;
    }

    public String getFileName() {
        return fileName;
    }

    public String getProductionTaskId() {
        return taskId;
    }

    public String getProductionTaskDescription() {
        return productionTaskDescription;
    }

    private void setProductionTaskDescription(String productionTaskDescription) {
        this.productionTaskDescription = productionTaskDescription;

        if (DELIVER_TO_WILEY.equals(productionTaskDescription)) {
            deliver = Boolean.TRUE;

        } else if (PUBLISH_ON_CLIB.equals(productionTaskDescription)) {
            deliver = Boolean.FALSE;
        }
    }

    public String getTargetOnlinePubDateSrc() {
        return targetOnlinePubDateSrc;
    }

    private void setTargetOnlinePubDate(String targetOnlinePubDateSrc, boolean ack) throws CmsException {
        if (targetOnlinePubDateSrc == null) {
            return;
        }
        this.targetOnlinePubDateSrc = ack ? targetOnlinePubDateSrc : PublishHelper.checkSPD(getDbTypeByArticleType(),
                targetOnlinePubDateSrc.trim(), LocalDate.now(), true);
    }

    public String getActualOnlinePubDate() {
        return actualOnlinePubDate;
    }

    private void setActualOnlinePubDate(String actualOnlinePubDate) {
        this.actualOnlinePubDate = actualOnlinePubDate;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getCdNumber() {
        return cdNumber;
    }

    public void setCdNumber(String cdNumber) {
        this.cdNumber = cdNumber;
    }

    public int getPub() {
        return pub;
    }

    public void setPub(int pub) {
        this.pub = pub;
    }

    public String getManuscriptNumber() {
        return manuscriptNumber;
    }

    public void setManuscriptNumber(String manuscriptNumber) {
        this.manuscriptNumber = manuscriptNumber;
    }

    public String getArticleType() {
        return articleType;
    }

    public void setArticleType(String articleType) {
        this.articleType = articleType;
    }

    public BaseType getDbTypeByArticleType() {
        String dbName = articleType == null ? null : ARTICLE_TYPE_DB_MAP.get().getStrSetting(articleType);
        return dbName == null ? BaseType.getCDSR().get() : BaseType.find(dbName).get();
    }

    public boolean isPublish() {
        return deliver != null && !deliver;
    }

    public boolean isDeliver() {
        return deliver != null && deliver;
    }

    public boolean isScheduled() {
        return isDeliver() && targetOnlinePubDateSrc != null;
    }

    public boolean isCancel() {
        return cancel;
    }

    public boolean isUnknown() {
        return deliver == null && !cancel;
    }

    void checkSPDCancel(boolean spdIssue) {
        if (spdIssue && isUnknown()) {
            cancel = true;
        }
    }

    public String error() {
        return err != null ? err : (isUnknown() ? String.format(
            "%s was not set or unknown for 'publish' and 'deliver' folders: %s", PRODUCTION_TASK_DESCRIPTION,
                productionTaskDescription) : null);
    }

    private void setImportElementValue(Document doc, String xPath, String tagName) throws Exception {
        setImportElementValue(doc, xPath, tagName, false);
    }

    private void setImportElementValue(Document doc, String xPath, String tagName, boolean ack) throws Exception {
        String value = ack ? JDOMHelper.getElementValue(doc, xPath) : JatsHelper.getMandatoryValue(doc, xPath, tagName);
        if ((value == null || value.isEmpty()) && xPath.equals(PRODUCTION_TASK_DESCRIPTION_PATH)) {
            throw new CmsException(String.format("no value found by tag '%s' in '%s'", tagName, getFileName()));
        }
        switch (xPath) {
            case PRODUCTION_TASK_DESCRIPTION_PATH:
                setProductionTaskDescription(value);
                break;
            case ACTUAL_ONLINE_PUB_DATE_PATH:
                setActualOnlinePubDate(value);
                break;
            case TARGET_ONLINE_PUB_DATE_PATH:
                setTargetOnlinePubDate(value, ack);
                break;
            case MANUSCRIPT_NUMBER_PATH:
                setManuscriptNumber(value);
                break;
            case ARTICLE_TYPE_PATH:
                setArticleType(value);
                break;
            default:
                break;
        }
    }
}
