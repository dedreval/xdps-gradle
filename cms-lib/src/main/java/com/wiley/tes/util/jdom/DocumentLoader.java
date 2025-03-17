// $Id: DocumentLoader.java,v 1.2 2011-11-25 14:05:50 sgulin Exp $
// Copyright (C) 2002-2004 by John Wiley & Sons Inc. All Rights Reserved.
package com.wiley.tes.util.jdom;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import javax.xml.parsers.SAXParserFactory;

import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.JDOMFactory;
import org.jdom.input.SAXBuilder;

import com.wiley.tes.util.Logger;


/**
 * This class simplifies the process of XML loading into JDOM tree.
 * Note, all entities will be expanded and external DTD won't be loaded
 * if Xerces is the XML parser used by defaults.
 * <p>If you don't need special builder configuration, then using
 * <code>load</code> methods of {@link JDOMUtils} is more effective then using
 * temporary <code>DocumentLoader</code> objects. For example, the following
 * statements:
 * <pre>
 * Document doc = new DocumentLoader().load(in);
 * Element elem = new DocumentLoader().load(in).getRootElement();
 * </pre>
 * can be changed to
 * <pre>
 * Document doc = JDOMUtils.load(in);
 * Element elem = JDOMUtils.loadElement(in);
 * </pre>
 * Loading via <code>JDOMUtils</code> is preferred because existing document
 * loaders will be re-used when appropriate.
 *
 * @author <a href='mailto:demidov@wiley.ru'>Andrey Demidov</a>
 * @author <a href='mailto:azhukov@wiley.ru'>Alexey Zhukov</a>
 * @version $Revision: 1.2 $
 */
public class DocumentLoader {
    private static final Logger LOG = Logger.getLogger(DocumentLoader.class);
    /**
     * Feature to switch off external DTD loading.
     */
    private static final String LOAD_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    /**
     * True, if Apache Xerces is the default SAX parser.
     */
    private static final boolean IS_XERCES_DEFAULT;

    static {
        SAXParserFactory fact = SAXParserFactory.newInstance();
        boolean sax = false;
        try {
            sax = fact.newSAXParser().getClass().getName().contains("org.apache.xerces");
        } catch (Exception e) {
            LOG.error(e);
        }
        IS_XERCES_DEFAULT = sax;
    }

    /**
     * The JDOM builder.
     */
    private SAXBuilder builder;

    /**
     * True, if Apache Xerces is the current SAX parser.
     */
    private boolean useLoadDTDFeature = IS_XERCES_DEFAULT;

    /**
     * True, is this is validating loader.
     */
    private boolean validate = false;

    /**
     * JDOM factory.
     */
    private JDOMFactory jdomFactory = null;

    /**
     * Creates the loader object by using default SAX driver. This equals
     * <code>DocumentLoader(false)</code>.
     * <p>Please consider using <code>load</code> methods of {@link JDOMUtils}
     * class instead of temporary default document loaders.</p>
     */
    public DocumentLoader() {
        builder = new SAXBuilder();
        configBuilder();
    }

    /**
     * Creates the loader by using default SAX driver and the given JDOM factory
     *
     * @param factory JDOM factory
     */
    public DocumentLoader(JDOMFactory factory) {
        builder = new SAXBuilder();
        jdomFactory = factory;
        configBuilder();
    }

    /**
     * Creates the loader object by using default SAX driver.
     *
     * @param validate if true then XML will be validated.
     */
    public DocumentLoader(boolean validate) {
        builder = new SAXBuilder(validate);
        if (validate)
            useLoadDTDFeature = false;
        this.validate = validate;
        configBuilder();
    }

    /**
     * Creates the loader by using default SAX driver and the given JDOM factory
     *
     * @param validate if true then XML will be validated.
     * @param factory  JDOM factory
     */
    public DocumentLoader(boolean validate, JDOMFactory factory) {
        builder = new SAXBuilder(validate);
        jdomFactory = factory;
        if (validate)
            useLoadDTDFeature = false;
        this.validate = validate;
        configBuilder();
    }

