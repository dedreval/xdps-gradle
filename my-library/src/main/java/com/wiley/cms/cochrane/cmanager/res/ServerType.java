package com.wiley.cms.cochrane.cmanager.res;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Base64;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.DataTable;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.03.13
 */
@XmlRootElement(name = ServerType.RES_NAME)
public class ServerType extends ResourceStrId {

    public static final String LOCALHOST = "LOCALHOST";
    static final String RES_NAME = "server";
    private static final String PASSWORD_SUFFIX = "_PASSWORD";
    private static final String USER_SUFFIX = "_USER";

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ServerType.class);

    private static final DataTable<String, ServerType> DT = new DataTable<>(RES_NAME);

    @XmlAttribute(name = "host")
    private String host = "";

    @XmlAttribute(name = "user")
    private String user = "";

    @XmlAttribute(name = "password")
    private String password;

    @XmlAttribute(name = "port")
    private int port = -1;
    
    @XmlAttribute(name = "timeout")
    private int timeout;

    private String identity;

    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, JaxbResourceFactory.create(ServerType.class, DT));
    }

    public static Res<ServerType> find(String sid) {
        return DT.findResource(sid);
    }

    static Res<ServerType> get(String sid) {
        return DT.get(sid);
    }

    @Override
    protected void resolve() {
        if (password == null || password.trim().isEmpty())  {
            if (Property.getStrProperty(RES_NAME + getId()) != null) {
                password = decode(Property.getStrProperty(RES_NAME + getId()));
            } else if (System.getenv(getId() + PASSWORD_SUFFIX) != null) {
                password = System.getenv(getId() + PASSWORD_SUFFIX);
            }
        }
        if (user == null || user.trim().isEmpty()) {
            if (Property.getStrProperty(getId() + RES_NAME) != null) {
                user = decode(Property.getStrProperty(getId() + RES_NAME));
            } else if (System.getenv(getId() + USER_SUFFIX) != null) {
                user = System.getenv(getId() + USER_SUFFIX);
            }
        }
        setIdentity(Property.getStrProperty(getId()));
    }

    public final String getHost() {
        return host;
    }

    public final String getUser() {
        return user;
    }

    public final String getPassword() {
        return password;
    }

    public final int getPort() {
        return port;
    }

    public final int getTimeout() {
        return timeout;
    }

    private void setIdentity(String value) {
        identity = value != null && !value.trim().isEmpty() && new File(value).exists() ? value : null;
    }

    public String getIdentity() {
        return identity;
    }

    public boolean hasIdentity() {
        return identity != null;
    }

    public boolean isLocalHost() {
        return LOCALHOST.equals(getId());
    }

    private static String decode(String value) {
        if (value != null) {
            try {
                return InputUtils.readStreamToString(new ByteArrayInputStream(Base64.getDecoder().decode(value)));
            } catch (Exception ex) {
                LOG.warn(ex.getMessage());
            }
        }
        return "";
    }

    @Override
    public String toString() {
        return String.format("%s [%s]", host, getId());
    }
}


