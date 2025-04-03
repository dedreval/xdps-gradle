package com.wiley.cms.cochrane.services.integration;

import java.net.URI;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.text.ParseException;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.BiFunction;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumResponseChecker;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.res.ServerType;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.ftp.FtpInteraction;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/9/2022
 */
public class EndPoint implements IEndPoint {

    public static final IEndPoint EP_ARCHIE = new IEndPoint() {
        @Override
        public String getName() {
            return PackageChecker.ARCHIE;
        }

        @Override
        public boolean isEnabled() {
            return CochraneCMSPropertyNames.isArchieDownload();
        }

        @Override
        public boolean isTestMode() {
            return CochraneCMSPropertyNames.isArchieDownloadTestMode();
        }

        @Override
        public String getHostName() {
            return EndPoint.getHostName(CochraneCMSPropertyNames.getArchieDownloadService());
        }

        @Override
        public String getServiceName() {
            return WebServiceUtils.PUBLISHING_SERVICE;
        }

        @Override
        public String[] getSchedule() {
            String schedule = CochraneCMSPropertyNames.getArchieDownloadSchedule();
            return new String[] {schedule, getNextValidTimeAfter(schedule)};
        }
    };

    public static final IEndPoint EP_ARIES = new IEndPoint() {
        @Override
        public String getName() {
            return PackageChecker.ARIES + " WILEY";
        }

        @Override
        public boolean isEnabled() {
            return CochraneCMSPropertyNames.isAriesDownloadSFTP();
        }

        @Override
        public String getHostName() {
            IEndPointLocation location = getLocation();
            return location != null ? location.getServerType().getHost() : null;
        }

        @Override
        public List<String> getSpecialOptions() {
            return CochraneCMSPropertyNames.isAriesVerificationSupported()
                    ? Collections.singletonList("verification folder supported") : Collections.emptyList();
        }

        @Override
        public Boolean testCall() {
            return testFtpCall(getLocation(), true);
        }

        @Override
        public String[] getSchedule() {
            String schedule = CochraneCMSPropertyNames.getAriesDownloadSchedule();
            return new String[] {schedule, getNextValidTimeAfter(schedule)};
        }

        private IEndPointLocation getLocation() {
            return PublishProfile.getProfile().get().getWhenReadyPubLocation(
                PubType.MAJOR_TYPE_ARIES_INT, PubType.TYPE_ARIES_D, BaseType.getCDSR().get().getId());
        }
    };

    public static final IEndPoint EP_CCA = new IEndPoint() {
        @Override
        public String getName() {
            return PackageChecker.APTARA;
        }

        @Override
        public boolean isEnabled() {
            return CochraneCMSPropertyNames.isCCADownload();
        }

        @Override
        public String getHostName() {
            return EndPoint.getHostName(CochraneCMSPropertyNames.getCCADownloadUrl());
        }

        @Override
        public Boolean testCall() {
            //try {
            //    URIWrapper uri = new URIWrapper(new URI(CochraneCMSPropertyNames.getCCADownloadUrl()));
            //    InputUtils.getConnection(uri);
            //    return true;
            //
            //} catch (Exception e) {
            //    LOG.warn(e.getMessage());
            //    return false;
            //}
            return null;
        }

        @Override
        public String[] getSchedule() {
            String schedule = CochraneCMSPropertyNames.getCCADownloadSchedule();
            return new String[] {schedule, getNextValidTimeAfter(schedule)};
        }
    };

    public static final IEndPoint EP_LIT_EVENTS = new IEndPoint() {
        @Override
        public String getName() {
            return "literatum events";
        }
        @Override
        public boolean isEnabled() {
            return CochraneCMSPropertyNames.isLiteratumIntegrationEnabled();
        }
        @Override
        public boolean isTestMode() {
            return CochraneCMSPropertyNames.isLiteratumPublishTestModeDev();
        }
        @Override
        public String getHostName() {
            return null;
        }
        @Override
        public String getServiceName() {
            return PublishProfile.PUB_PROFILE.get().getLiteratumUrl();
        }
        @Override
        public Boolean testCall() {
            try {
                LiteratumResponseChecker.Responder.instance().testConnection();
                return true;
            } catch (Exception e) {
                LOG.warn(e.getMessage());
                return false;
            }
        }
        @Override
        public List<String> getSpecialOptions() {
            return !CochraneCMSPropertyNames.isLiteratumEventPublishTestMode() ? Collections.emptyList()
                : (CochraneCMSPropertyNames.isLiteratumPublishTestModeDev() ? Collections.singletonList(
                    "events emulation supported (DEV)") : Collections.singletonList("events emulation supported"));
        }
    };

