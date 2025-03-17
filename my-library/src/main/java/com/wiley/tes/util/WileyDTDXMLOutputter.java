package com.wiley.tes.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jdom.Attribute;
import org.jdom.DocType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.Text;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.helpers.DefaultHandler;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 05.09.11
 */
public class WileyDTDXMLOutputter extends XMLOutputter {
    private static final Logger LOG = Logger.getLogger(WileyDTDXMLOutputter.class);

    private static final Pattern PATTERN = Pattern.compile("&([\\w\\d]+);");
    private static final String ENTYTYDEF_SEPARATOR = " is ";

    private static final int DECIMAL = 10;
    private static final int HEX = 16;
    private static final int CC = 127;

    private boolean mIsDoNotUseWileyEntities;

    public static Map<String, String> getEntityNameToValueMap() {
        return WileyDTDXMLOutputterData.instance().mEntitiesMapReverse;
    }

    public static String output(Document xmlDoc) throws Exception {
        XMLOutputter xout = new WileyDTDXMLOutputter();
        String xpath = "//component";
        Object docRoot = XPath.selectSingleNode(xmlDoc, xpath);
        if (docRoot == null) {
            throw new Exception(String.format("document doesn't contain %s", xpath));
        }
        Element root = docRoot instanceof Element ? (Element) docRoot : docRoot instanceof Document
                ? ((Document) docRoot).getRootElement() : null;
        if (root != null && root != xmlDoc.getRootElement()) {
            for (Object ns : xmlDoc.getRootElement().getAdditionalNamespaces()) {
                root.addNamespaceDeclaration((Namespace) ns);
            }
        }

        processPseudoEntities(root);

        xout.setFormat(Format.getRawFormat());
        Document doc = new Document().addContent(root.detach());
        doc.setDocType(new DocType(doc.getRootElement().getName(), "/diamond/dtd/wileyml21/wileyml21.dtd"));

        return xout.outputString(doc);
    }

    public static void processPseudoEntities(Element e) {
        List content = e.getContent();
        for (Object obj : content) {
            if (obj instanceof Text) {
                Text t = (Text) obj;
                if (t.getText().trim().length() > 0) {
                    StringBuffer buf = processEntity(t);
                    t.setText(buf.toString());
                }
            } else if (obj instanceof Element) {
                processPseudoEntities((Element) obj);
            }
        }
    }

    private static StringBuffer processEntity(Text t) {
        Matcher matcher = PATTERN.matcher(t.getText());
        StringBuffer buf = new StringBuffer();
        while (matcher.find()) {
            String entName = matcher.toMatchResult().group(1);
            String value = WileyDTDXMLOutputter.getEntityNameToValueMap().get(entName);
            if (value != null) {
                matcher.appendReplacement(buf, value);
            } else {
                LOG.debug("Cannot replace pseudoEntity " + matcher.toMatchResult().group(0));
            }
        }
        matcher.appendTail(buf);
        return buf;
    }

    public static String correctDtdPath(final String data, boolean prefix) {
        String tagDtd = "<!DOCTYPE component SYSTEM \"";

        int ind = data.indexOf(tagDtd);
        if (ind == -1) {
            return data;
        }
        String dtdPath = data.substring(ind + tagDtd.length(), data.indexOf(Extensions.DTD));
        String nameDtd = dtdPath.substring(dtdPath.lastIndexOf("/") + 1);
        String prop = CochraneCMSProperties.getProperty("cms.resources.dtd." + nameDtd);
        if (prop == null) {
            LOG.error("Not found System property for dtd " + nameDtd);
        } else if (!prefix) {
            prop = prop.replace("_" + nameDtd, nameDtd);
        }

        return data.replaceFirst("<!DOCTYPE component SYSTEM \".*.dtd\">", tagDtd + prop + "\">");
    }

    public static void refresh() {
        WileyDTDXMLOutputterData.reload();
    }

    public void setDoNotUseWileyEntities(boolean m) {
        this.mIsDoNotUseWileyEntities = m;
    }

    @Override
    protected void printAttributes(Writer out, List attributes, Element parent, XMLOutputter.NamespaceStack namespaces)
        throws IOException {

        WileyDTDXMLOutputterData instance = WileyDTDXMLOutputterData.instance();

        for (int i = 0; i < attributes.size(); i++) {
            Attribute attribute = (Attribute) attributes.get(i);

            String prefix = attribute.getNamespace().getPrefix();
            String name = attribute.getName();

            if ("xlink:href".equals(prefix + ":" + name)) {
                String value = attribute.getValue();
                StringBuilder valueBuf = new StringBuilder(value.length());
                for (char ch : value.toCharArray()) {
                    if (ch < CC && !instance.mEntitiesKbMap.containsKey("" + ch)) {
                        valueBuf.append(ch);
                    } else {
                        valueBuf.append(URLEncoder.encode(ch + "", XmlUtils.UTF_8));
                    }
                }
                attribute.setValue(valueBuf.toString());
            }
            List<Attribute> attributeList = new ArrayList<Attribute>(1);
            attributeList.add(attribute);
            super.printAttributes(out, attributeList, parent, namespaces);
        }
    }

    @Override
    protected void printElement(Writer out, Element element, int level, XMLOutputter.NamespaceStack namespaces)
        throws IOException {

        String elName = element.getName();
        mIsDoNotUseWileyEntities = "email".equals(elName)
                || "CLnumber".equals(elName)
                || "fileId".equals(elName);
        super.printElement(out, element, level, namespaces);
    }

