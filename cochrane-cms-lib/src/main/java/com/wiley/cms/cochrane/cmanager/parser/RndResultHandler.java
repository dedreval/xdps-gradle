package com.wiley.cms.cochrane.cmanager.parser;

import java.util.ArrayList;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.CachedPath;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.services.DataTransferer;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 25-Apr-2007
 */
public class RndResultHandler extends DefaultHandler {
    private static final Logger LOG = Logger.getLogger(RndResultHandler.class);

    private static final String RESULT = "result";
    private static final String OUTPUT = "output";
    private static final String FILE = "file";
    private static final String JOB = "job";
    private static final String COMPLETED = "completed";
    private static final String SUCCESSFUL = "successful";

    private static final String TRUE = "true";
    private static final String URL = "url";

    protected Record curr;
    protected RndParsingResult result;

    private List<String> files;
    private List<Record> recs;
    private int jobId;
    private int badCount;
    private final boolean toImport;

    public RndResultHandler(int jobId, RndParsingResult result) {
        this(jobId, result, false);
    }

    public RndResultHandler(int jobId, RndParsingResult result, boolean toImport) {
        this.jobId = jobId;
        this.result = result;
        this.toImport = toImport;
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (qName.equals(RESULT)) {
            if (recs == null) {
                recs = new ArrayList<Record>();
            }
            parseOneRecord(atts);
        }
        if (qName.equals(OUTPUT)) {
            files = new ArrayList<String>();

        }
        if (qName.equals(FILE)) {
            files.add(atts.getValue(URL));
        }

        if (qName.equals(JOB)) {
            result.setCompleted(atts.getValue(COMPLETED).equals(TRUE));
            result.setSuccessful(atts.getValue(SUCCESSFUL).equals(TRUE));

            RenderingPlan plan = RenderingPlan.get(atts.getValue("plan"));

            if (plan == null) {
                throw new IllegalStateException("'plan' can't be null");
            }

            if (RenderingPlan.UNKNOWN == plan) {
                LOG.info("plan= unknown => jobId=" + jobId + " not exists");
                throw new SAXException("JobId not exists");
            }
            result.setPlan(plan);
        }
    }

    protected void fillIn(String filePath, boolean completed) {
        String[] parts = toImport && FilePathBuilder.isPreviousPath(filePath)
                ? FilePathBuilder.getPreviousUriIssueParts(filePath, CachedPath.ISSUE_PREVIOUS_RECORD)
                : FilePathCreator.getSplitedUri(filePath);
        if (result.getIssueId() == 0) {
            result.setIssueId(Integer.parseInt(parts[0]));
        }
        if (result.getDbName() == null) {
            result.setDbName(parts[1]);
        }

        curr = new Record(parts[2], completed);
        curr.setRecordSourceUri(filePath);
    }

    private void parseOneRecord(Attributes atts) {
        String jobPartId = atts.getValue("id");
//        if (!result.isCompleted())
//        {
//            jobPartList.add(Integer.parseInt(jobPartId));
//        }
        String uri = atts.getValue(URL);
        uri = uri.substring(uri.indexOf(DataTransferer.class.getSimpleName()));
        //int ind = uri.indexOf(FilePathCreator.SEPARATOR + CochraneCMSProperties.getProperty(
        //        CochraneCMSPropertyNames.PREFIX_REPOSITORY) + FilePathCreator.SEPARATOR);
        //LOG.debug("uri: " + uri);
        String filePath = uri.substring(uri.indexOf(CochraneCMSProperties.getProperty(
            CochraneCMSPropertyNames.PREFIX_REPOSITORY)));
        //LOG.debug("url: " + filePath);

        fillIn(filePath, atts.getValue(COMPLETED).equals(TRUE));

        String isSuccessful = atts.getValue(SUCCESSFUL);
        if (!isSuccessful.equals(TRUE)) {
            badCount++;
            curr.setMessages(atts.getValue("message"));
        }
        curr.setSuccessful(isSuccessful.equals(TRUE));
        curr.setJobPartId(new Integer(atts.getValue("id")));
        curr.setRecordSourceUri(filePath);
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("results")) {
//            if (!result.isCompleted())
//            {
//                int[] jobPartIds = new int[jobPartList.size()];
//                for (int i = 0; i < jobPartList.size(); i++)
//                {
//                    jobPartIds[i] = jobPartList.get(i);
//
//                }
//                result.setJobPartIds(jobPartIds);
//            }
            result.setBadCount(badCount);
            result.setRecords(recs);
        }
        if (qName.equals(RESULT)) {
            recs.add(curr);
        }
        if (qName.equals(OUTPUT)) {
            curr.setFilesList(files);
        }
    }
}

