package com.wiley.cms.cochrane.cmanager.parser;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 05-Jun-2007
 */
public abstract class SourceParser {
    private static final Logger LOG = Logger.getLogger(SourceParser.class);

    protected SourceParsingResult result;
    protected int tagsCount;
    protected StringBuilder text;
    protected int level;

    public void setResult(SourceParsingResult result) {
        this.result = result;
    }

    public void setTagsCount(int tagsCount) {
        this.tagsCount = tagsCount;
    }

    public StringBuilder startElement(int number, Attributes atts) {
        if (number != -1 && level == number) {
            level++;
        }
        if (level == tagsCount && number == tagsCount - 1) {
            text = new StringBuilder();
        }
        return text;
    }

    public abstract void endElement(int i) throws FinishException;

    public static SourceParsingResult parseSource(String xml) {
        String data = CmsUtils.correctDtdPath(xml);
        SourceParsingResult result = new SourceParsingResult();

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(true);

        String[] tagMeth = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_METH).split("/");
        String[] tagDoi = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_DOI).split("/");
        String[] tagClIssue = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_CL_ISSUE).split("/");
        String[] tagGroupCode = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TAG_GROUP).split("/");

        String[][] tags = new String[][]{tagDoi, tagMeth, tagClIssue, tagGroupCode};

        SourceParser doiParser = new DoiParser();
        doiParser.setTagsCount(tagDoi.length);
        doiParser.setResult(result);

        SourceParser methodologyParser = new MethodologyParser();
        methodologyParser.setTagsCount(tagMeth.length);
        methodologyParser.setResult(result);

        SourceParser clIssueParser = new CLIssueParser();
        clIssueParser.setTagsCount(tagClIssue.length);
        clIssueParser.setResult(result);

        SourceParser groupCodeParser = new GroupCodeParser();
        groupCodeParser.setTagsCount(tagGroupCode.length);
        groupCodeParser.setResult(result);

        try {
            SAXParser parser = factory.newSAXParser();
            parser.parse(new ByteArrayInputStream(data.getBytes()), new SourceHandler(result, tags,
                    new SourceParser[]{doiParser, methodologyParser, clIssueParser, groupCodeParser}));
        } catch (Exception e) {
            LOG.error(e, e);
        }
        return result;
    }
}