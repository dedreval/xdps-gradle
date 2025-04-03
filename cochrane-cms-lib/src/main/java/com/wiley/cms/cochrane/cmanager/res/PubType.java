package com.wiley.cms.cochrane.cmanager.res;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.res.DataTable;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.03.13
 */
@XmlRootElement(name = PubType.RES_NAME)
public class PubType extends ResourceStrId {

    public static final String MAJOR_TYPE_CCH = "CCH";
    public static final String MAJOR_TYPE_UDW = "UDW";
    public static final String MAJOR_TYPE_DS  = "DS";
    public static final String MAJOR_TYPE_ARIES  = "ARIES";
    public static final String MAJOR_TYPE_ARIES_INT  = "ARIES-INT";

    public static final String MAJOR_TYPE_COCHRANE  = "COCHRANE";

    public static final String MAJOR_TYPE_S   = "SEMANTICO";
    public static final String MAJOR_TYPE_LIT = "LITERATUM";

    public static final String MAJOR_TYPE_CENTRAL_SRC = "CENTRAL-SRC";

    public static final String TYPE_CCH         = "cch";
    public static final String TYPE_CCH_SITE    = "cochraneSite";
    public static final String TYPE_CCH_RAW     = "cchRaw";
    public static final String TYPE_CCH_MESH    = "cchMesh";

    public static final String TYPE_DS          = "ds";
    public static final String TYPE_DS_MESH     = "dsMesh";
    public static final String TYPE_DS_MONTHLY  = "dsMonthly";

    public static final String TYPE_WOL         = "wol";

    public static final String TYPE_SEMANTICO          = "semantico";
    public static final String TYPE_SEMANTICO_TOPICS   = "semanticoTop";
    public static final String TYPE_SEMANTICO_DELETE   = "semanticoDel";

    public static final String TYPE_LITERATUM   = "literatum";
    public static final String TYPE_UDW         = "udw";

    public static final String TYPE_ARIES_D = "aries-deliver";
    public static final String TYPE_ARIES_P = "aries-publish";
    public static final String TYPE_ARIES_C = "aries-cancel";
    public static final String TYPE_ARIES_V = "aries-verification";
    public static final String TYPE_ARIES_ACK_D = "aries-ack-deliver";
    public static final String TYPE_ARIES_ACK_P = "aries-ack-publish";

    public static final String TYPE_COCHRANE_P = "cochrane-publish";
    public static final String TYPE_CENTRAL_SRC = "central-src-manual";
    public static final String TYPE_CENTRAL_SRC_AUT = "central-src-aut";

    public static final List<Integer> SEMANTICO_DB_TYPES = new ArrayList<>();
    public static final Set<Integer>  SEMANTICO_RT_TYPES = new HashSet<>();
    public static final Set<Integer>  SEMANTICO_DEL_TYPES = new HashSet<>(Arrays.asList(38, 64));
    public static final List<Integer> LITERATUM_DB_TYPES = new ArrayList<>();
    public static final Set<Integer>  LITERATUM_RT_TYPES = new HashSet<>();
    public static final List<Integer> DS_DB_TYPES = new ArrayList<>();
    public static final List<Integer> UDW_DB_TYPES = new ArrayList<>();
    public static final Set<Integer>  UI_TYPES = new HashSet<>(Arrays.asList(2, 4, 6, 9, 12, 13, 24, 36, 38, 39, 41));
    public static final List<Integer> UI_STATE_TYPES = new ArrayList<>();

    public static final Set<Integer> TYPES_ARIES_ACK_D = new HashSet<>(Arrays.asList(58, 60));
    public static final Set<Integer> TYPES_ARIES_ACK_P = new HashSet<>(Arrays.asList(59, 61));

    static final String RES_NAME = "pubtype";

    private static final long serialVersionUID = 1L;

    private static final int LITERATUM_TYPE_ID             = 42;
    private static final int LITERATUM_TYPE_WR_ID          = 44;
    private static final int LITERATUM_TYPE_INCREMENTAL_ID = 45;
    private static final int SEMANTICO_TYPE_ID             = 34;
    private static final int SEMANTICO_TYPE_WR_ID          = 33;
    private static final int SEMANTICO_TYPE_INCREMENTAL_ID = 35;
    private static final int UDW_TYPE_ID                   = 47;
    private static final int UDW_TYPE_INCREMENTAL_ID       = 48;
    private static final int DS_TYPE_ID                    = 50;
    private static final int DS_TYPE_WR_ID                 = 52;
    private static final int DS_TYPE_INCREMENTAL_ID        = 53;
    private static final int DS_TYPE_MONTHLY_ID            = 54;
    private static final int DS_TYPE_MESH_ID               = 55;
    private static final int DS_TYPE_MONTHLY_MESH_ID       = 56;
    private static final int DS_TYPE_MONTHLY_MESH_WR_ID    = 57;

    private static final DataTable<String, PubType> DT = new DataTable<>(RES_NAME);

    @XmlAttribute(name = "uiname")
    private String uiname;

    @XmlAttribute(name = "selective")
    private boolean selective = true;

