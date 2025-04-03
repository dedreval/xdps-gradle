package com.wiley.cms.cochrane.cmanager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.notification.SuspendNotificationEntity;
import com.wiley.cms.notification.SuspendNotificationSender;
import com.wiley.cms.notification.service.AsNotification;
import com.wiley.cms.notification.service.INotificationWebService;
import com.wiley.cms.notification.service.Levels;
import com.wiley.cms.notification.service.NewNotification;
import com.wiley.cms.notification.service.NotificationResult;
import com.wiley.cms.notification.service.User;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.XmlUtils;
import com.wiley.tes.util.res.Property;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import javax.xml.datatype.DatatypeFactory;
import java.io.IOException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.wiley.cms.cochrane.cmanager.contentworker.EntryValidator.FILENAME_MISMATCH_PREFIX;
import static com.wiley.cms.cochrane.cmanager.contentworker.EntryValidator.ZERO_LENGTH_PREFIX;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

public final class MessageSender {
    public static final String MSG_TITLE_ERRORS = "errors";
    public static final String MSG_TITLE_WARNINGS = "warnings";
    public static final String MSG_TITLE_RESUPPLY_REQUEST = "resupply_request";
    public static final String MSG_TITLE_QAS_JOB_CREATED = "qas_job_created";
    public static final String MSG_TITLE_RENDER_JOB_CREATED = "render_job_created";
    public static final String MSG_TITLE_RENDER_REPORT = "rendering_report";
    public static final String MSG_TITLE_QAS_JOB_FAILED = "qas_job_failed";
    public static final String MSG_TITLE_QAS_JOB_SUCCESSFUL = "qas_job_successful";
    public static final String MSG_TITLE_CONVERSION_STARTED = "conversion_started";
    public static final String MSG_TITLE_CONVERSION_FAILED = "conversion_failed";
    public static final String MSG_TITLE_DB_REVMAN_CONVERSION_FAILED = "db_revman_conversion_failed";
    public static final String MSG_TITLE_ENTIRE_REVMAN_CONVERSION_FAILED = "entire_revman_conversion_failed";
    public static final String MSG_TITLE_ENTIRE_RENDERING_COMPLETED = "entire_rendering_completed";
    public static final String MSG_TITLE_RENDERING_FAILED = "rendering_failed";
    public static final String MSG_TITLE_CONVERSION_FINISHED = "data_converted";
    public static final String MSG_TITLE_DATA_EXTRACTED = "data_extracted";
    public static final String MSG_TITLE_LOAD_STARTED = "loading_started";
    public static final String MSG_TITLE_LOAD_FAILED = "loading_failed";
    public static final String MSG_TITLE_LOAD_FINISHED = "loading_finished";
    public static final String MSG_TITLE_CENTRAL_WARNINGS = "warnings_central";
    public static final String MSG_TITLE_MESHTERM_WARNINGS = "warnings_meshterm";
    public static final String MSG_TITLE_TA_LOAD_FAILED = "qa_abstracts_failed";
    public static final String MSG_TITLE_TA_LOAD_WARNINGS = "qa_abstracts_warning";
    public static final String MSG_TITLE_GENERATION_WARNINGS = "generation_warnings";
    public static final String MSG_TITLE_GENERATION_ERRORS = "generation_errors";
    public static final String MSG_TITLE_GENERATION_INFO = "generation_info";
    public static final String MSG_TITLE_CCA_REPORT_GENERATION_SUCCESS = "cca_report_generation_successful";
    public static final String MSG_TITLE_CCA_REPORT_GENERATION_FAILED = "cca_report_generation_failed";
    public static final String MSG_TITLE_CCA_REPORT_GENERATION_EXCEPTION = "cca_report_generation_exception";
    public static final String MSG_TITLE_CCA_VALIDATION_FAILED = "cca_validation_failed";
    public static final String MSG_TITLE_RECORD_VALIDATION_FAILED = "record_validation_failed";
    public static final String MSG_TITLE_WR_SEND_NOTIFICATION_WARN = "when_ready_notification_warned";
    public static final String MSG_TITLE_WR_SEND_NOTIFICATION_FAIL = "when_ready_notification_failed";
    public static final String MSG_TITLE_WR_DOWNLOAD_FAIL = "when_ready_download_failed";
    public static final String MSG_TITLE_PREVIEW_SERVICE_EXCEPTION = "preview_service_exception";
    public static final String MSG_TITLE_SENDING_SUCCESSFUL = "sending_successful";
    public static final String MSG_TITLE_SENDING_FAILED = "sending_failed";
    public static final String MSG_TITLE_SENDING_STAGE_QA_STARTED = "sending_to_stage_qa_started";
    public static final String MSG_TITLE_UNPACK_SUCCESS = "unpacking_successful_notification";
    public static final String MSG_TITLE_SYSTEM_INFO = "system_information";
    public static final String MSG_TITLE_SYSTEM_WARN = "system_warning";
    public static final String MSG_TITLE_SYSTEM_ERROR = "system_error";
    public static final String MSG_TITLE_PUBLISH_EVENT_SUCCESS = "publish_event_received";
    public static final String MSG_TITLE_PUBLISH_EVENT_ERROR = "publish_event_error";
    public static final String MSG_TITLE_PUBLISH_EVENT_WARN = "publish_event_warning";
    public static final String MSG_TITLE_PUBLISH_TIMEOUT = "when_ready_publishing_timeout";
    public static final String EXPORT_COMPLETED_ID = "export_completed";
    public static final String EXPORT_3G_COMPLETED_ID = "export_3g_completed";
    public static final String MSG_PARAM_PLAN = "plan";
    public static final String MSG_PARAM_LIST = "list";
    public static final String MSG_PARAM_MESSAGE = "message";
    public static final String MSG_PARAM_DELIVERY_FILE = "deliveryFileName";
    public static final String MSG_PARAM_JOB_ID = "jobId";
    public static final String MSG_PARAM_REPORT = "report";
    public static final String MSG_PARAM_REQUEST = "request";
    public static final String MSG_PARAM_DATABASE = "database";
    public static final String MSG_PARAM_LOCATION = "location";
    public static final String MSG_PARAM_ISSUE = "issue";
    public static final String MSG_PARAM_LINK = "link";
    public static final String MSG_PARAM_SERVICE = "service";
    public static final String MSG_PARAM_ERROR = "error";
    public static final String MSG_PARAM_OPERATION = "operation";
    public static final String MSG_PARAM_RECORD_ID = "record_id";
    public static final String MSG_PARAM_RECORD_TITLE = "record_title";
    public static final String MSG_PARAM_RECORD_MANUSCRIPT_NUMBER = "manuscript_number";
    public static final String MSG_PARAM_RECORD_NAMES = "record_names";
    public static final String MSG_RECORD_PROCESSED = "is being processed and cannot be updated right now";
    public static final String MSG_RM_CONVERTED = "RevMan->WML21 converted: %d, errors: %d - %s";

