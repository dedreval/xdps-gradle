package com.wiley.cms.cochrane.cmanager.data.record;

import java.io.File;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;

/**
 * Record manifest representation.
 * <p/>
 * <pre>
 * &lt;record&gt;
 *  &lt;metadata&gt;
 *   &lt;issue id="issue id"/&gt;
 *   &lt;database id="cochrane database"/&gt;
 *   &lt;record id="record id"/&gt;
 *  &lt;/metadata&gt;
 *  &lt;groups&gt;
 *   &lt;group id="source"&gt;
 *    &lt;file uri="cochrane/clsysrev/cl2006.4/CD000227/source/CD000227.xml" type="text/xml" /&gt;
 *    &lt;file uri="cochrane/clsysrev/cl2006.4/CD000227/source/CD000227RawData.xml" type="text/xml" /&gt;
 *   &lt;/group&gt;
 *   &lt;group id="temp"&gt;
 *    &lt;file uri="cochrane/clsysrev/cl2006.4/CD000227/temp/CD000227.xml" type="text/xml" /&gt;
 *    &lt;file uri="cochrane/clsysrev/cl2006.4/CD000227/temp/CD000227RawData.xml" type="text/xml" /&gt;
 *   &lt;/group&gt;
 *   &lt;group id="rendered-pdf"&gt;
 *    &lt;file uri="cochrane/clsysrev/cl2006.4/CD000227/rendered-pdf/CD000227.pdf" type="application/pdf" /&gt;
 *   &lt;/group&gt;
 *   &lt;group id="rendered-html_diamond"&gt;
 *    &lt;file uri="cochrane/clsysrev/cl2006.4/CD000227/rendered-html/CD000227.html" type="text/html" /&gt;
 *    ... some support files
 *   &lt;/group&gt;
 *  &lt;/groups&gt;
 * &lt;/record&gt;
 * </pre>
 * <p/>
 * Next task is replacement this simple manifest into
 * <a href="http://www.imsproject.org/content/packaging/cpv1p1p2/imscp_infov1p1p2.html">IMS manifest</a>
 *
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @version 1.0
 */
