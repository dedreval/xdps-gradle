package com.wiley.cms.process;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.ParseException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.validation.constraints.NotNull;
import javax.xml.ws.BindingProvider;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.xpath.XPath;
import org.quartz.CronExpression;

import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskManager;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.DbConstants;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.jdom.DocumentLoader;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 06.09.13
 */
public class ProcessHelper {
    public static final String LOOKUP_PROCESS_STORAGE =
            "java:global/CMS/CMS/ProcessStorage!com.wiley.cms.process.IProcessStorage";
    public static final String LOOKUP_TASK_MANAGER =
            "java:global/CMS/CMS/TaskManager!com.wiley.cms.process.task.ITaskManager";
    public static final String LOOKUP_PROCESS_CACHE =
            "java:global/CMS/CMS/ProcessCache!com.wiley.cms.process.IProcessCache";
    //public static final String LOOKUP_TASK_DOWNLOADER =
    //        "java:global/CMS/CMS/TaskDownloader!com.wiley.cms.process.task.IDownloader";

    public static final String REPORT_PASSED = "PASSED";
    public static final String REPORT_FAILED_1 = "FAILED_1";
    public static final String REPORT_FAILED_2 = "FAILED_2";
    public static final String REPORT_ERROR_EMPTY = "Error message is empty";
    public static final String REPORT_STATS = "STATS";

    public static final String REPORT_MSG_OPEN_TAG = "<message ";
    public static final String REPORT_MSG_FINAL_TAG = "</messages>";

    private static final String KEY_VALUE_DELIMITER = "=";
    private static final String PARAM_DELIMITER = ";";
    private static final String SUB_PARAM_DELIMITER = ",";
    private static final String SUB_PARAM_MAP_DELIMITER = ":";
    private static final String SPEC_PARAM_DELIMITER_TO_SUPPORT_OLD_SERVICE = ":=";

    private static final String PARAM_IDS = "ids";

    private static final Logger LOG = Logger.getLogger(ProcessHelper.class);
    private static final int DEFAULT_ERROR_QUALITY = 10;

    private static final Res<Property> WS_TIMEOUT = Property.get("cms.cochrane.ws-timeout", ""
            + Now.calculateMillisInMinute());
    private static final String CONNECT_TIMEOUT = "javax.xml.ws.client.connectionTimeout";
    private static final String REQUEST_TIMEOUT = "javax.xml.ws.client.receiveTimeout";

    private static final Map<String, Class> SUPPORTING_CLASSES = new HashMap<String, Class>();

    private ProcessHelper() {
    }

    public static void addSupportingClass(Class cl) {
        String name = cl.getName();
        //if (!SUPPORTING_CLASSES.containsKey(name)) {
        SUPPORTING_CLASSES.put(name, cl);
        //}
    }

    public static void setWSTimeout(Object port) {
        int timeout = WS_TIMEOUT.get().asInteger();
        if (timeout <= 0) {
            return;
        }
        Map<String, Object> requestContext = ((BindingProvider) port).getRequestContext();
        requestContext.put(REQUEST_TIMEOUT, timeout);
        requestContext.put(CONNECT_TIMEOUT, timeout);

        requestContext.put("com.sun.xml.internal.ws.request.timeout", timeout);
        requestContext.put("com.sun.xml.internal.ws.connect.timeout", timeout);
    }

    public static Class getSupportingClass(String name) {
        return SUPPORTING_CLASSES.get(name);
    }

    public static ITaskExecutor createExecutor(String name, String... params) throws Exception {

        Class cl = ProcessHelper.getSupportingClass(name);
        if (cl == null) {
            ClassLoader cld = Thread.currentThread().getContextClassLoader();
            cl = cld.loadClass(name);
        }

        return ((ITaskExecutor) cl.newInstance()).initialize(params);
    }

    public static String buildProcessMsg(int prId, String msg) {
        return (msg == null || msg.length() == 0) ? buildProcessMsg(prId) : buildProcessMsg(prId) + ", " + msg;
    }

    public static String buildProcessMsg(int prId) {
        return "process id=" + prId;
    }