    /**
     * Creates a non-validating loader with the given SAX driver.
     *
     * @param saxDriver the SAX driver name.
     */
    public DocumentLoader(String saxDriver) {
        builder = new SAXBuilder(saxDriver);
        useLoadDTDFeature = saxDriver.indexOf("org.apache.xerces") >= 0;
        configBuilder();
    }

    /**
     * Creates a non-validating loader with the given SAX driver and JDOM
     * factory.
     *
     * @param saxDriver the SAX driver name.
     * @param factory   JDOM factory
     */
    public DocumentLoader(String saxDriver, JDOMFactory factory) {
        builder = new SAXBuilder(saxDriver);
        jdomFactory = factory;
        useLoadDTDFeature = saxDriver.indexOf("org.apache.xerces") >= 0;
        configBuilder();
    }

    /**
     * Creates a loader with the given SAX driver.
     *
     * @param saxDriver the SAX driver name.
     * @param validate  true for validating loader.
     */
    public DocumentLoader(String saxDriver, boolean validate) {
        builder = new SAXBuilder(saxDriver, validate);
        if (validate)
            useLoadDTDFeature = false;
        else
            useLoadDTDFeature = saxDriver.indexOf("org.apache.xerces") >= 0;
        this.validate = validate;
        configBuilder();
    }

    /**
     * Creates a loader with the given SAX driver and JDOM factory.
     *
     * @param saxDriver the SAX driver name.
     * @param validate  true for validating loader.
     * @param factory   JDOM factory
     */
    public DocumentLoader(String saxDriver, boolean validate,
                          JDOMFactory factory) {
        builder = new SAXBuilder(saxDriver, validate);
        jdomFactory = factory;
        if (validate)
            useLoadDTDFeature = false;
        else
            useLoadDTDFeature = saxDriver.indexOf("org.apache.xerces") >= 0;
        this.validate = validate;
        configBuilder();
    }

    /**
     * Returns the builder object.
     *
     * @return the builder object.
     */
    public SAXBuilder getBuilder() {
        return builder;
    }

    /**
     * Returns current JDOM factory.
     *
     * @return current JDOM factory.
     */
    public JDOMFactory getJDOMFactory() {
        if (jdomFactory == null)
            jdomFactory = JDOMUtils.getJDOMFactory();
        return jdomFactory;
    }

    /**
     * (Re)sets current JDOM factory.
     *
     * @param factory new JDOM factory (can be <code>null</code> to reset the
     *                factory to the global one).
     * @see JDOMUtils#getJDOMFactory()
     */
    public void setJDOMFactory(JDOMFactory factory) {
        jdomFactory = factory;

        // re-configure the builder as well
        builder.setFactory(getJDOMFactory());
    }

    /**
     * Loads XML from a file.
     *
     * @param f the file.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     * @see JDOMUtils#load(java.io.File)
     */
    public Document load(File f) throws IOException, JDOMException {
        return load(new FileInputStream(f));
    }

    /**
     * Loads XML from a file.
     *
     * @param f        the file.
     * @param systemId the system id.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     * @see JDOMUtils#load(java.io.File, String)
     */
    public Document load(File f, String systemId) throws IOException,
            JDOMException {
        return load(new FileInputStream(f), systemId);
    }

    /**
     * Loads XML from a string.
     *
     * @param xml the string containing XML.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     * @see JDOMUtils#load(String)
     */
    public Document load(String xml) throws IOException, JDOMException {
        return load(new StringReader(xml));
    }

    /**
     * Loads XML from a string.
     *
     * @param xml      the string containing XML.
     * @param systemId the system id.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     * @see JDOMUtils#load(String, String)
     */
    public Document load(String xml, String systemId) throws IOException,
            JDOMException {
        return load(new StringReader(xml), systemId);
    }

    /**
     * Loads XML from an array of bytes.
     *
     * @param xml byte array containing XML.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     * @see JDOMUtils#load(byte[])
     */
    public Document load(byte[] xml) throws IOException, JDOMException {
        return load(new ByteArrayInputStream(xml));
    }

