package com.wiley.cms.cochrane.medlinedownloader;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJBException;
import javax.ejb.MessageDriven;
import javax.ejb.TimedObject;
import javax.ejb.Timer;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 22.10.2009
 */

@MessageDriven(
    activationConfig =
        {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/medline-downloader-service"),
            @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
        }, name = "MedlineQueue")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class MedlineDownloader implements MessageListener, TimedObject {
    private static final Logger LOG = Logger.getLogger(MedlineDownloader.class);
    private static final int FIVE_AM = 5;
    private static final int NINE_PM = 21;
    private static final int SATURDAY = 6;
    private static final int MILLISECONDS_IN_SECOND = 1000;
    private static final int SECONDS_IN_MINUTE = 60;
    private static final int MINUTES_IN_HOUR = 60;

    @Resource
    javax.ejb.MessageDrivenContext mc;

    private IMedlineDownloader downloader;
    private String destinationDirectory;

    @Resource(mappedName = "java:jboss/exported/jms/queue/medline-downloader-service")
    private javax.jms.Queue queue;

    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        try {
            if (!(message instanceof ObjectMessage)) {
                throw new JMSException("ObjectMessage expected!");
            }
            Serializable obj = ((ObjectMessage) message).getObject();
            isInstanseOfIMedlineRequest(obj);

            initProcess((IMedlineRequest) obj);
            TimerService ts = mc.getTimerService();
            long waitTime = getWaitTime();
            ts.createTimer(waitTime, obj);
        } catch (Exception e) {
            throw new EJBException();
        }
    }

    private long getWaitTime() {
        if (!CochraneCMSPropertyNames.useMeshtermUpdateCalendar()) {
            return 0;
        }

        long ret;
        Calendar now = new GregorianCalendar(TimeZone.getTimeZone("GMT-5:00"));
        int dayNow = now.get(Calendar.DAY_OF_WEEK);
        int hourNow = now.get(Calendar.HOUR_OF_DAY);
        if (hourNow < FIVE_AM || hourNow >= NINE_PM || dayNow >= SATURDAY) {
            ret = 0;
        } else {
            int minuteNow = now.get(Calendar.MINUTE);
            ret = (long) (MILLISECONDS_IN_SECOND * SECONDS_IN_MINUTE * ((NINE_PM - hourNow) * MINUTES_IN_HOUR
                - minuteNow));
        }
        return ret;
    }

    private void initProcess(IMedlineRequest request)
        throws ClassNotFoundException, IllegalAccessException, InstantiationException {

        downloader = (IMedlineDownloader) Class.forName(request.getPlan().getCode()).newInstance();
    }

    private boolean process(IMedlineRequest request) throws Exception {
        LOG.info("monthly mesh terms update started");
        MedlineDownloaderParameters params = request.getParameters();
        destinationDirectory = params.getDestinationDirectory();
        List<Map<String, String>> singleParams = params.getDownloaderParameterList();
        Iterator it = singleParams.iterator();
        boolean ret = true;
        String err = null;
        while (getWaitTime() == 0 && it.hasNext()) {
            Map<String, String> p = (Map<String, String>) it.next();
            p.put("destinationDirectory", destinationDirectory);
            try {
                downloader.download(p);
            } catch (MedlineDownloaderException e) {
                err = String.format("Failed to download, destinationDirectory=%s, Exception: %s", destinationDirectory,
                    e.getMessage());
                LOG.warn(err);
            } catch (RuntimeException re) {
                err = String.format("Failed to download by run-time exception, destinationDirectory=%s, %s",
                     destinationDirectory, re.getMessage());
                LOG.warn(err);
                break;
            }
        }
        if (it.hasNext()) {
            finishIncompleteProcess(it, request);
            ret = false;
        } else if (err != null) {
            MessageSender.sendReport(MessageSender.MSG_TITLE_MESHTERM_WARNINGS, err);
        }
        return ret;
    }

    private void finishIncompleteProcess(Iterator it, final IMedlineRequest request) throws Exception {
        List<Map<String, String>> param = new ArrayList<Map<String, String>>();
        while (it.hasNext()) {
            param.add((Map<String, String>) it.next());
        }

        MedlineDownloaderParameters params = new MedlineDownloaderParameters(param, destinationDirectory);
        request.setParameters(params);
        JMSSender.send(JMSSender.lookupQueue(), queue, new JMSSender.MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                ObjectMessage message = session.createObjectMessage();
                message.setObject(request);
                return message;
            }
        });
    }

    private void finishProcess(IMedlineRequest request) throws MedlineDownloaderException {
        request.getCallback().sendCallback(destinationDirectory);
    }

    public void ejbTimeout(Timer timer) {
        try {
            Serializable obj = timer.getInfo();
            isInstanseOfIMedlineRequest(obj);

            boolean successful = process((IMedlineRequest) obj);
            if (successful) {
                finishProcess((IMedlineRequest) obj);
            }
        } catch (Exception e) {
            throw new EJBException();
        }
    }

    private void isInstanseOfIMedlineRequest(Serializable obj) throws JMSException {

        if (!(obj instanceof IMedlineRequest)) {
            throw new JMSException("IMedlineRequest expected!");
        }
    }
}
