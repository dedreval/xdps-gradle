package com.wiley.cms.cochrane.process.handler;

import java.util.List;

import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessSupportVO;
import com.wiley.cms.process.ProcessVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/27/2019
 *
 * @param <R> Results of process execution to pass or take
 */
public interface IContentResultAcceptor<R> {

    default void passResult(ProcessVO pvo, IContentResultAcceptor toHandler) {
    }

    default void acceptResult(R results) {
    }

    default void acceptResult(PackageUnpackHandler fromHandler, ProcessVO from) {
    }

    default void acceptResult(Wml3gValidationHandler fromHandler, ProcessVO from) {
    }

    default void acceptResult(QaServiceHandler fromHandler, ProcessVO from) {
    }

    default R takeResult(ProcessSupportVO from) {
        return (R) from.giveOutput();
    }

    default R takeResult(List<ProcessPartVO> from) throws ProcessException {
        if (from == null || from.isEmpty()) {
            return null;
        }
        if (from.size() > 1) {
            throw new ProcessException("collecting results from multiple parts is not supported by default");
        }
        return (R) from.get(0).giveOutput();
    }
}
