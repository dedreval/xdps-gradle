package com.wiley.cms.cochrane.repository;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import java.util.Date;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

import com.wiley.cms.cochrane.utils.Constants;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.tes.util.Logger;

/**
 * The Cochrane Repository.
 *
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @version 1.0
 */
public class RepositoryImpl implements IRepository {
    private static String xquerySearch =
        "declare namespace cochrane = \"http://wiley.ru/cochrane/search/function\"\n"
            + " import module "
            + "\"http://wiley.ru/cochrane/search/function\""
            + " at \"cochrane-search.xqy\" ";

    private static String xqueryManifest =
        "import module namespace cochrane=\"http://wiley.ru/cochrane/manifest/update\" at \"manifest.xqy\"\n";
    //"declare namespace cochrane = \"http://wiley.ru/cochrane/manifest/update\"\n"
//                    + " import module "
//                    + "\"http://wiley.ru/cochrane/manifest/update\""
//                    + " at \"manifest.xqy\" ";

    private static String xqueryTriggerIndex =
        "declare namespace shadow = \"http://wiley.ru/search/shadow\"\n"
            + " import module "
            + "\"http://wiley.ru/search/shadow\""
            + " at \"/mod-shadow.xqy\" ";


    private static String xqueryDeleteDir =
        "import module namespace del=\"http://wiley.ru/utils/delete\" "
            + "at \"/fast-directory-delete.xqy\" ";

    private static String getAllTags =
        "declare namespace cochrane = \"http://wiley.ru/cochrane/search/function\"\n"
            + " import module "
            + "\"http://wiley.ru/cochrane/search/function\""
            + " at \"cochrane-search.xqy\" ";
    private static final String CASE_SENSITIVE = "case-sensitive";
    private static final String CASE_INSENSITIVE = "case-insensitive";


    private enum ModeUsedFlags {
        LOGIC_ONLY(true, false, false), //
        FILE_ONLY(false, false, true), //
        LOGIC_FILE(true, false, true), //
        LOGIC_FTP(true, true, false); //

        private boolean isMarklogicUsed;

        private boolean isFilesystemUsed;

        private boolean isFtpHttpUsed;

        ModeUsedFlags(boolean isMarklogicUsed, boolean isFtpHttpUsed, boolean isFilesystemUsed) {
            this.isMarklogicUsed = isMarklogicUsed;
            this.isFtpHttpUsed = isFtpHttpUsed;
            this.isFilesystemUsed = isFilesystemUsed;
        }
    }

    private static final int BUFFER_SIZE = 1024;

    private static final Logger LOG = Logger.getLogger(RepositoryImpl.class);

    private boolean isMarklogicUsed;

    private boolean isFilesystemUsed;

    private boolean isFtpHttpUsed;

    private ModeUsedFlags modeUsedFlags;

    //private String marklogicConnection;

    private String filesystemRoot;

    // ==
    private String serverFtp;

    private String userFtp;

    private String passwordFtp;

    private String serverHttp;

    private FTPClient ftpClient = new FTPClient();
    //private ContentCreateOptions options;
    // ==
    private String dtd;

    private void checkUsedFlags() {
        int i;
        for (i = 0; i < ModeUsedFlags.values().length; i++) {
            modeUsedFlags = ModeUsedFlags.values()[i];
            if (isMarklogicUsed == modeUsedFlags.isMarklogicUsed && isFtpHttpUsed == modeUsedFlags.isFtpHttpUsed
                && isFilesystemUsed == modeUsedFlags.isFilesystemUsed) {
                break;
            }
        }
        if (1 == ModeUsedFlags.values().length) {
            throw new IllegalStateException("RepositoryImpl. Bad configured 'used' flags.");
        }
    }

