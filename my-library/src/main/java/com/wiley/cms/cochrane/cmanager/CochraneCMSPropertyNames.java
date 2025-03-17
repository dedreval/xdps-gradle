package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.kafka.IKafkaMessageProducer;
import com.wiley.cms.cochrane.cmanager.data.stats.IStatsManager;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.services.IWREventReceiver;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.AbstractBeanFactory;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.DbConstants;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Pair;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

import javax.naming.NamingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * @author <a href='mailto:sgulin@wiley.com'>Svyatoslav Gulin</a>
 * @version 22.11.2011
 */
public class CochraneCMSPropertyNames {

    public static final String CMS_COCHRANE_PREFIX = "cms.cochrane.";
    public static final String PREFIX_REPOSITORY = "cms.cochrane.prefix.repository";
    public static final String RENDERRING_SAMESERVER = "cochrane.rendering.sameserver";

    public static final String QAS_SERVICE_URL = "cochrane.qas.service.url";
    public static final String RENDERER_SERVICE_URL = "cochrane.render.service.url";
    public static final String AUTHSERVICE_APP_URL = "cochrane.authservice.app.url";
    public static final String AUTHSERVICE_SERVICE_URL = "cochrane.authservice.service.url";
    public static final String CMS_SERVICE_URL = "cochrane.cms.service.url";

    public static final String CLSYSREV = "cms.cochrane.clsysrev";
    public static final String CLABOUT = "cms.cochrane.clabout";
    public static final String CLEDITORIAL = "cms.cochrane.cleditorial";
    public static final String CLCENTRAL = "cms.cochrane.clcentral";
    public static final String CLMETHREV = "cms.cochrane.clmethrev";
    public static final String CLCMR = "cms.cochrane.clcmr";
    public static final String CLEED = "cms.cochrane.cleed";
    public static final String CCA = "cms.cochrane.cca";

    public static final String QAS_PACKAGE_SIZE = "cms.cochrane.qas.packagesize";
    public static final String QAS_STARTING_DELAY = "cms.cochrane.qas.starting.delay";
    public static final String QAS_FLUSH_PACK_SIZE = "cms.cochrane.qas.flush.pack.size";

    public static final String COMMON_UI_FILES = "cms.cochrane.common.UI.files";

    public static final String COCHRANE_RESOURCES = "cms.cochrane.resources";
    public static final String RESOURCES_DTD_PREFIX = "cms.resources.dtd.";
    public static final String RESOURCES_TERM2NUM_OUTPUT = "cms.resources.term2num.output";

    public static final String RENDER_WEB_URL = "cochrane.render.web.url";

    public static final String ACTIVITY_LOG_SYSTEM_NAME = "cms.cochrane.activity_log.system_name";

    public static final String TERM2NUM_REMOTE_DESC_YYYYXML = "cms.cochrane.term2num.remote.descYYYYxml";
    public static final String TERM2NUM_REMOTE_Q_YYYYBIN = "cms.cochrane.term2num.remote.qYYYYbin";
    public static final String TERM2NUM_REMOTE_QUAL_YYYYXML = "cms.cochrane.term2num.remote.qualYYYYxml";
    public static final String TERM2NUM_REMOTE_MTREES_YYYYBIN = "cms.cochrane.term2num.remote.mtreesYYYYbin";

    public static final String TERM2NUM_PERLSCRIPTS = "cms.resources.term2num.perlscripts";
    public static final String TERM2NUM_DOWNLOADS = "cms.resources.term2num.downloads";
    public static final String TERM2NUM_PERL = "cms.cochrane.term2num.perl";

    public static final String ACTIVITY_LOG_WRITE_RECORDS = "cms.cochrane.activity_log.write_records";

    public static final String TAG_UNIT_STATUS = "cms.cochrane.tagUnitStatus";
    public static final String TAG_UNIT_STATUS_CLCMR = "cms.cochrane.tagUnitStatus.clcmr";
    public static final String TAG_UNIT_STATUS_CLCENTRAL = "cms.cochrane.tagUnitStatus.clcentral";
    public static final String TAG_DOI = "cms.cochrane.tagDoi";
    public static final String TAG_METH = "cms.cochrane.tagMeth";
    public static final String TAG_GROUP = "cms.cochrane.tagGroup";
    public static final String TAG_CL_ISSUE = "cms.cochrane.tagClIssue";
    public static final String TAG_SUB_TITLE = "cms.cochrane.tagSubTitle";

