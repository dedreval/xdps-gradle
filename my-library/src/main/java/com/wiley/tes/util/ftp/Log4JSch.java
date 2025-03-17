package com.wiley.tes.util.ftp;


import com.wiley.cms.cochrane.test.Hooks;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 12/30/2020
 */
enum Log4JSch {

    DEBUG {
        @Override
        boolean isEnabled() {
            return LOG.isDebugEnabled();
        }

        @Override
        void log(String message) {
            Hooks.LOG.debug(message);
        }
    },
    INFO {
        @Override
        boolean isEnabled() {
            return LOG.isInfoEnabled();
        }

        @Override
        void log(String message) {
            Hooks.LOG.info(message);
        }
    },
    WARN {
        @Override
        boolean isEnabled() {
            return LOG.isWarnEnabled();
        }

        @Override
        void log(String message) {
            LOG.warn(message);
        }
    },
    ERROR {
        @Override
        boolean isEnabled() {
            return LOG.isErrorEnabled();
        }

        @Override
        void log(String message) {
            LOG.error(message);
        }
    },
    FATAL {
        @Override
        boolean isEnabled() {
            return LOG.isFatalEnabled();
        }

        @Override
        void log(String message) {
            LOG.fatal(message);
        }
    },
    EMPTY {
        @Override
        boolean isEnabled() {
            return false;
        }

        @Override
        void log(String message) {
        }
    };

    static final com.jcraft.jsch.Logger JSCH_LOG = new com.jcraft.jsch.Logger() {

        @Override
        public boolean isEnabled(int level) {
            return get(level).isEnabled();
        }

        @Override
        public void log(int level, String message) {
            get(level).log(message);
        }
    };

    private static final com.wiley.tes.util.Logger LOG = com.wiley.tes.util.Logger.getLogger(Log4JSch.class);

    abstract boolean isEnabled();

    abstract void log(String message);

    static Log4JSch get(int level) {
        Log4JSch ret;

        switch (level) {
            case com.jcraft.jsch.Logger.DEBUG:
                ret = DEBUG;
                break;
            case com.jcraft.jsch.Logger.INFO:
                ret = INFO;
                break;
            case com.jcraft.jsch.Logger.WARN:
                ret = WARN;
                break;
            case com.jcraft.jsch.Logger.ERROR:
                ret = ERROR;
                break;
            case com.jcraft.jsch.Logger.FATAL:
                ret = FATAL;
                break;
            default:
                ret = EMPTY;
        }
        return ret;
    }
}