    public RepositoryImpl() {
        // ==
        serverFtp = CochraneCMSProperties.getProperty("ftp.server");
        userFtp = CochraneCMSProperties.getProperty("ftp.user");
        passwordFtp = CochraneCMSProperties.getProperty("ftp.password");
        serverHttp = CochraneCMSProperties.getProperty("http.server");
        // ==
        //isMarklogicUsed = Boolean.valueOf(CochraneCMSProperties.getProperty("marklogic.used", "false"));
        isFilesystemUsed = Boolean.valueOf(CochraneCMSProperties.getProperty("filesystem.used", "true"));
        isFtpHttpUsed = Boolean.valueOf(CochraneCMSProperties.getProperty("ftphttp.used", "false"));

//        filesystemRoot = System.getProperty("filesystem.root");
        filesystemRoot =
            CochraneCMSProperties.getProperty("filesystem.root", System.getProperty("jboss.server.config.url")
                + "/repository/");
        //marklogicConnection = CochraneCMSProperties.getProperty("marklogic.connection", "");

        checkUsedFlags();

        //if (isMarklogicUsed) {
        //    LOG.info("Marklogic connection: " + marklogicConnection);
        //}
        if (isFilesystemUsed) {
            LOG.info("Filesystem root: " + filesystemRoot);
        }
        if (isFtpHttpUsed) {
            LOG.info("FTP connection: " + serverFtp);
            LOG.info("HTTP connection: " + serverHttp);
        }
        //if (isMarklogicUsed) {
        //    createOptionsForMarkLogic();
        //}
    }

    private boolean isMarkLogicEnabled() {
        return false;
    }

    /*@Deprecated
    public Element search(String folder, String issueId, String databaseId, String area,
                          String text, String tagName, boolean caseSensitive) throws IOException {
        if (!isMarkLogicEnabled()) {
            return null;
        }

        String caseSensitiveName = caseSensitive ? CASE_SENSITIVE : CASE_INSENSITIVE;
        String searchArea = area;
        boolean procInstruction = false;

        Res<Settings> instructions = CmsResourceInitializer.getProcessingInstructions();
        if (Res.valid(instructions)) {
            for (Settings.Setting instruction: instructions.get().getSettings()) {

                if (instruction.getLabel().equals(tagName)) {
                    searchArea = instruction.getValue();
                    procInstruction = databaseId.equals(CochraneCMSPropertyNames.getCDSRDbName());
                    break;
                }
            }
        }

        if (databaseId.equals(CochraneCMSPropertyNames.getCcaDbName())) {
            return executeMLFunction(searchWithNamespace(folder, issueId, databaseId, searchArea, text, tagName,
                    caseSensitiveName, procInstruction));
        }

        String queryString = queryString(folder, issueId, databaseId, searchArea, text, tagName,
                caseSensitiveName, procInstruction) + ")";
        LOG.debug("Search in the MarkLogic: " + queryString);
        return executeMLFunction(queryString);
    }*/

    /*@Deprecated
    public Element search(String folder, String dbName, String searchArea, String text,
                          String tagName, boolean caseSensitive) throws IOException {
        return search(folder, "", dbName, searchArea, text, tagName, caseSensitive);
    }*/

    private String searchWithNamespace(String folder, String issueId, String databaseId, String area, String text,
                                       String tagName, String caseSensitiveName, boolean procInstruction) {
        String queryString = queryString(folder, issueId, databaseId, area, text, tagName, caseSensitiveName,
                procInstruction) + ", \"" + Constants.WILEY_NAMESPACE_URI + "\")";
        LOG.debug("Search in the MarkLogic: " + queryString);
        return queryString;
    }

    private String queryString(String folder, String issueId, String databaseId, String area, String text,
                               String tagName, String caseSensitiveName, boolean procInstruction) {
        String normalizedText = text.replaceAll("\"", "&quot;");
        return xquerySearch
                + (procInstruction ? "cochrane:search-processing-instruction(\"" : "cochrane:search(\"")
                + fixUri(folder) + "\", \""
                + issueId + "\", \""
                + databaseId + "\", \""
                + area + "\", \"\""
                /*+ recordStatus + */ + ", \""
                + normalizedText + "\", \""
                + tagName + "\""
                + ",\"" + caseSensitiveName + "\"";
    }

    /*@Deprecated
    public Element getAllTagsEntire(String dbName) throws IOException {
        if (!isMarkLogicEnabled()) {
            return null;
        }
        String folder =
            fixUri(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY_SHADOW) + "/"
                + dbName + "/entire/");
//        LOG.debug("Get all tags from xml in folder : " + folder);
        String group = getGroup(dbName);

        String queryString = getAllTags + "cochrane:getAllTags(\"" + folder + "\", \"" + group + "\")";
        return executeMLFunction(queryString);
    }*/

    private String getGroup(String title) {
        String ret = "wml2.1";
        if (title.equals(CochraneCMSPropertyNames.getCentralDbName()) || title
            .equals(CochraneCMSPropertyNames.getCmrDbName())) {
            ret = "usw";
        } else if (title.equals(CochraneCMSPropertyNames.getCcaDbName())) {
            ret = "wml3g";
        }
        return ret;
    }

