package com.wiley.tes.util.res;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 01.04.13
 */
@XmlRootElement(name = Settings.RES_NAME)
public class Settings extends ResourceStrId implements Serializable {
    static final String RES_NAME = "settings";
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(Settings.class);

    private static final DataTable<String, Settings> DT = new DataTable<>(RES_NAME);

    private Map<String, Setting> settings = new LinkedHashMap<>();

    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, JaxbResourceFactory.create(Settings.class, DT));
    }

    public static Res<Settings> findSettings(String id) {
        return DT.findResource(id);
    }

    public static Res<Settings> getSettings(String id) {
        return DT.get(id);
    }

    public Collection<Setting> getSettings() {
        return settings.values();
    }

    public Setting getSetting(String label) {
        return settings.get(label);
    }

    public String getStrSetting(String label) {

        Setting set = getSetting(label);
        if (set != null) {
            return set.getValue();
        }

        return null;
    }

    public Integer getIntSetting(String label) {

        String value = getStrSetting(label);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                LOG.warn(e.getMessage());
            }
        }

        return null;
    }

    public Boolean getBoolSetting(String label) {

        Setting set = getSetting(label);
        if (set == null) {
            return null;
        }

        Boolean ret = null;

        Object value = set.getObject();
        if (value != null) {
            if (value instanceof Boolean) {
                ret = (Boolean) value;
            } else {
                ret = Boolean.parseBoolean(value.toString());
                set.setObject(ret);
            }
        }
        return ret;
    }

    public String[] getArraySetting(String label) {

        Setting set = getSetting(label);
        if (set == null) {
            return null;
        }

        String[] ret = null;

        Object value = set.getObject();
        if (value != null) {
            if (value instanceof String[]) {
                ret = (String[]) value;
            } else {
                ret = value.toString().split(",");
                set.setObject(ret);
            }
        }
        return ret;
    }

    public void setSetting(String label, Object value) {

        Setting set = getSetting(label);
        if (set != null) {
            set.setValue(value.toString());
        } else {
            settings.put(label, new Setting(label, value.toString()));
        }
    }

    //public static synchronized void updateSettings(Settings set) {
    //    DT.add(set.getId(), set);
    //    set.save();
    //}

    //public void save() {
    //    try {
    //        ResourceManager.instance().getFactory(RES_NAME).saveResource(this,
    //                ResourceManager.instance().getResourceFolders()[0] + File.separator + getId() + ".xml");
    //    } catch (Exception e) {
    //        LOG.warn(e.getMessage());
    //    }
    //}

    @XmlElement(name = "set")
    public void setItems(Setting[] list) {

        settings.clear();

        Map<String, Setting> map = new LinkedHashMap<>();

        for (Setting set : list) {
            map.put(set.label, set);
        }

        settings = map;
    }

    private Setting[] getItems() {
        return settings.values().toArray(new Setting[0]);
    }

    /**
     *
     */
    public static class Setting implements Serializable {
        private static final long serialVersionUID = 1L;
        private String label;
        private Object value;

        public Setting() {
        }

        public Setting(String label, String value) {

            setLabel(label);
            setValue(value);
        }

        @XmlAttribute(name = "value")
        public String getValue() {
            return value == null ? null : value.toString();
        }

        @XmlAttribute(name = "label")
        public String getLabel() {
            return label;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public void setObject(Object value) {
            this.value = value;
        }

        public Object getObject() {
            return value;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public boolean contains(String val) {
            return value != null && value.toString().contains(val);
        }
    }
}


