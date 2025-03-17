package com.wiley.cms.process;

import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 09.07.13
 */
@Singleton
@Local(IProcessCache.class)
@Lock(LockType.READ)
@Startup
public class ProcessCache extends AbstractCache implements com.wiley.cms.process.jmx.ProcessCacheMXBean, IProcessCache {

    private final TCache<Integer, ProcessVO> processes = new TCache<Integer, ProcessVO>();

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void addProcess(ProcessVO process) {
        processes.addObject(process.getId(), process);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ProcessVO getProcess(int id) {
        return processes.getObject(id);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ProcessVO removeProcess(int id) {
        return processes.removeObject(id);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void clear()  {
        processes.clear();
        LOG.info("process cache is cleared");
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String toString() {
        return String.format("%d processes: %s", processes.getObjects().size(), processes.getObjects());
    }

    /**
     * Just a factory
     */
    public static class Factory extends AbstractBeanFactory<IProcessCache> {

        private static final Factory INSTANCE = new Factory();

        private Factory() {
            super("CMS", "ProcessCache", IProcessCache.class);
        }

        public static Factory getFactory() {
            return INSTANCE;
        }
    }
}