    /*@Deprecated
    public Element getAllTags(Integer issueId, String title) throws IOException {
        if (!isMarkLogicEnabled()) {
            return null;
        }
        String folder =
            fixUri(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY_SHADOW) + "/"
                + issueId + "/" + title + "/");
//        LOG.debug("Get all tags from xml in folder : " + folder);
        String group = getGroup(title);

        String queryString = getAllTags + "cochrane:getAllTags(\"" + folder + "\", \"" + group + "\")";
        return executeMLFunction(queryString);
    } */

    /*private Element executeMLFunction(String queryString)
        throws IOException {
        Session session = null;
        ResultSequence rs = null;

        try {

            session = getMlSession();

            RequestOptions opts = new RequestOptions();
            opts.setLocale(Locale.US);

            Request request = session.newAdhocQuery(queryString, opts);
            rs = session.submitRequest(request);
            if (!rs.hasNext()) {
                throw new IOException("No results returned!");
            }
            ResultItem item = rs.next();
            return (Element) JDOMUtils.loadElement(item.asString()).detach();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            LOG.error(e, e);
            throw new IOException(e.getMessage());
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (session != null) {
                session.close();
            }
        }
    }*/

    /*public InputStream getLinksXML(String issueId, String databaseId, Date date) throws IOException {
        String search = "declare namespace doi = \"http://wiley.ru/cochrane/publish/doi\"\n"
            + "import module "
            + "\"http://wiley.ru/cochrane/publish/doi\""
            + "at \"mod-doi.xqy\" ";

        String queryString = search + "doi:links(\""
            + issueId + "\", \""
            + databaseId + "\")";
        return queryForExport(queryString);
    }*/

    /*public InputStream getLinksXML(Set<String> recordPaths) throws IOException {
        String search = "declare namespace doi = \"http://wiley.ru/cochrane/publish/doi\"\n"
            + "import module "
            + "\"http://wiley.ru/cochrane/publish/doi\""
            + "at \"mod-doi.xqy\" ";
        StringBuilder linksBuilder = new StringBuilder();
        String linksString = "";
        if (recordPaths.size() > 0) {
            for (String link : recordPaths) {
                linksBuilder.append("\"" + link + "\", ");
            }
            linksString = "(" + linksBuilder.toString().substring(0, linksBuilder.length() - 2) + ")";
        }
        String queryString = search + "doi:retro-links(" + linksString + ")";
        return queryForExport(queryString);
    }*/

    /*public InputStream getUnavailableJS(String issueId, String databaseId, Date date) throws IOException {
        String search = "declare namespace doi = \"http://wiley.ru/cochrane/publish/doi\"\n"
            + "import module "
            + "\"http://wiley.ru/cochrane/publish/doi\""
            + "at \"mod-doi.xqy\" ";

        String queryString = search + "doi:unavailablejs(\""
            + issueId + "\", \""
            + databaseId + "\")";
        return queryForExport(queryString);
    }*/

    public InputStream getUnavailableJSEntire(String dbName, Date date) throws IOException {
        //todo implement unavailablejsentire in mod-doi.xqy if needed
        /*String search = "declare namespace doi = \"http://wiley.ru/cochrane/publish/doi\"\n"
                + "import module "
                + "\"http://wiley.ru/cochrane/publish/doi\""
                + "at \"mod-doi.xqy\" ";

        String queryString = search + "doi:unavailablejsentire(\""
                + dbName + "\")";

        return queryForExport(queryString);*/
        return new ByteArrayInputStream("function nonempty(){}".getBytes());
    }

    /*public InputStream getDoiXML(String issueId, String databaseId, Date date) throws IOException {
        String search = "declare namespace doi = \"http://wiley.ru/cochrane/publish/doi\"\n"
            + "import module "
            + "\"http://wiley.ru/cochrane/publish/doi\""
            + "at \"mod-doi.xqy\" ";
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);

        String timestamp = calendar.get(Calendar.YEAR) + "" + date.getTime();
        String queryString = search + "doi:doixml(\""
            + issueId + "\", \""
            + databaseId + "\", \""
            + timestamp + "\")";
        return queryForExport(queryString);
    }*/

