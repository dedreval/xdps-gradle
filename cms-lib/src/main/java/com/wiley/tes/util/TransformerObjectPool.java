package com.wiley.tes.util;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

public class TransformerObjectPool {
    public static final String DEFAULT_FACTORY_VERSION = "net.sf.saxon.TransformerFactoryImpl";

    private static final Logger LOG = Logger.getLogger(TransformerObjectPool.class);

    private HashMap<String, TemplatesCache> factoryMap = new HashMap<String, TemplatesCache>();

    public static TransformerObjectPool getInstance() {
        return Factory.instance();
    }

    public synchronized Transformer getIdentityTransformer(String saxonVersion)
            throws TransformerConfigurationException {
        return getTransformerFactory(saxonVersion).factory.newTransformer();
    }

    public Transformer getTransformer(String key) throws TransformerException, IOException, URISyntaxException {
        return getTransformer(key, DEFAULT_FACTORY_VERSION);
    }

    public synchronized Transformer getTransformer(String key, String saxonVersion)
        throws TransformerException, IOException, URISyntaxException {

        TemplatesCache cache = getTransformerFactory(saxonVersion);
        Templates templ = cache.templates.get(key);
        if (templ == null) {
            templ = loadXslt(key, cache);
        }
        return templ.newTransformer();
    }

    public synchronized Transformer getTransformer(String xmlFile, URL url)
        throws TransformerException, IOException, URISyntaxException {

        TemplatesCache cache = getTransformerFactory();
        Templates templ = cache.templates.get(url.toString());

        if (templ == null) {
            templ = loadXslt(xmlFile, url, cache);
        }
        Transformer trans = templ.newTransformer();
        trans.setURIResolver(URIResolverCreator.createURIResolver(url));
        return trans;
    }

    public synchronized Transformer getTransformer(URL url)
        throws TransformerException, IOException, URISyntaxException {
        return getTransformer(url, true);
    }

    public synchronized Transformer getTransformer(URL url, boolean resolver)
        throws TransformerException, IOException, URISyntaxException {

        TemplatesCache cache = getTransformerFactory();
        Templates templ = cache.templates.get(url.toString());

        if (templ == null) {
            templ = loadXslt(url, cache);
        }
        Transformer trans = templ.newTransformer();
        if (resolver) {
            trans.setURIResolver(URIResolverCreator.createURIResolver(url));
        }
        return trans;
    }

    public synchronized void clearMaps() {

        factoryMap.clear();
        LOG.info("Transformers cache has been cleared");
    }

    private Templates loadXslt(String xslPath, TemplatesCache cache) throws  TransformerConfigurationException  {
        Templates templ = cache.factory.newTemplates(new StreamSource(xslPath));
        cache.templates.put(xslPath, templ);
        return templ;
    }

    private Templates loadXslt(URL url, TemplatesCache cache) throws TransformerConfigurationException {
        return loadXslt(url.toString(), cache);
    }

    private Templates loadXslt(String xslFile, URL xslUrl, TemplatesCache cache)
        throws TransformerException, IOException, URISyntaxException {

        Templates templ = cache.factory.newTemplates(new StreamSource(new StringReader(xslFile)));
        cache.templates.put(xslUrl.toString(), templ);
        return templ;
    }

    private TemplatesCache getTransformerFactory() throws TransformerConfigurationException {
        return getTransformerFactory(DEFAULT_FACTORY_VERSION);
    }

    private TemplatesCache getTransformerFactory(String version) throws TransformerConfigurationException {

        TemplatesCache cache = factoryMap.get(version);
        if (cache == null) {
            try {
                cache = new TemplatesCache((TransformerFactory) Class.forName(version).newInstance());
                factoryMap.put(version, cache);
            } catch (Exception e) {
                LOG.error(e, e);
                throw new TransformerConfigurationException(e.getMessage());
            }
        }
        return cache;
    }

    /** Just a factory */
    public static class Factory  {
        private static final TransformerObjectPool INSTANCE = new TransformerObjectPool();

        public static TransformerObjectPool instance() {
            return INSTANCE;
        }
    }

    private static class TemplatesCache {

        private final TransformerFactory factory;
        private final Map<String, Templates> templates =  new HashMap<String, Templates>();

        private TemplatesCache(TransformerFactory factory) {
            this.factory = factory;
        }
    }

    private static class URIResolverCreator {

        public static synchronized URIResolver createURIResolver(final URL url) {
            return new URIResolver() {
                public Source resolve(String href, String base) throws TransformerException {
                    try {
                        String path = url.getPath();
                        path = path.substring(0, path.lastIndexOf('/') + 1);
                        return new StreamSource(new URL(url.getProtocol(), url.getHost(),
                            url.getPort(), path + href).openStream());
                    } catch (IOException e) {
                        throw new TransformerException(e.getMessage());
                    }
                }
            };
        }
    }
}