    public static final IEndPoint EP_KAFKA = new IEndPoint() {
        @Override
        public String getName() {
            return "kafka";
        }

        @Override
        public boolean isEnabled() {
            //return KafkaMessageProducer.status().open;
            //try {
            //    return CochraneCMSPropertyNames.lookupFlowLogger().getKafkaProducer().isOpen();
            //} catch (Exception e) {
            //    return false;
            //}
            return CochraneCMSPropertyNames.isKafkaProducerEnabled();
        }

        @Override
        public boolean isTestMode() {
            //return KafkaMessageProducer.status().testMode;
            //try {

            //    return CochraneCMSPropertyNames.lookupFlowLogger().getKafkaProducer().isTestMode();
            //} catch (Exception e) {
            //    return false;
            //}
            return false;
        }

        @Override
        public String getHostName() {
            return CochraneCMSPropertyNames.getKafkaBootstrapServers();
        }
    };

    public static final IEndPoint EP_SF = new IEndPoint() {
        @Override
        public String getName() {
            return "snow flake";
        }

        @Override
        public String getName(BaseType bt) {
            return getName() + " " + bt.getShortName();
        }

        @Override
        public boolean isEnabled() {
            return CochraneCMSPropertyNames.getSnowFlakeSwitch().get().asBoolean();
        }

        @Override
        public boolean isEnabled(BaseType bt) {
            return isEnabled() && bt.hasSFLogging();
        }

        @Override
        public String getHostName() {
            return null;
        }
    };

