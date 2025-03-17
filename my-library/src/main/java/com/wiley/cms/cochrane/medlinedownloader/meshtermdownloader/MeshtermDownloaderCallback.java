package com.wiley.cms.cochrane.medlinedownloader.meshtermdownloader;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.naming.InitialContext;

import com.wiley.cms.cochrane.medlinedownloader.IMedlineCallback;
import com.wiley.cms.cochrane.medlinedownloader.MedlineDownloaderException;
import com.wiley.cms.cochrane.meshtermmanager.MeshtermManagerParameters;
import com.wiley.cms.process.jms.JMSSender;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 23.10.2009
 */
public class MeshtermDownloaderCallback implements IMedlineCallback {
    public static final String QUEUE_NAME = "java:jboss/exported/jms/queue/meshterm-manager-service";

    private int issue;
    private int issueId;
    private String title;

    public MeshtermDownloaderCallback(int issue, int issueId, String title) {
        this.issue = issue;
        this.issueId = issueId;
        this.title = title;
    }

    private File[] getFileList(String path) throws Exception {
        File dir = new File(new URI(path));
        if (!dir.exists() || !dir.isDirectory()) {
            throw new Exception("Directory do not exists " + path);
        }

        return dir.listFiles(new FilenameFilter() {
            public boolean accept(File file, String s) {
                return s.contains(".xml") && !s.contains("search.xml");
            }
        });
    }

    private MeshtermManagerParameters createParams(String path) throws Exception {
        File[] files = getFileList(path);
        return new MeshtermManagerParameters(files, issue, issueId, title);
    }

    public void sendCallback(String s) throws MedlineDownloaderException {
        try {
            InitialContext ctx = new InitialContext();
            final MeshtermManagerParameters params = createParams(s);
            JMSSender.send((QueueConnectionFactory) ctx.lookup(JMSSender.CONNECTION_LOOKUP),
                    (Queue) ctx.lookup(QUEUE_NAME), new JMSSender.MessageCreator() {
                        public Message createMessage(Session session) throws JMSException {
                            ObjectMessage message = session.createObjectMessage();
                            message.setObject(params);
                            return message;
                        }
                    });
        } catch (Exception e) {
            throw new MedlineDownloaderException();
        }
    }
}
