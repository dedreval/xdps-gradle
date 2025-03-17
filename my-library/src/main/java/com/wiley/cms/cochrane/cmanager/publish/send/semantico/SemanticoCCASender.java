package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import java.util.HashMap;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSenderWhenReady;

import static com.wiley.cms.cochrane.cmanager.MessageSender.MSG_PARAM_RECORD_ID;
import static com.wiley.cms.cochrane.cmanager.MessageSender.getCDnumbersFromMessageByPattern;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/5/2016
 */
public class SemanticoCCASender extends SemanticoSender {

    public SemanticoCCASender(ClDbVO dbVO) {
        super(dbVO);
    }

    //@Override
    //protected void init(PublishWrapper publish) throws Exception {
    //    super.init(publish);
    //    packagePath = AbstractSenderWhenReady.definePackagePath(publish, getBaseName(),
    //        getDb().getIssue().getId(), getExportTypeName());
    //}

    @Override
    protected boolean byIssue() {
        return true;
    }

    @Override
    protected void logSendingSuccessful(PublishWrapper publish, Boolean relatedFiles) {
        Map<String, String> additional = new HashMap<>();
        additional.put(MessageSender.MSG_PARAM_RECORD_NAMES, AbstractSenderWhenReady.getSentRecords(
                publish, getPackagePath(), rps));

        Map<String, String> map = new HashMap<>();
        map.put(MessageSender.MSG_PARAM_REPORT, getIssueAndDb(AbstractSenderWhenReady.SENDING_SUCCESSFUL_MESSAGE_KEY,
                additional));
        map.put(MessageSender.MSG_PARAM_DATABASE, getDbName());

        MessageSender.sendMessage(AbstractSenderWhenReady.SENDING_SUCCESSFUL_NOTIFICATION_KEY, map);
    }

    @Override
    protected void logSendingFailed(PublishWrapper publish, String message) {
        Map<String, String> additional = new HashMap<>();
        additional.put(MessageSender.MSG_PARAM_RECORD_NAMES, AbstractSenderWhenReady.getSentRecords(
            publish, getPackagePath(), rps));

        Map<String, String> map = new HashMap<>();
        map.put("issue_and_db_name", getIssueAndDb(AbstractSenderWhenReady.SENDING_FAILED_MESSAGE_KEY, additional));
        map.put("details", message);
        String report = CochraneCMSProperties.getProperty("sending.failed", map);

        map.clear();
        map.put(MessageSender.MSG_PARAM_REPORT, report);
        map.put(MessageSender.MSG_PARAM_DATABASE, getDbName());
        String identifiers = getCDnumbersFromMessageByPattern(report, map);
        map.put(MSG_PARAM_RECORD_ID, identifiers);

        MessageSender.sendMessage(AbstractSenderWhenReady.SENDING_FAILED_NOTIFICATION_KEY, map);
    }
}
