package com.wiley.cms.cochrane.cmanager.res;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;
import com.wiley.cms.cochrane.services.integration.IEndPointLocation;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.res.DataTable;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.03.13
 */
@XmlRootElement(name = PublishProfile.RES_NAME)
public class PublishProfile extends ResourceStrId {
    public static final Res<PublishProfile> PUB_PROFILE;

    static final String RES_NAME = "publication";

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(PublishProfile.class);

    private static final String PUBLISH_POSTFIX_WHEN_READY = "_WhenReady";
    private static final String PUBLISH_POSTFIX_BY_RECORDS = "_Incremental";
    private static final String ACTIVE_KEY = "active-profile";
    private static final String ALL_KEY = "*";

    private static final DataTable<String, PublishProfile> DT = new DataTable<>(RES_NAME);

    private static final String DB_NAME = "{db_name}";

    private static final String ATTR_BATCH = "batch-size";
    private static final String ATTR_MFT_URL = "mft-url";
    private static final String ATTR_LITERATUM_URL = "literatum-url";

    @XmlAttribute(name = "environment")
    private String env = "";

    @XmlAttribute(name = ATTR_BATCH)
    private int batch = 1;

    @XmlAttribute(name = ATTR_MFT_URL)
    private String mftUrl;

    @XmlAttribute(name = "mft-user")
    private String mftUser;

    @XmlAttribute(name = "mft-passw")
    private String mftPassw;

    @XmlAttribute(name = "mft-request")
    private String mftRequest;

    @XmlAttribute(name = "mft-response")
    private String mftResponse;

    @XmlAttribute(name = "hw-process-attempt")
    private int hwProcessAttemptCount = 2;

    @XmlAttribute(name = "hw-process-attempt-delay")
    private int hwProcessAttemptDelay = Now.calculateMillisInMinute() / 2;

    @XmlAttribute(name = ATTR_LITERATUM_URL)
    private String literatumUrl;

    @XmlAttribute(name = "literatum-user")
    private String literatumUser;

    @XmlAttribute(name = "literatum-passw")
    private String literatumPassword;

    @XmlAttribute(name = "literatum-response")
    private String literatumResponse;

    @XmlAttribute(name = "lit-process-attempt")
    private int litProcessAttemptCount = 2;

    @XmlAttribute(name = "lit-process-attempt-delay")
    private int litProcessAttemptDelay = Now.calculateMillisInMinute() / 2;

    @XmlAttribute(name = "active")
    private boolean active;

    private PublishDestination dest = PublishDestination.NONE;

    static {
        PUB_PROFILE = get(ACTIVE_KEY);
    }

    private Map<String, PubLocation> entireLocations = new HashMap<>();
    private Map<String, PubLocation> issueLocations = new HashMap<>();
    private Map<String, PubLocation> wrLocations = new HashMap<>();

    public static void register(ResourceManager loader) {
        LOG.info("register");
        loader.register(RES_NAME, JaxbResourceFactory.create(PublishProfile.class));
    }

    /** @return the current publication profile of host environment */
    public static Res<PublishProfile> getProfile() {
        Res<PublishProfile> ret = find(ACTIVE_KEY);
        if (!Res.valid(ret)) {
            LOG.error("active publication profile is not valid");
        }
        return ret;
    }

    private static Res<PublishProfile> get(String key) {
        return DT.get(key);
    }

    /** @return the reference to a publication profile by the external id */
    public static Res<PublishProfile> find(String sid) {
        return DT.findResource(sid);
    }

    public static boolean isWhenReadyType(String dbType) {
        return dbType.endsWith(PUBLISH_POSTFIX_WHEN_READY);
    }

    public static boolean isIncrementalType(String dbType) {
        return dbType.endsWith(PUBLISH_POSTFIX_BY_RECORDS);
    }

