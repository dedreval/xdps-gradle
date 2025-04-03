package com.wiley.cms.cochrane.utils;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 29.07.2013
 */
public interface Constants {

    String SBN = "14651858";
    String DOI_PREFIX = "10.1002/";
    String DOI_CENTRAL = "10.1002/central";
    String DOI_PREFIX_CENTRAL = "10.1002/central/";
    String DOI_CDSR = "10.1002/14651858";
    String DOI_PREFIX_CDSR = "10.1002/14651858.";
    String DOI_CCA = "10.1002/20504217";
    String PUB_PREFIX = "pub";
    String NAME_POINT = ".";
    String PUB_PREFIX_POINT = NAME_POINT + PUB_PREFIX;

    int FIRST_PUB = 1;

    String ENGLISH_CODE = "en";

    String NAME_SPLITTER = "\\.";

    String WILEY_NAMESPACE_URI = "http://www.wiley.com/namespaces/wiley";

    /**
     * Cookie constants
     */
    String JSESSIONID = "JSESSIONID";
    String WILEY_DOMAIN = ".wiley.com";

    /**
     *
     */
    int UNDEF = -1;

    /**
     * String constants
     */
    String XML_STR = "xml";
    String ASSETS_STR = "assets";
    String PDF_ABSTRACT_SUFFIX = "_abstract";
    String HTML_META_FILENAME = "sect0-meta.html";
    String IMAGE_N_FOLDER = "/image_n";
    String IMAGE_N_DIR = "image_n";
    String IMAGE_T_DIR = "image_t";
    String TABLE_N_DIR = "table_n";
    String SUPINFO_DIR = "supinfo";
    String STATS_DATA_ONLY = "StatsDataOnly";
    String JATS_FINAL_SUFFIX = "-final";
    String JATS_FINAL_EXTENSION = "-final.xml";
    String JATS_INPUT_SUFFIX = "-input";
    String JATS_INPUT_EXTENSION = "-input.xml";
    String JATS_FIG_DIR_SUFFIX = "-figures";
    String JATS_TMBNLS_DIR_SUFFIX = "-thumbnails";
    String JATS_STATS_DIR_SUFFIX = "-stats";
    String ARIES_IMPORT_SUFFIX = "_Import";
    String ARIES_IMPORT_EXTENSION = "_Import.xml";
    String PACKAGE_DESCRIPTOR = "package-descriptor";
    String MESH_TERMS = "mesh-terms";
    String PUB_DATES = "pub-dates";
    String TOPICS_SOURCE = "topics.xml";
    String METADATA_SOURCE_SUFFIX = "_metadata";
    String TMP_STR = "tmp";

    /**
     * Groups of assets
     */
    String SOURCE = "source";
    String RENDERED_PDF_TEX = "rendered-pdf_tex";
    String RENDERED_PDF_FOP = "rendered-pdf_fop";
    String RENDERED_HTML_DIAMOND = "rendered-html_diamond";

    /**
     * SQL statements parameters
     */
    String ID_PRM = "id";
    String STATE_PRM = "state";
    String NAME_PRM = "name";
    String NAME1_PRM = "name1";
    String DATE_PRM = "date";

    String TEMP = "temp";

    String CONTROL_FILE_MANIFEST = "manifest.xml";

    /**
     * WOL publishing
     */
    String CONTROL_FILE = "control_file.xml";
    
    /** LITERATUM Publishing */
    String CONTROL_FILE_LIT_HIGH = "manifest_high.xml";
    String CONTROL_FILE_LIT_LOW = "manifest_low.xml";
    String CONTROL_FILE_LIT_NORMAL = "manifest_normal.xml";

    /** CCH/DS Publishing */
    String DOIURL_FILE = "doiurl.xml";

    int NO_ISSUE = 0;
    int LAST_ACTUAL_MONTH_AMOUNT = 3;

    String ISSUE = "issue";
    String ENTIRE = "entire";
    String WHEN_READY = "whenready";
    String SELECTIVE = "selective";
    String PACKAGE = "package";

    String NA = "n/a";
    String TR = "TR";
    String SPD = "SPD";

    String REL = "rel%04d";

    /**
     * a last months amount when temp files or publishing archives or other else are expired and can be removed
     * beyond that limit
     **/
    int DEFAULT_EXPIRATION_LIMIT = 6;
    int NO_EXPIRATION_LIMIT = -1;

    int HUNDRED_PERCENT = 100;

    int GB = 1073741824;
    int MB = 1048576;
    int KB = 1024;

    Integer IMPORT_JATS_ISSUE_ID = 1;
    int IMPORT_JATS_ISSUE_NUMBER = 101;

    Integer SPD_ISSUE_ID = 2;
    int SPD_ISSUE_NUMBER = 102;
    
    Integer SPD_DB_CDSR_ID      = 14;
    Integer SPD_DB_EDITORIAL_ID = 16;
    Integer SPD_DB_CCA_ID       = 17;
    Integer SPD_DB_CENTRAL_ID   = 15;

    String ISSUE_NUMBER_FORMAT = "%02d";

    int CHUNK_SIZE_4_DEBUG = 500;
}
