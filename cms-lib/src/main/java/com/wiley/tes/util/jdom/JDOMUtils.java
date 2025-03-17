// $Id: JDOMUtils.java,v 1.3 2011-11-25 14:05:50 sgulin Exp $
// Created: Dec 26, 2002 T 12:03:51 PM
// Copyright (C) 2002-2003 by John Wiley & Sons Inc. All Rights Reserved.
package com.wiley.tes.util.jdom;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jdom.CDATA;
import org.jdom.DefaultJDOMFactory;
import org.jdom.DocType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.JDOMFactory;
import org.jdom.Namespace;
import org.jdom.Text;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

import com.wiley.tes.util.Pool;

/**
 * Contains various JDOM utilities.
 *
 * @author <a href='mailto:demidov@wiley.ru'>Andrey Demidov</a>
 * @author <a href='mailto:azhukov@wiley.ru'>Alexey Zhukov</a>
 * @version $Revision: 1.3 $
 */
public class JDOMUtils {
    /**
     * Global JDOM factory.
     */
    private static JDOMFactory jdomFactory = null;

    /**
     * Global pool of default document loaders.
     */
    private static final Pool<DocumentLoader> loaders =
            Pool.synchronize(new Pool<DocumentLoader>(DocumentLoader.class));

    /**
     * This class can't be instantiated!
     */
    private JDOMUtils() {
    }

    /**
     * Returns global JDOM factory.
     *
     * @return global JDOM factory.
     */
    public static JDOMFactory getJDOMFactory() {
        if (jdomFactory == null) {
            synchronized (JDOMUtils.class) {
                if (jdomFactory == null)
                    jdomFactory = new DefaultJDOMFactory();
            }
        }
        return jdomFactory;
    }

    /**
     * (Re)sets global JDOM factory.
     *
     * @param factory new factory (can be <code>null</code> to reset the factory
     *                to JDOM's default one).
     */
    public static void setJDOMFactory(JDOMFactory factory) {
        synchronized (JDOMUtils.class) {
            jdomFactory = factory;
        }
    }

    /**
     * Compares two document types. The document types are equal if they are
     * referencing the same object or if they have equal public and system
     * IDs.
     *
     * @param docType1
     * @param docType2
     * @return true if the document types are equal.
     */
    public static boolean compare(DocType docType1, DocType docType2) {
        if (docType1 == docType2)
            return true;

        if (docType1 == null || docType2 == null)
            return false;

        return docType1.getPublicID().equals(docType2.getPublicID()) &&
                docType1.getSystemID().equals(docType2.getSystemID());
    }

    /**
     * Cuts white spaces from the elements content. This method is recursive.
     *
     * @param elm the element, which will be modified after this call.
     */
    @SuppressWarnings({"unchecked"})
    public static void cutWhiteSpaces(Element elm) {
        final JDOMFactory factory = getJDOMFactory();

        List newContent = new ArrayList();
        List content = elm.getContent();
        for (final Iterator i = content.iterator(); i.hasNext(); ) {
            final Object obj = i.next();
            // Process Element and Text nodes only.
            if (obj instanceof Element) {
                cutWhiteSpaces((Element) obj);
                newContent.add(obj);
                i.remove();
            } else if (obj instanceof Text) {
                final String str = ((Text) obj).getTextNormalize();
                if (str.length() > 0) {
                    if (obj instanceof CDATA)
                        newContent.add(factory.cdata(str));
                    else
                        newContent.add(factory.text(str));
                }
            }
        }
        elm.setContent(newContent);
    }

    /**
     * Prints the document into a string.
     *
     * @param doc the document.
     * @return the string representation of the document.
     * @throws IOException on I/O error.
     */
    public static String printToString(Document doc) throws IOException {
        StringWriter out = new StringWriter();
        new XMLOutputter(Format.getCompactFormat()).output(doc, out);
        return out.toString();
    }

    /**
     * Prints the document into a string.
     *
     * @param doc      the document.
     * @param encoding the encoding format.
     * @return the string representation of the document.
     * @throws IOException on I/O error.
     */
    public static String printToString(Document doc, String encoding) throws IOException {
        StringWriter out = new StringWriter();
        XMLOutputter outputter = new XMLOutputter();
        Format fmt = Format.getCompactFormat();
        fmt.setEncoding(encoding);
        outputter.setFormat(fmt);
        outputter.output(doc, out);
        return out.toString();
    }

    /**
     * Prints the document into a string.
     *
     * @param doc the document.
     * @return the string representation of the document.
     * @throws java.io.IOException on I/O error.
     */
    public static String printToString(Element doc) throws IOException {
        StringWriter out = new StringWriter();
        new XMLOutputter(Format.getCompactFormat()).output(doc, out);
        return out.toString();
    }

    /**
     * Prints the document into a string.
     *
     * @param doc the document.
     * @return the string representation of the document.
     * @throws java.io.IOException on I/O error.
     */
    public static String printToStringWithNewLines(Element doc) throws IOException {
        StringWriter out = new StringWriter();
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        outputter.output(doc, out);
        return out.toString();
    }