    /**
     * Loads XML from an array of bytes.
     *
     * @param xml      byte array containing XML.
     * @param systemId the system id.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     * @see JDOMUtils#load(byte[], String)
     */
    public Document load(byte[] xml, String systemId) throws IOException,
            JDOMException {
        return load(new ByteArrayInputStream(xml), systemId);
    }

    /**
     * Loads XML from an input stream.
     *
     * @param in the input stream.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     * @see JDOMUtils#load(InputStream)
     */
    public Document load(InputStream in) throws IOException, JDOMException {
        InputStream stream = in;
        if (!validate && !useLoadDTDFeature)
            stream = skipDTD(in);
        Document doc = builder.build(stream);
        stream.close();
        return doc;
    }

    /**
     * Loads XML from an input stream.
     *
     * @param in       the input stream.
     * @param systemId the system id.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     * @see JDOMUtils#load(InputStream, String)
     */
    public Document load(InputStream in, String systemId) throws IOException,
            JDOMException {
        InputStream stream = in;
        if (!validate && !useLoadDTDFeature)
            stream = skipDTD(in);
        Document doc = builder.build(stream, systemId);
        stream.close();
        return doc;
    }

    /**
     * Loads XML from a reader.
     *
     * @param in the reader.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     * @see JDOMUtils#load(Reader)
     */
    public Document load(Reader in) throws IOException, JDOMException {

        Reader reader = in;
        if (!validate && !useLoadDTDFeature)
            reader = skipDTD(in);
        Document doc = builder.build(reader);
        reader.close();
        return doc;
    }

    /**
     * Loads XML from a reader.
     *
     * @param in       the reader.
     * @param systemId the system id.
     * @return loaded JDOM document.
     * @throws IOException   on I/O error.
     * @throws JDOMException on JDOM error.
     * @see JDOMUtils#load(Reader, String)
     */
    public Document load(Reader in, String systemId) throws IOException,
            JDOMException {
        Reader reader = in;
        if (!validate && !useLoadDTDFeature)
            reader = skipDTD(in);
        Document doc = builder.build(reader, systemId);
        reader.close();
        return doc;
    }

    /**
     * Configures this builder.
     */
    private void configBuilder() {
        builder.setExpandEntities(true);
        if (useLoadDTDFeature)
            builder.setFeature(LOAD_DTD, false);
        builder.setFactory(getJDOMFactory());
    }

    /**
     * Cuts doctype declaration off the reader.
     *
     * @param in the reader.
     * @return reader for an XML stream without doctype declaration.
     * @throws IOException
     */
    private Reader skipDTD(Reader in) throws IOException {
        StringBuilder sbuf = new StringBuilder();
        char[] buf = new char[2048];
        for (int sz; (sz = in.read(buf)) >= 0; ) sbuf.append(buf, 0, sz);
        in.close();
        String xml = sbuf.toString();
        int posDTD = xml.indexOf("<!DOCTYPE");
        if (posDTD > 0) {
            int posEnd = xml.indexOf('>', posDTD) + 1;
            xml = xml.substring(0, posDTD) + xml.substring(posEnd);
        }
        return new StringReader(xml);
    }

    /**
     * Cuts doctype declaration off the input stream.
     * <b>Warning:</b> this method works with UTF-8 streams only!
     *
     * @param in the input stream.
     * @return input stream for without doctype declaration.
     * @throws IOException
     */
    private InputStream skipDTD(InputStream in) throws IOException {
        StringBuilder sbuf = new StringBuilder();
        byte[] buf = new byte[2048];
        for (int sz; (sz = in.read(buf)) >= 0; ) sbuf.append(new String(buf, 0, sz, "UTF-8"));
        in.close();
        String xml = sbuf.toString();
        int posDTD = xml.indexOf("<!DOCTYPE");
        if (posDTD > 0) {
            int posEnd = xml.indexOf('>', posDTD) + 1;
            xml = xml.substring(0, posDTD) + xml.substring(posEnd);
        }
        return new ByteArrayInputStream(xml.getBytes());
    }
}