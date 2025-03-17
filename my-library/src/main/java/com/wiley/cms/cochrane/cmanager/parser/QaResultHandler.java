package com.wiley.cms.cochrane.cmanager.parser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.services.DataTransferer;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.XmlUtils;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 28-Apr-2007
 */
public class QaResultHandler extends DefaultHandler {
    private static final Logger LOG = Logger.getLogger(QaResultHandler.class);
    //private static final String SUCCESSFUL_MESSAGE =
    //        CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.MESSAGE_SUCCESSFUL_QA);

    private static final String MESSAGES_QNAME = "messages";
    private static final String MESSAGE = "message";
    private static final String SUCCESSFUL = "successful";
    private static final String TRUE = "true";
    private static final String COMPLETED = "completed";

    //private Record curr;
    private QaParsingResult result;
    private int jobId;

    private int badCount;
    private int goodCount;

    private StringBuilder messages;
    private StringBuilder message;
    private StringBuilder messageBody;
    private SortedMap<String, Record> recordsMap;
    private int dbId;
    private IResultsStorage rs;
    private List<String> badFiles;
    //private StringBuilder resupplyList;
    private Record record;
    private String fileName;
    private String stage;

    public QaResultHandler(int jobId, QaParsingResult result) {
        this.jobId = jobId;
        this.result = result;

        rs = ResultStorageFactory.getFactory().getInstance();
    }


    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (qName.equals("files")) {
            recordsMap = new TreeMap<String, Record>();
            badFiles = new ArrayList<String>();
            //recs = new ArrayList<Record>();

        }
        if (qName.equals("file")) {
            parseOneUri(atts);
        }
        if (qName.equals(MESSAGES_QNAME)) {
            messages = new StringBuilder();
            stage = atts.getValue("stage");
        }
        if (qName.equals(MESSAGE)) {
            message = new StringBuilder();
            messageBody = new StringBuilder();
            message.append("<message quality=\"").append(atts.getValue("quality")).append("\">");
        }
        if (qName.equals("job")) {
            result.setCompleted(atts.getValue(COMPLETED).equals(TRUE));
            result.setSuccessful(atts.getValue(SUCCESSFUL).equals(TRUE));
            if (atts.getValue(MESSAGE) != null) {
                result.setMessage(atts.getValue(MESSAGE));
            }
        }

    }

    public void characters(char[] ch, int start, int length) {
        if (message != null) {
            messageBody.append(new String(ch, start, length));
        }
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("results")) {
            result.setBadCount(badCount);
            result.setGoodCount(goodCount);
            Iterator<Record> it = recordsMap.values().iterator();
            List<Record> recs = new ArrayList<Record>();
            while (it.hasNext()) {
                recs.add(it.next());
            }
            result.setRecords(recs);
            result.setBadFiles(badFiles);
        }
        if (qName.equals(MESSAGE)) {
            if (message.length() != 0) {
                message.append(XmlUtils.escapeElementEntities(messageBody.toString())).append("</message>");
                messages.append(message);
                // add to current record
            }
        }

        if (qName.equals(MESSAGES_QNAME)) {
            if (messages.length() != 0) {
                StringBuilder msgsTmp = (StringUtils.isEmpty(record.getMessages())
                        ? new StringBuilder()
                        : new StringBuilder(record.getMessages()));
                msgsTmp.append("<messages stage=\"").append(stage).append("\">")
                        .append("<uri name=\"").append(fileName).append("\"/>")
                        .append(messages).append("</messages>");
                record.setMessages(msgsTmp.toString());
                messages = null;
            }
        }
    }

    private void parseOneUri(Attributes atts) {
        //String jobPartId = atts.getValue("id");

        String uri = atts.getValue("uri");
        //String filePath1 = uri.substring(uri.indexOf("/"
        //        + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/") + 1);

        uri = uri.substring(uri.indexOf(DataTransferer.class.getSimpleName()));
        String filePath = uri.substring(uri.indexOf(CochraneCMSProperties.getProperty(
                    CochraneCMSPropertyNames.PREFIX_REPOSITORY)));

        fileName = filePath.substring(filePath.lastIndexOf("/") + 1);

        String[] parts = FilePathCreator.getSplitedUri(filePath);


        if (result.getIssueId() == 0) {
            LOG.debug("filePath:" + filePath + "  parts[0](issueId):" + parts[0]);
            result.setIssueId(Integer.parseInt(parts[0]));
        }
        if (result.getDbName() == null) {
            result.setDbName(parts[1]);
        }

        String recordName = parts[2];
        record = recordsMap.get(recordName);
        if (record == null) {
            record = new Record(parts[2], atts.getValue(COMPLETED).equals(TRUE));
            recordsMap.put(recordName, record);
        }

        String isSuccessful = atts.getValue(SUCCESSFUL);
        if (!isSuccessful.equals(TRUE)) {
            badCount++;
            badFiles.add(filePath);
        } else {
            goodCount++;
        }
        record.setSuccessful(isSuccessful.equals(TRUE));
        //       record.setJobPartId(new Integer(atts.getValue("id")));
        if (filePath.endsWith(parts[2] + Extensions.XML)) {
            record.setRecordSourceUri(filePath);
        } //else if (filePath.endsWith(parts[parts.length - 1] + Extensions.XML)) {

        //}
        record.addUri(filePath);

        if (dbId == 0) {
            dbId = rs.findDb(result.getIssueId(), result.getDbName());
        }
    }
}