    /**
     * Prints the document into a string.
     *
     * @param doc      the document.
     * @param encoding the encoding format
     * @return the string representation of the document.
     * @throws java.io.IOException on I/O error.
     */
    public static String printToString(Element doc, String encoding) throws IOException {
        StringWriter out = new StringWriter();
        XMLOutputter xo = new XMLOutputter();
        Format fmt = Format.getPrettyFormat();
        fmt.setEncoding(encoding);
        xo.setFormat(fmt);
        xo.output(doc, out);

        return out.toString();
    }

    /**
     * Prints the document into a string preserving original format.
     *
     * @param doc the document.
     * @return the string representation of the document.
     * @throws java.io.IOException on I/O error.
     */
    public static String printToStringPreserveFormat(Element doc) throws IOException {
        StringWriter out = new StringWriter();
        XMLOutputter outputter = new XMLOutputter(Format.getRawFormat());
        outputter.output(doc, out);
        return out.toString();
    }

    /**
     * Replaces XML entities by symbols.
     * @param text the string
     * @return modified text.
     */
    /*public static String resolveEntity(String text)
    {
        for (final String entity : INV_ENTITY_TBL.keySet())
            text = StringUtils.sReplace(entity, INV_ENTITY_TBL.get(entity), text);
        return text;
    } */

    /**
     * Inverse entity map (entity / symbol).
     */
    private static final Map<String, String> INV_ENTITY_TBL = new HashMap<String, String>();

    static {
        INV_ENTITY_TBL.put("&amp;", "&");
        INV_ENTITY_TBL.put("&lt;", "<");
        INV_ENTITY_TBL.put("&gt;", ">");
    }

    /**
     * Returns true if one of parent elements is of math namespace.
     *
     * @param element
     * @return true if one of parent elements is of math namespace.
     */
    public static Element getAncestor(Element element, String parentName) {
        element = element.getParentElement();
        if (element != null) {
            do {
                String name = element.getName();
                if (name.equals(parentName)) {
                    return element;
                }
                element = element.getParentElement();
            }
            while (element != null);
        }
        return null;
    }

    /**
     * Returns only Elements from XPath result.
     *
     * @param context the node to use as context for evaluating the XPath expression.
     * @param path    the XPath expression to evaluate.
     * @return list of Elements.
     * @throws JDOMException
     */
    public static List<Element> selectElements(Object context, String path) throws JDOMException {
        List result = XPath.selectNodes(context, path);
        if (result != null) {
            ArrayList<Element> ret = new ArrayList<Element>(result.size());
            for (Object o : result)
                if (o instanceof Element)
                    ret.add((Element) o);
            return ret;
        }
        return null;
    }

    public static List<Element> getChildren(Element parent) {
        List result = parent.getChildren();
        ArrayList<Element> ret = new ArrayList<Element>(result.size());
        for (Object o : result)
            if (o instanceof Element)
                ret.add((Element) o);
        return ret;
    }

    public static List<Element> getChildren(Element parent, String name) {
        List result = parent.getChildren(name);
        ArrayList<Element> ret = new ArrayList<Element>(result.size());
        for (Object o : result)
            if (o instanceof Element)
                ret.add((Element) o);
        return ret;
    }

    public static List<Element> getChildren(Element parent, String name, Namespace ns) {
        List result = parent.getChildren(name, ns);
        ArrayList<Element> ret = new ArrayList<Element>(result.size());
        for (Object o : result)
            if (o instanceof Element)
                ret.add((Element) o);
        return ret;
    }

    /**
     * For each element within the given list moves its content into the result
     * list.
     *
     * @param content the content list
     * @return list containing content of each top-level elements within the
     *         given list.
     */
    public static List getContentIgnoreRoot(List content) {
        List resList = new ArrayList();
        for (final Object obj : content) {
            if (obj instanceof Element) {
                Element root = (Element) obj;
                moveContent(root, resList);
            }
        }
        return resList;
    }

    /**
     * Moves content of the given element to the given list.
     *
     * @param elem the element.
     * @param list the list.
     */
    @SuppressWarnings({"unchecked"})
    public static void moveContent(Element elem, List list) {
        list.addAll(elem.getContent());
        elem.getContent().clear();
    }


