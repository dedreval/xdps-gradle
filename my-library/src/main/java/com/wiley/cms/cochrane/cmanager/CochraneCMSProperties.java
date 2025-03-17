package com.wiley.cms.cochrane.cmanager;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.kafka.KafkaMessageProducer;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesConnectionManager;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.HWClient;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.SemanticoMessenger;
import com.wiley.cms.cochrane.cmanager.res.CmsResourceInitializer;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.translated.TransformersPool;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.jmx.JMXHolder;
import com.wiley.cms.process.task.IDownloader;
import com.wiley.cms.process.task.ITaskManager;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.TransformerObjectPool;
import com.wiley.tes.util.UserFriendlyMessageBuilder;
import com.wiley.tes.util.WileyDTDXMLOutputter;
import com.wiley.tes.util.res.ResourceManager;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
@Local(CochraneCMSPropertiesMBean.class)
@Singleton
@Startup
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class CochraneCMSProperties extends JMXHolder implements CochraneCMSPropertiesMBean {
    private static final Logger LOG = Logger.getLogger(CochraneCMSProperties.class);

    private static String jbossServerConfigUrl = System.getProperty("jboss.server.config.url") + "cochrane-props/cms/";
    private static Properties props;

    @EJB(beanName = "TaskDownloader")
    private IDownloader td;

    @EJB(beanName = "ArchieDownloader")
    private IDownloader archieDownloader;

    @EJB(beanName = "AriesDownloader")
    private IDownloader ariesDownloader;

    @EJB(beanName = "CcaPackageDownloader")
    private IDownloader ccaDownloader;

    @EJB(beanName = "LiteratumResponseChecker")
    private IDownloader lit;

    @EJB(beanName = "UdwFeedDataDeliveryTrigger")
    private IDownloader udw;

    @EJB(beanName = "CentralDownloader")
    private IDownloader centralDownloader;

    @EJB(beanName = "RecordCache")
    private IRecordCache rc;

    @EJB(lookup = ProcessHelper.LOOKUP_TASK_MANAGER)
    private ITaskManager tm;

    @EJB(beanName = "CochraneContentSupport")
    private ICochraneContentSupport ccs;

    public CochraneCMSProperties() {
        resetPrefix();
    }

    public static String getConfigUrl() {
        return jbossServerConfigUrl;
    }

    /**
     * @param key          - property name
     * @param defaultValue - default value if property not exists
     * @return "" if property not found, so client not need check returned value for NULL
     */
    public static String getProperty(String key, String defaultValue) {
        String prop = getProperty(key, false);
        return (prop == null) ? defaultValue : prop;
    }

    public static int getIntProperty(String key, int defaultValue) {

        String prop = getProperty(key, false);
        if (prop != null) {
            try {
                return Integer.valueOf(prop);
            } catch (Exception e) {
                LOG.error(String.format("%s: can not parse %s to int", key, prop), e);
            }
        }
        return defaultValue;
    }

    public static long getLongProperty(String key, int defaultValue) {

        String prop = getProperty(key, false);
        if (prop != null) {
            try {
                return Long.valueOf(prop);
            } catch (Exception e) {
                LOG.error(String.format("%s: can not parse %s to long", key, prop), e);
            }
        }
        return defaultValue;
    }

    public static boolean getBoolProperty(String key, boolean defaultValue) {

        String prop = getProperty(key, false);
        if (prop != null) {
            try {
                return Boolean.valueOf(prop);
            } catch (Exception e) {
                LOG.error(String.format("%s: can not parse %s to boolean", key, prop), e);
            }
        }
        return defaultValue;
    }

    /**
     * @param key - property name
     * @return "" if property not found, so client not need check returned value for NULL
     */
    public static String getProperty(String key) {
        return getProperty(key, true);
    }

    /**
     * @param key     The property name.
     * @param showMsg If property value is null and it is TRUE then the "not exists in config.." message will be shown.
     * @return The property value.
     */
    public static String getProperty(String key, boolean showMsg) {
        if (props == null) {
            init();
        }
        if (showMsg && props.getProperty(key) == null) {
            LOG.warn(key + " not exists in config files");
            return "";
        } else {
            return props.getProperty(key);
        }
    }

    public static String getProperty(String key, Map<String, String> parameters) {
        return replaceProperty(getProperty(key), parameters);
    }

    public static String getProperty(String templateName, String key, String value) {
        return replaceProperty(getProperty(templateName), key, value);
    }

    public static void setProperty(String key, String value) {
        if (value == null) {
            props.remove(key);
            return;
        }
        props.put(key, value);
    }

    public static String replaceProperty(String result, Map<String, String> parameters) {
        String ret = result;
        if (!"".equals(ret) && parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (entry.getValue() != null) {
                    ret = replaceProperty(ret, entry.getKey(), entry.getValue());
                }
            }
        }
        return ret;
    }

    public static String replaceProperty(String template, String key, String value) {
        return template.replaceAll("%" + key + "%", Matcher.quoteReplacement(value));
    }

    public static Map getProperties() {
        if (props == null) {
            init();
        }
        return props;
    }

    public static void refresh() {
        LOG.debug("refresh");
        init();
    }

    private static void init() {
        LOG.debug("CochraneCMSProperties init");
        props = new Properties();

        File folder;
        try {
            folder = new File(new URL(jbossServerConfigUrl).getPath());
            if (!folder.isDirectory()) {
                throw new Exception("");
            }
        } catch (Exception e) {
            LOG.error("Folder " + jbossServerConfigUrl + " doesn't correct but it need to be for loading properties "
                    + e.getMessage());
            return;
        }

        File[] files = folder.listFiles();

        List<URL> fileURLList = new ArrayList<URL>();

        for (File file : files) {
            if (!file.getName().equals(".") && !file.getName().equals("..") && file.getName().endsWith(".properties")) {
                try {
                    fileURLList.add(file.toURI().toURL());
                } catch (MalformedURLException e) {
                    LOG.error(e, e);
                }
            }
        }

        for (URL file : fileURLList) {
            try {
                props.load(file.openStream());
            } catch (IOException e) {
                LOG.error(e, e);
            }
        }
    }

    @PostConstruct
    public void start() {

        registerInJMX();

        init();
        RepositoryFactory.refresh();
        CmsResourceInitializer.initialize();

        rc.update();
        tm.update();
        //tm.registerSupportingClasses(JatsConversionHandler.class, DbHandler.class);

        updateDownLoaders();

        HWClient.Factory.getFactory();

        if (CochraneCMSPropertyNames.isKafkaProducerAutResendMode()) {
            ccs.reproduceFlowLogEvents();
        }
    }

    @PreDestroy
    public void stop() {
        super.unregisterFromJMX();
    }

    @Lock(LockType.WRITE)
    public void update() {
        LOG.debug("updated");

        SemanticoMessenger.reset();

        init();
        ResourceManager.instance().loadXmlResources(true, CmsResourceInitializer.getResourceFolders());

        reloadComponentFactories();

        MessageSender.init();
        RepositoryFactory.refresh();
        TransformersPool.refresh();
        WileyDTDXMLOutputter.refresh();

        TransformerObjectPool.Factory.instance().clearMaps();
                
        UserFriendlyMessageBuilder.load();

        tm.update();

        updateDownLoaders();

        WebServiceUtils.reset();
        AriesConnectionManager.reset();
        KafkaMessageProducer.reset();
    }

    private void updateDownLoaders() {
        td.update();
        archieDownloader.update();
        ariesDownloader.update();
        ccaDownloader.update();
        centralDownloader.update();
        lit.update();
        udw.update();
    }

    public String printState() {
        String ret = PublishProfile.getProfileState();
        LOG.info(ret);
        return ret;
    }

    public void reloadComponentFactories() {
        ccs.reloadComponentFactories();
    }
}