    /**
     * delete dir
     * if ML used- delete from ML && from repository for binary files
     *
     * @param xUri
     * @throws IOException
     */
    public void deleteDir(String xUri) throws Exception {
        String errorMessage = null;

        switch (modeUsedFlags) {
            case FILE_ONLY:
                //if (xUri.contains(FilePathCreator.getFixedManifestFolder())
                //    || xUri
                //    .contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY_SHADOW))) {
                //    deleteDirXcc(fixDirUri(xUri));
                //} else {
                deleteFileLocal(fixDirUri(xUri));
                //}
                break;
            case LOGIC_FILE:
                //deleteLogicFile(fixDirUri(xUri));
                break;
            case LOGIC_FTP:

                errorMessage = null;

                //try {
                //    deleteDirXcc(fixDirUri(xUri));
                //} catch (IOException e) {
                //    errorMessage += e.getMessage() + "\n";
                //}
                try {
                    deleteFileFtp(fixUriForFtp(xUri));
                } catch (IOException e) {
                    errorMessage += e.getMessage() + "\n";
                }

                if (errorMessage != null) {
                    throw new IOException(errorMessage);
                }

                break;
            case LOGIC_ONLY:
                //deleteDirXcc(fixDirUri(xUri));
                break;
            default:
        }
    }

    @Deprecated
    private void deleteLogicFile(String xUri)
        throws IOException {
        String errorMessage = null;

        try {
            deleteDirXcc(fixUri(xUri));
        } catch (Exception e) {
            errorMessage += e.getMessage() + "\n";
        }

        try {
            deleteFileLocal(fixUri(xUri));
        } catch (IOException e) {
            errorMessage += e.getMessage() + "\n";
        }

        if (errorMessage != null) {
            throw new IOException(errorMessage);
        }
    }

    @Deprecated
    private void deleteDirXcc(String uri) throws Exception {
        //if (isMarkLogicEnabled()) {
        //    startXquery(xqueryDeleteDir + "del:fast-directory-delete(\"" + uri + "\")");
        //}
    }

    /*private InputStream queryForExport(String queryString) throws IOException {
        LOG.trace("Query to the MarkLogic: " + queryString);
        Session session = null;
        ResultSequence rs = null;
        ByteArrayOutputStream out;
        try {
            session = getMlSession();

            RequestOptions opts = new RequestOptions();
            opts.setLocale(Locale.US);

            Request request = session.newAdhocQuery(queryString, opts);
            rs = session.submitRequest(request);
            if (rs.hasNext()) {
                ResultItem item = rs.next();
                InputStream in = new BufferedInputStream(item.getItem().asInputStream());
                out = new ByteArrayOutputStream();
                byte[] buffer = new byte[BUFFER_SIZE];

                int m;
                while ((m = in.read(buffer)) > 0) {
                    out.write(buffer, 0, m);
                }

                in.close();
                return new ByteArrayInputStream(out.toByteArray());
            }
            throw new IllegalArgumentException("Query to MarkLogic is wrong!");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            LOG.error(e, e);
            throw new IOException(e.getMessage());
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (session != null) {
                session.close();
            }
        }
    }*/

    private static boolean isXml(String uri) {
        return uri.endsWith(".xml") && !FilePathBuilder.containsRawData(uri) && !uri.contains("xmlurls");
    }

    public void putFile(String uri, InputStream data) throws IOException {
        putFile(uri, data, true);
    }

    public void putFile(String xUri, InputStream data, boolean isClose) throws IOException {
        try {
            switch (modeUsedFlags) {
                case FILE_ONLY:
                    putFile2FileSystem(fixUri(xUri), data, isClose);
                    break;
                case LOGIC_FILE:
                    if (isXml(xUri)) {
                        putFile2MarkLogic(fixUri(xUri), data);
                    } else {
                        putFile2FileSystem(fixUri(xUri), data, isClose);
                    }
                    break;
                case LOGIC_FTP:
                    if (isXml(xUri)) {
                        putFile2MarkLogic(fixUri(xUri), data);
                    } else {
                        putFileFtp(fixUriForFtp(xUri), data);
                    }
                    break;
                case LOGIC_ONLY:
                    putFile2MarkLogic(fixUri(xUri), data);
                    break;
                default:
            }
        } catch (IOException e) {
            throw new IOException(e.getMessage());
        } finally {
            if (isClose) {
                data.close();
            }
        }
    }