    public static ProcessVO createProcess(ProcessHandler handler, ProcessType type, String label, String user) {
        IProcessStorage pr = ProcessStorageFactory.getFactory().getInstance();
        return pr.createProcess(DbEntity.NOT_EXIST_ID, handler, type, label, type.getPriority(), ProcessState.WAITED,
                user);
    }

    public static ProcessVO createIdPartsProcess(ProcessHandler handler, ProcessType type, int priority,
                                                 Object[] recordIds) throws Exception {
        return createIdPartsProcess(DbEntity.NOT_EXIST_ID, handler, type, priority, null, recordIds);
    }

    public static ProcessVO createIdPartsProcess(ProcessHandler handler, ProcessType type, int priority, String user,
                                                 Object[] recordIds, int nextId, int batch) throws Exception {

        IProcessStorage pr = ProcessStorageFactory.getFactory().getInstance();
        ProcessVO pvo = pr.createProcess(DbEntity.NOT_EXIST_ID, handler, type, priority, user);

        createIdPartsProcess(pvo, recordIds, nextId, batch, pr);
        return pvo;
    }

    public static ProcessVO createIdPartsProcess(int parentId, ProcessHandler handler, ProcessType type, int priority,
                                                 String user, Object[] recordIds) throws Exception {

        IProcessStorage pr = ProcessStorageFactory.getFactory().getInstance();
        ProcessVO pvo = pr.createProcess(parentId, handler, type, priority, user);

        createIdPartsProcess(pvo, recordIds, DbEntity.NOT_EXIST_ID, 0, pr);
        return pvo;
    }


    private static void createIdPartsProcess(ProcessVO pvo, Object[] recordIds, int nextId, int batch,
                                             IProcessStorage pr) throws Exception {
        if (nextId != DbEntity.NOT_EXIST_ID) {
            pvo.setNextId(nextId);
            pr.setNextProcess(pvo);
        }

        if (recordIds == null) {
            return;
        }

        if (batch == 0) {
            createIdPartsProcess(pvo, recordIds, pr);
        } else {
            createIdPartsProcess(pvo, recordIds, batch, pr);
        }
        pvo.setSize(recordIds.length);
    }

    public static void createIdPartsProcess(ProcessVO pvo, Object[] recordIds, int batch, IProcessStorage pr)
            throws ProcessException {
        int i = 0;
        int j = 0;
        StringBuilder sb = new StringBuilder(batch * String.valueOf(Integer.MAX_VALUE).length());
        for (Object recId : recordIds) {

            if (i == 0) {
                addKeyValue(PARAM_IDS, recId.toString(), sb);
            } else {
                sb.append(SUB_PARAM_DELIMITER).append(recId);
            }

            if (++i == batch) {
                pr.createProcessPart(pvo.getId(), "" + j, sb.toString());
                i = 0;
                sb = new StringBuilder(batch * String.valueOf(Integer.MAX_VALUE).length());
                if (++j % DbConstants.DB_PACK_SIZE == 0) {
                    LOG.debug(String.format("insert  %d of %s ", j, pvo));
                }
            }
        }

        if (i != 0) {
            pr.createProcessPart(pvo.getId(), "" + j, sb.toString());
        }
    }

    public static void addIdsParam(Integer id, StringBuilder sb) {
        if (sb.length() == 0) {
            addKeyValue(PARAM_IDS, id, sb);
        } else {
            sb.append(SUB_PARAM_DELIMITER).append(id);
        }
    }

    public static List<Integer> getIdsParam(Map<String, String> paramMap) {
        if (paramMap != null) {
            String ids = paramMap.get(PARAM_IDS);
            if (ids != null) {
                return parseSubParamInt(ids);
            }
        }
        return null;
    }

    public static void addInt2IntMapParam(String paramName, Integer key, Integer value, StringBuilder sb) {
        if (sb.length() == 0) {
            addKeyValue(paramName, key + SUB_PARAM_MAP_DELIMITER + value, sb);
        } else {
            sb.append(SUB_PARAM_DELIMITER).append(key).append(SUB_PARAM_MAP_DELIMITER).append(value);
        }
    }

    public static Map<Integer, Integer> getInt2IntMapParam(String paramName, Map<String, String> paramMap) {
        if (paramMap != null) {
            String ids = paramMap.get(paramName);
            if (ids != null) {
                return parseSubParamInt2IntMap(ids);
            }
        }
        return null;
    }