    @Override
    public String escapeElementEntities(String str) {
        if (mIsDoNotUseWileyEntities) {
            return super.escapeElementEntities(str);
        }

        StringBuffer buffer;
        char ch;
        String entity;

        WileyDTDXMLOutputterData instance = WileyDTDXMLOutputterData.instance();

        buffer = null;
        for (int i = 0; i < str.length(); i++) {
            ch = str.charAt(i);
            entity = getEntity(ch, instance);
            if (buffer == null) {
                if (entity != null) {
                    buffer = new StringBuffer(str.length());
                    buffer.append(str.substring(0, i));
                    buffer.append(entity);
                }
            } else {
                if (entity != null) {
                    buffer.append(entity);
                } else {
                    buffer.append(ch);
                }
            }
        }

        return (buffer == null) ? str : buffer.toString();
    }

    private String getEntity(char ch, WileyDTDXMLOutputterData instance) {
        String entity;
        switch (ch) {
            case '>':
                entity = "&gt;";
                break;
            case '<':
                entity = "&lt;";
                break;
            case '&':
                entity = "&amp;";
                break;
            case '\r':
                entity = "&#xD;";
                break;
            case '\n':
                entity = currentFormat.getLineSeparator();
                break;
            default:
                String entityName = instance.mEntitiesMap.get(ch + "");
                if (entityName != null) {
                    entity = "&" + entityName + ";";
                } else if (ch >= CC) {
                    entity = "&#" + (int) ch + ';';
                } else {
                    entity = null;
                }

                break;
        }
        return entity;
    }

    private static class WileyDTDContentHandler implements DeclHandler {

        private List mExcludeEntNamesList;
        private Map<String, String> mEntitiesMap;
        private Map<String, String> mEntitiesMapReverse;

        public WileyDTDContentHandler(List excludeEntNamesList, Map<String, String> entitiesMap,
            Map<String, String> entitiesMapReverse) {

            mExcludeEntNamesList = excludeEntNamesList;
            mEntitiesMap = entitiesMap;
            mEntitiesMapReverse = entitiesMapReverse;
        }

        public void elementDecl(String name, String model) throws SAXException {
        }

        public void attributeDecl(String eName, String aName, String type, String mode, String value)
            throws SAXException {
        }

        public void internalEntityDecl(String name, String value) throws SAXException {
            if (value.length() == 1 && !name.startsWith("%")) { //character

                if (!mExcludeEntNamesList.contains(name)) {
                    mEntitiesMap.put(value, name);
                    mEntitiesMapReverse.put(name, value);
                }
            }
        }

        public void externalEntityDecl(String name, String publicId, String systemId) throws SAXException {
        }
    }

    private static class WileyDTDXMLOutputterData {

        private static WileyDTDXMLOutputterData instance = new WileyDTDXMLOutputterData(true);

        private HashMap<String, String> mEntitiesMap;
        private HashMap<String, String> mEntitiesMapReverse;
        private HashMap<String, String> mEntitiesKbMap;

        private WileyDTDXMLOutputterData(boolean init) {

            mEntitiesMap = new HashMap<String, String>();
            mEntitiesMapReverse = new HashMap<String, String>();
            mEntitiesKbMap = new HashMap<String, String>();

            if (!init) {
                return;
            }

            try {
                init();
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        }

        static WileyDTDXMLOutputterData instance() {
            return instance;
        }

        static void reload() {

            WileyDTDXMLOutputterData newInstance = new WileyDTDXMLOutputterData(false);
            try {
                newInstance.init();
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                return;
            }

            instance = newInstance;
        }

        private void init() throws Exception {

            InputStream entKbIn = new URI(CochraneCMSProperties.getProperty(
                    "cms.cochrane.dtd.entities.keyboard.list")).toURL().openStream();
            InputStream entExcludeIn = new URI(CochraneCMSProperties.getProperty(
                    "cms.cochrane.dtd.entities.exclude.list")).toURL().openStream();

            List<String> entExcludeList = ListReader.readList(entExcludeIn, "entityName");
            List<String> entKbList = ListReader.readList(entKbIn, "entityDef");

            loadDTDEntities(new File(new URI(CochraneCMSProperties.getProperty("cms.resources.dtd.sample.xml"))),
                entExcludeList);

            for (String entityDef : entKbList) {

                int isPos = entityDef.indexOf(ENTYTYDEF_SEPARATOR);
                if (isPos == -1) {
                    continue;
                }

                String entityChar = entityDef.substring(0, isPos).trim();
                String entityName = entityDef.substring(isPos + ENTYTYDEF_SEPARATOR.length()).trim();

                if (entityChar.length() > 1) {
                    try {
                        int radix = DECIMAL;
                        if (entityChar.startsWith("0x")) {
                            radix = HEX;
                            entityChar = entityChar.substring(2);
                        }
                        entityChar = "" + (char) Integer.parseInt(entityChar, radix);
                    } catch (NumberFormatException e) {
                        LOG.error("incorrect entity def: " + entityDef + ". Cannot parse character code");
                        continue;
                    }

                }
                mEntitiesKbMap.put(entityChar, entityName);
                extendEntityMap(entityChar, entityName);
            }
        }

        private void extendEntityMap(String value, String entityName) {
            if (!mEntitiesMap.containsKey(value)) {
                mEntitiesMap.put(value, entityName);
                mEntitiesMapReverse.put(entityName, value);
            }
        }

        private void loadDTDEntities(File xmlSampleFile, List excludeEntNames) throws Exception {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser parser = spf.newSAXParser();
            parser.setProperty("http://xml.org/sax/properties/declaration-handler",
                new WileyDTDContentHandler(excludeEntNames, mEntitiesMap, mEntitiesMapReverse));

            String fileContent = correctDtdPath(
                InputUtils.readStreamToString(new FileInputStream(xmlSampleFile)), false);

            parser.parse(new ByteArrayInputStream(fileContent.getBytes(XmlUtils.UTF_8)), new DefaultHandler());
        }
    }
}