    public static final String FIELD_SIZE_UNIT_TITLE = "cms.cochrane.field_size_unitTitle";

    public static final String SPECRND_ASSETS_PATH_PREFIX = "cms.cochrane.specrnd.assets_path.";
    public static final String SPECRND_ARTICLE_LIST_XSL = "cms.cochrane.specrnd.article_list_xsl";
    public static final String SPECRND_ARTICLE_CDSR_LIST_BY_GROUP_XSL =
            "cms.cochrane.specrnd.article_cdsr_list_by_group_xsl";
    public static final String SPECRND_ARTICLE_ABOUT_LIST_BY_GROUP_XSL =
            "cms.cochrane.specrnd.article_about_list_by_group_xsl";
    public static final String SPECRND_EMRW_TRANSFORM_XSL = "cms.cochrane.specrnd.emrw_transform_xsl";
    public static final String SPECRND_ABC_LINK_TEMPLATE = "cms.cochrane.specrnd.abc_link_template";

    public static final String SERVER_PRIVATE_KEY = "cochrane.cms.server.private.key";

    public static final String SPECRND_DEFAULT_FRAME_FILE_NAME_PREFIX = "specrnd.default.frame.file.name.";

    public static final String MESSAGE_MAPPING = "message_mapping";

    public static final String DELIMITER = ",|;";

    private static final Logger LOG = Logger.getLogger(CochraneCMSPropertyNames.class);

    private static final int DEFAULT_QUERY_SIZE_4_MESHTERM_UPDATE = 5000;
    private static final int WOLLIT_EVENT_DOI_BATCH = 500;

    private static final String COCHRANE_EAR = "CochraneCMS";
    private static final String CMS_EAR = "CMS";

    private CochraneCMSPropertyNames() {
    }

    public static String getCentralDbName() {
        return CochraneCMSProperties.getProperty(CLCENTRAL);
    }

    public static String getCDSRDbName() {
        return CochraneCMSProperties.getProperty(CLSYSREV);
    }

    public static String getAboutDbName() {
        return CochraneCMSProperties.getProperty(CLABOUT);
    }

    public static String getCmrDbName() {
        return CochraneCMSProperties.getProperty(CLCMR);
    }

    public static String getEditorialDbName() {
        return CochraneCMSProperties.getProperty(CLEDITORIAL);
    }

    public static String getEedDbName() {
        return CochraneCMSProperties.getProperty(CLEED);
    }

    public static String getCcaDbName() {
        return CochraneCMSProperties.getProperty(CCA);
    }

    public static String getMethrevDbName() {
        return CochraneCMSProperties.getProperty(CLMETHREV);
    }

    public static String getHtaDbName() {
        return CochraneCMSProperties.getProperty("cms.cochrane.clhta");
    }

    public static String getDareDbName() {
        return CochraneCMSProperties.getProperty("cms.cochrane.cldare");
    }

    public static String getRenderingRepository() {
        return CochraneCMSProperties.getProperty("cochrane.rendering.repository");
    }

    public static String getWebPrefix() {
        boolean isSameServer = CochraneCMSProperties.getBoolProperty(RENDERRING_SAMESERVER, false);
        return isSameServer ? null : CochraneCMSProperties.getProperty(RENDER_WEB_URL) + "DataTransferer?file=";
    }

    public static String getRenderEntireDir() {
        return CochraneCMSProperties.getProperty("cms.cochrane.render.entire.dir", "");
    }

    public static String getNotAvailableMsg() {
        return CochraneCMSProperties.getProperty("not_available");
    }

    public static String getSPDNotFoundMsg() {
        return CochraneCMSProperties.getProperty("spd_not_found");
    }

    public static String getImageDirectories() {
        return CochraneCMSProperties.getProperty("cms.cochrane.images.directorys");
    }

    public static int getQaPartSize() {
        return CochraneCMSProperties.getIntProperty(QAS_PACKAGE_SIZE, 1);
    }