    private void putFile2FileSystem(String uri, InputStream data, boolean isClose) throws IOException {
        OutputStream out = null;
        try {
            File f = getRealFile(uri.substring(0, uri.lastIndexOf("/")));
            if (!f.exists()) {
                if (!f.mkdirs()) {
                    // try once more as it can failed because of multi-threading,
                    // don't turn on synchronized as performance will slow down
                    // don't insert check as a dir can already appear
                    f.mkdirs();
                }
            }

            out = new BufferedOutputStream(new FileOutputStream(getRealFile(uri)));
            byte[] buffer = new byte[BUFFER_SIZE];

            int m;
            int sum = 0;
            while ((m = data.read(buffer)) > 0) {
                out.write(buffer, 0, m);
                sum += m;
            }
            if (m == 0) {
                LOG.error("File " + uri + "  size=0");
            }

            out.flush();
        } finally {
            if (out != null) {
                out.close();
            }

            if (isClose) {
                data.close();
            }
        }
    }

    /*private void createOptionsForMarkLogic() {
        options = new ContentCreateOptions();
        options.setLocale(Locale.US);
        options.setLanguage("en");
        options.setFormatXml();
        options.setCollections(new String[]{"http://wiley.ru/cochrane/source"});
    }*/

    /*@Deprecated
    public void putFileToMarklogicAsXml(String uri, InputStream is) throws Exception {
        if (isMarkLogicEnabled()) {
            if (options == null) {
                createOptionsForMarkLogic();
            }
            putFile2MarkLogic(fixUri(uri), is, options);
        }
    }*/

    public String getRepositoryPlace() {
        return filesystemRoot;
    }

    public String getRealFilePath(String uri) {
        File f = getRealFile(uri);
        return f.getAbsolutePath();
    }

    public File[] getFilesFromDir(String uri) {
        if (modeUsedFlags == ModeUsedFlags.LOGIC_FTP ||
            modeUsedFlags == ModeUsedFlags.LOGIC_ONLY) {
            throw new IllegalStateException("getFilesFromDir() not implemented for FTP and MarkLogic");
        }
        File f = getRealFile(uri);
        if (f != null && f.isDirectory()) {
            return f.listFiles();
        }
        return null;
    }

    public void putImage(BufferedImage image, String type, String path) throws IOException {
        String uri = fixUri(path);
        File f = getRealFile(uri.substring(0, uri.lastIndexOf("/")));
        f.mkdirs();
        f = getRealFile(uri);
        OutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(f));
            ImageIO.write(image, type, out);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    public ImageOutputStream getImageOutputStream(String path) throws IOException {
        String uri = fixUri(path);
        File f = getRealFile(uri.substring(0, uri.lastIndexOf("/")));
        f.mkdirs();

        return new FileImageOutputStream(getRealFile(uri));
    }

    /*private void putFile2MarkLogic(String uri, InputStream data, ContentCreateOptions opts) throws Exception {
        LOG.trace("Put into MarkLogic: " + uri);
        Session session = null;
        ByteArrayOutputStream out = null;
        try {
            out = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];

            int m;
            while ((m = data.read(buffer)) > 0) {
                out.write(buffer, 0, m);
            }

            session = getMlSession();
            Content content;
            if (uri.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))
                || uri.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCMR))
                || uri.contains(CochraneCMSPropertyNames.getCcaDbName())
                || !opts.getFormat().equals(DocumentFormat.XML)) {
                content = ContentFactory.newContent(uri, out.toByteArray(), opts);
            } else {
//                content = ContentFactory.newContent(uri,
//                        out.toByteArray(), opts);
                String doc = new EntitiesResolver()
                    .resolveEntity(new ByteArrayInputStream(out.toByteArray()));
                content = ContentFactory.newContent(uri, doc, opts);
            }
            synchronized (session) {
                session.insertContent(content);
            }
        } finally {
            if (session != null) {
                session.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }*/

    private void putFile2MarkLogic(String uri, InputStream data) throws IOException {
        //try {
        //    putFile2MarkLogic(uri, data, getCreateOptions(uri));
        //} catch (Exception e) {
        //    LOG.error(e, e);
        //    throw new IOException(e.getMessage());
        //}
    }

    /*private ContentCreateOptions getCreateOptions(String uri) {
        ContentCreateOptions opts = new ContentCreateOptions();
        opts.setLocale(Locale.US);
        opts.setLanguage("en");

        if (!isXml(uri)) {
            opts.setFormatBinary();
        } else {
            opts.setFormatXml();
        }
        return opts;
    }*/

