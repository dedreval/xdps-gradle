package com.wiley.cms.cochrane.cmanager.res;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.tes.util.res.DataTable;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/19/2016
 */
@XmlRootElement(name = ConnectionType.RES_NAME)
public class ConnectionType extends ResourceStrId {
    static final String RES_NAME = "connection";

    static final ConnectionType EMPTY = new ConnectionType(-1);

    private static final long serialVersionUID = 1L;

    private static final DataTable<String, ConnectionType> DT = new DataTable<>(RES_NAME);

    @XmlAttribute(name = "url")
    private String url;

    @XmlAttribute(name = "timeout")
    private int timeout;

    @XmlAttribute(name = "authorization")
    private String authorization;

    @XmlAttribute(name = "method")
    private int method;

    public ConnectionType() {
    }

    private ConnectionType(int method) {
        this.method = method;
    }

    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, JaxbResourceFactory.create(ConnectionType.class, DT));
    }

    public static Res<ConnectionType> find(String sid) {
        return DT.findResource(sid);
    }

    static Res<ConnectionType> get(String sid) {
        return DT.get(sid);
    }

    public final String getUrl() {
        return url;
    }

    public final int getTimeout() {
        return timeout;
    }

    public final String getAuthorization() {
        return authorization;
    }

    public final boolean exists() {
        return method >= 0;
    }

    @Override
    public String toString() {
        return String.format("%s timeout=%d", url, timeout);
    }
}
