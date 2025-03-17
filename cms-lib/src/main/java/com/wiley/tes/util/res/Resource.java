package com.wiley.tes.util.res;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.w3c.dom.Node;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 28.03.13
 *
 * @param <I> Resource identifier
 */
@XmlAccessorType(XmlAccessType.NONE)
public abstract class Resource<I extends Serializable> implements Serializable {

    protected static final Logger LOG = Logger.getLogger(Resource.class);

    protected static final String TAG_ID = "id";

    private static final long serialVersionUID = 1L;

    private I id;

    protected abstract I getIdFromString(String id);

    public I getId() {
        return id;
    }

    void setId(I id) {
        this.id = id;
    }

    final void loadFull(Node xml) throws Exception {

        loadMetaInfo(xml);
        load(xml);
    }

    protected void load(Node xml) {
    }

    /**
     *  To set and initialize some inner resource data after load(). It is strongly recommended to publish
     *  a resource-with-not-empty-resolve() via populate() method after resolving
     */
    protected void resolve() {
    }

    /** To publish the resource (set it visible) in case the load() doesn't do all the actions required. */
    protected void populate() {
    }

    protected boolean check() {
        return true;
    }

    final void loadMetaInfo(Node xml) {

        Node attrId = xml.getAttributes().getNamedItem(TAG_ID);
        if (attrId != null) {
            setId(getIdFromString(attrId.getNodeValue()));
        }
    }

    public String asXmlString(String resName) {
        try {
            return asString(ResourceManager.instance().getFactory(resName));
        } catch (Exception e) {
            LOG.warn(e.getMessage());
            return null;
        }
    }

    public String asString(IResourceFactory factory) {
        try {
            return factory.convertResourceToString(this);
        } catch (Exception e) {
            LOG.warn(e.getMessage());
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {

        boolean ret = o instanceof Resource;
        if (ret && o != this) {
            Resource res = (Resource) o;
            ret = id.equals(res.id);
        }
        return ret;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}


