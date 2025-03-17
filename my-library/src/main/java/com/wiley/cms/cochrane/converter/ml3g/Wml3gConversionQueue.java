package com.wiley.cms.cochrane.converter.ml3g;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.process.IConversionManager;
import com.wiley.cms.cochrane.process.IWml3gConversionManager;
import com.wiley.cms.cochrane.process.QueueProvider;
import com.wiley.cms.cochrane.process.handler.Wml3gConversionHandler;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.IProcessCache;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.IProcessStorage;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessPartQueue;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.ProcessVO;
import com.wiley.tes.util.Logger;
import org.apache.commons.lang.StringUtils;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 03.09.2014
 */
@MessageDriven(
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/wml3g-conversion"),
                @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "12")},
        name = QueueProvider.QUEUE_CONVERSION)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class Wml3gConversionQueue extends ProcessPartQueue<ProcessHandler> implements MessageListener {

    public static final String DESTINATION_QUEUE = "java:jboss/exported/jms/queue/wml3g-conversion";
    private static final Logger LOG = Logger.getLogger(Wml3gConversionQueue.class);

    @EJB(lookup = ProcessHelper.LOOKUP_PROCESS_STORAGE)
    private IProcessStorage procStorage;

    @EJB(lookup = ProcessHelper.LOOKUP_PROCESS_CACHE)
    private IProcessCache procCache;

    @EJB
    private IWml3gConversionManager manager;

    @EJB
    private IConversionManager convManager;

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected void processNextPart(ProcessVO process, List<ProcessPartVO> results, IProcessManager manager)
        throws ProcessException {
        convManager.processNextPart(process, 1);
    }

    @Override
    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        ProcessPartVO ppvo;
        int procPartId = Constants.UNDEF;
        Wml3gConversionHandler handler;
        Wml3gConversionProcessPartParameters procPartParams;
        ProcessVO pvo;
        try {
            procPartId = getProcessPartId(message);
            if (procPartId == 0) {
                // it's a common conversion message
                onMessage(message, convManager, ProcessHandler.class);
                return;
            }

            storage.startProcessPart(procPartId);
            ppvo = storage.getProcessPart(procPartId);
            procPartParams = Wml3gConversionProcessPartParameters.parseParameters(ppvo.parametersMap);

            pvo = manager.findProcess(ppvo.parentId);
            if (pvo == null) {
                throw new Exception(String.format("Failed to get process %d", ppvo.parentId));
            }
            handler = ProcessHandler.castProcessHandler(pvo.getHandler(), Wml3gConversionHandler.class);
        } catch (Exception e) {
            processErrors(procPartId, e.toString());
            return;
        }

        Wml3gConverter converter = Wml3gConverter.getConverter(handler.getDbId(), procPartParams);
        converter.execute();

        String msg = manager.getStatisticMessage(
                converter.getConvertedCnt(),
                converter.getConvertedCnt() - converter.getFailedCnt(),
                converter.getExcludedRecordNames(),
                StringUtils.EMPTY);
        storage.setProcessPartState(procPartId, ProcessState.SUCCESSFUL, msg);

        try {
            synchronized (pvo) {
                manager.processNextPart(pvo, 1);
            }
        } catch (ProcessException e) {
            String err = "Failed to start next process; " + e;
            LOG.error("Conversion to WML3G failed; " + err);
            MessageSender.sendWml3gConversion("Process id " + ppvo.parentId + "; process part id " + ppvo.getId(), err);
        }
    }

    private int getProcessPartId(Message message) throws JMSException {
        if (!(message instanceof ObjectMessage)) {
            throw new JMSException("ObjectMessage expected!");
        }
        Object id = ((ObjectMessage) message).getObject();
        if (id instanceof Integer) {
            return (Integer) id;
        }
        return 0;
    }

    private void processErrors(int procPartId, String errCause) {
        String procPartIdMsg;
        if (procPartId == Constants.UNDEF) {
            procPartIdMsg = CochraneCMSPropertyNames.getNotAvailableMsg();
        } else {
            procPartIdMsg = String.valueOf(procPartId);
            storage.setProcessPartState(procPartId, ProcessState.FAILED, errCause);
        }

        LOG.error(String.format("Conversion to WML3G failed; process part %s; %s", procPartIdMsg, errCause));
        MessageSender.sendWml3gConversion("process part " + procPartIdMsg, errCause);
    }
}
