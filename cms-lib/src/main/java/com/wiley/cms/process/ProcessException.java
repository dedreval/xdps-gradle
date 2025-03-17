package com.wiley.cms.process;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 25.06.13
 */
public class ProcessException extends Exception {

    private Code code = Code.NONE;

    public ProcessException(String msg) {
        super(msg);
    }

    public ProcessException(String msg, int processId) {
        super(String.format("%s, processId=%d", msg, processId));
    }

    public ProcessException(Throwable ex) {
        super(ex);
    }

    public ProcessException(String msg, Code code) {
        super(msg);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    /**
     * Error code to pass
     */
    public enum Code {
        NONE, REDELIVERY {
            @Override
            public boolean isRedelivery() {
                return true;
            }
        };
        public boolean isRedelivery() {
            return false;
        }
    }
}
