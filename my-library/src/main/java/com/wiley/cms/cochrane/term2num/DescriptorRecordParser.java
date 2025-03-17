package com.wiley.cms.cochrane.term2num;

import com.wiley.tes.util.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// JAXP
// SAX
// DOM

/**
 * // DescriptorRecordParser.java
 * // $Id: DescriptorRecordParser.java,v 1.6 2011-11-28 05:49:58 sgulin Exp $
 *
 * @author <a href='mailto:stirnov@wiley.ru'>Tyrnov Sergey</a>
 *         Date: 16-Jan-2009
 */

public class DescriptorRecordParser {
    private static final Logger LOG = Logger.getLogger(DescriptorRecordParser.class);
    private static final String DESCRIPTOR_NAME = "DescriptorName";
    private static final String DESCRIPTOR_UI = "DescriptorUI";
    private static final String DESCRIPTOR_CLASS = "DescriptorClass";
    private static final String OFFENDING_RECORD = "OFFENDING RECORD";
    private static final String[] DESC_TYPES = {"treeElement", "publicationType", "checkTag", "geographicLocation"};

    private final Map<String, ArrayList<String>> mtreeNumsMap;
    private final List<String> descs = new ArrayList<String>();
    private DocumentBuilder docBuilder;

    public DescriptorRecordParser(Map<String, ArrayList<String>> mtreeNumsMap) throws Exception {
        this.mtreeNumsMap = mtreeNumsMap;
        try {
            // get DOM DOC builder factory
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            docBuilderFactory.setValidating(false);
            docBuilderFactory.setNamespaceAware(true);

            // get DOM DOC builder
            docBuilder = docBuilderFactory.newDocumentBuilder();
        } catch (Exception e) {
            throw new Exception("DescriptorRecordParser initialization failed", e);
        }
    }

    public void parseRecord(String descriptorRecord) {
        try {
            int i;  // for looping
            Document doc = docBuilder.parse(new InputSource(new StringReader(descriptorRecord)));
            Element rootElem = doc.getDocumentElement();

            // descriptor type
            String descType = rootElem.getAttribute(DESCRIPTOR_CLASS);
            int descTypeInt = Integer.parseInt(descType);
            descType = DESC_TYPES[descTypeInt - 1];

            // descriptor ui
            Node descUINode = rootElem.getElementsByTagName(DESCRIPTOR_UI).item(0);
            String descUI = descUINode.getFirstChild().getNodeValue();

            // descriptor name
            Node descNameNode = rootElem.getElementsByTagName(DESCRIPTOR_NAME).item(0);
            Node descNameStrNode = descNameNode.getFirstChild().getFirstChild();  // fc: <String/>, fc: text element
            String descName = descNameStrNode.getNodeValue();

            // allowable qualifiers
            // todo do nothing
            allowableQualifiers(rootElem);

            // tree numbers
            StringBuffer treeNumsBuf = makeTreeNumbers(rootElem);

            // supplement tree numbers with paMeSH entries (if extant)
            supTreeNumbers(rootElem, descUI, descName, treeNumsBuf);
        } catch (SAXException e) {
            LOG.error(e.getMessage(), e);
        } catch (ArrayIndexOutOfBoundsException e) {
            LOG.error(OFFENDING_RECORD + "\n" + descriptorRecord, e);
        } catch (IOException e) {
            LOG.error(OFFENDING_RECORD + "\n" + descriptorRecord, e);
        } catch (Exception e) {
            LOG.error(OFFENDING_RECORD + "\n" + descriptorRecord, e);
        }
    }

    private void allowableQualifiers(Element rootElem) {
        int i;
        StringBuffer aqsBuf = new StringBuffer();
        Element aqsListElem = (Element) rootElem.getElementsByTagName("AllowableQualifiersList").item(0);
        if (aqsListElem != null) {
            NodeList aqsAbbrevNodes = aqsListElem.getElementsByTagName("Abbreviation");
            int numAqs = aqsAbbrevNodes.getLength();
            for (i = 0; i < numAqs; i++) {
                Node aqTextNode = aqsAbbrevNodes.item(i).getFirstChild();
                aqsBuf.append(aqTextNode.getNodeValue());
                if (i < (numAqs - 1)) {
                    aqsBuf.append(" ");
                }
            }
        }
    }

    /**
     * The method do nothing. The method body is a copy of the method DescriptorRecordParser.supTreeNumbers().
     * The method from the DescriptorRecordParser class do nothing too because of the condition at
     * the beginning of the method (Term2Num.havePAMeSH) which one is always resolved to true preventing from
     * execute the rest code of the method.
     */
    private void supTreeNumbers(Element rootElem,
                                String descUI,
                                String descName,
                                StringBuffer treeNumsBuf) throws IOException {
        if (true) {
            return;
        }

        int i;
        Element paListElem = (Element) rootElem.getElementsByTagName("PharmacologicalActionList").item(0);
        if (paListElem != null) {
            NodeList paNodes = paListElem.getElementsByTagName("DescriptorReferredTo");
            for (i = 0; i < paNodes.getLength(); i++) {
                Node paNode = paNodes.item(i);
                String paName = ((Element) paNode).getElementsByTagName(DESCRIPTOR_NAME).item(0)
                        .getFirstChild().getFirstChild().getNodeValue();
                ArrayList<String> paNums = mtreeNumsMap.get(paName);
                if (paNums != null) {
                    for (String paNum : paNums) {
                        descs.add(descName + ";" + paNum + "." + descUI + "\n");
                        // the same tree numbers in the generated XML source
                        treeNumsBuf.append(" ").append(paNum + "." + descUI);
                    }
                }
            }
        }
    }

    private StringBuffer makeTreeNumbers(Element rootElem) {
        int i;
        StringBuffer treeNumsBuf = new StringBuffer();
        Element treeNumListElem = (Element) rootElem.getElementsByTagName("TreeNumberList").item(0);
        if (treeNumListElem != null) {
            NodeList treeNumNodes = treeNumListElem.getElementsByTagName("TreeNumber");
            int numTreeNums = treeNumNodes.getLength();
            for (i = 0; i < numTreeNums; i++) {
                Node treeNumTextNode = treeNumNodes.item(i).getFirstChild();
                treeNumsBuf.append(treeNumTextNode.getNodeValue());
                if (i < (numTreeNums - 1)) {
                    treeNumsBuf.append(" ");
                }
            }
        }
        return treeNumsBuf;
    }

    public List<String> getDescriptors() {
        return descs;
    }
}
