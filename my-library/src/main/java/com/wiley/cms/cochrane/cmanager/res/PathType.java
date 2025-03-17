package com.wiley.cms.cochrane.cmanager.res;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.utils.Constants;
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
@XmlRootElement(name = PathType.RES_NAME)
public class PathType extends ResourceStrId implements Serializable {

    static final String RES_NAME = "path";

    private static final long serialVersionUID = 1L;
    private static final DataTable<String, PathType> DT = new DataTable<>(RES_NAME);

    private static final String PATH_FORBIDDEN = ":*?\"<>{}|";
    private static final Res<Property> COCHRANE_REPO_NAME = Property.get("cms.cochrane.prefix.repository", "");

    private Node root;

    private String template;

    @XmlAttribute(name = "weak")
    private boolean weak = false;

    @XmlAttribute(name = "expiration")
    private int expiration = Constants.NO_EXPIRATION_LIMIT;

    private LinkedHashMap<String, Integer> keyPositionMap = null;


    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, JaxbResourceFactory.create(PathType.class));
    }

    public static String getCochraneRepositoryPrefix() {
        return COCHRANE_REPO_NAME.get().getValue();
    }

    public static void freshWeakPaths() {
        for (Res<PathType> path: DT.values()) {
            if (Res.valid(path)) {
                path.get().fresh(true);
            }
        }
    }

    public static Res<PathType> get(String sid) {
        Res<PathType> ret = DT.get(sid);
        if (!Res.valid(ret)) {

            LOG.warn("path type resource hasn't been initialized yet");
            ResourceManager.instance();
            return DT.get(sid);
        }
        return ret;
    }

    @XmlAttribute(name = "use-as-keyword")
    public void setAsKeyword(boolean value) {
        if (value) {
            keyPositionMap = new LinkedHashMap<>();
        }
    }

    @XmlAttribute(name = "only-keyword-positions")
    public void setCalculatedPositions(boolean value) {
        if (value && !hasCalculatedPositions()) {
            keyPositionMap = new LinkedHashMap<>();
        }
    }

    public boolean hasCalculatedPositions() {
        return keyPositionMap != null;
    }

    public int getCalculatedPosition(String positionKey) {
        return keyPositionMap.get(positionKey);
    }

    public String getLastCalculatedKeyword() {
        return keyPositionMap.keySet().iterator().next();
    }

    @XmlAttribute(name = "template")
    public String getTemplate() {
        return template;
    }

    public void setTemplate(String value) {
        template = value.trim();
        root = new Node();
    }

    @Override
    protected void resolve() {
        if (hasEmbeddedTemplate()) {
            resolveTemplate(new HashSet<>());
        }

        if (hasCalculatedPositions()) {
            String[] keys = FilePathBuilder.splitPath(template);
            for (int i = keys.length - 1; i >= 0; i--) {
                keyPositionMap.put(keys[i], i);
            }
        }
    }

    @Override
    protected void populate() {
        DT.publish(this);
    }

    private boolean resolveTemplate(Set<String> checked) {

        String key = getEmbeddedTemplate();
        if (checked.contains(getId())) {
            LOG.error(String.format("path %s has template=%s with cycled dependency!", this, key));
            return false;
        }
        boolean ret = false;
        checked.add(getId());

        Res<PathType> res = get(key);

        if (!res.exist()) {
            LOG.error(String.format("path %s has not existed template=%s", this, key));

        }  else {

            PathType owner = res.get();
            if (!owner.hasEmbeddedTemplate() || owner.resolveTemplate(checked)) {
                template = owner.getTemplate() + template.substring(2 + key.length() + 1);
                ret = true;
            }
        }
        return ret;
    }

    @Override
    protected boolean check() {
        StringTokenizer st = new StringTokenizer(template, PATH_FORBIDDEN);
        boolean ret = true;
        if (st.countTokens() > 1) {
            LOG.error(String.format("path %s contains forbidden chars: %s", this, PATH_FORBIDDEN));
            ret = false;
        }
        return ret;
    }

    public final int countNodes() {
        int ret = root.size();
        if (ret == 1 && !root.isInitialized()) {
            ret = 0;
        }
        return ret;
    }

    public final boolean hasCachedPath(String value) {
        return root.findNodeByValue(value) != null;
    }

    public final void fresh(boolean onlyWeak) {
        if ((!onlyWeak || isWeak()) && countNodes() > 0) {
            root = new Node();
        }
    }

    public final void freshCachedPath(String[] keys, Object... values) {
        int size = values.length;
        if (!isValidParams(keys, size)) {
            return;
        }

        Node path = root;
        Node pathHolder = null;
        Object value = null;
        for (int i = 0; i < size; i++) {

            String key = keys[i];
            value = values[i];
            if (value == null) {
                continue;
            }

            Node keyHolder = path;
            pathHolder = keyHolder.findNode(key);
            if (pathHolder == null) {
                break;
            }

            path = pathHolder.findNode(value);
            if (path == null) {
                break;
            }
        }

        if (pathHolder != null && path != null) {
            pathHolder.removeNode(value);
            path.clear();
        }
    }

    public final String getCachedPath(String[] keys, Object... values) {

        int size = values.length;
        if (!isValidParams(keys, size)) {
            return null;
        }

        Node finalPath = root;
        for (int i = 0; i < size; i++) {

            String key = keys[i];
            Object value = values[i];
            if (value == null) {
                continue;
            }

            Node keyHolder = finalPath;
            Node pathHolder = keyHolder.createNode(key);
            finalPath = pathHolder.findNode(value);

            if (finalPath == null) {
                finalPath = pathHolder.addNode(value, null);
            }
        }

        if (finalPath.body == null) {
            finalPath.body = buildResult(size, keys, values);
        }
        return finalPath.body;
    }

    public final String getPath(String[] params, Object... values) {
        return buildResult(values.length, params, values);
    }

    public boolean isWeak() {
        return weak;
    }

    public final int getExpiration() {
        return expiration;
    }

    private boolean isValidParams(String[] keys, int size) {
        if (keys.length < size) {
            LOG.error(String.format("path %s - wrong keys size: %d, values size = %d", this, keys.length, size));
            return false;
        }
        return true;
    }

    private boolean hasEmbeddedTemplate() {
        return template.startsWith("${");
    }

    private String getEmbeddedTemplate() {
        int endInd = template.indexOf("}", 2);
        if (endInd == -1) {

            LOG.error(String.format("path %s: can not find closing } in template", this));
            return "";
        }
        return template.substring(2, endInd);
    }

    private String buildResult(int size, String[] params, Object... values) {
        String ret = getTemplate();
        for (int i = 0; i < size; i++)  {

            Object value = values[i];
            if (value == null) {
                continue;
            }
            ret = ret.replaceAll(params[i], values[i].toString());
        }
        return getCochraneRepositoryPrefix() + ret;
    }

    public static void append(StringBuilder sb) {
        sb.append("\nCached paths:");
        int count = 0;
        for (Res<PathType> res: DT.values()) {

            PathType pt = res.get();
            int size = pt.countNodes();
            count += size;
            sb.append("\n").append(pt).append(", size=").append(size);
            pt.root.append(sb, "", pt.root.isInitialized());
        }
        sb.append("\ncount: ").append(count);
    }

    @Override
    public String toString() {
        return String.format("%s [%s]", template, getId());
    }

    private static class Node implements Serializable {
        private static final long serialVersionUID = 1L;

        private String body;
        private final Map<Object, Node> nodes = new HashMap<>();

        private Node() {
        }

        private Node(String val) {
            body = val;
        }

        private String getBody() {
            return body;
        }

        private Node removeNode(Object key) {
            return nodes.remove(key);
        }

        private Node addNode(Object key, String value) {
            Node node = new Node(value);
            nodes.put(key, node);
            return node;
        }

        private Node findNode(Object key) {
            return nodes.get(key);
        }

        private Node findNodeByValue(String value) {
            if (value.equals(body)) {
                return this;
            }

            Node ret = null;
            for (Node n: nodes.values()) {
                ret = n.findNodeByValue(value);
                if (ret != null) {
                    break;
                }
            }
            return ret;
        }

        private Node createNode(Object key) {
            Node node = findNode(key);
            if (node == null) {
                node = addNode(key, key.toString());
            }
            return node;
        }

        private boolean isInitialized() {
            return getBody() != null;
        }

        private void append(StringBuilder sb, String step, boolean includeBody) {
            if (includeBody) {
                sb.append(" :").append(body);
            }

            nodes.forEach((key, n) -> {
                    sb.append("\n").append(step).append("has child: ").append(key).append("-> ");
                    n.append(sb, step + "    ", true);
                });
        }

        public int size() {
            int size = 1;
            for (Node n: nodes.values()) {
                size += n.size();
            }
            return size;
        }

        public void clear() {
            body = null;
            for (Node n: nodes.values()) {
                n.clear();
            }
            nodes.clear();
        }

        @Override
        public String toString() {
            return body;
        }
    }
}

