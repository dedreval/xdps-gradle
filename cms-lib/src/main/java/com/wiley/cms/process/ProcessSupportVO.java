package com.wiley.cms.process;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 5/9/2019
 */
public class ProcessSupportVO extends CmsVO {

    private Object output;
    private ProcessState state;
    //private Map<String, Integer> stats;

    private String msg = null;

    public ProcessSupportVO(int id) {
        setId(id);
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    public Object giveOutput() {
        Object ret = output;
        output = null;
        return ret;
    }

    public Object getOutput() {
        return output;
    }

    public void setOutput(Object result) {
        output = result;
    }

    public boolean hasOutput() {
        return output != null;
    }

    /*public void addStats(String statName, int count) {
        Map<String, Integer> map = stats();
        Integer stat = map.get(statName);
        if (stat == null) {
            map.put()
        }
    }

    private Map<String, Integer> stats() {
        if (stats == null) {
            stats = new HashMap<>();
        }
        return stats;
    }

    public int getSuccessfulCount() {
        return successfulCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public String getStatsAsString() {
        return String.format("%s%d,%d", ProcessHelper.REPORT_STATS, successfulCount, failedCount);
    } */


    public String getMessage() {
        return msg;
    }

    public void setMessage(String message) {
        msg = message;
    }

    public void addMessage(String message) {
        if (msg == null) {
            setMessage(message);
        } else {
            msg = message + "\n" + msg;
        }
    }

    public String getStats() {
        if (msg == null) {
            return null;
        }
        int ind = msg.indexOf(ProcessHelper.REPORT_STATS);
        return ind > -1 ? msg.substring(ind + ProcessHelper.REPORT_STATS.length()) : null;
    }

    public void setStats(IProcessStats stats) {
        if (stats == null) {
            return;
        }
        String statsStr = ProcessHelper.REPORT_STATS + stats.asString();
        if (msg == null || msg.startsWith(ProcessHelper.REPORT_STATS)) {
            setMessage(statsStr);
            
        } else {
            int ind = msg.indexOf(ProcessHelper.REPORT_STATS);
            if (ind > 1) {
                msg = msg.substring(0, ind);

            } else {
                msg += "\n" + statsStr;
            }
        }
    }
}
