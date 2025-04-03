package com.wiley.cms.cochrane.cmanager.publish.generate.cch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveHolder;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 14.11.11
 */
public class CCHCentralGeneratorEntire extends AbstractGeneratorEntire<ArchiveHolder> {
    private static final Logger LOG = Logger.getLogger(CCHCentralGeneratorEntire.class);

    private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String DUMMY_OPEN = "<dummy>\n";
    private static final String DUMMY_CLOSE = "</dummy>\n";

    private int fileNumber;

    public CCHCentralGeneratorEntire(EntireDbWrapper db) {
        super(db, "CCH:ENTIRE:MAIN:" + db.getDbName(), PubType.TYPE_CCH);
    }

    public CCHCentralGeneratorEntire(EntireDbWrapper db, String generateName, String pubType) {
        super(db, generateName, pubType);
    }

    public static int getCentralCCHArticlesInXmlSize() {

        int articlesInXml = CochraneCMSPropertyNames.getCentralCCHArticlesInXmlSize();
        int batchSize = CochraneCMSPropertyNames.getCentralCCHArticlesBatchSize();
        if (batchSize < articlesInXml) {
            LOG.warn(String.format("'cms.cochrane.publish.cch.clcentral.articlesInXml=%d'", articlesInXml)
                + String.format(" cannot be more than 'cms.cochrane.publish.cch.clcentral.pieceSize=%d'", batchSize)
                + String.format(", so the %d instead of %d will be used", batchSize, articlesInXml));
            articlesInXml = batchSize;
        }
        return articlesInXml;
    }

    @Override
    protected int getBatchSize() {
        return CochraneCMSPropertyNames.getCentralCCHArticlesBatchSize();
    }

    @Override
    protected List<EntireRecordWrapper> getRecordList(int startIndex, int count) {
        return EntireRecordWrapper.getRecordWrapperList(getDb().getDbName(), startIndex, count);
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<EntireRecordWrapper> recordList) throws Exception {
        List<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();

        int articlesInXml = getCentralCCHArticlesInXmlSize();
        int currentCount = 0;
        StringBuilder xml = new StringBuilder();
        xml.append(XML_DECL);
        xml.append("\n").append(DUMMY_OPEN);
        for (EntireRecordWrapper record : recordList) {
            if (currentCount == articlesInXml) {
                addArchiveEntry(ret, xml);
                fileNumber += articlesInXml;
                currentCount = 0;
            }

            InputStream is = null;
            try {
                is = rps.getFile(record.getRecordPath());
                String source = IOUtils.toString(is, StandardCharsets.UTF_8);
                xml.append(source.substring(XML_DECL.length())).append("\n");

                onRecordArchive(record);
            } catch (IOException e) {
                LOG.error("Failed to read file: [" + record.getRecordPath() + "]", e);
            } finally {
                IOUtils.closeQuietly(is);
            }
            currentCount++;
        }
        addArchiveEntry(ret, xml);
        fileNumber += articlesInXml;
        return ret;
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        List<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
        String xml = "<emrw:targets xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
                + "xmlns:emrw=\"http://wiley.com/interscience/emrw\"/>";
        ret.add(new ArchiveEntry("links.xml", null, xml));
        return ret;
    }

    @Override
    protected String getArchivePrefix(String recName) {
        return "";
    }

    private void addArchiveEntry(List<ArchiveEntry> ret, StringBuilder xml) {
        xml.append(DUMMY_CLOSE);
        ret.add(new ArchiveEntry("clcentral" + (fileNumber == 0 ? 0 : fileNumber + 1) + ".xml", null, xml.toString()));
        xml.setLength(0);
        xml.append(XML_DECL).append("\n");
        xml.append(DUMMY_OPEN);
    }
}
