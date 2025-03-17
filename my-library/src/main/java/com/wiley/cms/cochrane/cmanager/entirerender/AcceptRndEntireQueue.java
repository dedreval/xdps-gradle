package com.wiley.cms.cochrane.cmanager.entirerender;

import java.io.IOException;
import java.util.List;

import javax.ejb.EJB;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.AcceptRenderingQueue;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 15.03.11
 */
//@MessageDriven(
//    activationConfig = {
//           @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
//           @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/entire_accept_rendering"),
//           @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "" + IRenderingProvider.MAX_SESSION)
//        }, name = "AcceptRenderingEntireQueue")
//@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
@Deprecated
public class AcceptRndEntireQueue extends AcceptRenderingQueue implements MessageListener {
    {
        log = Logger.getLogger(AcceptRndEntireQueue.class);
    }

    @EJB
    private IActivityLogService logService;
    private final String theLogUser = CochraneCMSProperties.getProperty(
            CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);
    private String otherDir = "";

    @Override
    public void onMessage(Message message) {
        log.trace("onMessage starts");
        try {
            if (!(message instanceof TextMessage)) {
                throw new JMSException("TextMessage expected!");
            }
            String text = ((TextMessage) message).getText();
            int ind = text.indexOf(":");
            jobId = Integer.decode(text.substring(0, ind));
            renderingResult = text.substring(ind + 1);
            log.debug(getClass().getSimpleName() + " jobid=" + jobId);
            try {
                parse();
                recs = result.getRecords();
                if (recs == null) {
                    logService.error(ActivityLogEntity.EntityLevel.FILE, ILogEvent.RND_FAILED,
                            -1, "unknown", theLogUser, "jobId=" + jobId);
                    log.debug("In parsing results not found records");
                    return;
                }

                otherDir = CochraneCMSPropertyNames.getRenderEntireDir();
                if (otherDir.length() > 0) {
                    otherDir += FilePathCreator.SEPARATOR;
                }
                loadContent(result.getDbName().equals(CochraneCMSPropertyNames.getCDSRDbName()));
                log.debug(getClass().getSimpleName() + " has finished jobid=" + jobId);

                clearJobResult();
                removeAbstractsTmp(recs);
            } catch (Exception e) {
                log.error(e);
            }

        } catch (JMSException e) {
            log.error(e);
        }
    }

    private void removeAbstractsTmp(List<Record> recs) throws Exception {
        IRepository rps = RepositoryFactory.getRepository();
        for (Record r : recs) {
            if (!r.isCompleted()) {
                continue;
            }

            if (r.getRecordSourceUri().contains("abstracts-tmp")) {
                rps.deleteFile(r.getRecordSourceUri());
                if (RepositoryUtils.getRealFile(r.getRecordSourceUri().replace(FilePathCreator.XML_EXT, "")).exists()) {
                    rps.deleteDir(r.getRecordSourceUri().replace(FilePathCreator.XML_EXT, ""));
                }
            }
        }
    }

    private boolean hasOtherDir() {
        return otherDir.length() > 0;
    }

    @Override
    protected String getBaseDirPath(String issue, String dbName, RenderingPlan plan) {
        return FilePathCreator.getRenderedDirPathEntire(dbName, "", plan);
    }

    protected String getDirPath(String dbName, Record record, RenderingPlan plan) {
        String uri = record.getRecordSourceUri();
        return uri.contains(FilePathCreator.PREVIOUS_DIR)
                ? FilePathCreator.getRenderedDirPathPrevious(dbName, uri, null, plan)
                : FilePathCreator.getRenderedDirPathEntire(dbName, null, plan);
    }

    @Override
    protected void loadRecord(Record record, String issue, String dbName, boolean isSameServer, String repository,
        String webUrl, String rndRepository) throws IOException {

        String baseRecordPath = getDirPath(dbName, record, result.getPlan());
        String recordName = record.getName();

        String path = repository + FilePathCreator.SEPARATOR;
        String basePath = hasOtherDir() ? path + otherDir : path;

        FileUtils.deleteDirectory(basePath + FilePathCreator.addRecordNameToPath(
            baseRecordPath, recordName, dbName) + FilePathCreator.SEPARATOR);

        for (String url : record.getFilesList()) {

            String newFilePath = FilePathCreator.getRenderedFilePathEntireByPlan(
                baseRecordPath, dbName, recordName, url);
            putFile(hasOtherDir() ? otherDir + newFilePath : newFilePath, isSameServer, url, webUrl, rndRepository);
        }
    }
}
