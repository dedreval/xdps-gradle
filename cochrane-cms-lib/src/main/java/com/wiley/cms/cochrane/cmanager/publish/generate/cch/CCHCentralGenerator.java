package com.wiley.cms.cochrane.cmanager.publish.generate.cch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 02.11.11
 */
public class CCHCentralGenerator extends CCHGenerator {
    private static final Logger LOG = Logger.getLogger(CCHCentralGenerator.class);

    private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String DUMMY_OPEN = "<dummy>\n";
    private static final String DUMMY_CLOSE = "</dummy>\n";

    private int fileNumber;

    public CCHCentralGenerator(ClDbVO db) {
        super(db);
    }

    protected CCHCentralGenerator(ClDbVO db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
    }

    @Override
    protected int getBatchSize() {
        return CochraneCMSPropertyNames.getCentralCCHArticlesBatchSize();
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<RecordWrapper> recordList) {
        List<ArchiveEntry> ret = new ArrayList<>();
        excludeDisabledRecords(recordList, false);
        if (recordList.isEmpty()) {
            return ret;
        }

        int articlesInXml = CCHCentralGeneratorEntire.getCentralCCHArticlesInXmlSize();
        int currentCount = 0;
        StringBuilder xml = new StringBuilder();
        xml.append(XML_DECL);
        xml.append("\n");
        xml.append(DUMMY_OPEN);
        for (RecordWrapper record : recordList) {

            if (currentCount == articlesInXml) {
                addArchiveEntry(ret, xml);
                fileNumber += articlesInXml;
                currentCount = 0;
            }

            InputStream is = null;
            try {
                is = rps.getFile(record.getRecordPath());
                String source = IOUtils.toString(is, StandardCharsets.UTF_8);
                xml.append(source.substring(XML_DECL.length()));
                xml.append("\n");

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
    protected boolean checkEmpty() {
        return true;
    }

    private void addArchiveEntry(List<ArchiveEntry> ret, StringBuilder xml) {
        xml.append(DUMMY_CLOSE);
        ret.add(new ArchiveEntry("clcentral" + (fileNumber == 0 ? 0 : fileNumber + 1) + ".xml", null, xml.toString()));
        xml.setLength(0);
        xml.append(XML_DECL);
        xml.append("\n");
        xml.append(DUMMY_OPEN);
    }
}