    public long getFileLastModified(String xUri) throws IOException {
        long ret = 0;
        switch (modeUsedFlags) {
            case FILE_ONLY:
                ret = getFileLastModifiedFromFileSystem(fixUri(xUri));
                break;
            case LOGIC_FILE:
                if (isXml(xUri)) {
                    ret = getFileLastModifiedFromMarkLogic(fixUri(xUri));
                } else {
                    ret = getFileLastModifiedFromFileSystem(fixUri(xUri));
                }
                break;
            case LOGIC_FTP:
                if (isXml(xUri)) {
                    ret = getFileLastModifiedFromMarkLogic(fixUri(xUri));
                } else {
                    ret = getFileLastModifiedHttp(fixUri(xUri));
                }
                break;
            case LOGIC_ONLY:
                ret = getFileLastModifiedFromMarkLogic(fixUri(xUri));
                break;
            default:
        }
        return ret;
    }

    public long getFileLength(String xUri) throws IOException {
        long length = 0;
        switch (modeUsedFlags) {
            case FILE_ONLY:
                length = getFileLengthFromFileSystem(fixUri(xUri));
                break;
            case LOGIC_FILE:
                if (isXml(xUri)) {
                    length = getFileLengthFromMarkLogic(fixUri(xUri));
                } else {
                    length = getFileLengthFromFileSystem(fixUri(xUri));
                }
                break;
            case LOGIC_FTP:
                if (isXml(xUri)) {
                    length = getFileLengthFromMarkLogic(fixUri(xUri));
                } else {
                    length = getFileLengthHttp(fixUri(xUri));
                }
                break;
            case LOGIC_ONLY:
                length = getFileLengthFromMarkLogic(fixUri(xUri));
                break;
            default:
        }
        return length;
    }

    private int getFileLengthFromMarkLogic(String uri) throws IOException {
        throw new IllegalStateException("The method getFileLengthFromMarkLogic doesn`t implemented yet.");
    }

    private int getFileLastModifiedFromMarkLogic(String uri) throws IOException {
        throw new IllegalStateException("The method getFileLastModifiedFromMarkLogic doesn`t implemented yet.");
    }

    private long getFileLengthFromFileSystem(String uri) throws FileNotFoundException {
        File f = getRealFile(uri);
        return f.length();
    }

    private long getFileLastModifiedFromFileSystem(String uri) throws FileNotFoundException {
        File f = getRealFile(uri);
        return f.lastModified();
    }

    private int getFileLengthHttp(String uri) throws IOException {
        throw new IllegalStateException("The method getFileLengthHttp doesn`t implemented yet.");
    }

    private int getFileLastModifiedHttp(String uri) throws IOException {
        throw new IllegalStateException("The method getFileLastModifiedHttp doesn`t implemented yet.");
    }

    public InputStream getFile(String xUri) throws IOException {
        InputStream inputStream = null;

        switch (modeUsedFlags) {
            case FILE_ONLY:
                inputStream = getFileFromFileSystem(fixUri(xUri));
                break;
            case LOGIC_FILE:
                //if (isXml(xUri)) {
                //    inputStream = null; // getFileFromMarkLogic(fixUri(xUri));
                //} else {
                inputStream = getFileFromFileSystem(fixUri(xUri));
                //}
                break;
            case LOGIC_FTP:
                //if (isXml(xUri)) {
                //    inputStream = getFileFromMarkLogic(fixUri(xUri));
                //} else {
                inputStream = getFileHttp(fixUri(xUri));
                //}
                break;
            case LOGIC_ONLY:
                //inputStream = getFileFromMarkLogic(fixUri(xUri));
                break;
            default:
        }
        return inputStream;
    }

    public byte[] getFileAsByteArray(String uri) throws IOException {
        ByteArrayOutputStream baos = null;
        InputStream is = getFile(uri);

        baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int num;
        while ((num = is.read(buffer)) > 0) {
            baos.write(buffer, 0, num);
        }
        baos.flush();
        byte[] bytes = baos.toByteArray();
        baos.close();
        return bytes;
    }


    public boolean renameFile(String oldName, String newName) {
        File f = getRealFile(oldName);
        File n = getRealFile(newName);
        if (n.exists()) {
            n.delete();
        }
        return f.renameTo(n);
    }

    private InputStream getFileFromFileSystem(String uri) throws FileNotFoundException {
        //LOG.debug("Get from filesystem: " + uri);
        File f = getRealFile(uri);
        return new BufferedInputStream(new FileInputStream(f));
    }

