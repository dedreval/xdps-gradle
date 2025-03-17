package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10/6/2017
 */
public class SemanticoCentralDelSender extends SemanticoSender {

    public SemanticoCentralDelSender(ClDbVO db) {
        super(db, "SEMANTICO:DEL:" + db.getTitle(), PubType.TYPE_SEMANTICO_DELETE);
    }

    @Override
    protected void logSendingSuccessful(PublishWrapper publish, Boolean relatedFiles) {

        publish.addMessage(MessageSender.MSG_TITLE_SENDING_SUCCESSFUL);
        publish.addMessage(getIssueAndDb("sending.issue_and_db_name_semantico_del", null));
    }

    @Override
    public String getDestinationPlace() {
        return "HW endpoint: " + getHWClient().getDeletePath();
    }

    @Override
    protected boolean isDelete() {
        return true;
    }
}