    public static final String MSG_PARAM_CODE = "code";
    public static final String DELIMITER = " - ";
    public static final String SEND_TO_COCHRANE_ALWAYS = "send_to_cochrane_always";
    public static final String WML_12_WML_3_G_CONVERSION_BODY = "wml12_wml3g_conversion.body";
    private static final Logger LOG = Logger.getLogger(MessageSender.class);
    private static final Logger LOG_MN = Logger.getLogger("MailNotification");
    private static final int MAX_LENGTH_MSG = CochraneCMSPropertyNames.getNotificationMaxLength();
    private static final String FAILED_2_SEND_NOTIF_MSG = "Failed to send notification; template key: ";

    private static final String FAILED_2_CREATE_NOTIF_MSG = "Failed to create notification ";
    private static final String MESSAGE_FOR_COCHRANE_SUPPORT =
            "The source file didn't pass validation, please check it with Cochrane";
    private static final String NEW_LINE_DELIMITER = "\n\n";

    private static final String PROPERTY_PATH = System.getProperty("jboss.server.config.url");
    private static final String NAME = "name";
    private static final String TODO = "todo";
    private static final String NOTIFICATIONS_SEND = "notifications/send";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final int INT_4 = 4;
    private static final String COCHRANE = "Cochrane";
    private static final String LOCALHOST = "localhost";
    private static String notificationsFile = PROPERTY_PATH + "cochrane-props/cms/cochrane.cms.notifications.xml";
    private static String notificationLevelUsersConfigFile = PROPERTY_PATH + "cochrane-props/cms/res/notifications.xml";
    private static final String NOT_AVAILABLE = "N/A";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static Map<String, NewNotification> notifTemplates;
    private static Map<String, List<User>> notificationLevelUsersConfig;
    private static int maxAttempts;
    private static long redeliveryDelay;
    private static String prefix;

    private static final String REGEX = "%.*%";

    private static volatile boolean enabled = true;
    private static int toLog = 1;

    private MessageSender() {
    }

    public static void sendWml3gConversion(String qualifier, String errs) {
        String msgId;
        String msgBody;

        Map<String, String> params = new HashMap<>();
        params.put(MSG_PARAM_DATABASE, qualifier);
        msgId = getMsgIdType(errs, params);
        msgBody = CochraneCMSProperties.getProperty(WML_12_WML_3_G_CONVERSION_BODY, params);

        params.clear();
        params.put(MSG_PARAM_REPORT, msgBody);
        sendMessage(msgId, params);
    }

    public static void sendWml3gConversion(String packageName, String qualifier, String errs) {
        String msgId;
        String msgBody;

        Map<String, String> params = new HashMap<>();
        params.put(MSG_PARAM_DATABASE, qualifier);
        params.put(MSG_PARAM_DELIVERY_FILE, packageName);
        msgId = getMsgIdType(errs, params);
        msgBody = CochraneCMSProperties.getProperty(WML_12_WML_3_G_CONVERSION_BODY, params);

        params.remove(MSG_PARAM_ERROR);
        String identifiers = getCDnumbersFromMessageByPattern(msgBody, params);
        params.put(MSG_PARAM_RECORD_ID, identifiers);
        params.put(MSG_PARAM_REPORT, msgBody);
        sendMessage(msgId, params);
    }

    private static String getMsgIdType(String errs, Map<String, String> params) {
        String msgId;
        if (StringUtils.isEmpty(errs)) {
            params.put(MSG_PARAM_ERROR, "");

            msgId = "3g_conversion_completed";
        } else {
            params.put(MSG_PARAM_ERROR, errs);

            msgId = "3g_conversion_failed";
        }
        return msgId;
    }

    public static void sendOpenAccessed(int year, int number, String message) {
        sendSomethingForIssue(null, year, number, message, "open_access_review_loaded");
    }

    public static void sendNihMandated(int year, int number, String message) {
        sendSomethingForIssue(null, year, number, message, "nih_review_loaded");
    }

    public static void sendCL(int year, int number, String message) {
        sendSomethingForIssue(null, year, number, message, "cl_review_loaded");
    }

    public static void sendOnCentralDownloadFailed(int year, int number, String message) {
        sendSomethingForIssue(null, year, number, message, "central_download_failed");
    }