    /*@Deprecated
    public InputStream getFileFromMarkLogic(String uri) throws IOException {
        if (!isMarkLogicEnabled()) {
            return null;
        }

        LOG.trace("Get from MarkLogic: " + uri);

        Session session = null;
        ResultSequence rs = null;
        ByteArrayOutputStream out = null;
        try {
            session = getMlSession();
            if (session == null) {
                throw new IOException("Coudn't connect to Marklogic");
            }
            Request request = session.newAdhocQuery("doc(\"" + uri + "\")");
            rs = session.submitRequest(request);
            if (rs.hasNext()) {
                ResultItem item = rs.next();
                InputStream in = new BufferedInputStream(item.getItem().asInputStream());
                out = new ByteArrayOutputStream();
                byte[] buffer = new byte[BUFFER_SIZE];

                int m;
                while ((m = in.read(buffer)) > 0) {
                    out.write(buffer, 0, m);
                }

                in.close();
                return new ByteArrayInputStream(out.toByteArray());
            }
            throw new FileNotFoundException("'" + uri + "' is not found!");
        } catch (XccException e) {
            LOG.error(e, e);
            throw new IOException(e.getMessage());
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (session != null) {
                session.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }*/

    @Deprecated
    public void deleteFileXcc(String url) throws IOException {
        /*if (!isMarkLogicEnabled()) {
            return;
        }

        String uri = fixUri(url);
        LOG.trace("Delete from MarkLogic: " + uri);

        Session session = null;
        ResultSequence rs = null;
        try {
            session = getMlSession();
            Request request = session.newAdhocQuery("xdmp:document-delete(\"" + uri + "\")");
            rs = session.submitRequest(request);
        } catch (XccException e) {
            LOG.error(e, e);
            throw new IOException(e.getMessage());
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (session != null) {
                session.close();
            }
        }*/
    }

    private void deleteFileLocal(String uri) throws IOException {
        File f = getRealFile(uri);
        if (!f.exists()) {
            return;
        }
        clearDir(f);
        f.delete();
    }

