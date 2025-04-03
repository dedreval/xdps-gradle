package com.wiley.cms.cochrane.process.handler;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.jms.Queue;

import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;

import com.wiley.cms.cochrane.process.BaseAcceptQueue;
import com.wiley.cms.cochrane.process.CMSProcessManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessVO;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/7/2019
 *
 */
public class QaServiceHandler extends ContentHandler<DbHandler, BaseAcceptQueue, Map<String, IRecord>> {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(QaServiceHandler.class);

    private Map<String, IRecord> initialRecords;

    public QaServiceHandler() {
    }

    public QaServiceHandler(DbHandler handler) {
        super(handler);
    }

    @Override
    protected Class<DbHandler> getTClass() {
        return DbHandler.class;
    }

    @Override
    public void onExternalMessage(ProcessVO pvo, BaseAcceptQueue queue)  {
        queue.processMessage(this, pvo);
    }

    @Override
    protected void onStartAsync(ProcessVO pvo, List<ProcessPartVO> inputData, CMSProcessManager manager, Queue queue)
            throws ProcessException {

        Map<String, IRecord> startRecords;
        if (inputData != null) {
            startRecords = takeResult(inputData);
        } else {
            startRecords = initialRecords;
            if (!pvo.getType().isCreatePartsBefore()) {
                checkInitialRecords(pvo, startRecords, true, IDeliveryFileStatus.STATUS_QAS_FAILED);
            }
        }
        startQA(pvo, getContentHandler().getDfName(), startRecords.values(), manager);
    }

    @Override
    public void acceptResult(Map<String, IRecord> records) {
        initialRecords = records;
    }

    @Override
    public void acceptResult(PackageUnpackHandler fromHandler, ProcessVO from) {
        initialRecords = fromHandler.takeResult(from);
    }

    @Override
    public void logOnStart(ProcessVO pvo, ProcessManager manager) {
        super.logOnStart(pvo, manager);
        setDeliveryFileStatus(IDeliveryFileStatus.STATUS_QAS_STARTED, true);
    }

    private void startQA(ProcessVO pvo, String dfName, Collection<IRecord> records, CMSProcessManager manager)
            throws ProcessException {
        try {
            manager.getQaManager().sendQa(pvo, getContentHandler().getDfId(), dfName, records);
            MessageSender.sendStartedQAS(pvo.getCreatorId(), dfName);

        } catch (Throwable th) {
            MessageSender.sendFailedQAS(pvo.getCreatorId(), dfName, getContentHandler().getDbName(), th.getMessage());
            throw new ProcessException(th);
        }
    }
}