    public static String getProfileState() {

        StringBuilder sb = new StringBuilder();
        PublishProfile pub = PUB_PROFILE.get();
        sb.append("\nActive Publication Profile: ").append(pub.getId()).append(" ").append(pub.env).append(
                "-").append(pub.dest).append("\n");
        sb.append(ATTR_BATCH).append("=").append(pub.batch).append("\n");
        sb.append(ATTR_LITERATUM_URL).append("=").append(pub.literatumUrl).append("\n");

        sb.append("issue-locations=\n");
        buildLocationState(sb, pub.issueLocations);
        sb.append("entire-locations=\n");
        buildLocationState(sb, pub.entireLocations);
        sb.append("whenready-locations=\n");
        buildLocationState(sb, pub.wrLocations);

        PathType.append(sb);
        return sb.toString();
    }

    private static void buildLocationState(StringBuilder sb, Map<String, PubLocation> locations) {

        for (PubLocation pl: locations.values()) {
            pl.buildLocationState(sb);
        }
        sb.append("\n");
    }

    public static String buildExportDbName(PubLocationPath path, boolean wr, boolean inc) {

        String postfix = wr ? PUBLISH_POSTFIX_WHEN_READY : (inc ? PUBLISH_POSTFIX_BY_RECORDS : "");
        return path.getPubType().getExportName() + postfix;
    }

    public static String buildExportDbName(String exportTypeName, boolean wr, boolean inc) {

        String exportName = PubType.get(exportTypeName).get().getExportName();

        String postfix = wr ? PUBLISH_POSTFIX_WHEN_READY : (inc ? PUBLISH_POSTFIX_BY_RECORDS : "");
        return exportName + postfix;
    }

    private static String buildPath(String path, String dbName) {
        return path.contains(DB_NAME) ? path.replace(DB_NAME, dbName) : path;
    }

    public static String buildExportFileName(String archiveTemplate, Map<String, String> replaceList) {
        return CochraneCMSProperties.replaceProperty(archiveTemplate, replaceList);
    }

    public PubLocationPath getPubLocation(String type, String dbName, boolean entire, boolean wr) {

        String majorType = PubType.find(type).get().getMajorType();
        return entire ? getEntirePubLocation(majorType, type, dbName)
            : (wr ? getWhenReadyPubLocation(majorType, type, dbName) : getIssuePubLocation(majorType, type, dbName));
    }

    public PubLocationPath getIssuePubLocation(String majorType, String type, String dbName) {
        PubLocation loc = issueLocations.get(majorType);
        if (loc == null) {
            return null;
        }
        return getPubLocation(type, dbName, loc, false);
    }

    public PubLocationPath getEntirePubLocation(String majorType, String type, String dbName) {
        PubLocation loc = entireLocations.get(majorType);
        if (loc == null) {
            return null;
        }
        return getPubLocation(type, dbName, loc, true);
    }

    public PubLocationPath getWhenReadyPubLocation(String majorType, String type, String dbName) {
        PubLocation loc = wrLocations.get(majorType);
        if (loc == null) {
            return getIssuePubLocation(majorType, type, dbName);
        }
        return getPubLocation(type, dbName, loc, false);
    }

    public PubLocationPath getReplicatePubLocation(String majorType, String type, String dbName) {
        PubLocation loc = wrLocations.get(majorType);
        if (loc != null) {
            return getPubLocation(type, dbName, loc, false);
        }
        return null;
    }

    private static PubLocationPath getPubLocation(String type, String dbName, PubLocation loc, boolean entire) {
        if (loc.paths == null) {
            return checkPatch(new PubLocationPath(type, dbName, loc.getFolder(), loc.getUnpackFolder(),
                    loc.getConnection()), loc, entire);
        }
        return findPatch(type, dbName, loc, entire);
    }

    public final int getBatch() {
        return batch;
    }

    public final String getEnvironment() {
        return env;
    }

    public final String getMFTUrl() {
        return mftUrl;
    }

    public final String getMFTUser() {
        return mftUser;
    }

    public final String getMFTPassword() {
        return mftUser;
    }

    public final String getMFTRequest() {
        return mftRequest;
    }

    public final String getMFTResponse() {
        return mftResponse;
    }

    public final int getHWProcessAttemptCount() {
        return hwProcessAttemptCount;
    }

    public final int getHWProcessAttemptDelay() {
        return hwProcessAttemptDelay;
    }

    public final int getLitProcessAttemptCount() {
        return litProcessAttemptCount;
    }

    public final int getLitProcessAttemptDelay() {
        return litProcessAttemptDelay;
    }

