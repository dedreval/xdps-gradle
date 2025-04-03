package com.wiley.cms.cochrane.cmanager.res;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.tes.util.res.DataTable;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceIntId;
import com.wiley.tes.util.res.ResourceManager;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.03.13
 */
@XmlRootElement(name = ContentAccessType.RES_NAME)
public class ContentAccessType extends ResourceIntId implements Serializable {
    public static final int NOT_SPECIFIED = -1;
    public static final int DEFAULT = 0;
    static final String RES_NAME = "accesstype";

    private static final long serialVersionUID = 1L;

    private static final DataTable<Integer, ContentAccessType> DT = new DataTable<>(ContentAccessType.RES_NAME);

    private static final Map<String, Res<ContentAccessType>> METADATA_MAP = new HashMap<>();

    @XmlAttribute(name = "type")
    private String type = "";

    @XmlAttribute(name = "metadata")
    private String metadata = "";

    @XmlAttribute(name = "gold")
    private boolean goldOpenAccess;

    public static void register(ResourceManager loader) {
        loader.register(ContentAccessType.RES_NAME, JaxbResourceFactory.create(ContentAccessType.class));
    }

    public static Res<ContentAccessType> find(int sid) {
        return DT.findResource(sid);
    }

    public static Res<ContentAccessType> findByMetadata(String metadata) {
        return metadata == null ? find(DEFAULT) : METADATA_MAP.get(metadata);
    }

    static Res<ContentAccessType> get(int sid) {
        return DT.get(sid);
    }

    public final String getType() {
        return type;
    }

    public final String getMetadata() {
        return metadata;
    }

    public final boolean isGoldOpenAccess() {
        return goldOpenAccess;
    }

    @Override
    protected void resolve() {
        METADATA_MAP.put(getMetadata(), DT.get(getId()));
    }

    @Override
    protected void populate() {
        DT.publish(this);
    }

    @Override
    public String toString() {
        return String.format("%s [%d]", metadata, getId());
    }
}