    public static int getEntireRerenderingPartSize() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.entire.piece.rerendering", 1);
    }

    public static int getCentralRerenderingPartSize() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.central.piece.rerendering", 1);
    }

    public static int getCentralPartSize() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.central.package.size", 1);
    }

    public static int getDbRecordBatchSize() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.db.record.batch", DbConstants.DB_PACK_SIZE);
    }

    public static String getArchieDownloadPassword() {
        return CochraneCMSProperties.getProperty("cms.cochrane.archie.download.password");
    }

    public static boolean isArchieDownload() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.archie.download", false);
    }

    public static boolean isAriesVerificationSupported() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.aries.verification", true);
    }

    public static String getArchieDownloadSchedule() {
        return CochraneCMSProperties.getProperty("cms.cochrane.archie.download.schedule");
    }

    public static String getAriesDownloadSchedule() {
        return CochraneCMSProperties.getProperty("cms.cochrane.aries.download.schedule", "0 0/5 * ? * MON-FRI");
    }

    public static boolean isAriesDownloadSFTP() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.aries.download.sftp", false);
    }

    public static String getCentralDownloadSchedule() {
        return CochraneCMSProperties.getProperty("cms.cochrane.central.download.schedule", "0 0/15 * 20-21 2-12 ?");
    }
        
    public static boolean isCentralDownload() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.central.download.sftp", false);
    }

    public static Res<Property> getCentralMonthlyScheduler() {
        return Property.get("cms.cochrane.publish.ds.start-pattern");
    }

    public static boolean canArchieAut() {
        return isArchieDownload() || isArchieDownloadTestMode() || isCochraneSftpPublicationNotificationFlowEnabled();
    }

    public static boolean isCCADownload() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.cca.download.enabled", false);
    }

    public static String getCCADownloadUrl() {
        return CochraneCMSProperties.getProperty("cms.cochrane.cca.ftp.aptara");
    }

    public static String getCCADownloadSchedule() {
        return CochraneCMSProperties.getProperty("cms.cochrane.cca.cron.schedule");
    }

    public static Pair<String[], String[]> getArchieDownloadScheduleHalfTime() {

        String halftime = CochraneCMSProperties.getProperty("cms.cochrane.archie.download.schedule.halftime", "");
        if (halftime.isEmpty()) {
            return null;
        }

        Pair<String[], String[]> pair = null;
        String[] strs = halftime.split("-");
        if (strs.length != 2) {
            LOG.warn("'cms.cochrane.archie.download.schedule.halftime' has incorrect time diapason " + halftime);
        } else {

            String del = "\\.";
            String[] start = strs[0].split(del);
            String[] end = strs[1].split(del);
            if (start.length != 2 || end.length != 2) {
                LOG.warn("'cms.cochrane.archie.download.schedule.halftime' has incorrect hours " + halftime);
            } else {
                pair = new Pair<>(start, end);
            }
        }

        return pair;
    }

    public static String getArchieDownloadService() {
        return CochraneCMSProperties.getProperty("cms.cochrane.archie.download.service.url");
    }

    public static boolean isArchieDownloadTestMode() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.archie.download.test", false);
    }

    public static boolean isCochraneSftpPublicationNotificationFlowEnabled() {
        return CochraneCMSProperties
                .getBoolProperty("cms.cochrane.cochrane.sftp.publication.notification.flow.enabled", false);
    }

    public static boolean isArchieDownloadDebugMode() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.archie.download.debug", false);
    }

    static String getCochraneDownloaderTimeZone() {
        return CochraneCMSProperties.getProperty("cms.cochrane.download.timezone", false);
    }

    public static int getArchieDownloaderLimit() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.archie.download.limit", 0);
    }

    public static int getAriesDownloaderLimit() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.aries.download.limit", 0);
    }

    public static boolean isUploadAriesOnlyLatest() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.aries.download.sftp.latest_only", true);
    }

    // only for debug
    public static boolean isUploadArchieLastMonthlyDay() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.archie.download.last.day", false);
    }

    // only for debug
    public static boolean isUploadArchieFirstMonthlyDay() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.archie.download.first.day", false);
    }

    public static boolean isImageMagicUse() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.imagemagic", false);
    }

    public static String getImageMagicPath() {
        return CochraneCMSProperties.getProperty("cms.cochrane.imagemagic.path", "");
    }

    public static String getImageMagicFilter() {
        return CochraneCMSProperties.getProperty("cms.cochrane.imagemagic.filter", "");
    }

    public static boolean isImageMagicResize() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.imagemagic.resize", false);
    }

    public static String getImageMagicCmd() {
        return CochraneCMSProperties.getProperty("cms.cochrane.imagemagic.cmd");
    }

    public static int getRevmanConversionBatchSize() {
        return Integer.valueOf(CochraneCMSProperties.getProperty("cms.cochrane.conversion.revman.batch", "0"));
    }

    @Deprecated
    public static int getRevmanConversionThreadCount() {
        return Integer.valueOf(CochraneCMSProperties.getProperty("cms.cochrane.conversion.revman.threadcount", "1"));
    }

    public static boolean isRevmanConversionDebugMode() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.conversion.revman.debug", false);
    }

    public static int getUploadCDSRIssue() {
        return Integer.parseInt(CochraneCMSProperties.getProperty("cms.cochrane.conversion.whenready.issue", "0"));
    }

    public static boolean isRevmanPdfValidation() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.conversion.revman_pdf.validation", false);
    }

    public static boolean isJatsPdfStrictValidation() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.conversion.jats_pdf.strict_validation", false);
    }

    public static int getRevmanValidation() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.conversion.revman.validation", 0);
    }

    public static boolean isRevmanStatusValidation() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.conversion.revman.validation.status", false);
    }

    public static boolean isCentralStatusValidation() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.central.validation.status", false);
    }

    public static boolean isPublishAfterUpload() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.whenready.publish", true);
    }

    public static int getPublishAfterUploadDelay() {
        return Integer.parseInt(CochraneCMSProperties.getProperty("cms.cochrane.whenready.publish.delay"));
    }

    public static Set<String> getPublishWhenReadyExcluded() {
        String value = CochraneCMSProperties.getProperty("cms.cochrane.whenready.publish.excludes", "").trim();
        return value.isEmpty() ? Collections.emptySet() : new HashSet<>(Arrays.asList(value.split(DELIMITER)));
    }

    public static String getReconvertedRevmanDir() {
        return CochraneCMSProperties.getProperty("cms.cochrane.conversion.revman.reconverted.dir", "tmp/");
    }

    public static int getPriority(String libName) {
        return Integer.parseInt(CochraneCMSProperties.getProperty(CMS_COCHRANE_PREFIX + libName + ".priority"));
    }

    public static String getMeshtermRecordUpdateDbs() {
        return CochraneCMSProperties.getProperty("cms.cochrane.meshterm.record.update.dbs", "");
    }

    public static String getLiteratumSourceSystemFilterWol() {
        return CochraneCMSProperties.getProperty("cms.cochrane.literatum.events.sourceSystem_filter.wol", "WOLLIT");
    }

    public static String getLiteratumSourceSystemFilterSemantico() {
        return CochraneCMSProperties.getProperty("cms.cochrane.literatum.events.sourceSystem_filter.semantico",
                "COCHRANE_LIBRARY");
    }

    public static String getLiteratumEventOnLoadFilter() {
        return CochraneCMSProperties.getProperty("cms.cochrane.literatum.events.eventType_onload_filter",
                "LOAD_TO_PUBLISH");
    }

    public static String getLiteratumEventOnlineFilter() {
        return CochraneCMSProperties.getProperty("cms.cochrane.literatum.events.eventType_online_filter",
                "CONTENT_ONLINE");
    }

    public static String getLiteratumEventOfflineFilter() {
        return CochraneCMSProperties.getProperty("cms.cochrane.literatum.events.eventType_offline_filter",
                "CONTENT_OFFLINE");
    }

    public static String[] getLiteratumErrorFilterWol() {
        return CochraneCMSProperties.getProperty("cms.cochrane.literatum.events.error_filter.wol", "").split(DELIMITER);
    }

    public static String[] getLiteratumErrorFilterSemantico() {
        return CochraneCMSProperties.getProperty(
                "cms.cochrane.literatum.events.error_filter.semantico", "").split(DELIMITER);
    }

    public static boolean isLiteratumIntegrationEnabled() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.literatum.events.checker.enabled", false);
    }

    public static boolean isPublishToSemanticoAfterLiteratum() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.publish.semantico-after-literatum", true);
    }

    public static int getMeshtermRecordUpdateBatchSize() {
        return Integer.parseInt(
                CochraneCMSProperties.getProperty("cms.cochrane.meshterm.record.update.batch.size", "10"));
    }

    public static int getMeshtermRecordUpdateQueryMaxSize() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.meshterm.record.update.query.max.size",
                DEFAULT_QUERY_SIZE_4_MESHTERM_UPDATE);
    }

    public static boolean getMeshUpdateMl3gToMl3gConversionEnabled() {
        return CochraneCMSProperties.getBoolProperty(
                "cms.cochrane.meshterm.record.update.ml3g_to_ml3g.conversion.enabled", true);
    }

    public static String getMeshtermRecordUpdateFileName() {
        return CochraneCMSProperties.getProperty("cms.cochrane.meshterm.record.update.file_name",
                "meshterm_outdated_records");
    }

    public static boolean useMeshtermUpdateCalendar() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.meshterm.record.update.calendar", false);
    }

    public static long getPreviewServiceResponseTimeout() {
        return Long.parseLong(
                CochraneCMSProperties.getProperty("cms.cochrane.preview_service.response.timeout", "300000"));
    }

    public static String getPreviewServiceResponseTimeoutExceededMsg(long time) {
        Map<String, String> params = new HashMap<>();
        params.put(MessageSender.MSG_PARAM_REPORT, String.format("%tM min %tS sec", time, time));
        return CochraneCMSProperties.getProperty("preview_service.response_timeout_exceeded", params);
    }

    public static String getPreviewServiceProcessingFailedMsg(String error) {
        String msg = (error == null || error.length() == 0) ? getNotAvailableMsg() : error;
        Map<String, String> params = new HashMap<>();
        params.put(MessageSender.MSG_PARAM_REPORT, msg);
        return CochraneCMSProperties.getProperty("preview_service.processing_failed", params);
    }

    public static String getLogCommentLoadingSomeErrorsMsg() {
        return CochraneCMSProperties.getProperty("log.comment.loading.someErrors");
    }

    public static String getLogCommentLoadingSuccessfulMsg() {
        return CochraneCMSProperties.getProperty("log.comment.loading.successful");
    }

    public static String getCentralDownloadFailedMsg(Map<String, String> params) {
        return CochraneCMSProperties.getProperty("central_download_failed", params);
    }

    public static String getWebLoadingUrl() {
        return CochraneCMSProperties.getProperty("cochrane.cms.web.loading.url");
    }

    public static String getExportCompletedSuccessfully(Map<String, String> params) {
        return CochraneCMSProperties.getProperty("export_process_successful_completed", params);
    }

    public static String getExportCompletedWithErrors(Map<String, String> params) {
        return CochraneCMSProperties.getProperty("export_process_completed_with_errors", params);
    }

    public static String getExportEntireCompletedSuccessfully(Map<String, String> params) {
        return CochraneCMSProperties.getProperty("entire_export_process_successful_completed", params);
    }

    public static String getExportEntireCompletedWithErrors(Map<String, String> params) {
        return CochraneCMSProperties.getProperty("entire_export_process_completed_with_errors", params);
    }

    public static String getActivityLogSystemName() {
        return CochraneCMSProperties.getProperty(ACTIVITY_LOG_SYSTEM_NAME);
    }

    public static boolean isKafkaProducerEnabled() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.kafka.producer.enabled", false);
    }

    public static boolean isKafkaProducerTestMode() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.kafka.producer.test", false);
    }

    public static int getKafkaProducerResendMode() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.kafka.producer.resend-mode",
                IKafkaMessageProducer.MODE_RESEND);
    }

    public static boolean isKafkaProducerAsyncMode() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.kafka.producer.async", true);
    }

    static boolean isKafkaProducerAutResendMode() {
        return getKafkaProducerResendMode() == IKafkaMessageProducer.MODE_AUT_RESEND;
    }

    public static int getKafkaProducerResendBatch() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.kafka.producer.resend-batch", 2);
    }

    public static String getKafkaBootstrapServers() {
        return CochraneCMSProperties.getProperty("cms.cochrane.kafka.producer.bootstrap.servers");
    }

    public static String getKafkaTopic() {
        return CochraneCMSProperties.getProperty("cms.cochrane.kafka.producer.topic");
    }

    public static int getMaxDisplaySizeSelective() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.common.UI.displaySelective.size", 0);
    }

    public static String getCochraneResourcesRoot() {
        return CochraneCMSProperties.getProperty(COCHRANE_RESOURCES);
    }

    public static boolean isArchieImitateError() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.archie.download.imitateError", false);
    }

    public static boolean isConversionImitateError() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.conversion.imitateError", false);
    }

    public static int getCentralCCHArticlesInXmlSize() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.publish.cch.clcentral.articlesInXml", 1);
    }

    public static int getCentralCCHArticlesBatchSize() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.publish.cch.clcentral.pieceSize", 1);
    }

    public static int checkCentralProcessing() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.central.check_processing_seconds", 0);
    }

    public static String getNotificationAppUrl() {
        return CochraneCMSProperties.getProperty("cms.cochrane.notification.app_url");
    }

    public static String getNotificationServiceUrl() {
        return CochraneCMSProperties.getProperty("cms.cochrane.notification.service_url");
    }

    static int getNotificationMaxRedeliveryAttempts() {
        return Integer.parseInt(
                CochraneCMSProperties.getProperty("cms.cochrane.notification.max_redelivery_attempts", "3"));
    }

    static Long getNotificationRedeliveryDelay() {
        return Long.parseLong(CochraneCMSProperties.getProperty("cms.cochrane.notification.redelivery_delay", "5000"));
    }

    static int getNotificationMaxLength() {
        return Integer.parseInt(CochraneCMSProperties.getProperty("cms.cochrane.notification.maxlength", "131072"));
    }

    static String getNotificationPrefix() {
        return CochraneCMSProperties.getProperty("cms.cochrane.notification.prefix", "");
    }

    public static String getSystemUser() {
        return CochraneCMSProperties.getProperty(ACTIVITY_LOG_SYSTEM_NAME);
    }

    public static <I> I lookup(String beanImpl, Class<I> beanInterface) throws NamingException {
        return AbstractBeanFactory.lookup(COCHRANE_EAR, COCHRANE_EAR, beanImpl, beanInterface);
    }

    public static String buildLookupName(String beanImpl, Class beanInterface) {
        return AbstractBeanFactory.buildGlobalJNDIName(COCHRANE_EAR, COCHRANE_EAR, beanImpl, beanInterface);
    }

    public static IRecordCache lookupRecordCache() throws NamingException {
        return lookup("RecordCache", IRecordCache.class);
    }

    public static IFlowLogger lookupFlowLogger() throws NamingException {
        return lookup("FlowLogger", IFlowLogger.class);
    }

    public static IStatsManager lookupStatsManager() throws NamingException {
        return lookup("StatsManager", IStatsManager.class);
    }

    public static CochraneCMSPropertiesMBean lookupCochraneCMSProperties() throws NamingException {
        return lookup("CochraneCMSProperties", CochraneCMSPropertiesMBean.class);
    }

    public static IRecordCache getRecordCacheOrNull() {
        try {
            return lookupRecordCache();
        } catch (NamingException ne) {
            LOG.error(ne.getMessage(), ne);
        }
        return null;
    }

    public static IWREventReceiver lookupWRReceiver() throws NamingException {
        return lookup("WREventReceiver", IWREventReceiver.class);
    }

    public static IWREventReceiver getWRReceiverOrNull() {
        try {
            return lookupWRReceiver();
        } catch (NamingException ne) {
            LOG.error(ne.getMessage(), ne);
        }
        return null;
    }

    public static boolean isLiteratumEventPublishTestMode() {
        return getLiteratumPublishTestMode() > 0;
    }

    public static boolean isLiteratumPublishTestModeDev() {
        return getLiteratumPublishTestMode() == 2;
    }

    public static int getLiteratumPublishTestMode() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.literatum.events.test", 0);
    }

    public static int getLiteratumPublishTestModeForSPD() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.literatum.events.test.spd", 0);
    }
    
    public static int getWOLLiteratumEventsImitateError() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.literatum.events.wol.imitateError", 0);
    }

    public static int getHWLiteratumEventsImitateError() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.literatum.events.semantico.imitateError", 0);
    }

    public static boolean isSemanticoPublishTestMode() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.publish.semantico.test", true);
    }

    public static boolean isSemanticoApiCallTestMode() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.publish.semantico.api.test",
                isSemanticoPublishTestMode());
    }

    public static boolean isWollitPublishTestMode() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.publish.wollit.test", true);
    }

    public static int getWollitEventBatch() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.publish.wollit.json_batch", WOLLIT_EVENT_DOI_BATCH);
    }

    public static boolean isSemanticoDebugMode() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.publish.semantico.debug", true);
    }

    public static int getHWClientImitateError() {
        return CochraneCMSProperties.getIntProperty("cms.cochrane.publish.semantico.imitateError", 0);
    }

    public static boolean addPreviousVersionForSemantico() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.publish.semantico.addPrevious", true);
    }

    public static boolean addPreviousVersionForDs() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.publish.ds.addPrevious", true);
    }

    public static boolean isCheckRevmanMissed() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.publish.check_revman_missed", false);
    }

    public static Res<Property> getAwaitingPublicationInterval() {
        return Property.get("cms.cochrane.when_ready.awaiting_timeout", "0");
    }

    public static boolean isAmongValues(String targetValue, String... possibleValues) {
        return Arrays.stream(possibleValues).anyMatch(possibleValue -> possibleValue.equalsIgnoreCase(targetValue));
    }

    public static int getAmountOfLastActualMonths() {
        int ret = CochraneCMSProperties.getIntProperty("cms.cochrane.last_actual_months_amount",
                Constants.LAST_ACTUAL_MONTH_AMOUNT);
        return ret < Constants.LAST_ACTUAL_MONTH_AMOUNT ? Constants.LAST_ACTUAL_MONTH_AMOUNT : ret;
    }

    public static boolean isExternalUserValidationUsed() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.external.user_validation.used", false);
    }

    public static boolean checkEmptyPublicationDateInWML3G4DS() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.publish.ds.check-empty-dates-content", true);
    }

    public static boolean checkEmptyPublicationDateInWML3G4HW() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.publish.hw.check-empty-dates-content", true);
    }

    public static String getStatsReportPath(int fullIssueNumber) {
        return CochraneCMSProperties.getProperty(PREFIX_REPOSITORY) + "/temp/Stats_" + fullIssueNumber + Extensions.XLS;
    }

    public static Res<Property> getActualPublicationDateValidated() {
        return Property.get("cms.cochrane.support-publication-dates.hw.validation", Boolean.FALSE.toString());
    }

    public static Res<Property> getActualPublicationDateThresholdPast() {
        return Property.get("cms.cochrane.support-publication-dates.hw.validation.threshold-past");
    }

    public static Res<Property> getActualPublicationDateThresholdFuture() {
        return Property.get("cms.cochrane.support-publication-dates.hw.validation.threshold-future");
    }

    public static Res<Property> getSelfCitationCheckForAmended() {
        return Property.get("cms.cochrane.support-publication-dates.cochrane.check.amended.self-citation");
    }

    public static Res<Property> getFirstOnlineCheck() {
        return Property.get("cms.cochrane.support-publication-dates.cochrane.check.first-online");
    }

    public static Res<Property> getFinalOnlineCheck() {
        return Property.get("cms.cochrane.support-publication-dates.cochrane.check.final-online");
    }

    public static Res<Property> getSnowFlakeSwitch() {
        return Property.get("cms.cochrane.snowflake", "false");
    }

    public static int getUnitTitleSizeLimit() {
        return CochraneCMSProperties.getIntProperty(FIELD_SIZE_UNIT_TITLE, DbEntity.STRING_MEDIUM_TEXT_LENGTH);
    }

    public static String getUnitTitleTruncatedTag() {
        return CochraneCMSProperties.getProperty("cms.cochrane.field_size_unitTitle_truncated_tag", "<...>");
    }

    public static boolean isPackageValidationActive() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.aries.package.validation.enabled", false);
    }

    public static String getCochraneASNotificationSender() {
        return CochraneCMSProperties.getProperty("cms.cochrane.notification.as.sender", false);
    }

    public static String getCochraneASNotificationEndpoint() {
        return CochraneCMSProperties.getProperty("cms.cochrane.notification.as.endpoint", false);
    }

    public static String getCochraneASNotificationEmail() {
        return CochraneCMSProperties.getProperty("cms.cochrane.notification.as.cochrane.email", false);
    }
}