    /**
     * Loads XML from a file.
     *
     * @param f the file.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Document load(final File f) throws IOException, JDOMException {
        final DocumentLoader loader = loaders.get();
        try {
            return loader.load(f);
        } finally {
            loaders.release(loader);
        }
    }

    /**
     * Loads XML from a file.
     *
     * @param f the file.
     * @return root element of loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Element loadElement(final File f) throws IOException,
            JDOMException {
        return load(f).getRootElement();
    }

    /**
     * Loads XML from a file.
     *
     * @param f        the file.
     * @param systemId the system id.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Document load(final File f, final String systemId)
            throws IOException, JDOMException {
        final DocumentLoader loader = loaders.get();
        try {
            return loader.load(f, systemId);
        } finally {
            loaders.release(loader);
        }
    }

    /**
     * Loads XML from a file.
     *
     * @param f        the file.
     * @param systemId the system id.
     * @return root element of loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Element loadElement(final File f, final String systemId)
            throws IOException, JDOMException {
        return load(f, systemId).getRootElement();
    }

    /**
     * Loads XML from a string.
     *
     * @param xml the string containing XML.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Document load(final String xml) throws IOException,
            JDOMException {
        final DocumentLoader loader = loaders.get();
        try {
            return loader.load(xml);
        } finally {
            loaders.release(loader);
        }
    }

    /**
     * Loads XML from a string.
     *
     * @param xml the string containing XML.
     * @return root element of loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Element loadElement(final String xml) throws IOException,
            JDOMException {
        return load(xml).getRootElement();
    }

    /**
     * Loads XML from a string.
     *
     * @param xml      the string containing XML.
     * @param systemId the system id.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Document load(final String xml, final String systemId)
            throws IOException, JDOMException {
        final DocumentLoader loader = loaders.get();
        try {
            return loader.load(xml, systemId);
        } finally {
            loaders.release(loader);
        }
    }

    /**
     * Loads XML from a string.
     *
     * @param xml      the string containing XML.
     * @param systemId the system id.
     * @return root element of loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Element loadElement(final String xml, final String systemId)
            throws IOException, JDOMException {
        return load(xml, systemId).getRootElement();
    }

    /**
     * Loads XML from an array of bytes.
     *
     * @param xml byte array containing XML.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Document load(final byte[] xml)
            throws IOException, JDOMException {
        final DocumentLoader loader = loaders.get();
        try {
            return loader.load(xml);
        } finally {
            loaders.release(loader);
        }
    }

    /**
     * Loads XML from an array of bytes.
     *
     * @param xml byte array containing XML.
     * @return root element of loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Element loadElement(final byte[] xml)
            throws IOException, JDOMException {
        return load(xml).getRootElement();
    }

    /**
     * Loads XML from an array of bytes.
     *
     * @param xml      byte array containing XML.
     * @param systemId the system id.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Document load(final byte[] xml, final String systemId)
            throws IOException, JDOMException {
        final DocumentLoader loader = loaders.get();
        try {
            return loader.load(xml, systemId);
        } finally {
            loaders.release(loader);
        }
    }

    /**
     * Loads XML from an array of bytes.
     *
     * @param xml      byte array containing XML.
     * @param systemId the system id.
     * @return root element of loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Element loadElement(final byte[] xml, final String systemId)
            throws IOException, JDOMException {
        return load(xml, systemId).getRootElement();
    }

    /**
     * Loads XML from an input stream.
     *
     * @param in the input stream.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Document load(final InputStream in)
            throws IOException, JDOMException {
        final DocumentLoader loader = loaders.get();
        try {
            return loader.load(in);
        } finally {
            loaders.release(loader);
        }
    }

    public static Document loadWithEntities(final InputStream in)
            throws IOException, JDOMException {
        final DocumentLoader loader = loaders.get();
        try {
            return loader.load(in);
        } finally {
            loaders.release(loader);
        }
    }

    /**
     * Loads XML from an input stream.
     *
     * @param in the input stream.
     * @return root element of loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Element loadElement(final InputStream in)
            throws IOException, JDOMException {
        return load(in).getRootElement();
    }

    /**
     * Loads XML from an input stream.
     *
     * @param in       the input stream.
     * @param systemId the system id.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Document load(final InputStream in, final String systemId)
            throws IOException, JDOMException {
        final DocumentLoader loader = loaders.get();
        try {
            return loader.load(in, systemId);
        } finally {
            loaders.release(loader);
        }
    }

    /**
     * Loads XML from an input stream.
     *
     * @param in       the input stream.
     * @param systemId the system id.
     * @return root element of loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Element loadElement(final InputStream in,
                                      final String systemId)
            throws IOException, JDOMException {
        return load(in, systemId).getRootElement();
    }

    /**
     * Loads XML from a reader.
     *
     * @param in the reader.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Document load(final Reader in)
            throws IOException, JDOMException {
        final DocumentLoader loader = loaders.get();
        try {
            return loader.load(in);
        } finally {
            loaders.release(loader);
        }
    }

    /**
     * Loads XML from a reader.
     *
     * @param in the reader.
     * @return root element of loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Element loadElement(final Reader in)
            throws IOException, JDOMException {
        return load(in).getRootElement();
    }

    /**
     * Loads XML from a reader.
     *
     * @param in       the reader.
     * @param systemId the system id.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Document load(final Reader in, final String systemId)
            throws IOException, JDOMException {
        final DocumentLoader loader = loaders.get();
        try {
            return loader.load(in, systemId);
        } finally {
            loaders.release(loader);
        }
    }

    /**
     * Loads XML from a reader.
     *
     * @param in       the reader.
     * @param systemId the system id.
     * @return root element of loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     */
    public static Element loadElement(final Reader in, final String systemId)
            throws IOException, JDOMException {
        return load(in, systemId).getRootElement();
    }
}