    public static void sendForDatabase(String database, String title, String message) {
        sendForDatabase(database, title, null, message);
    }

    public static void sendForDatabase(String database, String title, String location, String message) {
        Map<String, String> map = new HashMap<>();
        map.put(MSG_PARAM_DATABASE, database);
        if (message != null) {
            map.put(MSG_PARAM_MESSAGE, message);
        }
        if (location != null) {
            map.put(MSG_PARAM_LOCATION, location);
        }
        String identifiers = getCDnumbersFromMessageByPattern(message, map);
        map.put(MSG_PARAM_RECORD_ID, identifiers);
        sendMessage(title, map);
    }

    public static void sendSomethingForIssue(String database, int year, int number, String message, String title) {
        sendSomethingForIssue(database, number + "-" + year, message, title, null);
    }

    public static void sendSomethingForIssue(String database, String issue, String message, String title, String plan) {
        Map<String, String> map = new HashMap<>();
        if (database != null) {
            map.put(MSG_PARAM_DATABASE, database);
        }
        map.put(MSG_PARAM_ISSUE, issue);
        map.put(MSG_PARAM_MESSAGE, message);
        if (plan != null) {
            map.put(MSG_PARAM_PLAN, plan);
        }
        sendMessage(title, map);
    }

    public static void sendStartedRender(int jobId, String plan, String message) {
        Map<String, String> map = initParamsRender(jobId, plan);
        map.put(MSG_PARAM_MESSAGE, message);
        sendMessage(MSG_TITLE_RENDER_JOB_CREATED, map);
    }

    public static void sendRenderReport(int jobId, String packageFileName, String report) {
        Map<String, String> map = initDeliveryPackageParams(jobId, packageFileName);
        map.put(MSG_PARAM_REPORT, report);
        sendMessage(MSG_TITLE_RENDER_REPORT, map);
    }

    public static void sendStartedQAS(int jobId, String packageFileName) {
        Map<String, String> map = initDeliveryPackageParams(jobId, packageFileName);
        sendMessage(MSG_TITLE_QAS_JOB_CREATED, map);
    }

    public static void sendSuccessfulQAS(int jobId, String packageFileName) {
        Map<String, String> map = initDeliveryPackageParams(jobId, packageFileName);
        sendMessage(MSG_TITLE_QAS_JOB_SUCCESSFUL, map);
    }

    public static void sendFailedQAS(int jobId, String packageFileName, String dbName, String report) {
        Map<String, String> map = initDeliveryPackageParams(jobId, packageFileName);
        map.put(MSG_PARAM_DATABASE, dbName);
        map.put(MSG_PARAM_REPORT, report);

        String identifiers = getCDnumbersFromMessageByPattern(report, map);
        map.put(MSG_PARAM_RECORD_ID, identifiers);
        sendMessage(MSG_TITLE_QAS_JOB_FAILED, map);
    }

    public static void sendFailed(int jobId, String packageFileName, String message, boolean isError,
        String dbName) {

        Map<String, String> map = initDeliveryPackageParams(jobId, packageFileName);
        String title = isError ? MSG_TITLE_ERRORS : (MSG_TITLE_WARNINGS);

        map.put(MSG_PARAM_DATABASE, CmsUtils.getOrDefault(dbName));
        map.put(MSG_PARAM_DELIVERY_FILE, CmsUtils.getOrDefault(packageFileName));
        String identifiers = getCDnumbersFromMessageByPattern(message, map);
        map.put(MSG_PARAM_RECORD_ID, identifiers);

        map.put(MSG_PARAM_REQUEST, message);
        sendMessage(title, map);
    }

    public static void sendResupply(int jobId, String packageFileName, String message) {

        Map<String, String> map = initDeliveryPackageParams(jobId, packageFileName);
        map.put(MSG_PARAM_REQUEST, message);
        sendMessage(MSG_TITLE_RESUPPLY_REQUEST, map);
    }

    public static void sendReport(String title, String report) {

        Map<String, String> map = new HashMap<>();
        map.put(MSG_PARAM_REPORT, report);
        sendMessage(title, map);
    }

    public static void sendReport(String title, String database, String report) {

        Map<String, String> map = new HashMap<>();
        map.put(MSG_PARAM_DATABASE, database);
        map.put(MSG_PARAM_REPORT, report);
        String identifiers = getCDnumbersFromMessageByPattern(report, map);
        map.put(MSG_PARAM_RECORD_ID, identifiers);
        sendMessage(title, map);
    }

    public static void sendReport(String title, String database, String report, String cdNames) {

        Map<String, String> map = new HashMap<>();
        map.put(MSG_PARAM_DATABASE, database);
        map.put(MSG_PARAM_REPORT, report);
        map.put(MSG_PARAM_RECORD_ID, CmsUtils.getOrDefault(cdNames));
        sendMessage(title, map);
    }

    public static void sendReport(String title, String database, String report, String packageName, String cdNumbers) {
        Map<String, String> map = new HashMap<>();
        map.put(MSG_PARAM_DATABASE, database);
        map.put(MSG_PARAM_REPORT, report);
        map.put(MSG_PARAM_DELIVERY_FILE, packageName);
        map.put(MSG_PARAM_RECORD_ID, CmsUtils.getOrDefault(cdNumbers));
        sendMessage(title, map);
    }

    public static void sendReport(String title, String database, String report, boolean testMode) {
        sendReport(title, database, appendTestMode(report, testMode));
    }

    private static String appendTestMode(String body, boolean testMode) {
        return testMode ? body + "\n\nTEST mode enabled!\n" : body;
    }