    private void clearDir(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();

            for (File file : files) {
                if (!file.getName().equals(".") && !file.getName().equals("..")) {
                    if (file.isDirectory()) {
                        clearDir(file);
                    }
                    file.delete();
                }
            }
        }
    }

    public void deleteFile(String xUri) throws IOException {
        switch (modeUsedFlags) {
            case FILE_ONLY:
                    deleteFileLocal(fixUri(xUri));
                break;
            case LOGIC_FILE:
                if (isXml(xUri)) {
                    deleteFileXcc(fixUri(xUri));
                } else {
                    deleteFileLocal(fixUri(xUri));
                }
                break;
            case LOGIC_FTP:
                if (isXml(xUri)) {
                    deleteFileXcc(fixUri(xUri));
                } else {
                    deleteFileFtp(fixUriForFtp(xUri));
                }
                break;
            case LOGIC_ONLY:
                deleteFileXcc(fixUri(xUri));
                break;
            default:
        }
    }

    public boolean isFileExistsQuiet(String uri) {
        try {
            return isFileExists(uri);
        } catch (IOException e) {
            LOG.error(e);
        }
        return false;
    }

    public boolean isFileExists(String xUri) throws IOException {
        boolean exists = false;
        switch (modeUsedFlags) {
            case FILE_ONLY:
                exists = isFileExistsLocal(fixUri(xUri));
                break;
            case LOGIC_FILE:
                if (isXml(xUri)) {
                    exists = isFileExistsXcc(fixUri(xUri));
                } else {
                    exists = isFileExistsLocal(fixUri(xUri));
                }
                break;
            case LOGIC_FTP:
                if (isXml(xUri)) {
                    exists = isFileExistsXcc(fixUri(xUri));
                } else {
                    exists = isFileExistsFtp(fixUriForFtp(xUri));
                }
                break;
            case LOGIC_ONLY:
                exists = isFileExistsXcc(fixUri(xUri));
                break;
            default:
        }
        return exists;
    }

    private boolean isFileExistsLocal(String uri) throws IOException {
        File f = getRealFile(uri);
        return f.exists();
    }

    private boolean isFileExistsXcc(String uri) throws IOException {
        throw new IllegalStateException("The method isFileExistsXcc doesn`t implemented yet.");
    }

    private boolean isFileExistsFtp(String uri) throws IOException {
        throw new IllegalStateException("The method isFileExistsFtp doesn`t implemented yet.");
    }

    private static String fixUri(String xUri) {
        String uri = xUri;
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        while (uri.contains("//")) {
            uri = uri.replace("//", "/");
        }
        return uri;
    }

    private static String fixDirUri(String xUri) {
        String uri = xUri;
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        if (!uri.endsWith("/")) {
            uri += "/";
        }
        return uri;
    }

    private static String fixUriForFtp(String xUri) {
        String uri = xUri;
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }

        return uri;
    }

    /**
     * Construct path to file at the file system
     *
     * @param uriString repository URI
     * @return file instance
     */
    private File getRealFile(String uriString) {
        return RepositoryUtils.getRealFile(filesystemRoot, uriString);
    }

    /**
     * Create MarkLogic session
     *
     * @return new ML session
     * @throws XccConfigException .
     */
    /*private Session getMlSession() throws XccConfigException {
//        try
//        {
        synchronized (RepositoryImpl.class) {
            for (int i = 0; i < 5; i++) {
                try {
                    ContentSource cs = ContentSourceFactory.newContentSource(new URI(marklogicConnection));
                    return cs.newSession();
                } catch (Exception e) {
                    LOG.error("Coudn't get session attempt=" + i + "   " + e, e);
                }
            }
        }
//        }
//        catch (URISyntaxException e)
//        {
//            throw new XccConfigException(e.getMessage());
//        }
        return null;
    } */

    // ==
    private boolean refreshFtpConnect() throws IOException {
        if (!ftpClient.isConnected()) {
            LOG.info("Open FTP connect to " + serverFtp);
            ftpClient.connect(serverFtp);
            ftpClient.login(userFtp, passwordFtp);
            int code = ftpClient.getReplyCode();
            if ((!FTPReply.isPositiveCompletion(code))) {
                LOG.error("Coudn't connect to ftp server");
                return false;
            }
        }
        return true;
    }

    private void deleteFileFtp(String uri) throws IOException {
        LOG.info("Delete from " + serverFtp + " file " + uri);

        refreshFtpConnect();
        ftpClient.deleteFile(uri);
    }

    private InputStream getFileHttp(String uri) throws IOException {
        LOG.info("Get from " + serverHttp + uri);

        URL url = new URL(serverHttp + uri);
        URLConnection connection = url.openConnection();

        return new BufferedInputStream(connection.getInputStream());
    }

    private void putFileFtp(String uri, InputStream data) throws IOException {
        //LOG.info("Put into " + serverFtp + " file " + uri);
        if (!refreshFtpConnect()) {
            throw new IOException("Couldn't connect to ftp server");
        }
        int ind = uri.lastIndexOf("/");
        while (ind != -1) {
            String path = uri.substring(0, ind);
            FTPFile[] files = ftpClient.listFiles(path);
            if (files != null && files.length != 0) {
                break;
            }
            ind = path.lastIndexOf("/");
        }
        if (ind == -1) {
            ind = uri.lastIndexOf("/");
        } else {
            ind = uri.indexOf("/", ind + 1);
        }
        while (ind != -1) {
            ftpClient.makeDirectory("/" + uri.substring(0, ind));
            ind = uri.indexOf("/", ind + 1);
        }
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        ftpClient.storeFile(uri, data);
    }

    //public void changeManifest(String xmlStr) throws Exception {
    //    startXquery(xqueryManifest + "cochrane:update(" + xmlStr + ")");
    //}

//    public void startTriggerIndex(String issueId, String dbName, String recordName, String timeStamp)
//            throws NamingException
//    {
//        InitialContext ctx = new InitialContext();
//        IResultsStorage rs = (IResultsStorage) ctx.lookup("CochraneCMS/ResultsStorage/local");
//        if (rs.getRecord(issueId, dbName, recordName).isRenderingCompleted())
//        {
//            startXquery(xqueryTriggerIndex + "shadow:shadow-copy" +
//                    "(\"" + issueId + "\", \"" + dbName + "\",\"" + timeStamp + "\")");
//        }
//    }


    /*private void startXquery(String queryString) throws Exception {
        Session session = null;
        ResultSequence rs = null;
        try {
            session = getMlSession();

            RequestOptions opts = new RequestOptions();
            opts.setLocale(Locale.US);

            Request request = session.newAdhocQuery(queryString, opts);
            rs = session.submitRequest(request);

        } catch (Exception e) {
            LOG.error("queryString " + queryString);
            LOG.error(e, e);
            throw new Exception(e);
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (session != null) {
                session.close();
            }
        }
    }*/

}