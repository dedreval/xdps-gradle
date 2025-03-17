package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.cmanager.central.IPackageDownloader;
import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.publish.IPublishService;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.HWClient;
import com.wiley.cms.cochrane.cmanager.render.IRenderManager;
import com.wiley.cms.cochrane.converter.IConverterAdapter;
import com.wiley.cms.cochrane.process.CMSProcessManager;
import com.wiley.cms.cochrane.process.ConversionManager;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.process.IConversionManager;
import com.wiley.cms.cochrane.process.IOperationManager;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.process.jms.IQueueProvider;
import com.wiley.cms.notification.INotificationManager;
import com.wiley.cms.process.AbstractBeanFactory;
import com.wiley.cms.process.AbstractFactory;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10/23/2017
 */
public class CochraneCMSBeans {

    private static AbstractFactory<ICochranePageManager> pageManager;
    private static AbstractFactory<IQueueProvider> queueProvider;
    private static AbstractFactory<IPublishStorage> publishStorage;
    private static AbstractFactory<INotificationManager> notificationManager;
    private static AbstractFactory<ICMSProcessManager> processManager;
    private static AbstractFactory<IConversionManager> convManager;
    private static AbstractFactory<IRecordManager> recordManager;
    private static AbstractFactory<IRenderManager> renderManager;
    private static AbstractFactory<IOperationManager> opManager;
    private static AbstractFactory<IPackageDownloader> packageDownloader;
    private static AbstractFactory<IPublishService> publishService;
    private static AbstractFactory<IRecordCache> recordCache;
    private static AbstractFactory<IConverterAdapter> converter;
    private static AbstractFactory<ICochraneContentSupport> contentSupport;
    private static AbstractFactory<IVersionManager> versionManager;
    private static AbstractFactory<IDeliveringService> deliveringService;
    private static AbstractFactory<IFlowLogger> flowLogger;

    static {
        initBeans();
    }

    private CochraneCMSBeans() {
    }

    public static IOperationManager getOperationManager() {
        return opManager.get();
    }

    public static IRecordManager getRecordManager() {
        return recordManager.get();
    }

    public static IRenderManager getRenderManager() {
        return renderManager.get();
    }

    public static ICochranePageManager getPageManager() {
        return pageManager.get();
    }

    public static IConversionManager getConversionManager() {
        return convManager.get();
    }

    public static IQueueProvider getQueueProvider() {
        return queueProvider.get();
    }

    public static IPublishStorage getPublishStorage() {
        return publishStorage.get();
    }

    public static INotificationManager getNotificationManager() {
        return notificationManager.get();
    }

    public static ICMSProcessManager getCMSProcessManager() {
        return processManager.get();
    }

    public static IPublishService getPublishService() {
        return publishService.get();
    }

    public static IPackageDownloader getPackageDownloader() {
        return packageDownloader.get();
    }

    public static IRecordCache getRecordCache() {
        return recordCache.get();
    }

    public static IConverterAdapter getConverter() {
        return converter.get();
    }

    public static ICochraneContentSupport getContentSupport() {
        return contentSupport.get();
    }

    public static IVersionManager getVersionManager() {
        return versionManager.get();
    }

    public static IDeliveringService getDeliveringService() {
        return deliveringService.get();
    }

    public static HWClient getHWClient() {
        return HWClient.Factory.getFactory().get();
    }

    public static IFlowLogger getFlowLogger() {
        return flowLogger.get();
    }

    private static void initBeans() {
        pageManager = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("CochranePageManager", ICochranePageManager.class));

        convManager = AbstractBeanFactory.getFactory(ConversionManager.LOOKUP_NAME);

        queueProvider = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("QueueProvider", IQueueProvider.class));

        publishStorage = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("PublishStorage", IPublishStorage.class));

        notificationManager = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("NotificationManager", INotificationManager.class));

        processManager = AbstractBeanFactory.getFactory(CMSProcessManager.LOOKUP_NAME);

        recordManager = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("RecordManager", IRecordManager.class));

        renderManager = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("RenderManager", IRenderManager.class));

        opManager = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("OperationManager", IOperationManager.class));

        packageDownloader = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("PackageDownloader", IPackageDownloader.class));

        publishService = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("PublishService", IPublishService.class));

        recordCache = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("RecordCache", IRecordCache.class));

        converter = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("ConverterAdapter", IConverterAdapter.class));

        contentSupport = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("CochraneContentSupport", ICochraneContentSupport.class));

        versionManager = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("VersionManager", IVersionManager.class));

        deliveringService = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("DeliveringService", IDeliveringService.class));

        flowLogger = AbstractBeanFactory.getFactory(
            CochraneCMSPropertyNames.buildLookupName("FlowLogger", IFlowLogger.class));
    }

    public static void reload() {
        pageManager.reload();
        convManager.reload();
        queueProvider.reload();
        publishStorage.reload();
        notificationManager.reload();
        processManager.reload();
        recordManager.reload();
        renderManager.reload();
        opManager.reload();
        packageDownloader.reload();
        publishService.reload();
        recordCache.reload();
        converter.reload();
        contentSupport.reload();
        versionManager.reload();
        deliveringService.reload();
        HWClient.Factory.getFactory().reload();
        flowLogger.reload();
    }
}