    public static void sendGenerationPackageWarningTa(String database, String packageFileName, String message) {
        sendGenerationPackageWarning(database, packageFileName, message, "cch.generation.translation");
    }

    public static void sendGenerationPackageWarning(String database, String packageFileName, String message,
                                                    String property) {
        Map<String, String> params = new HashMap<>();
        params.put(MSG_PARAM_DELIVERY_FILE, packageFileName);
        params.put(MSG_PARAM_REPORT, message);
        String report = CochraneCMSProperties.getProperty(property, params);
        LOG.warn(report);
        sendReport(MSG_TITLE_GENERATION_WARNINGS, database, report);
    }

    private static Map<String, String> initDeliveryPackageParams(int jobId, String packageFileName) {
        Map<String, String> map = new HashMap<>();
        map.put(MSG_PARAM_DELIVERY_FILE, packageFileName);
        map.put(MSG_PARAM_JOB_ID, Integer.toString(jobId));
        return map;
    }

    private static Map<String, String> initParamsRender(int jobId, String plan) {
        Map<String, String> map = new HashMap<>();
        map.put(MSG_PARAM_PLAN, plan);
        map.put(MSG_PARAM_JOB_ID, Integer.toString(jobId));
        return map;
    }

    public static void sendFinishedConversionPackageMessage(String packageFileName, String msg) {
        Map<String, String> notifyMessage = new HashMap<>();
        notifyMessage.put(MSG_PARAM_DELIVERY_FILE, packageFileName);
        sendMessage(MSG_TITLE_CONVERSION_FINISHED, notifyMessage);

        if (msg != null && !msg.isEmpty()) {
            notifyMessage.put(MSG_PARAM_LIST, msg);
            sendMessage(MSG_TITLE_CONVERSION_FAILED, notifyMessage);
        }
    }

    public static void sendFailedConversionPackageMessage(String packageFileName, String shortMsg, String msg) {
        sendFailedPackageMessage(packageFileName, shortMsg, msg, MSG_TITLE_CONVERSION_FAILED, null);
    }

    public static void sendFailedLoadPackageMessage(String packageFileName, String shortMsg) {
        sendFailedPackageMessage(packageFileName, shortMsg, null, MSG_TITLE_LOAD_FAILED, null);
    }

    public static void sendFailedLoadPackageMessage(String packageFileName,
                                                    String shortMsg, String sendToCochraneAlways) {
        sendFailedPackageMessage(packageFileName, shortMsg, null, MSG_TITLE_LOAD_FAILED, sendToCochraneAlways);
    }

    public static void sendFailedLoadPackageMessage(String packageFileName, String shortMsg,
                                                    String dbName, String manuscriptNumber) {
        sendFailedPackageMessageCDSR(packageFileName, shortMsg, null,
                MSG_TITLE_LOAD_FAILED, dbName, manuscriptNumber);
    }

    public static void sendWarnForMissingNotificationFileInDB(PublishedAbstractEntity pae, String warnMessage){
        Map<String, String> notifyMessage = new HashMap<>();
        notifyMessage.put(MSG_PARAM_DATABASE, BaseType.getCDSR().get().getShortName());
        notifyMessage.put(MSG_PARAM_RECORD_MANUSCRIPT_NUMBER,
                StringUtils.isNotBlank(pae.getManuscriptNumber()) ? pae.getManuscriptNumber() : NOT_AVAILABLE);
        notifyMessage.put(MSG_PARAM_REPORT, warnMessage);
        sendMessage(MSG_TITLE_GENERATION_WARNINGS, notifyMessage);
    }

    public static void sendCochraneCriticalError(String errorMessage, String packageName) {
        Map<String, String> notifyMessage = new HashMap<>();
        notifyMessage.put(MSG_PARAM_DATABASE, BaseType.getCDSR().get().getShortName());
        PublishedAbstractEntity pae = CochraneCMSBeans.getPublishStorage()
                .findWhenReadyByCochraneNotification(packageName);
        notifyMessage.put(MSG_PARAM_RECORD_ID, CmsUtils.getOrDefault(pae.getRecordName()));
        notifyMessage.put(MSG_PARAM_RECORD_MANUSCRIPT_NUMBER,
                StringUtils.isNotBlank(pae.getManuscriptNumber()) ? pae.getManuscriptNumber() : NOT_AVAILABLE);
        notifyMessage.put(MSG_PARAM_REPORT, errorMessage);
        String messageKey = "cochrane_send_error";
        MessageSender.sendMessage(messageKey, notifyMessage);
    }

    public static void sendErrorMovingNotificationFile(PublishedAbstractEntity pae, String errorMessage) {
        Map<String, String> notifyMessage = new HashMap<>();
        notifyMessage.put(MSG_PARAM_DATABASE, BaseType.getCDSR().get().getShortName());
        notifyMessage.put(MSG_PARAM_RECORD_ID, CmsUtils.getOrDefault(pae.getRecordName()));
        notifyMessage.put(MSG_PARAM_RECORD_MANUSCRIPT_NUMBER,
                StringUtils.isNotBlank(pae.getManuscriptNumber()) ? pae.getManuscriptNumber() : NOT_AVAILABLE);
        notifyMessage.put(MSG_PARAM_REPORT, errorMessage);
        String messageKey = "cochrane_move_notification_error";
        MessageSender.sendMessage(messageKey, notifyMessage);
    }