    public final String getLiteratumUrl() {
        return literatumUrl;
    }

    public final void setLiteratumUrl(String literatumUrl) {
        this.literatumUrl = literatumUrl;
    }

    public final String getLiteratumUser() {
        return literatumUser;
    }

    public final void setLiteratumUser(String literatumUser) {
        this.literatumUser = literatumUser;
    }

    public final String getLiteratumPassword() {
        return literatumPassword;
    }

    public final void setLiteratumPassword(String literatumPassword) {
        this.literatumPassword = literatumPassword;
    }

    public final String getLiteratumResponse() {
        return literatumResponse;
    }

    public final void setLiteratumResponse(String literatumResponse) {
        this.literatumResponse = literatumResponse;
    }

    private static PubLocationPath findPatchByDb(String type, String dbName, PubLocation loc) {
        PubLocationPath ret = null;
        Map<String, PubLocationPath> map = loc.paths.get(dbName);

        if (map != null) {

            ret = map.get(type);
            if (ret != null && ret.basetype == null) {
                LOG.warn(String.format("strange pub location for %s.%s : %s", type, dbName, ret));
            }
        }
        return ret;
    }

    private static PubLocationPath findPatchByType(String type, String dbName, PubLocation loc, boolean entire) {
        PubLocationPath ret;
        Map<String, PubLocationPath> map = loc.paths.get(ALL_KEY);
        if (map == null) {
            // no any specified paths for this base
            ret = new PubLocationPath(type, dbName, loc.getFolder(), loc.getUnpackFolder(), loc.getConnection());
        } else {
            ret = map.get(type);
            if (ret != null && ret.basetype == null) {
                // found a path:  basetype="*" pubtype="type"
                ret = new PubLocationPath(type, dbName, ret.folder, ret.unpackFolder, ret.contype);
            } else if (ret == null) {
                // no any specified paths for this pubtype
                ret = new PubLocationPath(type, dbName, loc.getFolder(), loc.getUnpackFolder(), loc.getConnection());
            } else {
                LOG.error(String.format("wrong pub location for %s.%s : %s", type, dbName, ret));
                ret = new PubLocationPath(type, dbName, loc.getFolder(), loc.getUnpackFolder(), loc.getConnection());
            }
        }
        return checkPatch(ret, loc, entire);
    }

    private static PubLocationPath findPatch(String type, String dbName, PubLocation loc, boolean entire) {
        PubLocationPath ret = findPatchByDb(type, dbName, loc);
        if (ret == null) {
            ret = findPatchByType(type, dbName, loc, entire);
        }
        return ret;
    }

    private static PubLocationPath checkPatch(PubLocationPath path, PubLocation loc, boolean entire) {
        if (!path.isFullyInitialized()) {
            return null;
        }
        loc.createPubLocationPath(path, entire);
        return path;
    }

