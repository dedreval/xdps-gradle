package com.wiley.cms.cochrane.process.handler;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.process.CMSProcessManager;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/4/2019

 * @param <Q>
 */
public class SendToPublishHandler<Q> extends DbHandler<CMSProcessManager, Q> {
    private static final long serialVersionUID = 1L;

    public SendToPublishHandler() {
    }

    public SendToPublishHandler(int issue, int issueId, String dbName, int dbId) {
        super(issue, dbName, dbId, DbEntity.NOT_EXIST_ID, issueId);
    }

    public SendToPublishHandler(int issue, int dbId) {
        super(issue, BaseType.getDbName(dbId), dbId, DbEntity.NOT_EXIST_ID, DbEntity.NOT_EXIST_ID);
    }

    public SendToPublishHandler(int dbId) {
        super(DbEntity.NOT_EXIST_ID, BaseType.getDbName(dbId), dbId, DbEntity.NOT_EXIST_ID, DbEntity.NOT_EXIST_ID);
    }

    @Override
    protected void onStartSync(ProcessVO pvo, List<ProcessPartVO> inputData, CMSProcessManager manager)
            throws ProcessException {

        super.onStartSync(pvo, inputData, manager);

        List<ProcessPartVO> parts = manager.getProcessStorage().getProcessParts(pvo.getId());
        if (parts.isEmpty()) {
            throw new ProcessException("no process parts to publish", pvo.getId());
        }
        BaseType bt = BaseType.find(getDbName()).get();
        Set<String> recordNames;
        Collection<String> pubTypes = new HashSet<>();
        boolean dsMonthlyCentral = false;
        if (ICMSProcessManager.PROC_TYPE_SEND_TO_PUBLISH_DS.equals(pvo.getType().getId())) {
            recordNames = null;
            pubTypes.add(PubType.TYPE_DS);
            if (bt.isCentral()) {
                dsMonthlyCentral = true;
            }
        } else {
            Set<String> cdNumbers = new HashSet<>();
            parts.forEach(f -> cdNumbers.add(f.uri));

            pubTypes.add(PubType.TYPE_LITERATUM);
            pubTypes.add(PubType.TYPE_SEMANTICO);
            pubTypes.add(PubType.TYPE_DS);
            recordNames = cdNumbers;
        }
        try {
            if (isEntire())  {
                CochraneCMSBeans.getPublishService().publishEntireDbSync(getDbName(),
                    PublishHelper.generatePublishList(bt, getDbId(), pubTypes, true, false, recordNames));
            } else {
                CochraneCMSBeans.getPublishService().publishDbSync(getDbId(),
                    PublishHelper.generatePublishList(bt, getDbId(), pubTypes, false, dsMonthlyCentral, recordNames));
            }
        } catch (Exception e) {
            throw new ProcessException(e.getMessage(), pvo.getId());
        }
    }
}
