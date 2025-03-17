package com.wiley.tes.util.res;


import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 01.04.13
 */
@XmlRootElement(name = Property.RES_NAME)
public class Property extends ResourceStrId {
    static final String RES_NAME = "property";
    private static final Logger LOG = Logger.getLogger(Property.class);

    private static final DataTable<String, Property> DT = new DataTable<String, Property>(RES_NAME);

    @XmlAttribute(name = "value")
    private String value;

    public Property() {
    }

    public Property(String id, String def) {
        setId(id);
        value = def;
    }

    @Override
    public String toString() {
        return String.format("%s=%s", getId(), getValue());
    }

    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, JaxbResourceFactory.create(Property.class, DT));
    }

    public static Res<Property> get(String id) {
        return DT.findResource(id);
    }

    public static Res<Property> get(String id, String def) {

        Res<Property> ret = get(id);
        if (Res.valid(ret) || def == null) {
            return ret;
        }

        if (ret == null) {
            ret = DT.get(id, new Property(id, def));
        } else {
            ret.get().setValue(def);
        }

        return ret;
    }

    public static String getStrProperty(String id) {

        Res<Property> r = get(id);
        if (Res.valid(r)) {
            return r.get().getValue();
        }

        return null;
    }

    public static Integer getIntProperty(String id) {

        Res<Property> r = get(id);
        if (Res.valid(r)) {
            return r.get().asInteger();
        }
        return null;
    }

    public static boolean getBooleanProperty(String id, boolean def) {

        Res<Property> r = get(id);
        return !Res.valid(r) ? def : r.get().asBoolean();
    }

    public Integer asInteger() {
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                LOG.warn(e.getMessage());
            }
        }
        return null;
    }

    public boolean asBoolean() {
        if (value != null) {
            try {
                return Boolean.parseBoolean(value);
            } catch (Exception e) {
                LOG.warn(e.getMessage());
            }
        }
        return false;
    }

    public String getValue() {
        return value;
    }

    public String[] getValues() {
        if (this.value != null) {
            return this.value.split(",");
        }
        return null;
    }

    public void setValue(String value) {
        this.value = value;
    }
}


