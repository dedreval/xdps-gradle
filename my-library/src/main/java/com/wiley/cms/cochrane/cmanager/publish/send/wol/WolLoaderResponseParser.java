package com.wiley.cms.cochrane.cmanager.publish.send.wol;

import com.wiley.cms.cochrane.cmanager.publish.send.wol.WolLoaderErrorTypeAnalyser.ErrorType;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.jdom.DocumentLoader;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.xpath.XPath;
import org.jsoup.Jsoup;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 10.01.2018
 */
@Deprecated
public class WolLoaderResponseParser {

    private static final Logger LOG = Logger.getLogger(WolLoaderResponseParser.class);
    private static final String VALID = "VALID";
    private static final String STATUS_ATTR = "status";
    private final WolLoaderErrorTypeAnalyser errorAnalyser;

    public WolLoaderResponseParser() {
        this(new WolLoaderErrorTypeAnalyser());
    }

    public WolLoaderResponseParser(WolLoaderErrorTypeAnalyser errorAnalyser) {
        this.errorAnalyser = errorAnalyser;
    }

    /**
     * @throws Exception   HttpResponse's payload can't be read or the payload can't parsed.
     */
    public WolLoaderResponse parse(HttpResponse response) throws Exception {
        String payload = readPayload(response);
        Header contentType = response.getFirstHeader("content-type");
        ParsingResult parsingResult = parsePayload(payload, contentType);
        int statusCode = response.getStatusLine().getStatusCode();
        return analyseResult(statusCode, parsingResult);
    }

    private String readPayload(HttpResponse response) throws Exception {
        try (InputStream is = response.getEntity().getContent()) {
            String payload = IOUtils.toString(is);
            if (LOG.isTraceEnabled()) {
                LOG.trace(String.format("Payload received from WOL loader: \n%s", payload));
            }
            return payload;
        } catch (Exception e) {
            throw new Exception("Unable to read response. " + e, e);
        }
    }

    private ParsingResult parsePayload(String payload, Header contentType) throws Exception {
        return (contentType != null && contentType.getValue().contains("text/html"))
                ? parseHtmlPayload(payload)
                : parseXmlPayload(payload);
    }

    private ParsingResult parseHtmlPayload(String payload) {
        org.jsoup.nodes.Document document = Jsoup.parse(payload);
        String message = Matcher.quoteReplacement(document.body().text());
        return new ParsingResult(Collections.emptyList(), false, message);
    }

    private ParsingResult parseXmlPayload(String payload) throws Exception {
        DocumentLoader dl = new DocumentLoader();
        String contentDeliveryStatus = "";
        String errorMessage = "";
        List<String> validUnits = new ArrayList<>();

        try {
            Document document = dl.load(payload);
            Element contentDeliveryContainer = (Element) XPath.selectSingleNode(document, "//contentDelivery");
            if (contentDeliveryContainer != null) {
                contentDeliveryStatus = contentDeliveryContainer.getAttribute(STATUS_ATTR).getValue();
            }

            Element messageContainer = (Element) XPath.selectSingleNode(document, "//contentDelivery/message");
            if (messageContainer != null) {
                errorMessage = messageContainer.getText();
            }

            List nodes = XPath.selectNodes(document, "//contentPackage/contentUnit");
            if (nodes != null) {
                for (Object node : nodes) {
                    Element element = (Element) node;
                    String recordStatus = element.getAttribute(STATUS_ATTR).getValue();
                    String file = element.getAttribute("path").getValue();
                    if (recordStatus.equals(VALID)) {
                        validUnits.add(file.replace(Extensions.XML, ""));
                    } else {
                        LOG.warn(file + " is not valid");
                    }
                }
            }
        } catch (Exception e) {
            throw new Exception(String.format("Unable to parse response:\n%s\n%s", payload, e), e);
        }

        boolean contentDelivered = contentDeliveryStatus.equals(VALID);
        if (!contentDelivered) {
            validUnits.clear();
        }
        return new ParsingResult(validUnits, contentDelivered, errorMessage);
    }

    private WolLoaderResponse analyseResult(int statusCode, ParsingResult parsingResult) {
        boolean success = statusCode == HttpStatus.SC_OK && parsingResult.isContentDelivered();
        boolean repeat = false;
        if (!success) {
            ErrorType errorType = errorAnalyser.getErrorType(parsingResult.getMessage());
            success = errorType == ErrorType.IGNORABLE;
            repeat = errorType == ErrorType.RECOVERABLE;
        }
        return new WolLoaderResponse(
                success,
                repeat,
                statusCode,
                parsingResult.getValidUnits(),
                parsingResult.getMessage());
    }

    /**
     *
     */
    private class ParsingResult {

        private final List<String> validUnits;
        private final boolean contentDelivered;
        private final String message;

        private ParsingResult(List<String> validUnits, boolean contentDelivered, String message) {
            this.validUnits = validUnits;
            this.contentDelivered = contentDelivered;
            this.message = message == null ? "" : message;
        }

        public List<String> getValidUnits() {
            return validUnits;
        }

        public boolean isContentDelivered() {
            return contentDelivered;
        }

        public String getMessage() {
            return message;
        }
    }
}