    @XmlAttribute(name = "expname")
    private String expname;

    @XmlAttribute(name = "majortype")
    private String type;

    @XmlAttribute(name = "generate")
    private boolean generate = true;

    @XmlAttribute(name = "delete")
    private boolean delete;

    @XmlAttribute(name = "archive")
    private String archiveName;

    @XmlAttribute(name = "entire-archive")
    private String entireArchiveName;

    @XmlAttribute(name = "file-prefix")
    private String filePrefix = "";

    @XmlAttribute(name = "sub-packages")
    private boolean canHaveSubPackages;

    @XmlAttribute(name = "expiration")
    private int expiration = Constants.NO_EXPIRATION_LIMIT;

    static {
        SEMANTICO_DB_TYPES.add(SEMANTICO_TYPE_ID);
        SEMANTICO_DB_TYPES.add(SEMANTICO_TYPE_WR_ID);
        SEMANTICO_DB_TYPES.add(SEMANTICO_TYPE_INCREMENTAL_ID);
        SEMANTICO_RT_TYPES.add(SEMANTICO_TYPE_WR_ID);
        SEMANTICO_RT_TYPES.add(SEMANTICO_TYPE_INCREMENTAL_ID);

        LITERATUM_DB_TYPES.add(LITERATUM_TYPE_ID);
        LITERATUM_DB_TYPES.add(LITERATUM_TYPE_WR_ID);
        LITERATUM_DB_TYPES.add(LITERATUM_TYPE_INCREMENTAL_ID);
        LITERATUM_RT_TYPES.add(LITERATUM_TYPE_WR_ID);
        LITERATUM_RT_TYPES.add(LITERATUM_TYPE_INCREMENTAL_ID);

        DS_DB_TYPES.add(DS_TYPE_ID);
        DS_DB_TYPES.add(DS_TYPE_WR_ID);
        DS_DB_TYPES.add(DS_TYPE_INCREMENTAL_ID);
        DS_DB_TYPES.add(DS_TYPE_MONTHLY_ID);
        DS_DB_TYPES.add(DS_TYPE_MESH_ID);
        DS_DB_TYPES.add(DS_TYPE_MONTHLY_MESH_ID);
        DS_DB_TYPES.add(DS_TYPE_MONTHLY_MESH_WR_ID);

        UDW_DB_TYPES.add(UDW_TYPE_ID);
        UDW_DB_TYPES.add(UDW_TYPE_INCREMENTAL_ID);

        UI_TYPES.add(SEMANTICO_TYPE_ID);
        UI_TYPES.add(LITERATUM_TYPE_ID);
        UI_TYPES.add(UDW_TYPE_ID);
        UI_TYPES.add(DS_TYPE_ID);

        UI_STATE_TYPES.addAll(LITERATUM_DB_TYPES);
        UI_STATE_TYPES.addAll(SEMANTICO_RT_TYPES);
    }

    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, JaxbResourceFactory.create(PubType.class, DT));
    }

    public static Res<PubType> find(String sid) {
        return DT.findResource(sid);
    }

    static Res<PubType> get(String sid) {
        return DT.get(sid);
    }

    public static boolean isDS(String type) {
        return MAJOR_TYPE_DS.equals(type);
    }

    public static boolean isSemantico(String type) {
        return MAJOR_TYPE_S.equals(type);
    }

    public static boolean isLiteratum(String type) {
        return MAJOR_TYPE_LIT.equals(type);
    }

    public final String getUIName() {
        return uiname;
    }

    public final String getExportName() {
        return expname;
    }

    public final String getMajorType() {
        return type;
    }

    public final String getArchiveName() {
        return archiveName;
    }

    public final String getEntireArchiveName() {
        return entireArchiveName;
    }

    public final boolean canUnpack() {
        return false;
    }

    public final boolean canGenerate() {
        return generate;
    }

    public final boolean canDelete() {
        return delete;
    }

    public final boolean canSelective() {
        return selective;
    }

    public final boolean canHaveSubPackages() {
        return canHaveSubPackages;
    }

    public final boolean isDS() {
        return isDS(type);
    }

    public final boolean isLiteratum() {
        return TYPE_LITERATUM.equals(getId());
    }

    public final boolean isAriesAck() {
        return TYPE_ARIES_ACK_D.equals(getId()) || TYPE_ARIES_ACK_P.equals(getId());
    }

    public static boolean isAriesAckPublish(Integer typeId) {
        return TYPES_ARIES_ACK_P.contains(typeId);
    }

    public static boolean isAriesAckDeliver(Integer typeId) {
        return TYPES_ARIES_ACK_D.contains(typeId);
    }

    public final boolean isMl3g() {
        return TYPE_SEMANTICO.equals(getId()) || TYPE_DS.equals(getId()) || TYPE_DS_MONTHLY.equals(getId());
    }

    public final boolean isHtml() {
        return TYPE_WOL.equals(getId());
    }

    public final int getExpiration() {
        return expiration;
    }

    @Override
    public String toString() {
        return String.format("%s.%s (%s)", getId(), type, uiname);
    }
}