    public static void sendPreLifePublishingMessage(String dbName, String dfName, String publishName,
                                                    String recordName, int year, int month, String endDate) {
        boolean completed = endDate != null;
        Map<String, String> map = new HashMap<>();
        map.put(NAME, publishName);
        map.put("db_name", dbName);
        map.put("initial_package_name", dfName);
        map.put("issue_num", String.valueOf(month));
        map.put("issue_year", String.valueOf(year));
        map.put("record_name", recordName);
        if (completed) {
            map.put("date", endDate);
        }
        String report = CochraneCMSProperties.getProperty(
                completed ? "publishing.completed.pre_live_qa" : "publishing.started.pre_live_qa", map);
        map.clear();
        map.put(MSG_PARAM_REPORT, report);
        map.put(MSG_PARAM_DATABASE, dbName);
        sendMessage(MSG_TITLE_SENDING_STAGE_QA_STARTED, map);
    }

    public static void sendMessage(String messageKey, Map<String, String> parametersMap) {
        NewNotification notifMsg = getNotificationTemplate(messageKey);
        addCochraneEmailToRecipientsForLoadingFailedError(messageKey, parametersMap);
        List<User> usersForNotification = getNotificationLevelUsersConfig(notifMsg.getLevel().value());
        String body = buildBody(notifMsg, parametersMap);
        String subj = buildSubject(notifMsg, parametersMap);

        String preBody = getPreBody(parametersMap, notifMsg);
        if (StringUtils.isNotBlank(preBody) || SEND_TO_COCHRANE_ALWAYS
                .equals(parametersMap.getOrDefault(SEND_TO_COCHRANE_ALWAYS, NOT_AVAILABLE))) {
            body = preBody + body;
            String cochraneASNotificationEmail = CochraneCMSPropertyNames.getCochraneASNotificationEmail();
            if (!LOCALHOST.equalsIgnoreCase(cochraneASNotificationEmail)) {
                usersForNotification.add(new User(cochraneASNotificationEmail, COCHRANE));
            }
        }

        String asNotificationMessage = buildJsonForASNotification(usersForNotification, subj, body, messageKey);

        if (isToLog()) {
            logMail(subj, body, notifMsg.getLevel(), buildTags(notifMsg.getTags()));
            if (isToLogOnly()) {
                return;
            }
        }

        if (isEnabled()){
            sendAsMessage(asNotificationMessage, messageKey, true);
        } else {
            CochraneCMSBeans.getNotificationManager().suspendASNotification(messageKey, asNotificationMessage, TODO,
                    SuspendNotificationEntity.TYPE_DISABLED_SERVICE);
        }
    }

    private static void addCochraneEmailToRecipientsForLoadingFailedError(String messageKey,
                                                                       Map<String, String> parametersMap) {
        if (MSG_TITLE_LOAD_FAILED.equalsIgnoreCase(messageKey)) {
            parametersMap.put(SEND_TO_COCHRANE_ALWAYS, SEND_TO_COCHRANE_ALWAYS);
        }
    }

    private static String getPreBody(Map<String, String> parametersMap, NewNotification notifMsg) {
        String preBody = "";
        String dbName = parametersMap.getOrDefault(MSG_PARAM_DATABASE, NOT_AVAILABLE);
        if ((BaseType.getCDSR().get().getShortName().equals(dbName) || dbName.contains("clsysrev"))
                && (Levels.ERROR.equals(notifMsg.getLevel()))) {
            // execute some queries to get data?
            preBody = buildPreBody(parametersMap);
        }
        return preBody;
    }

    private static String buildPreBody(Map<String, String> parametersMap) {
        String deliveryFileName = getDeliveryFileName(parametersMap);
        String identifiers = getCDnumbersFromMessageByPattern(
                parametersMap.getOrDefault(MSG_PARAM_DELIVERY_FILE, NOT_AVAILABLE), parametersMap);

        return String.format("Package: %s\n\nIdentifier: %s\n\nManuscript number: %s\n\n",
                deliveryFileName,
                identifiers,
                parametersMap.getOrDefault(MSG_PARAM_RECORD_MANUSCRIPT_NUMBER, NOT_AVAILABLE));
    }

    private static String getDeliveryFileName(Map<String, String> parametersMap) {
        String deliveryFileNames = parametersMap.getOrDefault(MSG_PARAM_DELIVERY_FILE, NOT_AVAILABLE);
        Set<String> uniqueResults = new HashSet<>();
        Arrays.stream(deliveryFileNames.split("\\s+"))
                .map(str -> str.replaceAll("[\":']", ""))
                .filter(str -> str.endsWith(".zip"))
                .forEach(uniqueResults::add);

        String result = String.join(", ", uniqueResults);
        return StringUtils.isNotBlank(result) ? result : NOT_AVAILABLE;
    }

    public static String getCDnumbersFromMessageByPattern(String msg, Map<String, String> parametersMap) {
        if (StringUtils.isNotBlank(msg) && !parametersMap.containsKey(MSG_PARAM_RECORD_ID)){
            Pattern pattern = Pattern.compile("(CD|MR)\\d{6}");
            Matcher matcher = pattern.matcher(msg);

            List<String> matches = new ArrayList<>();
            while (matcher.find()){
                matches.add(matcher.group());
            }
            return !matches.isEmpty() ? String.join(", ", matches) : NOT_AVAILABLE;
        }
        return parametersMap.getOrDefault(MSG_PARAM_RECORD_ID, NOT_AVAILABLE);
    }

    private static String buildJsonForASNotification(List<User> usersForNotification, String subj,
                                                     String body, String messageKey) {
        String senderEmail = CochraneCMSPropertyNames.getCochraneASNotificationSender();
        AsNotification asNotification = new AsNotification(senderEmail, usersForNotification, subj, body);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        String json = null;
        try {
            json = mapper.writeValueAsString(asNotification);
        } catch (JsonProcessingException e) {
            LOG.error(FAILED_2_CREATE_NOTIF_MSG + messageKey + ": " + body);
        }
        return json;
    }