public class RecordManifest implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String CLOSE_TAG = "\"/>";
    private final int issue;
    private final String dbName;
    private final String record;
    private final String recordPath;
    private boolean isJats;
    private boolean isCentral;
    private Integer historyVersion;

    private Map<String, FileGroup> groups = new HashMap<String, FileGroup>();

    public RecordManifest(int issue, String dbName, String recordName, String recordPath) {
        this(issue, dbName, recordName, recordPath, null);
    }

    public RecordManifest(int issue, String dbName, String recordName, String recordPath, Integer historyVersion) {
        this.issue = issue;
        this.dbName = dbName;
        this.record = recordName;
        this.recordPath = recordPath;
        this.historyVersion = historyVersion;
        this.isCentral = CochraneCMSPropertyNames.getCentralDbName().equals(dbName);
    }

    public List<String> getUris() {

        List<String> ret = new ArrayList<String>();
        ret.addAll(getUris(Constants.SOURCE));
        ret.addAll(getUris(Constants.RENDERED_HTML_DIAMOND));
        ret.addAll(getUris(Constants.RENDERED_PDF_TEX));
        ret.addAll(getUris(Constants.RENDERED_PDF_FOP));
        return ret;
    }

    public List<String> getUris(List<String> groups) {

        List<String> ret = new ArrayList<String>();
        for (String groupKey: groups) {
            ret.addAll(getUris(groupKey));
        }
        return ret;
    }

    public List<String> getUris(String groupKey) {

        FileGroup group = groups.get(groupKey);
        if (group == null) {

            IRepository rp = RepositoryFactory.getRepository();
            group = new FileGroup(groupKey);

            if (Constants.SOURCE.equals(groupKey))  {
                if (isJats) {
                    RepositoryUtils.addFolderPaths(new File(recordPath).getParent() + FilePathCreator.SEPARATOR,
                                                   group.getFiles(), rp);
                } else {
                    group.addFile(recordPath);
                    RepositoryUtils.addFolderPaths(RepositoryUtils.getRecordNameByFileName(recordPath)
                                                           + FilePathCreator.SEPARATOR, group.getFiles(), rp);
                }
            } else if (historyVersion == null && (group.isPDF() || Constants.RENDERED_HTML_DIAMOND.equals(groupKey))) {
                String basePath;
                if (FilePathBuilder.isEntirePath(recordPath)) {
                    basePath = group.isPDF() ? FilePathBuilder.PDF.getPathToEntirePdfFop(dbName, record)
                            : FilePathBuilder.getPathToEntireHtml(dbName, record, isCentral);
                } else {
                    String path = FilePathCreator.getFilePathToSource(groupKey, "" + issue, dbName, record);
                    basePath = RepositoryUtils.getRecordNameByFileName(path) + FilePathCreator.SEPARATOR;
                }
                RepositoryUtils.addFolderPaths(basePath, group.getFiles(), rp);

            } else if (historyVersion != null && group.isPDF()) {
                RepositoryUtils.addFolderPaths(ContentLocation.ISSUE_PREVIOUS.getPathToPdf(issue, dbName,
                        historyVersion, record), group.getFiles(), rp);
            }
            groups.put(groupKey, group);
        }

        return group.getFiles();
    }

    private static boolean isPDF(String groupKey) {
        return Constants.RENDERED_PDF_FOP.equals(groupKey) || Constants.RENDERED_PDF_TEX.equals(groupKey);
    }


    //public RecordManifest(final InputSource source) throws Exception {
    //    SAXParserFactory factory = SAXParserFactory.newInstance();
    //    SAXParser parser = factory.newSAXParser();

    //    parser.parse(source, new ManifestParser());
    //}

    public FileGroup addGroup(final String name) {
        final FileGroup group = new FileGroup(name);
        groups.put(name, group);
        return group;
    }

    //public void removeGroup(final String name) {
    //    groups.remove(name);
    //}

    public FileGroup getGroup(final String name) {
        FileGroup group = groups.get(name);
        if (group == null) {
            group = addGroup(name);
        }
        return group;
    }

    public int getIssue() {
        return issue;
    }

    public String getDb() {
        return dbName;
    }

    public String getRecord() {
        return record;
    }

    public String getRecordPath() {
        return recordPath;
    }

    public void setJats(boolean isJats) {
        this.isJats = isJats;
    }

    public Map<String, FileGroup> getGroups() {
        return groups;
    }

    public String toXml() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.println("<record>");
        out.println(" <metadata>");
        out.println("  <issue id=\"" + issue + CLOSE_TAG);
        out.println("  <database id=\"" + dbName + CLOSE_TAG);
        out.println("  <record id=\"" + record + CLOSE_TAG);
        out.println(" </metadata>");
        out.println(" <groups>");
        for (Map.Entry<String, FileGroup> entry : groups.entrySet()) {
            out.println("  <group id=\"" + entry.getKey() + "\">");
            for (String f : entry.getValue().getFiles()) {
                out.println("   <file uri=\"" + f + CLOSE_TAG);
            }
            out.println("  </group>");
        }
        out.println(" </groups>");
        out.println("</record>");
        return sw.toString();
    }

    public String generateBaseUri() {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + issue
                + FilePathCreator.SEPARATOR + dbName + FilePathCreator.SEPARATOR + record + FilePathCreator.SEPARATOR;
    }

    /**
     * Simple manifest SAX loader
     */
    /*private class ManifestParser extends DefaultHandler {
        private FileGroup current = null;

        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            if (qName.equals("issue")) {
                issue = Integer.parseInt(atts.getValue("id"));
            } else if (qName.equals("database")) {
                dbName = atts.getValue("id");
            } else if (qName.equals("record")) {
                record = atts.getValue("id");
            } else if (qName.equals("group")) {
                current = addGroup(atts.getValue("id"));
            } else if (qName.equals("file")) {
                current.addFile(atts.getValue("uri"));
            }
        }
    } */

    /**
     * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
     * @version 1.0
     */
    private static class FileGroup implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private List<String> files = new ArrayList<>();
        private final boolean pdf;

        public FileGroup(final String id) {
            this.id = id;
            pdf = Constants.RENDERED_PDF_FOP.equals(id) || Constants.RENDERED_PDF_TEX.equals(id);
        }

        public String getId() {
            return id;
        }

        public List<String> getFiles() {
            return files;
        }

        public void addFile(final String fileName) {
            files.add(fileName);
        }

        public boolean isPDF() {
            return pdf;
        }
    }
}
