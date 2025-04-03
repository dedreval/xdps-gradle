package com.wiley.cms.cochrane.cmanager.res;

import java.net.MalformedURLException;
import java.net.URL;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.TransferMsg;

import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.IResourceInitializer;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceInitializer;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.Settings;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 28.03.13
 */
public class CmsResourceInitializer extends ResourceInitializer implements IResourceInitializer {
    private static final Logger LOG = Logger.getLogger(CmsResourceInitializer.class);

    @Override
    public URL[] getResourceRoot() {
        return getResourceFolders();
    }

    public static URL[] getResourceFolders() {
        try {
            return new URL[] {getMainResourceFolder(), getTransAbsFolder()};
        } catch (Exception e) {
            LOG.error(e);
            return new URL[0];
        }
    }

    public static URL getMainResourceFolder() throws MalformedURLException {
        return getUrl(getResPath(CochraneCMSProperties.getConfigUrl()));
    }

    private static URL getTransAbsFolder() throws MalformedURLException {
        return getUrl(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.COCHRANE_RESOURCES) + "transabs");
    }

    @Override
    public void initialize(ResourceManager loader)  {

        super.initialize(loader);

        BaseType.register(loader);
        PubType.register(loader);
        ServerType.register(loader);
        PublishProfile.register(loader);
        ContentAccessType.register(loader);
        PathType.register(loader);
        TransAbs.register(loader);
        CheckList.register(loader);
        TransitionSet.register(loader);
        RevmanTransitions.register(loader);
        RetryType.register(loader);
        ConnectionType.register(loader);
        PackageType.register(loader);
        HookType.register(loader);

        TransferMsg.register(loader);
    }

    public static void initialize() {
        ResourceManager.instance().initialize(new CmsResourceInitializer());
    }

    public static Res<Settings> getProcessingInstructions() {
        return Settings.getSettings("processing-instructions");
    }

    public static Res<Settings> getCDNumberPrefixMapping() {
        return Settings.getSettings("cdnumber-prefix-mapping");
    }

    public static Res<Settings> getDatabaseNumberMapping() {
        return Settings.getSettings("database-number-mapping");
    }

    public static Res<Settings> getRevmanTypesMapping() {
        return getSettings("revman-types-mapping");
    }

    public static Res<Settings> getContentTypesMapping() {
        return getSettings("article-types-mapping");
    }

    public static Res<Settings> getJatsStagesMapping() {
        return getSettings("jats-stages-mapping");
    }

    public static Res<Settings> getWebUISettings() {
        return getSettings("WEB-UI");
    }

    public static Res<Settings> getHWDbMapping() {
        return getSettings("HW-db-mapping");
    }

    public static Res<Settings> getHWDoiDbMapping() {
        return getSettings("HW-doi-db-mapping");
    }


    public static Res<Settings> getHWFrequency() {
        return getSettings("HW-frequency");
    }

    public static Res<Settings> getLiteratumDbMapping() {
        return getSettings("WOLLIT-db-mapping");
    }

    public static Res<Settings> getWOLLITDoiDbMapping() {
        return getSettings("WOLLIT-doi-db-mapping");
    }

    public static Res<Settings> getDsDoiDbMapping() {
        return getSettings("DS-doi-db-mapping");
    }

    public static Res<Settings> getUdwDbMapping() {
        return getSettings("UDW-db-mapping");
    }

    public static Res<Settings> getAriesArticleTypeDbMapping() {
        return getSettings("aries-article-types-db-mapping");
    }

    public static Res<Property> getTakeIntermediateResults4FopPdf() {
        return Property.get("cms.cochrane.render.fop.take-intermediate-results");
    }

    public static Res<Settings> getSettings(String sid) {
        Res<Settings> ret = Settings.getSettings(sid);
        checkInitialization(ret, sid);
        return ret;
    }

    private static void checkInitialization(Res ret, String sid) {
        if (!ret.exist()) {
            LOG.warn(String.format("%s hasn't been initialized yet", sid));
            initialize();
        }
    }
}