    public static void sendRecordFailedValidationReport(BaseType baseType, String packageFileName, String recordName,
                                                        String title, String report, String manuscriptNumber) {
        Map<String, String> map = new HashMap<>();
        map.put(MSG_PARAM_DATABASE, baseType.getShortName());
        map.put(MSG_PARAM_DELIVERY_FILE, packageFileName);
        map.put(MSG_PARAM_RECORD_ID, recordName);
        map.put(MSG_PARAM_RECORD_TITLE, title == null ? "" : XmlUtils.normalize(title));
        map.put(MSG_PARAM_REPORT, addMsgForCochraneIfNeeded(report));
        map.put(MSG_PARAM_RECORD_MANUSCRIPT_NUMBER,
                StringUtils.isNotBlank(manuscriptNumber) ? manuscriptNumber : NOT_AVAILABLE);
        sendMessage(baseType.isCCA() ? MSG_TITLE_CCA_VALIDATION_FAILED : MSG_TITLE_RECORD_VALIDATION_FAILED, map);
    }

    private static String addMsgForCochraneIfNeeded(String report) {
        if (report.contains(ZERO_LENGTH_PREFIX) || report.contains(FILENAME_MISMATCH_PREFIX)) {
            return report + NEW_LINE_DELIMITER + MESSAGE_FOR_COCHRANE_SUPPORT;
        }
        return report;
    }

    private static void sendFailedPackageMessage(String packageFileName, String shortMsg, String msg,
                                                 String title, String sendToCochraneAlways) {
        Map<String, String> notifyMessage = new HashMap<>();
        notifyMessage.put(SEND_TO_COCHRANE_ALWAYS, StringUtils.isNotBlank(sendToCochraneAlways)
                ? sendToCochraneAlways : NOT_AVAILABLE);
        notifyMessage.put(MSG_PARAM_DELIVERY_FILE, packageFileName
            + (shortMsg != null && !shortMsg.isEmpty() ? DELIMITER + shortMsg : ""));
        if (msg != null && !msg.isEmpty()) {
            notifyMessage.put(MSG_PARAM_LIST, msg);
        }
        sendMessage(title, notifyMessage);
    }

