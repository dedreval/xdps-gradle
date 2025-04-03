package com.wiley.cms.cochrane.process.handler;

import java.util.Collection;
import java.util.List;

import javax.jms.Queue;

import com.wiley.cms.cochrane.process.BaseAcceptQueue;
import com.wiley.cms.cochrane.process.CMSProcessManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessVO;


/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/7/2019
 *
 */
public class UswConversionHandler extends ContentHandler<DbHandler, BaseAcceptQueue, Collection<String>> {

    private static final long serialVersionUID = 1L;

    public UswConversionHandler() {
    }

    public UswConversionHandler(DbHandler handler) {
        super(handler);
    }

    @Override
    protected Class<DbHandler> getTClass() {
        return DbHandler.class;
    }

    @Override
    public void onMessage(ProcessVO pvo, ProcessPartVO processPart) throws ProcessException {
    }

    @Override
    protected void onStartAsync(ProcessVO pvo, List<ProcessPartVO> inputData, CMSProcessManager manager, Queue queue)
            throws ProcessException {
        super.onStartAsync(pvo, inputData, manager, queue);
    }

    @Override
    protected void onStartSync(ProcessVO pvo, List<ProcessPartVO> inputData, CMSProcessManager manager)
            throws ProcessException {
        super.onStartSync(pvo, inputData, manager);
    }

    @Override
    public void acceptResult(Collection<String> recordNames) {
    }

    @Override
    public void acceptResult(QaServiceHandler fromHandler, ProcessVO from) {
    }
}
