package com.wiley.cms.cochrane.process;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.jms.JMSSender;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 26.09.2014
 */
public abstract class Wml3gManager extends BaseManager {

    protected String getErrorMessage(int procId, String qualifier, String errHeader, String errCause) {
        StringBuilder errs = new StringBuilder();
        errs.append(errHeader)
                .append("; process ").append(procId)
                .append("; ").append(qualifier);
        if (StringUtils.isNotEmpty(errCause)) {
            errs.append("; ").append(errCause);
        }
        errs.append('.');

        return errs.toString();
    }

    protected void onNextPart(ProcessVO pvo, List<Integer> partIds, Queue destQueue) throws ProcessException {
        for (final int partId : partIds) {
            sendMessage(pvo.getPriority(), destQueue, new JMSSender.MessageCreator() {

                public Message createMessage(Session session) throws JMSException {
                    ObjectMessage msg = session.createObjectMessage();
                    msg.setObject(partId);
                    return msg;
                }
            });
        }
    }

    protected Statistic getStatistic(int procId, String procName) {
        return new Statistic(procId, procName);
    }

    protected abstract String getRecordNamesMessage(List<String> recNames);

    protected String getLoginName() {
        return getLoginName(null);
    }

    protected String getLoginName(String logName) {
        return StringUtils.isNotEmpty(logName)
                ? logName
                : CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);
    }

    public String getStatisticMessage(int procCnt, int procSuccessCnt, List<String> unprocRecs, String comments) {
        return procCnt
                + "#" + procSuccessCnt
                + "#" + getUnprocessedRecordsMessage(unprocRecs)
                + "#" + comments;
    }

    private String getUnprocessedRecordsMessage(List<String> unprocRecs) {
        String msg = unprocRecs.toString();
        return msg.substring(1, msg.length() - 1);
    }

    /**
     *
     */
    public class Statistic {

        private static final int PROC_CNT_PARAM_NUMB = 0;
        private static final int PROC_CNT_SUCCESS_PARAM_NUMB = 1;
        private static final int REC_NAMES_PARAM_NUMB = 2;
        private static final int COMMENTS_PARAM_NUMB = 3;

        private final String procName;
        private int procCnt;
        private int procSuccessCnt;
        private List<String> recNames = new ArrayList<String>();
        private String comments = StringUtils.EMPTY;

        private Statistic(int procId, String procName) {
            this.procName = procName;

            int offset = 0;
            int batchSize = CochraneCMSPropertyNames.getDbRecordBatchSize();

            List<String> messages = ps.getProcessPartMessages(procId, offset, batchSize);
            while (!messages.isEmpty()) {
                for (String message : messages) {
                    parseStatisticMessage(message);
                }

                offset += batchSize;
                messages = ps.getProcessPartMessages(procId, offset, batchSize);
            }
        }

        private void parseStatisticMessage(String message) {
            String[] params = message.split("#");
            if (params == null) {
                params = ArrayUtils.EMPTY_STRING_ARRAY;
                LOG.error("Invalid statistic message has been passed: " + message);
            }

            for (int i = 0; i < params.length; i++) {
                if (i == PROC_CNT_PARAM_NUMB) {
                    procCnt += Integer.parseInt(params[i]);
                } else if (i == PROC_CNT_SUCCESS_PARAM_NUMB) {
                    procSuccessCnt += Integer.parseInt(params[i]);
                } else if (i == REC_NAMES_PARAM_NUMB) {
                    recNames.addAll(parseRecordNames(params[i]));
                } else {
                    if (StringUtils.isNotEmpty(comments)) {
                        comments += '\n';
                    }
                    comments += params[i];
                }
            }
        }

        private List<String> parseRecordNames(String recNamesMsg) {
            List<String> names = new ArrayList<String>();
            String[] recNamesArr = recNamesMsg.split(",");
            for (String recName : recNamesArr) {
                names.add(recName.trim());
            }

            return names;
        }

        public int getProcessedCount() {
            return procCnt;
        }

        public int getProcessedSuccessCount() {
            return procSuccessCnt;
        }

        @Override
        public String toString() {
            StringBuilder msg = new StringBuilder();
            msg.append(procName).append(": ").append(procSuccessCnt).append(" of ").append(procCnt);
            if (!recNames.isEmpty()) {
                msg.append(getRecordNamesMessage(recNames));
            }
            if (StringUtils.isNotEmpty(comments)) {
                msg.append('\n').append("Details: ").append('\n').append(comments);
            }
            return msg.toString();
        }
    }
}
