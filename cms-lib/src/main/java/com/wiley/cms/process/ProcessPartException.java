package com.wiley.cms.process;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12.12.13
 */
public class ProcessPartException extends Exception {

    private ProcessPartVO processPart;

    public ProcessPartException(ProcessPartVO processPart) {
        super(processPart.getMessage());
        this.processPart = processPart;
    }

    public ProcessPartException(String msg, ProcessPartVO processPart) {
        super(msg);
        this.processPart = processPart;
    }

    public boolean isError() {
        return processPart.getState().isFailed();
    }

    public ProcessPartVO getProcessPart() {
        return processPart;
    }
}
