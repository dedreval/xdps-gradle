package com.wiley.cms.process.res;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.cms.process.IProcessManager;
import com.wiley.tes.util.res.DataTable;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceIntId;
import com.wiley.tes.util.res.ResourceManager;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.03.13
 */
@XmlRootElement(name = ProcessType.RES_NAME)
public class ProcessType extends ResourceIntId implements Serializable {
    //private static final Logger LOG = Logger.getLogger(ProcessType.class);

    static final String RES_NAME = "process";
    private static final long serialVersionUID = 1L;

    private static final int EMPTY = 0;
    private static final int DEFAULT = 1;

    private static final DataTable<Integer, ProcessType> DT = new DataTable<>(ProcessType.RES_NAME);

    @XmlAttribute(name = "name")
    private String name;

    @XmlAttribute(name = "next-by-part")
    private boolean nextByPart = false;

    @XmlAttribute(name = "batch")
    private int batch = 1;

    @XmlAttribute(name = "capacity")
    private int capacity = 1;

    @XmlAttribute(name = "container")
    private int container = 0;

    @XmlAttribute(name = "parts-in-msg")
    private int parts = 1;

    @XmlAttribute(name = "create-parts-before")
    private boolean createPartsBefore = false;

    @XmlAttribute(name = "serial")
    private boolean serial = false;

    @XmlAttribute(name = "delete-on-end")
    private boolean endDelete = true;

    @XmlAttribute(name = "delete-on-fail")
    private boolean failDelete = false;

    @XmlAttribute(name = "log-on-start")
    private boolean logOnStart = false;

    @XmlAttribute(name = "log-on-end")
    private boolean logOnEnd = false;

    @XmlAttribute(name = "log-on-fail")
    private boolean logOnFail = true;

    @XmlAttribute(name = "write-stats")
    private boolean writeStats = false;

    @XmlAttribute(name = "handler")
    private String handler;

    @XmlAttribute(name = "queue")
    private String queue;

    @XmlAttribute(name = "priority")
    private int priority = IProcessManager.USUAL_PRIORITY;

    private Res<ProcessType> next;

    private Res<ProcessType> prev;

    private List<ProcessType> children;

    public ProcessType() {
        super();
    }

    public ProcessType(int id) {
        super();
        setId(id);
    }

    public static void register(ResourceManager loader) {
        loader.register(ProcessType.RES_NAME, JaxbResourceFactory.create(ProcessType.class, DT));
    }

    public static Res<ProcessType> find(int id) {
        Res<ProcessType> ret = DT.findResource(id);
        if (ret == null) {
            throw new RuntimeException(String.format("cannot find process type %d", id));
        }
        return ret;
    }

    public static ProcessType empty() {
        return DT.get(EMPTY).get();
    }

    public static boolean isEmpty(Integer id) {
        return empty().getId().equals(id);
    }

    public static ProcessType simple() {
        return DT.get(DEFAULT).get();
    }

    public static Res<ProcessType> get(int id, Function<Integer, ProcessType> externalTypeProvider) {
        Res<ProcessType> ret = DT.findResource(id);
        return Res.valid(ret) ? ret : DT.get(id, externalTypeProvider.apply(id));
    }

    static Res<ProcessType> get(int id) {
        return DT.get(id);
    }

    public boolean isSync() {
        return capacity == 0;
    }

    public boolean startNextByPart() {
        return nextByPart;
    }

    public boolean startNextByEnd() {
        return !nextByPart;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isContainer() {
        return container > 0;
    }

    public boolean isDependChildren() {
        return container == 1;
    }

    public boolean hasExternalChildren() {
        return container == 2;
    }

    @Override
    protected void resolve() {

        if (startNextByPart()) {
            ProcessType pt = getNext();
            if (pt != null) {
                pt.prev = get(getId());
            }
        }

        if (children == null) {
            return;
        }

        Set<Integer> nextIds = new HashSet<>();
        for (ProcessType pt: children) {
            if (pt.hasNext()) {
                nextIds.add(pt.getNext().getId());
            }
        }
        if (!nextIds.isEmpty()) {
            List<ProcessType> newChildren = new ArrayList<>();
            for (ProcessType pt: children) {
                if (!nextIds.contains(pt.getId())) {
                    newChildren.add(pt);
                }
            }
            children = newChildren;
        }
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public int batch() {
        return batch;
    }

    public int capacity() {
        return capacity;
    }

    public int countInMessage() {
        return parts;
    }

    public boolean isCreatePartsBefore() {
        return createPartsBefore;
    }

    public boolean isSerial() {
        return serial;
    }

    public String getGroup() {
        return isSerial() ? getName() : null;
    }

    @XmlAttribute(name = "next-ref")
    public void setNextType(int typeId) {
        if (typeId > EMPTY) {
            next = get(typeId);
        }
    }

    public boolean hasNext() {
        return next != null;
    }

    public String getName() {
        return name;
    }

    public ProcessType getNext() {
        return Res.valid(next) ? next.get() : null;
    }

    public ProcessType getPrev() {
        return Res.valid(prev) ? prev.get() : null;
    }

    public boolean stopByPrev() {
        return getPrev() != null;
    }

    public String getHandlerName() {
        return handler;
    }

    public String getQueueName() {
        return queue;
    }

    public boolean deleteOnEnd() {
        return endDelete;
    }

    public boolean deleteOnFail() {
        return failDelete;
    }

    public boolean canWriteStats() {
        return writeStats;
    }

    @XmlElement(name = RES_NAME)
    public void setChildren(ProcessType[] values) {
        List<ProcessType> list = new ArrayList<>();
        for (ProcessType pt: values) {
            list.add(pt);
            DT.add(pt.getId(), pt);
        }
        children = list;
    }

    public Iterator<ProcessType> getNextChildren() {
        return children.iterator();
    }

    public boolean logOnStart() {
        return logOnStart;
    }

    public boolean logOnEnd() {
        return logOnEnd;
    }

    public boolean logOnFail() {
        return logOnFail;
    }

    public boolean isEmpty() {
        return isEmpty(getId());
    }
}