    public static List<Integer> parseSubParamInt(String param) {
        String[] strs = param.split(SUB_PARAM_DELIMITER);
        List<Integer> ret = new ArrayList<>();
        for (String str : strs) {
            ret.add(Integer.parseInt(str));
        }
        return ret;
    }

    public static Map<Integer, Integer> parseSubParamInt2IntMap(String param) {
        String[] strs = param.split(SUB_PARAM_DELIMITER);
        Map<Integer, Integer> ret = new HashMap<>();
        for (String str : strs) {
            String[] keyValue = str.split(SUB_PARAM_MAP_DELIMITER);
            if (keyValue.length < 2) {
                continue;
            }
            ret.put(Integer.parseInt(keyValue[0]), Integer.parseInt(keyValue[1]));
        }
        return ret;
    }

    public static void createIdPartsProcess(ProcessVO pvo, Object[] recordIds) throws ProcessException {
        IProcessStorage pr = ProcessStorageFactory.getFactory().getInstance();
        ProcessType pt = pvo.getType();
        if (ProcessType.isEmpty(pt.getId())) {
            createIdPartsProcess(pvo, recordIds, pr);
        } else {
            createIdPartsProcess(pvo, recordIds, pt.batch(), pr);
        }
    }

    private static void createIdPartsProcess(ProcessVO pvo, Object[] recordIds, IProcessStorage pr)
            throws ProcessException {
        int i = 0;
        for (Object id : recordIds) {
            pr.createProcessPart(pvo.getId(), id.toString(), null);
            i++;
            if (i % DbConstants.DB_PACK_SIZE == 0) {
                LOG.debug(String.format("insert %d of %s ", i, pvo));
            }
        }
    }

    public static int[] asArray(List<Integer> ids) {
        Integer[] integerArray = ids.toArray(new Integer[0]);
        return ArrayUtils.toPrimitive(integerArray);
    }

    public static String buildErrorReportMessageXml(String recordName, String errMsg) {
        return endErrorReportMessageXml(errMsg, startReportMessageXml(recordName)).toString();
    }

    private static StringBuilder startReportMessageXml(String recordName) {
        StringBuilder ret = new StringBuilder();
        return ret.append("<messages><uri name=\"").append(recordName).append(Extensions.XML).append("\"/>");
    }

    private static StringBuilder endErrorReportMessageXml(String msg, StringBuilder sb) {
        return sb.append("<message quality=\"").append(DEFAULT_ERROR_QUALITY).append("\">").append(msg).append(
                "</message></messages>");
    }

    public static String parseErrorReportMessageXml(String report, boolean includeName) {
        if (StringUtils.isEmpty(report)) {
            return ProcessHelper.REPORT_ERROR_EMPTY;
        }

        StringBuilder errs = new StringBuilder();
        Map<String, StringBuilder> uriErrsMap = new HashMap<String, StringBuilder>();
        Map<String, String> uriStageMap = new HashMap<String, String>();
        try {
            StringBuilder tmpMsgs = new StringBuilder();
            tmpMsgs.append("<results>").append(report).append("</results>");

            Document doc = new DocumentLoader().load(tmpMsgs.toString());
            List nodes = XPath.selectNodes(doc, "//messages/message[@quality < 100]");

            for (Object node : nodes) {

                Element msgElm = (Element) node;
                Element msgsElm = msgElm.getParentElement();
                String stage = msgsElm.getAttributeValue("stage");
                String uriName = ((Element) XPath.selectSingleNode(msgsElm, "uri")).getAttributeValue("name");
                StringBuilder tmpErrs;

                if (uriErrsMap.containsKey(uriName)) {
                    tmpErrs = uriErrsMap.get(uriName);
                } else {
                    tmpErrs = new StringBuilder();
                    uriErrsMap.put(uriName, tmpErrs);
                    uriStageMap.put(uriName, stage);
                }
                tmpErrs.append(msgElm.getText()).append("; ");
            }
            for (String uriName : uriErrsMap.keySet()) {
                if (includeName) {
                    String stage = uriStageMap.get(uriName);
                    errs.append(uriName).append(" (").append(stage == null ? "validation" : stage).append("): ");
                }
                errs.append(uriErrsMap.get(uriName)).append("\n");
            }
        } catch (Exception e) {
            errs.append("Failed to parse QA results, ").append(e.getMessage()).append("\n").append(report);
        }
        return errs.toString();
    }