    @XmlAttribute(name = "main-destination")
    public void setDestinationXml(String destination) {
        try {
            dest = PublishDestination.valueOf(destination);
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
    }

    public PublishDestination getDestination() {
        return dest;
    }

    private boolean isActive() {
        return active;
    }

    @XmlElement(name = "location")
    public final void setPubLocationsXml(PubLocation[] list) {
        for (PubLocation pl: list) {

            if (pl.isWhenReady()) {
                wrLocations.put(pl.getMajorType(), pl);
            } else if (pl.canEntire()) {
                entireLocations.put(pl.getMajorType(), pl);
            } else {
                issueLocations.put(pl.getMajorType(), pl);
            }
        }
    }

    @Override
    protected void resolve() {

        resolveLocation(entireLocations, true);
        resolveLocation(issueLocations, false);
        resolveLocation(wrLocations, false);

        if (isActive()) {
            DT.add(ACTIVE_KEY, this);
        }
    }

    @Override
    protected void populate() {
        DT.publish(this);
    }

    private static void resolveLocation(Map<String, PubLocation> locations, boolean entire) {
        for (PubLocation loc: locations.values()) {
            loc.resolve(entire);
        }
    }

    /**
     * Path to the publish data of a concrete database and a kind of publishing
     */
    public static final class PubLocationPath implements IEndPointLocation, Serializable {
        private static final long serialVersionUID = 1L;

        private String folder;

        private Res<ConnectionType> contype;

        private String unpackFolder;

        private String archive;

        private Res<BaseType> basetype;

        private PubLocation parent;

        private Res<PubType> pubtype;

        @XmlAttribute(name = "replicate-ref")
        private String replicateLocationRef;

        private PubLocationPath() {
        }

        private PubLocationPath(String pub, String base, String folder, String unpackFolder,
                                Res<ConnectionType> contype) {
            basetype = BaseType.find(base);
            pubtype = PubType.find(pub);
            setFolder(buildPath(folder, base));
            if (unpackFolder != null) {
                setUnpackFolder(buildPath(unpackFolder, base));
            }
            this.contype = contype;
        }

        @XmlAttribute(name = "database-ref")
        public void setBaseTypeXml(String type) {
            basetype = BaseType.get(type);
        }

        @XmlAttribute(name = "pubtype-ref")
        public void setPubTypeXml(String type) {
            pubtype = PubType.get(type);
        }

        @XmlAttribute(name = "connection-ref")
        public void setConnectionTypeXml(String type) {
            contype = ConnectionType.get(type);
        }

        @Override
        public ConnectionType getConnectionType() {
            return Res.valid(contype) ? contype.get() : ConnectionType.EMPTY;
        }

        public boolean hasReplicateLocationRef() {
            return replicateLocationRef != null;
        }

        @Override
        public PublishProfile.PubLocationPath getReplication() {
            return replicateLocationRef == null ? null : getProfile().get().getReplicatePubLocation(
                    replicateLocationRef, getPubType().getId(), getBaseType().getId());
        }

        @Override
        public PubType getPubType() {
            return pubtype.get();
        }

        public String getParentMajorType() {
            return parent.majorType;
        }

        @Override
        public BaseType getBaseType() {
            return basetype.get();
        }

        @Override
        public boolean isEntirePath() {
            return parent.canEntire();
        }

        @Override
        public ServerType getServerType() {
            return parent.server.get();
        }

        public Res<ServerType> changeServerType(String serverTypeName) {
            Res<ServerType> serverType = ServerType.find(serverTypeName);
            if (Res.valid(serverType)) {
                Res<ServerType> old = parent.server;
                LOG.warn(String.format("a host template configuration was changed: %s -> %s",
                        old.get().getId(), serverTypeName));
                parent.server = serverType;
                return old;
            }
            return null;
        }

        boolean isFullyInitialized() {
            return folder != null && pubtype != null;
        }

        @XmlAttribute(name = "folder")
        public void setFolder(String folder) {
            this.folder = folder.trim();
        }

        @Override
        public String getFolder() {
            return folder;
        }

        @XmlAttribute(name = "unpack-folder")
        public void setUnpackFolder(String folder) {
            this.unpackFolder = folder.trim();
        }

        public String getUnpackFolder() {
            return unpackFolder;
        }

        public String getLoader() {
            return getConnectionType().getUrl();
        }

        public void setArchive(String archive) {
            this.archive = archive.trim();
        }

        public String getArchive() {
            return archive;
        }

        @Override
        public String toString() {
            return String.format("base=%s type=%s folder=%s connection=%s (unpack=%s) server=%s replicate=%s",
                basetype, pubtype, folder, contype, unpackFolder, parent == null ? null : parent.getServerType(),
                    replicateLocationRef);
        }
    }

    private static class PubLocation implements Serializable {
        private static final long serialVersionUID = 1L;

        @XmlAttribute(name = "entire")
        private boolean entire;

        @XmlAttribute(name = "whenready")
        private boolean wr;

        @XmlAttribute(name = "majortype")
        private String majorType;

        @XmlAttribute(name = "folder")
        private String folder;

        @XmlAttribute(name = "unpack-folder")
        private String unpackFolder;

        private Res<ServerType> server;
        private Res<ConnectionType> connection;

        private Map<String, Map<String, PubLocationPath>> paths;

        @XmlAttribute(name = "server-ref")
        public void setServerTypeXml(String type) {
            this.server = ServerType.get(type);
        }

        @XmlAttribute(name = "connection-ref")
        public void setConnectionTypeXml(String type) {
            this.connection = ConnectionType.get(type);
        }

        @XmlElement(name = "path")
        public final void setPubLocationPathsXml(PubLocationPath[] list) {

            paths = new HashMap<>();
            Map<String, PubLocationPath> values = new HashMap<>();
            paths.put(ALL_KEY, values);

            for (int i = 0; i < list.length; i++) {
                values.put(String.valueOf(i), list[i]);
            }
        }

        public boolean canEntire() {
            return entire;
        }

        public boolean isWhenReady() {
            return wr;
        }

        public final String getMajorType() {
            return majorType;
        }

        public final String getFolder() {
            return folder;
        }

        public final String getUnpackFolder() {
            return unpackFolder;
        }

        private ServerType getServerType() {
            return server.get();
        }

        private Res<ConnectionType> getConnection() {
            return connection;
        }

        private void resolve(boolean entire) {

            if (paths == null) {
                return;
            }
            Map<String, PubLocationPath> values = paths.remove(ALL_KEY);
            for (PubLocationPath plp: values.values()) {
                setPubLocationPath(plp, entire);
            }
        }

        private void createPubLocationPath(PubLocationPath path, boolean entire) {
            if (paths == null) {
                paths = new HashMap<>();
            }
            setPubLocationPath(path, entire);
        }

        private static boolean isEmptyFolder(PubLocationPath plp) {
            return plp.folder == null && plp.contype == null;
        }

        private void setPubLocationPath(PubLocationPath plp, boolean entire) {

            if (isEmptyFolder(plp) || !Res.valid(plp.pubtype)) {
                return;
            }

            if (plp.basetype != null && !Res.valid(plp.basetype)) {
                LOG.warn(String.format("a database is not initialized for path %s", plp));
                return;
            }

            String base = plp.basetype == null ? ALL_KEY : plp.basetype.get().getId();
            plp.parent = this;

            Map<String, PubLocationPath> map = paths.computeIfAbsent(base, f -> new HashMap<>());

            if (entire) {
                plp.setArchive(plp.pubtype.get().getEntireArchiveName());

            } else if (plp.basetype != null) {

                BaseType.PubInfo pubInfo = plp.basetype.get().getPubInfo(plp.getPubType().getId());
                plp.setArchive(pubInfo != null && pubInfo.getArchiveName() != null ? pubInfo.getArchiveName()
                        : plp.pubtype.get().getArchiveName());
            }

            map.put(plp.pubtype.get().getId(), plp);
        }

        @Override
        public String toString() {
            return String.format("%s %s", majorType, folder);
        }

        void buildLocationState(StringBuilder sb) {

            sb.append("    ").append(majorType).append(" ").append(server.get().getId()).append(":").append(folder);
            if (Res.valid(connection)) {
                appendConnection(connection.get(), sb);
            }

            sb.append("\n");

            if (paths == null) {
                return;
            }

            for (Map.Entry<String, Map<String, PubLocationPath>> entry : paths.entrySet()) {

                String key = entry.getKey();
                sb.append("        ").append(key).append(" => ");
                Map<String, PubLocationPath> map = entry.getValue();
                for (Map.Entry<String, PubLocationPath> pathEntry: map.entrySet()) {

                    String pubtype = pathEntry.getKey();
                    PubLocationPath path = pathEntry.getValue();
                    sb.append("\n            ").append(pubtype);
                    if (path.basetype != null) {
                        sb.append(".").append(path.getBaseType().getId());
                    }

                    sb.append(" | ").append(path.getServerType().getHost()).append(":");
                    if (path.getFolder() != null) {
                        sb.append(path.getFolder());
                    }
                    if (path.getUnpackFolder() != null) {
                        sb.append(" =-> ").append(path.getUnpackFolder());
                    }
                    if (path.archive != null) {
                        sb.append(" [").append(path.archive).append("]");
                    }
                    if (Res.valid(path.contype)) {
                        appendConnection(path.contype.get(), sb);
                    }
                }
                sb.append("\n");
            }
        }

        private static void appendConnection(ConnectionType con, StringBuilder sb) {
            sb.append(" connection:[").append(con).append("]");
        }
    }
}

