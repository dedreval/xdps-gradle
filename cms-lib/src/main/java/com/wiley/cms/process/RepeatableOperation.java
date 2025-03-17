package com.wiley.cms.process;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 18.03.14
 */
public abstract class RepeatableOperation {
    private static final Logger LOG = Logger.getLogger(RepeatableOperation.class);
    private static final int DELAY = 1000;

    public final Object[] params;

    private final int maxCount;
    private int counter = 0;

    public RepeatableOperation() {
        this(2);
    }

    public RepeatableOperation(int countOperation) {
        maxCount = countOperation;
        this.params = null;
    }

    public RepeatableOperation(int countOperation, Object... params) {
        maxCount = countOperation;
        this.params = params;
    }

    protected abstract void perform() throws Exception;

    private void resetCounter() {
        counter = 0;
    }

    private void increaseCounter() {
        counter++;
    }

    protected void fillCounter() {
        counter = maxCount + 2;
    }

    public boolean performOperation() {

        resetCounter();

        boolean ret = false;
        while (!ret && counter <= maxCount) {
            ret = performRepeatableOperation();
        }
        return ret;
    }

    public void performOperationThrowingException() throws Exception {

        resetCounter();

        boolean ret = false;
        while (!ret && counter <= maxCount) {
            ret = performRepeatableOperationWithException();
        }
    }

    protected int getDelay() {
        return DELAY;
    }

    protected Exception onLastAttempt(Exception e)  {
        if (counter == maxCount + 1) {
            LOG.warn(String.format("operation was performed %d times and finished with: %s", counter, e.getMessage()));
        }
        return e;
    }

    protected Exception onNextAttempt(Exception e) {
        LOG.debug(String.format("trying to repeat operation %d time with: %s", counter, e.getMessage()));
        pause();
        return null;
    }

    private boolean performRepeatableOperationWithException() throws Exception {
        try {
            increaseCounter();
            perform();
            return true;

        } catch (Exception e)  {

            Exception ex = counter > maxCount ? onLastAttempt(e) : onNextAttempt(e);
            if (ex != null) {
                throw ex;
            }
            return false;
        }
    }

    private boolean performRepeatableOperation() {
        try {
            increaseCounter();
            perform();
            return true;

        } catch (Exception e)  {
            if (counter > maxCount) {
                onLastAttempt(e);
            } else {
                onNextAttempt(e);
            }
            return false;
        }
    }

    private void pause() {
        try {
            Thread.sleep(getDelay());
        } catch (InterruptedException ie) {
            LOG.error(ie);
        }
    }
}
