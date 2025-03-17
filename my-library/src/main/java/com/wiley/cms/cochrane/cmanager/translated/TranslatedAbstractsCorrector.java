package com.wiley.cms.cochrane.cmanager.translated;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.XmlUtils;

/**
 * @author Sergey Trofimov
 *         XML corrector for translated abstracts
 */
public class TranslatedAbstractsCorrector {
    private static final Logger LOG = Logger.getLogger(TranslatedAbstractsCorrector.class);

    private static final int MAX_WINDOW_SIZE = 30;
    private static final int PIECE_FOR_LOG = 200;

    private static final String TITLE_TAG = "title";

    private static final String SID_TAG = "ID";
    private static final String SID_TAG2 = "SOURCE_ID";
    private static final String LANGUAGE_TAG = "LANGUAGE";
    private static final String VERSION_TAG = "VERSION_NO";
    private static final String XML_VERSION_TAG = "XML_VERSION";
    private static final String DOI_TAG = "DOI";

    private static final List<String> ALLOWED_TAGS = Collections.unmodifiableList(
            Arrays.asList(TITLE_TAG, "p", "u", "b", "i")
    );

    private TranslatedAbstractsCorrector() {
    }

    public static String windowToStr(List<Integer> w, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            sb.appendCodePoint(w.get(i));
        }
        return sb.toString();
    }

    public static String windowToStr(List<Integer> w, int from) {
        return windowToStr(w, from, w.size());
    }

    public static String windowToStr(List<Integer> w) {
        return windowToStr(w, 0);
    }

    public static InputStream correctBrokenTags(InputStream in) throws IOException {

        StringBuffer out = new StringBuffer();

        InputStreamReader isr = new InputStreamReader(in, XmlUtils.UTF_8);
        LinkedList<Integer> window = new LinkedList<Integer>();
        windowToStr(window);

        int ch, tch;
        boolean tagStart = false;
        boolean endFlag = false;

        do {
            ch = isr.read();
            if (ch > -1) {
                window.addLast(ch);
            } else {
                endFlag = true;
            }

            if (ch == -1 || window.size() > MAX_WINDOW_SIZE) {
                tch = window.isEmpty() ? 0 : window.removeFirst();

                if (tch == '<' && tagStart) {
                    tagStart = false;
                    int idx = window.get(0) == '/' ? 1 : 0;
                    int supposedTagEnd = beginsWithTag(windowToStr(window, idx));
                    if (supposedTagEnd > 0) {
                        window.add(idx + supposedTagEnd, (int) '>');
                        out.appendCodePoint(tch);
                        flush(window, out, idx + supposedTagEnd + 1);
                        continue;
                    }
                }

                if (tch != 0) {
                    out.append(encode(tch, window));
                }
            }

            switch (ch) {
                case '<':
                    if (tagStart) {
                        int supposedTagStart = beginsWithTag(windowToStr(window, 1));
                        if (supposedTagStart > 0) {
                            window.add(1 + supposedTagStart, new Integer('>'));
                            flush(window, out, window.size() - 1);
                        }
                    } else {
                        // suppose that tag opened
                        tagStart = true;
                    }
                    break;
                case '>':
                    if (tagStart) {
                        // check just closed tag
                        int idx = window.size() - 1;

                        // todo: or >0
                        StringBuilder tag = new StringBuilder();
                        while ((tch = window.get(--idx)) != '<') {
                            tag.appendCodePoint(tch);
                        }

                        flush(window, out, window.size());
                        tagStart = false;
                    } else {
                        int supposedTagStart = endsWithTag(windowToStr(window, 0, window.size() - 1));
                        if (supposedTagStart > 0) {
                            window.add(window.size() - 1 - supposedTagStart, new Integer('<'));
                            flush(window, out, window.size());
                        }
                    }
                    break;
                default:
            }
        } while (!endFlag || window.size() > 0);

        return new ByteArrayInputStream(out.toString().getBytes());
    }

    public static String closeTags(InputStream stream) throws IOException {

        StringBuffer out = new StringBuffer();

        InputStreamReader isr = new InputStreamReader(stream, XmlUtils.UTF_8);
        LinkedList<String> tagStack = new LinkedList<String>();

        int ch;
        int okIdx = 0;
        boolean tagFlag = false;
        StringBuilder tagBuilder = new StringBuilder();

        while ((ch = isr.read()) != -1) {
            switch (ch) {
                case '<':
                    tagFlag = true;
                    break;

                case '>':
                    tagFlag = false;
                    String tag = tagBuilder.toString();


                    if (tag.charAt(tag.length() - 1) == '/') {
                        ; // do nothing - <br/> case
                    } else if (tag.charAt(0) == '/') {
                        // closing tag
                        // check if there is opening for it
                        ListIterator<String> i = tagStack.listIterator(tagStack.size());
                        String prev;
                        boolean fixed = false;
                        while (i.hasPrevious()) {
                            prev = i.previous();
                            if (prev.equals(tag)) {
                                while (!tagStack.getLast().equals(tag)) {
                                    out.append('<').append(tagStack.removeLast()).append('>');
                                }
                                tagStack.removeLast();
                                fixed = true;
                                break;
                            }
                        }

                        if (!fixed) {
                            // not found sibling
                            if ((tag.equals("/p") || tag.equals("/" + TITLE_TAG)) && tagStack.size() > 0) {
                                // error, ignore tag
                                tagBuilder = new StringBuilder();
                                break;
                            } else {
                                out.insert(okIdx, '>').insert(okIdx, tag.substring(1)).insert(okIdx, '<');
                            }
                        }
                    } else {
                        if ("p".equals(tag) || TITLE_TAG.equals(tag)) {
                            ListIterator<String> i = tagStack.listIterator(tagStack.size());
                            String prev;
                            while (i.hasPrevious()) {
                                prev = i.previous();
                                if ("/p".equals(prev) || ("/" + TITLE_TAG).equals(prev)) {
                                    while (!tagStack.isEmpty() && !tagStack.getLast().equals(tag)) {
                                        out.append('<').append(tagStack.removeLast()).append('>');
                                    }
                                    break;
                                }
                            }
                        }
                        int paramStart = tag.indexOf(" ");
                        tagStack.add("/" + (paramStart == -1 ? tag : tag.substring(0, paramStart)));
                    }

                    out.append('<').append(tag).append('>');
                    okIdx = out.length();

                    tagBuilder = new StringBuilder();
                    break;

                default:
                    if (tagFlag) {
                        tagBuilder.appendCodePoint(ch);
                    } else {
                        out.appendCodePoint(ch);
                    }
            }
        }

        while (!tagStack.isEmpty()) {
            out.append('<').append(tagStack.removeLast()).append('>');
        }

        return out.toString();
    }

    private static void flush(LinkedList<Integer> window, StringBuffer out, int i) {
        int tagIdx = i - 1;
        while (tagIdx >= 0 && window.get(tagIdx) != '<') {
            tagIdx--;
        }

        for (int k = 0; k < i; k++) {
            int ch = window.removeFirst();
            if (k < tagIdx) {
                out.append(encode(ch, window));
            } else {
                out.appendCodePoint(ch);
            }
        }
    }

    private static String encode(int ch, List<Integer> w) {
        if (ch == '&') {
            boolean isEntity = false;
            Iterator<Integer> i = w.iterator();
            Integer cur;
            while (i.hasNext()) {
                cur = i.next();
                isEntity = cur == ';';
                if (!(cur >= 'a' && cur <= 'z')) {
                    break;
                }
            }

            if (!isEntity) {
                return "&amp;";
            }
        } else if (ch == '<') {
            return "&lt;";
        } else if (ch == '>') {
            return "&gt;";
        }

        return new Character((char) ch).toString();
    }

    private static int beginsWithTag(String s) {
        for (String tag : ALLOWED_TAGS) {
            if (s.startsWith(tag)) {
                return tag.length();
            }
        }
        return -1;
    }

    private static int endsWithTag(String s) {
        String test = s.trim();
        for (String tag : ALLOWED_TAGS) {
            if (test.endsWith(tag)) {
                int slashIdx = test.length() - tag.length() - 1;
                return (slashIdx >= 0 && test.charAt(slashIdx) == '/')
                        ? tag.length() + 1
                        : tag.length();
            }
        }
        return -1;
    }

    public static InputStream checkAndCorrectLanguage(InputStream in, String lang, String recordName)
        throws IOException {

        String taContent = getContent(in);
        String languageTag = "<abstract language=\"";
        int ind = taContent.indexOf(languageTag);
        if (ind == -1) {
            LOG.warn("language tag not found in " + recordName
                    + "" + taContent.substring(0, PIECE_FOR_LOG));
        }
        if (!taContent.contains(languageTag + lang.toUpperCase() + "\">")) {
            int langSubstrIndex = ind + languageTag.length();
            String abstractLang = taContent.substring(langSubstrIndex, taContent.indexOf("\"", langSubstrIndex + 1));

            taContent = taContent.replace(languageTag + abstractLang + "\">", languageTag + lang.toUpperCase() + "\">");
        }

        return new ByteArrayInputStream(taContent.getBytes(XmlUtils.UTF_8));
    }

    public static InputStream correct(InputStream in) throws IOException {
        return new ByteArrayInputStream(CmsUtils.decodeEntities(closeTags(
                correctBrokenTags(in))).getBytes(XmlUtils.UTF_8));
    }

    public static InputStream correctXmlHead(InputStream in) throws IOException {

        String taContent = getContent(in);

        if (!taContent.contains("<?xml")) {
            return in;
        }

        taContent = taContent.substring(XmlUtils.XML_HEAD.length());

        return new ByteArrayInputStream(taContent.getBytes(XmlUtils.UTF_8));
    }

    public static boolean parseAbstract(InputStream in, TranslatedAbstractVO record,
                                            List<ErrorInfo<TranslatedAbstractVO>> failedQa) {
        return parseAbstract(in, record, null, failedQa);
    }

    public static boolean parseAbstract(InputStream in, TranslatedAbstractVO record, String[] rmIds,
                                        List<ErrorInfo<TranslatedAbstractVO>> failedQa) {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(true);
        factory.setNamespaceAware(true);

        TAHandler ta = new TAHandler(record, rmIds);

        try {
            SAXParser parser = factory.newSAXParser();

            record.setLanguage(null);
            parser.parse(in, ta);

        } catch (StopParsingException spe) {
            return true;
        } catch (Exception e) {
            failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.CONTENT, e.getMessage()));
            LOG.error(e.getMessage());
        }

        return false;
    }

    private static String getContent(InputStream in) throws IOException {

        final int bufferSize = 4096;
        char[] buffer = new char[bufferSize];

        Reader reader = new BufferedReader(new InputStreamReader(in, XmlUtils.UTF_8));
        Writer writer = new StringWriter();

        int n;
        while ((n = reader.read(buffer)) != -1) {
            writer.write(buffer, 0, n);
        }

        return writer.toString();
    }

    private static class StopParsingException extends SAXException {
    }

    private static class TAHandler extends DefaultHandler {

        private final String doiEl;
        private final String langEl;

        private final TranslatedAbstractVO record;
        private final String[] rmIds;

        TAHandler(TranslatedAbstractVO record, String[] rmIds) {

            doiEl = CochraneCMSProperties.getProperty("cms.cochrane.ta.tagDoi", "REVIEW_METADATA");
            langEl = CochraneCMSProperties.getProperty("cms.cochrane.ta.tagLang", "COCHRANE_REVIEW_TRANSLATION");

            this.record = record;
            this.rmIds = rmIds;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {

            if (doiEl.equals(qName)) {

                record.setDoi(attrs.getValue(DOI_TAG));
                if (rmIds != null) {
                    rmIds[0] = attrs.getValue(SID_TAG);
                    rmIds[1] = attrs.getValue(VERSION_TAG);
                }
                throw new StopParsingException();

            } else if (langEl.equals(qName)) {
                String attr = attrs.getValue(LANGUAGE_TAG);
                if (attr != null && attr.length() != 0) {
                    record.setLanguage(attrs.getValue(LANGUAGE_TAG));
                }

                attr = attrs.getValue(SID_TAG2);
                if (attr != null && attr.length() != 0) {
                    record.setSid(attrs.getValue(SID_TAG2));
                }

                attr = attrs.getValue(SID_TAG);
                if (attr != null && attr.length() != 0) {
                    record.setSid(attrs.getValue(SID_TAG));
                }

                attr = attrs.getValue(VERSION_TAG);
                if (attr != null && attr.length() != 0) {
                    record.setCochraneVersion(attrs.getValue(VERSION_TAG));
                }

                attr = attrs.getValue(XML_VERSION_TAG);
                if (attr != null && attr.length() != 0) {
                    record.setXmlVersion(attrs.getValue(XML_VERSION_TAG));
                }
            }
        }
    }
}