    public static String addErrorReportMessageXml(String errMsg, String report) {

        if (StringUtils.isEmpty(report) || report.endsWith(REPORT_MSG_FINAL_TAG)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(report.substring(0, REPORT_MSG_FINAL_TAG.length()));
        return endErrorReportMessageXml(errMsg, sb).toString();
    }

    public static String buildReportMessage(String... params) {

        StringBuilder sb = new StringBuilder();
        sb.append(params[0]);

        for (int i = 1; i < params.length; i++) {
            sb.append(PARAM_DELIMITER);
            sb.append(params[i]);
        }
        return sb.toString();
    }

    public static String[] parseKeyValueString(String param) {
        return param == null ? null : param.split(KEY_VALUE_DELIMITER);
    }

    public static String buildKeyValueParam(String key, String value) {
        return buildKeyValueParam(key, value, false);
    }

    public static String buildKeyValueParam(String key, int value) {
        return buildKeyValueParam(key, value, false);
    }

    public static String buildKeyValueParam(String key, String... values) {
        StringBuilder sb = new StringBuilder(key);
        for (String value : values) {
            sb.append(KEY_VALUE_DELIMITER).append(value);
        }
        return sb.toString();
    }

    public static String buildKeyValueParam(String key, int value, boolean commas) {
        StringBuilder sb = new StringBuilder();
        sb.append(key).append(KEY_VALUE_DELIMITER);
        return commas ? sb.append("\"").append(value).append("\"").toString() : sb.append(value).toString();
    }

    public static String buildKeyValueParam(String key, String value, boolean commas) {
        StringBuilder sb = new StringBuilder(key.length() + KEY_VALUE_DELIMITER.length()
                + (commas ? value.length() + 2 : value.length()));
        sb.append(key).append(KEY_VALUE_DELIMITER);
        return commas ? sb.append("\"").append(value).append("\"").toString() : sb.append(value).toString();
    }

    public static void addKeyValue(String key, int value, StringBuilder sb) {
        checkFirstParam(sb);
        sb.append(key).append(KEY_VALUE_DELIMITER).append(value);
    }

    public static void addKeyValue(String key, String value, StringBuilder sb) {
        checkFirstParam(sb);
        sb.append(key).append(KEY_VALUE_DELIMITER).append(value);
    }

    public static String buildUriParam(String uri, String param) {
        return uri + SPEC_PARAM_DELIMITER_TO_SUPPORT_OLD_SERVICE + param;
    }

    public static String[] parseUriParam(String param) {
        return param.split(SPEC_PARAM_DELIMITER_TO_SUPPORT_OLD_SERVICE);
    }

    public static void addParam(String param, StringBuilder sb) {
        checkFirstParam(sb);
        sb.append(param);
    }

    private static boolean checkFirstParam(StringBuilder sb) {
        boolean first = sb.length() == 0;
        if (!first) {
            sb.append(PARAM_DELIMITER);
        }
        return first;
    }

    public static String buildSubParametersString(String... parameters) {

        if (parameters.length == 1) {
            return parameters[0];
        }

        StringBuilder sb = new StringBuilder();
        sb.append(parameters[0]);
        for (int i = 1; i < parameters.length; i++) {
            addSubParamString(parameters[i], sb);
        }

        return sb.toString();
    }

    public static void addSubParamString(String param, StringBuilder sb) {
        sb.append(SUB_PARAM_DELIMITER).append(param);
    }

    public static String[] parseSubParamString(String param) {
        return param == null ? null : param.split(SUB_PARAM_DELIMITER);
    }

    public static String[] parseParamString(String param) {
        if (param == null) {
            return null;
        }
        String[] ret = parseUriParam(param);
        if (ret.length < 2) {
            // just in case to support old approach
            ret = param.split("\":");
        }
        return ret;
    }

    public static String buildParametersString(Map<String, String> parameters) {
        if (parameters == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (String key : parameters.keySet()) {
            addKeyValue(key, parameters.get(key), sb);
        }
        return sb.toString();
    }

    public static Map<String, String> buildParametersMap(String parameters) {
        if (parameters == null) {
            return null;
        }

        String[] requestParameters = parameters.split(PARAM_DELIMITER);

        Map<String, String> parametersMap = new HashMap<String, String>();
        for (String parameter : requestParameters) {

            if (parameter.contains(KEY_VALUE_DELIMITER)) {
                String[] parts = parameter.split(KEY_VALUE_DELIMITER);
                if (parts.length < 2) {
                    parametersMap.put(parts[0], "");
                } else {
                    parametersMap.put(parts[0], parts[1]);
                }
            }
        }
        return parametersMap;
    }

    /**
     * Update schedule of the specified task. Schedule will be changed to the specified new value in case
     * the new value is valid cron expression and it differs from the old schedule value assigned to the task.
     *
     * @param task        TaskVO object
     * @param newSchedule new schedule expression
     * @return true in case schedule has been change and false otherwise
     */
    public static boolean rescheduleIfChanged(TaskVO task, String newSchedule) {
        if (task != null && !newSchedule.equals(task.getSchedule())) {
            if (CronExpression.isValidExpression(newSchedule)) {
                task.setSchedule(newSchedule);
                TaskManager.Factory.getFactory().getInstance().updateTask(task);
                LOG.debug(String.format("Task %s schedule updated, the new schedule is %s", task, newSchedule));
                return true;
            } else {
                LOG.error(String.format("Failed to update task %s schedule, %s is not a valid cron expression",
                        task, newSchedule));
            }
        }
        return false;
    }

    public static long getDelayBySchedule(String schedule) {
        try {
            return TaskVO.getDelayBySchedule(schedule);
        } catch (ParseException pe) {
            LOG.warn(pe.getMessage());
            return 0;
        }
    }

    public static Date getNextDateBySchedule(String schedule) {
        try {
            return TaskVO.getNextDateBySchedule(schedule);
        } catch (ParseException pe) {
            LOG.warn(pe.getMessage());
            return null;
        }
    }

    public static List<Integer> asList(final int[] ids) {
        return new AbstractList<Integer>() {
            public Integer get(int index) {
                return ids[index + 1];
            }

            public int size() {
                return ids.length - 1;
            }
        };
    }

    public static int execCommand(String command, File procDir) throws Exception {
        try {
            Process process = procDir == null ? Runtime.getRuntime().exec(command)
                    : Runtime.getRuntime().exec(command, null, procDir);
            process.waitFor();
            int ret = process.exitValue();
            process.destroy();
            return ret;

        } catch (InterruptedException ie) {
            LOG.error(ie.getMessage());
            return -1;
        }
    }
    public static int execCommand(String command, File procDir, int timeOutSec, TimeUnit tumeUnit,
                                  @NotNull StringBuilder err) {
        Process process = null;
        try {
            process = procDir == null ? Runtime.getRuntime().exec(command)
                    : Runtime.getRuntime().exec(command, null, procDir);
            if (process.waitFor(timeOutSec, tumeUnit)) {
                return process.exitValue();
            }
            err.append("time-out");

        } catch (Throwable tr) {
            LOG.error(tr.getMessage());
        } finally {
            finalizeProcess(process, err, null);
        }
        return -1;
    }

    private static void finalizeProcess(Process process, StringBuilder err, StringBuilder output) {
        if (process != null) {
            try {
                if (output != null) {
                    readProcessStream(process.getInputStream(), "output:\n", output);
                }
                if (err != null) {
                    readProcessStream(process.getErrorStream(), "errors:\n", err);
                }
                process.destroy();

            } catch (Throwable tr) {
                LOG.error(tr.getMessage());
            }
        }
    }

    private static void readProcessStream(InputStream is, String msg, StringBuilder sb) throws Exception {
        if (is.available() > 0) {

            byte[] buf = new byte[is.available()];
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(buf.length);
            int read = is.read(buf);
            byteStream.write(buf, 0, read);
            byteStream.close();
            sb.append(msg).append(byteStream);
        }
    }
}
