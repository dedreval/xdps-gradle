package com.wiley.tes.util.res;

import java.io.StringReader;

import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.w3c.dom.Node;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.03.13
 *
 * @param <R> Resource
 */
public class JaxbResourceFactory<R extends Resource> implements IResourceFactory {

    private final Class<R> mainClass;

    private Class[] resClasses;
    private Unmarshaller um = null;
    private Marshaller m = null;
    private IResourceContainer<? super R> publisher;

    public JaxbResourceFactory(Class<R> resClass,  Class... additionalClasses) {
        this(resClass, null, additionalClasses);
    }

    public JaxbResourceFactory(Class<R> resClass, IResourceContainer<? super R> pub, Class... additionalClasses) {

        mainClass = resClass;
        resClasses = additionalClasses;
        publisher = pub;
    }

    public static <R extends Resource> JaxbResourceFactory<R> create(Class<R> resClass, Class... additionalClasses) {
        return new JaxbResourceFactory<>(resClass, null, additionalClasses);
    }

    /**
     * To create and load a new resource. Also it can be published if the publisher is not NULL.
     * @param resClass           The main resource class
     * @param publisher          The resource publisher. Warning: if it is not NULL, the resource will be published
     *                           on load() at once that not suitable if the resource need to be resolved after loading
     * @param additionalClasses  The additional resource classes
     * @param <R>                The new created resource
     * @return  a new resource object
     */
    public static <R extends Resource> JaxbResourceFactory<R> create(Class<R> resClass,
        IResourceContainer<? super R> publisher, Class... additionalClasses) {

        return new JaxbResourceFactory<>(resClass, publisher, additionalClasses);
    }

    public JaxbResourceFactory<R> enableFormatter() {
        synchronized (this) {
            JAXBHelper.setFormattedOutput(getMarshaller(), Boolean.TRUE);
        }
        return this;
    }

    public IResourceContainer getResourceContainer() {
        return publisher;
    }

    public R createResource(Node node) throws Exception {

        R res;
        synchronized (this) {
            res = JAXBHelper.load(node, mainClass, getUnmarshaller());
        }
        return publishResource(res);
    }

    public R createResource(String str) throws Exception {

        StringReader reader = new StringReader(str);
        R res;
        synchronized (this) {
            res = JAXBHelper.load(reader, mainClass, getUnmarshaller());
        }
        return publishResource(res);
    }

    private R publishResource(R res) throws Exception {
        if (res == null) {
            throw new Exception("JaxbResFactory: cannot create instance: " + mainClass.getName());
        }

        if (publisher != null) {
            publisher.publish(res);
        }

        return res;
    }

    public void saveResource(Resource res, String fileName) {
        synchronized (this) {
            JAXBHelper.save(fileName, res, getMarshaller());
        }
    }

    public String convertResourceToString(Resource res) {
        synchronized (this) {
            return JAXBHelper.convertToString(res, getMarshaller());
        }
    }

    private Marshaller getMarshaller() {
        if (m == null) {
            m = JAXBHelper.getMarshaller(mainClass, resClasses);
            //m.setProperty(Marshaller.JAXB_ENCODING, "cp1251");
        }
        return m;
    }

    private Unmarshaller getUnmarshaller() {
        if (um == null) {
            um = JAXBHelper.getUnmarshaller(mainClass, resClasses);
        }
        return um;
    }
}

