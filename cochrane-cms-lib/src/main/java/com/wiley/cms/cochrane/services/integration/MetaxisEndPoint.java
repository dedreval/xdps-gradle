package com.wiley.cms.cochrane.services.integration;

import java.net.URI;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.URIWrapper;
import com.wiley.tes.util.ftp.FtpInteraction;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/9/2022
 */
public final class MetaxisEndPoint extends EndPoint {
    private static final Logger LOG = Logger.getLogger(MetaxisEndPoint.class);

    private MetaxisEndPoint() {
        super(PackageChecker.METAXIS, getPubLocationPath(true));
    }

    public static IEndPoint create() {
        return new MetaxisEndPoint();
    }

    @Override
    public boolean isEnabled() {
        return CochraneCMSPropertyNames.isCentralDownload();
    }

    @Override
    public Boolean testCall() {
        return testFtpCall(true);
    }

    @Override
    public String[] getSchedule() {
        String schedule = CochraneCMSPropertyNames.getCentralDownloadSchedule();
        return new String[] {schedule, getNextValidTimeAfter(schedule)};
    }

    private static PublishProfile.PubLocationPath getPubLocationPath(boolean aut) {
        return PublishProfile.getProfile().get().getWhenReadyPubLocation(
            PubType.MAJOR_TYPE_CENTRAL_SRC, aut ? PubType.TYPE_CENTRAL_SRC_AUT : PubType.TYPE_CENTRAL_SRC,
                BaseType.getCentral().get().getId());
    }

    public static FtpInteraction getCentralInteraction(boolean aut) throws Exception {
        if (!aut) {
            String path = CochraneCMSProperties.getProperty("cms.cochrane.central.crs.uri", "");
            if (!path.isEmpty()) {
                URIWrapper uri = new URIWrapper(new URI(path));
                FtpInteraction interaction = InputUtils.getConnection(uri);
                changeFolder(interaction, uri.getGoToPath() != null && !uri.getGoToPath().isEmpty()
                        && !uri.getGoToPath().equals("/") ? uri.getGoToPath() : null);
                return interaction;
            }
        }
        PublishProfile.PubLocationPath path = getPubLocationPath(aut);
        if (path != null) {
            return checkFtpInteraction(path, true);
        }
        throw new CmsException("no configurable location for CENTRAL downloading found");
    }
}
