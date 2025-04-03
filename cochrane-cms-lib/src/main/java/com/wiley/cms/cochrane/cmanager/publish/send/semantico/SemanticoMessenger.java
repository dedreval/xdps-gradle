package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.publish.event.JmsQueueConnectionData;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.process.RepeatableOperation;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 6/9/2016
 */
public class SemanticoMessenger {
    private static final Logger LOG = Logger.getLogger(SemanticoMessenger.class);

    private static final SemanticoMessenger INSTANCE = new SemanticoMessenger();

    private static final String MFT_MSG_FOLDER = "tmp/tibco_mft/transfer/";

    private QueueHolder holder = null;

    private SemanticoMessenger() {
    }

    public static void reset() {
        INSTANCE.holder = null;
    }

    public static Map<String, ITibcoMessage> consumeResponse() throws Exception {

        final Map<String, ITibcoMessage> ret = new HashMap<>();

        QueueHolder holder = INSTANCE.getHolder();

        final int[] countFalse = {0};
        JMSSender.MessageSelector ms = new JMSSender.MessageSelector() {

            public boolean selectMessage(Message msg) throws JMSException {
                String text = ((TextMessage) msg).getText();
                TransferMsg tm = TransferMsg.create(text, true);
                if (tm != null) {
                    printResponse(tm);
                    if (!tm.hasEmptyResponse() && !tm.isOurResponse()) {
                        countFalse[0] = ++countFalse[0];
                        return false;
                    }

                    if (ret.containsKey(tm.getResponseSid())) {
                        LOG.warn(String.format("repeatable response for %s ", tm.getResponseSid()));
                    }
                    ret.put(tm.getResponseSid(), tm);
                } else {
                    printResponse(text);
                }
                return true;
            }
        };

        JMSSender.consume(holder.factory, holder.responseQueue, ms, holder.principal, holder.password);

        if (countFalse[0] > 0) {
            LOG.warn(String.format("%s has %d alien messages", holder.responseQueue, countFalse[0]));
        }
        return ret;
    }

    public static void printResponse(String text) {
        if (LOG.isDebugEnabled()) {
            printMessage("\nTIBCO-MFT response", text);
        }
    }

    public static void printResponse(ITibcoMessage msg) {
        printResponse(msg.asFormatXmlString());
    }

    public static void printRequest(ITibcoMessage msg) {
        if (LOG.isDebugEnabled()) {
            printMessage("\nTIBCO-MFT request", msg.asFormatXmlString());
        }
    }

    private static void printMessage(String prefix, String msg) {
        LOG.debug(String.format("%s: %s", prefix, msg));
    }

    public static String sendResponse(String sid, boolean success, String code) throws Exception {
        String ret = asXmlString(new TransferMsg().setResponse(sid, success, code));
        try {
            sendResponse(asXmlString(new TransferMsg().setResponse(sid, success, code)));
        } catch (Exception e) {
            saveResponse(ret, sid);
            throw new CmsException(new ErrorInfo<>(ret, e.getMessage()));
        }
        return ret;
    }

    public static void sendResponse(final String response) throws Exception {

        checkImitateError();

        QueueHolder holder = INSTANCE.getHolder();

        JMSSender.send(holder.factory, holder.responseQueue, new JMSSender.MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return session.createTextMessage(response);
            }
        }, holder.principal, holder.password);
    }
    
    public static String sendRequest(ITibcoMessageData request, RepeatableOperation ro) throws Exception {

        ITibcoMessage msg = request.createStubMessageForRequest();
        printRequest(msg);

        String ret = asXmlString(msg);
        sendRequest(ret, request.getMsgId(), ro);
        return ret;
    }

    public static void sendRequest(String request, String msgId, RepeatableOperation ro) throws Exception {
        try {
            ro.params[0] = request;
            ro.performOperationThrowingException();

        } catch (Exception e) {
            saveRequest(request, msgId);
            throw new CmsException(new ErrorInfo<>(request, e.getMessage()));
        }
    }

    public static void sendRequest(final String request) throws Exception {

        checkImitateError();

        QueueHolder holder = INSTANCE.getHolder();

        JMSSender.send(holder.factory, holder.requestQueue, new JMSSender.MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return session.createTextMessage(request);
            }
        }, holder.principal, holder.password);
    }

    public static void saveResponse(String out, String sid) {
        CmsUtils.saveNotification(out, CmsUtils.addIssueToPath(MFT_MSG_FOLDER) + "response_" + sid + Extensions.XML);
    }

    public static void saveRequest(String out, String sid) {
        CmsUtils.saveNotification(out, CmsUtils.addIssueToPath(MFT_MSG_FOLDER) + "request_" + sid + Extensions.XML);
    }

    //public static void saveResponse(String out) {
    //    CmsUtils.saveNotification(out, CmsUtils.addIssueToPath(MFT_MSG_FOLDER) + "err_" + System.currentTimeMillis()
    //            + Extensions.XML);
    //}

    private static String asXmlString(ITibcoMessage msg) throws CmsException {
        return asXmlString(msg.asXmlString());
    }

    private static String asXmlString(String msg) throws CmsException {
        if (msg == null) {
            throw new CmsException("cannot create MFT message for Semantico");
        }
        return msg.replace(" id=\"\"", "");
    }

    private static void checkImitateError() throws Exception {
        if (CochraneCMSPropertyNames.getHWClientImitateError() != 0) {
            throw createImitateError();
        }
    }

    public static Exception createImitateError() {
        return new Exception("This is a generic error for testing.\n"
            + "Just set 'cms.cochrane.revman.publish.semantico.imitateError=0' to go through.");
    }

    private QueueHolder getHolder() throws Exception {

        QueueHolder ret = holder;
        if (ret == null) {
            ret = new QueueHolder();
            ret.init();
            holder = ret;
        }
        return ret;
    }

    private static class QueueHolder {

        private QueueConnectionFactory factory;
        private Queue requestQueue;
        private Queue responseQueue;
        private String principal;
        private String password;

        private void init() throws Exception {

            PublishProfile pub = PublishProfile.PUB_PROFILE.get();

            principal = pub.getMFTUser();
            password = pub.getMFTPassword();

            Properties properties = new Properties();
            properties.put(Context.INITIAL_CONTEXT_FACTORY, "com.tibco.tibjms.naming.TibjmsInitialContextFactory");
            properties.put(Context.SECURITY_PRINCIPAL, principal);
            properties.put(Context.SECURITY_CREDENTIALS, password);
            properties.put(Context.PROVIDER_URL, pub.getMFTUrl());

            InitialContext ctx = new InitialContext(properties);

            factory = (QueueConnectionFactory) ctx.lookup("QueueConnectionFactory");

            requestQueue = (Queue) ctx.lookup(pub.getMFTRequest());
            responseQueue = (Queue) ctx.lookup(pub.getMFTResponse());
        }
    }

    /**
     *
     */
    public static class ResponseQueueConnectionData extends JmsQueueConnectionData {

        public ResponseQueueConnectionData() throws Exception {
            super(
                    INSTANCE.getHolder().factory,
                    INSTANCE.getHolder().responseQueue,
                    INSTANCE.getHolder().principal,
                    INSTANCE.getHolder().password);
        }
    }
}
