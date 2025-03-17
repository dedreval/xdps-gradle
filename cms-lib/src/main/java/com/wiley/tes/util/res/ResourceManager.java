package com.wiley.tes.util.res;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileFilter;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;


/**
 * @author Olga Soletskaya
 * date: 03.06.12
 */
public final class ResourceManager {
    private static final Logger LOG = Logger.getLogger(ResourceManager.class);

    private static final FileFilter RES_FILTER = new FileFilter() {
        public boolean accept(File fl) {
            return ((!fl.getName().startsWith("_") && fl.getName().endsWith(Extensions.XML))
                || (fl.isDirectory() && !fl.isHidden()));
        }
    };

    private static final ResourceManager INSTANCE = new ResourceManager();

    private final Map<String, IResourceFactory> factories = new HashMap<>();

    private volatile long lastModify;

    private ResourceManager() {

        IResourceInitializer init = createInitializer();
        if (init != null) {
            initialize(init);
        }
    }

    public static ResourceManager instance() {
        return INSTANCE;
    }

    public void register(String key, IResourceFactory factory) {
        factories.put(key, factory);
    }

    public synchronized void loadXmlResources(boolean onlyLast, URL[] folders) {

        DocumentBuilderFactory factory = getDocumentBuilderFactory();
        for (URL folder: folders) {
            String protocol = folder.getProtocol();
            if ("jar".equals(protocol)) {
                loadJarXmlResources(folder, onlyLast, factory);
            } else {
                loadXmlResources(folder.getPath(), onlyLast, factory);
            }
        }
        lastModify = new Date().getTime();
        countContainers();
    }

    private static DocumentBuilderFactory getDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setIgnoringComments(true);
        return factory;
    }

    private void loadXmlResources(String folder, boolean onlyLast, DocumentBuilderFactory factory) {
        File confDir = new File(folder);
        if (!confDir.exists()) {
            LOG.warn(String.format("folder %s  doesn't exist.", confDir.getAbsolutePath()));
            return;
        }
        loadXmlResources(confDir, onlyLast, factory);
    }

    private void loadJarXmlResources(URL url, boolean onlyLast, DocumentBuilderFactory factory) {
        LOG.info(String.format("xml resources from jar %s are being loaded ... onlyLast=%b", url.getPath(), onlyLast));
        Loader loader = new Loader(this);

        String[] path = url.getPath().split("!/");
        if (path.length < 2) {
            return;
        }
        try (ZipInputStream zis = new ZipInputStream(new URL(path[0]).openStream())) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (!ze.isDirectory() && ze.getName().startsWith(path[1] + "/")) {
                    InputStream is = getClass().getResourceAsStream("/" + ze.getName());
                    loader.loadResource(is, ze.getName(), factory);
                }
            }
            loader.checkResources();

        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private void loadXmlResources(File confDir, boolean onlyLast, DocumentBuilderFactory factory) {

        LOG.info(String.format("xml resources from %s are being loaded ... onlyLast=%b",
                confDir.getPath(), onlyLast));
        Loader loader = new Loader(this);
        File override = loader.loadDirectory(confDir, factory, onlyLast, true);
        if (override != null) {
            loader.loadDirectory(override, factory, onlyLast, false);
        }
        loader.checkResources();
    }

    public IResourceFactory getFactory(String key) throws Exception {

        IResourceFactory rf = factories.get(key);
        if (rf == null) {
            throw new Exception(String.format("resource factory %s doesn't exist", key));
        }
        return rf;
    }

    public synchronized void initialize(IResourceInitializer init) {

        LOG.info("resources are being initialized by " + init.getClass().getName());
        init.initialize(this);

        LOG.info(String.format("resources are initialized by %s and being loaded ... ", init.getClass().getName()));
        loadXmlResources(false, init.getResourceRoot());
    }

    private void countContainers() {

        int contCount = 0;
        int resCount = 0;

        for (Map.Entry<String, IResourceFactory> entry: factories.entrySet()) {

            IResourceContainer cont = entry.getValue().getResourceContainer();

            if (cont != null) {

                cont.validate();
                contCount++;
                resCount += cont.size();
            }
        }

        LOG.info(String.format("checked containers (%d), resources (%d) ", contCount, resCount));
    }

    private static IResourceInitializer createInitializer() {

        String initializerClassName = System.getProperty(IResourceInitializer.INITIALIZER_CLASS);
        if (initializerClassName != null) {
            LOG.info(String.format("resource initializer class is %s", initializerClassName));
            try {
                Class cl = Class.forName(initializerClassName);
                Object obj = cl.newInstance();
                if (obj instanceof IResourceInitializer) {
                    return (IResourceInitializer) obj;
                }
            } catch (Exception e) {
                LOG.warn(e.getMessage());
            }
        }
        return null;
    }

    private static final class Loader {

        private final ResourceManager resourceManager;

        private final List<Resource> resList = new ArrayList<>();

        private Loader(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        private static File[] listFiles(File dir) {
            File[] ret = dir.listFiles(RES_FILTER);
            if (ret == null) {
                return FileUtils.EMPTY_DIR;
            }
            Arrays.sort(ret, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
            return ret;
        }

        private File loadDirectory(File dir, DocumentBuilderFactory factory, boolean onlyLast, boolean override) {

            if (!dir.isDirectory()) {
                return null;
            }

            File ret = null;
            if (override && dir.getName().equals("override")) {
                ret = dir;
            } else {
                for (File file: listFiles(dir)) {
                    loadFile(file, factory, onlyLast);
                }
            }
            return ret;
        }

        private void loadFile(File file, DocumentBuilderFactory factory, boolean onlyLast) {

            if (file.isDirectory()) {
                loadDirectory(file, factory, onlyLast, false);

            } else if (!onlyLast || file.lastModified() > resourceManager.lastModify) {
                try {
                    loadResource(new FileInputStream(file), file.getName(), factory);
                } catch (Exception e) {
                    LOG.error("error parsing: " + file.getName(), e);
                }
            }
        }

        private void loadResource(InputStream is, String resName, DocumentBuilderFactory factory) throws Exception {
            Document doc = factory.newDocumentBuilder().parse(is);
            loadResource(doc.getFirstChild(), resName);
        }

        private void loadResource(Node parent, String resName) {

            for (Node n = parent; n != null; n = n.getNextSibling()) {
                String name = n.getNodeName();
                if ("dataTable".equalsIgnoreCase(name)) {
                    for (Node rn = n.getFirstChild(); rn != null; rn = rn.getNextSibling()) {
                        loadResource(rn);
                    }
                } else  {
                    loadResource(n);
                }
            }
            LOG.info("resource file loaded: " + resName);
        }

        private void loadResource(Node n) {

            if (n.getNodeType() == Node.ELEMENT_NODE) {
                try {
                    Resource res = resourceManager.getFactory(n.getNodeName()).createResource(n);
                    resList.add(res);

                } catch (Exception e) {
                    LOG.warn(e.getMessage());
                }
            }
        }

        private void checkResources() {

            for (Resource r : resList) {
                r.resolve();
                r.populate();
            }

            for (Resource r : resList) {
                r.check();
            }

            LOG.info("resources checked (" + resList.size() + ")");
        }
    }
}