    public static final IEndPoint EP_QAS = new IEndPoint() {
        @Override
        public String getName() {
            return "QA module";
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getHostName() {
            return EndPoint.getHostName(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.QAS_SERVICE_URL));
        }

        @Override
        public Boolean testCall() {
            try {
                WebServiceUtils.getProvideQa().getVersion();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    };

    public static final IEndPoint EP_RENDER = new IEndPoint() {
        @Override
        public String getName() {
            return "Render module";
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getHostName() {
            return EndPoint.getHostName(
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.RENDERER_SERVICE_URL));
        }

        @Override
        public Boolean testCall() {
            try {
                WebServiceUtils.getProvideRendering().getVersion();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    };

    public static final IEndPoint EP_DB = new IEndPoint() {

        @Override
        public String getName() {
            return "DATABASE";
        }

        @Override
        public String getHostName() {
            String result = "";
            try {
                Context initialContext = new InitialContext();
                DataSource dataSource = (DataSource) initialContext.lookup(DB_JNDI_NAME);
                Connection connection = dataSource.getConnection();
                DatabaseMetaData databaseMetaData = connection.getMetaData();
                result = databaseMetaData.getURL().split("/")[2];
                connection.close();
            } finally {
                return result;
            }
        }

        @Override
        public boolean isEnabled() {
            boolean result = false;
            Connection connection;
            try {
                Context initialContext = new InitialContext();
                DataSource dataSource = (DataSource) initialContext.lookup(DB_JNDI_NAME);
                connection = dataSource.getConnection();
                result = connection.isValid(TIMEOUT);
                connection.close();
            } finally {
                return result;
            }
        }
    };

    public static final IEndPoint EP_NS = new IEndPoint() {
        @Override
        public String getName() {
            return "NS";
        }

        @Override
        public boolean isEnabled() {
            MessageSender.init();
            return MessageSender.isEnabled() && !MessageSender.isToLogOnly();
        }

        @Override
        public String getHostName() {
            try {
                return new URI(CochraneCMSPropertyNames.getNotificationAppUrl()).getHost();
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public String getServiceName() {
            return null;
        }
    };

    private static final Logger LOG = Logger.getLogger(EndPoint.class);
    private static final String DB_JNDI_NAME = "java:jboss/datasources/CochraneCmsDS";
    private static final int TIMEOUT = 7;
    private final IEndPointLocation epl;
    private final String name;

    EndPoint(String name, IEndPointLocation epl) {
        this.name = name;
        this.epl = epl;
    }

    public static IEndPoint create(String name, IEndPointLocation epl) {
        return new EndPoint(name, epl);
    }

    public static Collection<IEndPoint> getByPublication(BaseType baseType, String publicationType,
            BiFunction<String, IEndPointLocation, IEndPoint> creator) {

        Collection<IEndPoint> ret =  new ArrayList<>();

        getByPublication(baseType, publicationType, false, creator, ret);
        getByPublication(baseType, publicationType, true, creator, ret);

        return ret;
    }

    private static void getByPublication(BaseType bt, String publicationType, boolean entire,
                                         BiFunction<String, IEndPointLocation, IEndPoint> creator,
                                         Collection<IEndPoint> ret) {
        PublishProfile pp = PublishProfile.getProfile().get();
        IEndPointLocation path = pp.getPubLocation(publicationType, bt.getId(), entire, false);
        if (path != null) {
            addByPublication(bt, path, publicationType, entire, false, creator, ret);
            IEndPointLocation replication = path.getReplication();
            if (replication != null) {
                addByPublication(bt, replication, publicationType, entire, true, creator, ret);
            }
        }
    }

    private static void addByPublication(BaseType bt, IEndPointLocation path, String publicationType, boolean entire,
                                         boolean replicate, BiFunction<String, IEndPointLocation, IEndPoint> creator,
                                         Collection<IEndPoint> ret) {
        String name = path.getPubType().getMajorType().toUpperCase().equals(publicationType.toUpperCase())
                ? publicationType : path.getPubType().getMajorType() + "." + publicationType;
        ret.add(creator.apply(String.format("%s%-10s %-10s%s", replicate ? "[replica] " : "", name, bt.getShortName(),
                entire ? " (entire)" : "").trim(), path));
    }

    public final String getName() {
        return name;
    }

    public final IEndPointLocation getEndPointLocation() {
        return epl;
    }

    public boolean isEnabled() {
        return true;
    }

    public final String getHostName() {
        return epl.getServerType().getHost();
    }

    public String getServiceName() {
        return epl.getConnectionType().exists() ? epl.getConnectionType().getUrl() : null;
    }

    Boolean testFtpCall(boolean sftp) {
        return testFtpCall(epl, sftp);
    }

    private static Boolean testFtpCall(IEndPointLocation epl, boolean sftp) {
        //FtpInteraction interaction = null;
        //Boolean ret;
        //try {
        //    interaction = checkFtpInteraction(epl, sftp);
        //    ret = interaction != null ? Boolean.TRUE : null;

        //} catch (Exception e) {
        //    LOG.warn(e.getMessage());
        //    ret = Boolean.FALSE;
        //} finally {
        //    InputUtils.closeConnection(interaction);
        //}
        //return ret;
        return null;
    }

    static FtpInteraction checkFtpInteraction(IEndPointLocation epl, boolean sftp) throws Exception {
        ServerType serverType = epl.getServerType();
        if (serverType.isLocalHost()) {
            return null;
        }
        FtpInteraction interaction = sftp
            ? InputUtils.getSftpConnection(serverType.getHost(), serverType.getPort(), serverType.getUser(),
                serverType.getPassword(), serverType.getTimeout())
            : InputUtils.getFtpConnection(serverType.getHost(), serverType.getPort(), serverType.getUser(),
                serverType.getPassword(), serverType.getTimeout());

        String folder = epl.getFolder();
        changeFolder(interaction, folder == null || folder.isEmpty() ? FilePathCreator.SEPARATOR : folder);
        return interaction;
    }

    static void changeFolder(FtpInteraction interaction, String folder) throws Exception {
        try {
            if (folder != null) {
                interaction.changeDirectory(folder);
            }
        } catch (Exception e){
            InputUtils.closeConnection(interaction);
            throw e;
        }
    }

    static String getNextValidTimeAfter(String expression) {
        try {
            Date date = Now.getNextValidTimeAfter(expression, new Date());
            if (date != null) {
                return Now.formatDateUTC(date);
            }

        } catch (ParseException | DateTimeException e) {
            LOG.warn(e.getMessage());
        }
        return Constants.NA;
    }

    private static String getHostName(String uriStr) {
        try {
            URI uri = new URI(uriStr);
            return uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();

        } catch (Exception e) {
            LOG.warn(e.getMessage());
            return null;
        }
    }

}