    private static void sendFailedPackageMessageCDSR(String packageFileName, String shortMsg, String msg,
                                                 String title, String dbName, String manuscriptNumber) {
        Map<String, String> notifyMessage = new HashMap<>();
        notifyMessage.put(MSG_PARAM_DELIVERY_FILE, packageFileName
                + (shortMsg != null && !shortMsg.isEmpty() ? DELIMITER + shortMsg : ""));
        if (msg != null && !msg.isEmpty()) {
            notifyMessage.put(MSG_PARAM_LIST, msg);
        }
        notifyMessage.put(MSG_PARAM_DATABASE, StringUtils.isNotBlank(dbName) ? dbName : NOT_AVAILABLE);
        notifyMessage.put(MSG_PARAM_RECORD_MANUSCRIPT_NUMBER,
                StringUtils.isNotBlank(manuscriptNumber) ? manuscriptNumber : NOT_AVAILABLE);
        sendMessage(title, notifyMessage);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static boolean isToLog() {
        return toLog > 0;
    }

    public static boolean isToLogOnly() {
        return toLog == 2;
    }

    public static void enable(boolean value) {
        enabled = value;
    }

    public static void addMessage(StringBuilder fails, String recName, String errMsg) {
        if (recName == null) {
            // this is a final
            if (fails.length() > 2) {
                fails.delete(fails.length() - 2, fails.length());
            }
            return;
        }

        fails.append(recName);
        if (errMsg != null && errMsg.length() > 1) {
            fails.append(" (").append(errMsg.replace("$", "\\$")).append(")");
        }
        fails.append(",\n");
    }

    public static int sendAsMessage(String asNotificationMessage, String messageKey, boolean canSuspend) {
        int ret = SuspendNotificationEntity.TYPE_NO_ERROR;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String createNotificationMessageUrl = CochraneCMSPropertyNames.getCochraneASNotificationEndpoint()
                    + NOTIFICATIONS_SEND;
            HttpPost httpPost = new HttpPost(createNotificationMessageUrl);
            httpPost.setHeader(CONTENT_TYPE, APPLICATION_JSON);

            StringEntity entity = new StringEntity(asNotificationMessage);
            httpPost.setEntity(entity);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity responseEntity = response.getEntity();
                int statusCode = response.getStatusLine().getStatusCode();
                if (HttpStatus.SC_CREATED == statusCode) {
                    if (responseEntity != null) {
                        String responseBody = EntityUtils.toString(responseEntity);
                        // check response in future if needed
                    }
                } else if (responseEntity != null) {
                    printErrorLogForASNotification(asNotificationMessage, messageKey, responseEntity);
                    ret = (HttpStatus.SC_BAD_REQUEST == statusCode)
                            ? SuspendNotificationEntity.TYPE_DEFINED_ERROR
                            : SuspendNotificationEntity.TYPE_UNDEFINED_ERROR;
                    suspendASNotificationIfNeeded(asNotificationMessage, messageKey, canSuspend, ret);
                } else {
                    LOG.error(FAILED_2_SEND_NOTIF_MSG + messageKey + ": " + asNotificationMessage);
                    ret = SuspendNotificationEntity.TYPE_UNDEFINED_ERROR;
                    suspendASNotificationIfNeeded(asNotificationMessage, messageKey, canSuspend, ret);
                }
            }
        } catch (Exception e) {
            if (!canSuspend || CochraneCMSBeans.getNotificationManager().suspendASNotification(messageKey,
                    asNotificationMessage, TODO, SuspendNotificationEntity.TYPE_UNDEFINED_ERROR)) {
                ret = SuspendNotificationEntity.TYPE_UNDEFINED_ERROR;
                enable(false);
                LOG.debug(SuspendNotificationSender.SUSPEND_NOTIFICATION_SERVICE);
            } else {
                LOG.error(FAILED_2_SEND_NOTIF_MSG + messageKey + ": " + e.getMessage());
            }
        }
        return ret;
    }

    private static void printErrorLogForASNotification(String asNotificationMessage, String messageKey,
                                                       HttpEntity responseEntity) throws IOException {
        JsonNode jsonNode = OBJECT_MAPPER.readTree(EntityUtils.toString(responseEntity));
        String code = jsonNode.get(MSG_PARAM_CODE).asText();
        String message = jsonNode.get(MSG_PARAM_MESSAGE).asText();
        LOG.error(String.format("%s%s: %s; %n response code: %s%n message: %s",
                FAILED_2_SEND_NOTIF_MSG, messageKey, asNotificationMessage, code, message));
    }

    private static void suspendASNotificationIfNeeded(String asNotificationMessage, String messageKey,
                                                      boolean canSuspend, int retErrorCode) {
        if (canSuspend) {
            CochraneCMSBeans.getNotificationManager().suspendASNotification(messageKey,
                    asNotificationMessage, TODO, retErrorCode);
        }
    }

    public static int sendNSMessage(NewNotification msg, String messageKey, boolean canSuspend) {

        INotificationWebService notifWs = null;
        NotificationResult result;
        int maxAttemptsTmp = maxAttempts;
        boolean tryAgain;
        int ret = SuspendNotificationEntity.TYPE_NO_ERROR;
        try {
            notifWs = WebServiceUtils.getNotification();

            do {
                result = notifWs.processNotification(msg);
                tryAgain = result.isTryAgain() && --maxAttemptsTmp > 0;
                if (tryAgain) {
                    Thread.sleep(redeliveryDelay);
                }
            } while (tryAgain);

            if (!result.isSuccess()) {
                LOG.error(FAILED_2_SEND_NOTIF_MSG + messageKey + ": " + result.getMessage());
                ret = SuspendNotificationEntity.TYPE_DEFINED_ERROR;
                if (canSuspend) {
                    CochraneCMSBeans.getNotificationManager().suspendNSNotification(
                            messageKey, msg, result.getMessage(), ret);
                }
            }
        } catch (Exception e) {
            if (!canSuspend || CochraneCMSBeans.getNotificationManager().suspendNSNotification(
                    messageKey, msg, e.getMessage(), SuspendNotificationEntity.TYPE_UNDEFINED_ERROR)) {
                ret = SuspendNotificationEntity.TYPE_UNDEFINED_ERROR;
                enable(false);
                LOG.debug(SuspendNotificationSender.SUSPEND_NOTIFICATION_SERVICE);

            } else {
                LOG.error(FAILED_2_SEND_NOTIF_MSG + messageKey + ": " + e.getMessage());
            }

        } finally {
            WebServiceUtils.releaseServiceProxy(notifWs, INotificationWebService.class);
        }
        return ret;
    }

    private static void logMail(String subj, String body, Levels level, String tags) {
        LOG_MN.info(String.format("\n<-- %s\nLevel: %s\nTags: %s\n%s\n>\n\n", subj, level, tags, body));
    }

    private static String buildTags(List<String> tags) {
        StringJoiner ret = new StringJoiner(", ");
        tags.forEach(ret::add);
        return ret.toString();
    }

    private static String buildBody(NewNotification notif, Map<String, String> parametersMap) {
        String bodyHeader = "Date/time: " + Now.SIMPLE_DATE_TIME_FORMATTER.format(ZonedDateTime.now()) + "\n\n";
        String bodyLevel = String.format("Level: %s%n%nMessage:%n%n", notif.getLevel().value());
        String body = bodyHeader + bodyLevel + notif.getBody();

        if (parametersMap != null) {
            for (Map.Entry<String, String> entry : parametersMap.entrySet()) {
                String value = entry.getValue();
                if (value != null) {
                    body = body.replaceFirst("%" + entry.getKey() + "%", Matcher.quoteReplacement(value));
                }
            }
        }

        body = body.replaceAll("\\\\n", "\n");
        body = body.replaceAll(REGEX, "");

        if (body.length() > MAX_LENGTH_MSG) {
            String cuttingText = "\n ----------------------- \n ... the message was cut off";
            body = body.substring(0, MAX_LENGTH_MSG - cuttingText.length()) + cuttingText;
        }

        notif.setBody(body);
        return body;
    }

    private static String buildSubject(NewNotification notif, Map<String, String> parametersMap) {
        String subject = notif.getSubject();
        if (parametersMap != null) {
            for (Map.Entry<String, String> entry : parametersMap.entrySet()) {
                subject = subject.replaceFirst("%" + entry.getKey() + "%", Matcher.quoteReplacement(entry.getValue()));
            }
        }

        if (subject != null) {
            subject = subject.replaceAll(REGEX, "");
            if (prefix != null) {
                subject = prefix + subject;
            }
        }

        notif.setSubject(subject);
        return subject;
    }

    public static synchronized void init() {
        LOG.debug("MessageSender init");

        initNotificationTemplates();
        initNotificationLevelUsersConfig();

        maxAttempts = CochraneCMSPropertyNames.getNotificationMaxRedeliveryAttempts();
        redeliveryDelay = CochraneCMSPropertyNames.getNotificationRedeliveryDelay();
        toLog = Property.get("cms.cochrane.ns.notification.on", "1").get().asInteger();
        setPrefix();
    }

    private static void initNotificationTemplates() {
        try {
            Document doc = new SAXBuilder().build(new URL(notificationsFile));
            Map<String, NewNotification> newNotifTemplates = new HashMap<>();

            List messageList = doc.getRootElement().getChildren();
            for (Object o : messageList) {
                parseMessage(o, newNotifTemplates);
            }
            notifTemplates = newNotifTemplates;
        } catch (Exception e) {
            LOG.error("Failed to parse notification templates", e);
        }
    }

    private static void initNotificationLevelUsersConfig() {
        try {
            Document document = new SAXBuilder().build(new URL(notificationLevelUsersConfigFile));
            Map<String, List<User>> newUsersConfig = new HashMap<>();

            List<Element> templateElements = document.getRootElement().getChildren("template");
            for (Element templateElement : templateElements) {
                String templateId = templateElement.getAttributeValue("id");

                List<Element> userElements = templateElement.getChildren("user");
                List<User> userList = userElements.stream()
                        .map(userElement -> new User(
                                userElement.getAttributeValue("email"),
                                userElement.getAttributeValue(NAME)))
                        .collect(Collectors.toList());

                newUsersConfig.put(templateId, userList);

                notificationLevelUsersConfig = newUsersConfig;
            }
        } catch (Exception e) {
            LOG.error("Failed to parse notification config file", e);
        }
    }

    private static void setPrefix() {
        prefix = CochraneCMSPropertyNames.getNotificationPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            prefix = prefix + " ";
        } else {
            prefix = null;
        }
    }

    private static void parseMessage(Object o, Map<String, NewNotification> notifTemplates) throws Exception {
        Element e = (Element) o;

        NewNotification notif = new NewNotification();
        notif.setSubject(e.getChild("subject").getValue());
        notif.setBody(e.getChild("body").getValue());
        notif.setLevel(Levels.valueOf(e.getChild("level").getValue()));
        notif.setProfileName(e.getChild("profile").getValue());
        notif.setMessageDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar()));

        List tagList = e.getChild("tags").getChildren();
        for (Object o2 : tagList) {
            Element e2 = (Element) o2;
            notif.getTags().add(e2.getValue());
        }

        notifTemplates.put(e.getAttributeValue("id"), notif);
    }

    public static NewNotification getNotificationTemplate(String key) {
        if (notifTemplates == null) {
            init();
        }
        Map<String, NewNotification> notifTemplatesLocal = notifTemplates;
        return notifTemplatesLocal.get(key).clone();
    }

    public static List<User> getNotificationLevelUsersConfig(String key) {
        if (notificationLevelUsersConfig == null) {
            init();
        }
        Map<String, List<User>> notificationLevelUsersConfigLocal = notificationLevelUsersConfig;
        return new ArrayList<>(notificationLevelUsersConfigLocal.get(key));
    }

    /**
     *  Titled and param message sender
     */
    public static class Help {

        private final String title;

        public Help(String title) {
            this.title = title;
        }

        public void sendMessage(String... params) {
            Map<String, String> notifyMessage = createNotifyMessage(params);
            if (notifyMessage != null) {
                MessageSender.sendMessage(title, notifyMessage);
            }
        }

        public void sendMessage(String packageName, PublishedAbstractEntity pae, String... params) {
            Map<String, String> notifyMessage = createNotifyMessage(params);
            if (notifyMessage != null) {
                notifyMessage.put(MSG_PARAM_DATABASE, BaseType.getCDSR().get().getShortName());
                notifyMessage.put(MSG_PARAM_DELIVERY_FILE, CmsUtils.getOrDefault(packageName));
                notifyMessage.put(MSG_PARAM_RECORD_ID, CmsUtils.getOrDefault(pae.getRecordName()));
                notifyMessage.put(MSG_PARAM_RECORD_MANUSCRIPT_NUMBER, CmsUtils.getOrDefault(pae.getManuscriptNumber()));
                MessageSender.sendMessage(title, notifyMessage);
            }
        }

        private Map<String, String> createNotifyMessage(String... params) {
            int size = params.length;

            if (size < 2 || (size % 2) != 0) {
                LOG.error(String.format("Incorrect params number: %d", size));
                return null;
            }

            Map<String, String> notifyMessage = new HashMap<>();
            for (int i = 0; i < size; i += 2) {
                notifyMessage.put(params[i], params[i + 1]);
            }

            return notifyMessage;
        }
    }

    /**
     *
     */
    public static class NotificationMessage {

        private String msgId;
        private final Map<String, String> params;
        private boolean empty;

        public NotificationMessage() {
            params = new HashMap<>();
        }

        public String getMsgId() {
            return msgId;
        }

        public void setMsgId(String msgId) {
            this.msgId = msgId;
        }

        public Map<String, String> getParams() {
            return params;
        }

        public String getParam(String key) {
            return params.get(key);
        }

        public void addParam(String key, String value) {
            params.put(key, value);
        }

        public boolean isEmpty() {
            return empty;
        }

        public void setEmpty(boolean empty) {
            this.empty = empty;
        }
    }

    /**
     *
     */
    public static class MessageSenderWrapper {

        public void sendReport(String title, String report) {
            MessageSender.sendReport(title, report);
        }

        public void sendReport(String title, String database, String report) {
            MessageSender.sendReport(title, database, report);
        }
    }
}
