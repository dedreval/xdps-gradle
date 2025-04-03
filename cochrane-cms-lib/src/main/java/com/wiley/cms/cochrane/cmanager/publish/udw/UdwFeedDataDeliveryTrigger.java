package com.wiley.cms.cochrane.cmanager.publish.udw;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishTypeEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishService;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.task.BaseDownloader;
import com.wiley.cms.process.task.IDownloader;
import com.wiley.tes.util.Logger;
import org.quartz.StatefulJob;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 05.02.2019
 */
@Singleton
@Local(IDownloader.class)
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class UdwFeedDataDeliveryTrigger extends BaseDownloader implements StatefulJob {
    private static final Logger LOG = Logger.getLogger(UdwFeedDataDeliveryTrigger.class);

    @EJB
    private IPublishService publishService;

    @EJB
    private IPublishStorage publishStorage;

    @PostConstruct
    public void start() {
        startDownloader(
                getClass().getSimpleName(),
                CochraneCMSProperties.getProperty("cms.cochrane.udw.auto_delivery.schedule", "0 0 1 * * ?"),
                true,
                false);
    }

    @Override
    protected boolean canDownload() {
        return CochraneCMSProperties.getBoolProperty("cms.cochrane.udw.auto_delivery.enabled", false);
    }

    @Override
    public void update() {
        start();
    }

    @PreDestroy
    public void stop() {
        stopDownloader(getClass().getSimpleName());
    }

    @Override
    protected void download() {
        int pubTypeId = PublishTypeEntity.getNamedEntityId(
                PublishProfile.buildExportDbName(PubType.TYPE_UDW, false, false));
        List<BaseType> dbTypes = BaseType.getAll().stream()
                .map(it -> it.get())
                .filter(it -> it.getPubInfo() != null && it.getPubInfo(PubType.TYPE_UDW) != null)
                .collect(Collectors.toList());
        for (BaseType dbType : dbTypes) {
            String dbTypeId = dbType.getId();
            PubType pubType = dbType.getPubInfo(PubType.TYPE_UDW).getType();
            if (anotherUdwDeliveryInProgress(pubTypeId, dbTypeId)) {
                LOG.debug(format("UDW delivery for %s was skipped because of another active delivery", dbTypeId));
                continue;
            }

            ArticlesForUdwFeedFinder finder = new ArticlesForUdwFeedFinder(dbTypeId);
            Optional<List<Integer>> articles = finder.find();
            if (!articles.isPresent()) {
                LOG.debug(format("UDW delivery for %s will be initiated for all articles", dbTypeId));
            } else if (articles.get().isEmpty()) {
                LOG.debug(format("UDW delivery for %s was skipped since no candidates", dbTypeId));
                continue;
            } else {
                LOG.debug(format("UDW delivery for %s will be initiated for %d articles",
                        dbTypeId, articles.get().size()));
            }

            int pubProcessId = createRecordPublishProcess(pubType, dbTypeId, articles);
            PublishWrapper publishWrapper = createPublishWrapper(pubType, dbTypeId, pubProcessId);
            publishService.publishEntireDb(dbTypeId, singletonList(publishWrapper));
        }
    }

    private boolean anotherUdwDeliveryInProgress(int pubTypeId, String dbTypeId) {
        PublishEntity publishEntity = publishStorage.takeEntirePublishByDbAndType(dbTypeId, pubTypeId);
        return publishEntity.isWaiting() || publishEntity.isGenerating() || publishEntity.isSending();
    }

    private int createRecordPublishProcess(PubType pubType, String dbTypeId, Optional<List<Integer>> articleIds) {
        if (!articleIds.isPresent()) {
            return DbEntity.NOT_EXIST_ID;
        }

        try {
            return PublishHelper.createRecordPublishProcess(pubType.getId(),
                    articleIds.get().toArray(new Integer[0])).getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create record publish process for " + dbTypeId, e);
        }
    }

    private PublishWrapper createPublishWrapper(PubType pubType, String dbTypeId, int pubProcId) {
        try {
            PublishWrapper publishWrapper = PublishWrapper.createEntirePublishWrapper(
                    pubType.getId(), PubType.MAJOR_TYPE_UDW, dbTypeId, false, new Date());
            publishWrapper.setGenerate(pubType.canGenerate());
            publishWrapper.setSend(true);
            publishWrapper.setUnpack(pubType.canUnpack());
            if (pubProcId != DbEntity.NOT_EXIST_ID) {
                publishWrapper.setRecordsProcessId(pubProcId);
            }
            return publishWrapper;
        } catch (CmsException e) {
            throw new RuntimeException(
                    format("Failed to create publishWrapper of %s for %s", pubType.getId(), dbTypeId), e);
        }
    }
}
