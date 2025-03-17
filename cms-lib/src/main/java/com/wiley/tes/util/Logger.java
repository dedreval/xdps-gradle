// $Id: Logger.java,v 1.4 2011-11-25 14:05:50 sgulin Exp $
// Created: 14.03.2005 T 20:45:17
// Copyright (C) 2005 by John Wiley & Sons Inc. All Rights Reserved.
package com.wiley.tes.util;

import java.lang.reflect.Method;

/**
 * This is universal interface to either Java standard or Log4J loggin API.
 * The underlaying log implementation can be configured by
 * <code>com.wiley.tes.log</code> system property:
 * <ul>
 * <li><code>java</code> - java.util.logging</li>
 * <li><code>log4j</code> - org.apache.log4j</li>
 * </ul>
 * If <code>com.wiley.tes.log</code> system property is not specified, then
 * the logger will use Log4J implemenation under JBoss, and the Java standard
 * otherwise.
 *
 * @author <a href='mailto:azhukov@wiley.ru'>Alexey Zhukov</a>
 * @version $Revision: 1.4 $
 */
public abstract class Logger {
    /**
     * The log level
     */
    public enum Level {
        /**
         * All messages are loggable.
         */
        TRACE {
            boolean isTraceEnabled() {
                return true;
            }

            boolean isDebugEnabled() {
                return true;
            }

            boolean isInfoEnabled() {
                return true;
            }

            boolean isWarnEnabled() {
                return true;
            }

            boolean isErrorEnabled() {
                return true;
            }

            boolean isFatalEnabled() {
                return true;
            }
        },

        /**
         * All messages except trace are loggable.
         */
        DEBUG {
            boolean isTraceEnabled() {
                return false;
            }

            boolean isDebugEnabled() {
                return true;
            }

            boolean isInfoEnabled() {
                return true;
            }

            boolean isWarnEnabled() {
                return true;
            }

            boolean isErrorEnabled() {
                return true;
            }

            boolean isFatalEnabled() {
                return true;
            }
        },

        /**
         * All messages except trace and debug are loggable.
         */
        INFO {
            boolean isTraceEnabled() {
                return false;
            }

            boolean isDebugEnabled() {
                return false;
            }

            boolean isInfoEnabled() {
                return true;
            }

            boolean isWarnEnabled() {
                return true;
            }

            boolean isErrorEnabled() {
                return true;
            }

            boolean isFatalEnabled() {
                return true;
            }
        },

        /**
         * Only warn, error, and fatal messages are loggable.
         */
        WARN {
            boolean isTraceEnabled() {
                return false;
            }

            boolean isDebugEnabled() {
                return false;
            }

            boolean isInfoEnabled() {
                return false;
            }

            boolean isWarnEnabled() {
                return true;
            }

            boolean isErrorEnabled() {
                return true;
            }

            boolean isFatalEnabled() {
                return true;
            }
        },

        /**
         * Only error and fatal messages are loggable.
         */
        ERROR {
            boolean isTraceEnabled() {
                return false;
            }

            boolean isDebugEnabled() {
                return false;
            }

            boolean isInfoEnabled() {
                return false;
            }

            boolean isWarnEnabled() {
                return false;
            }

            boolean isErrorEnabled() {
                return true;
            }

            boolean isFatalEnabled() {
                return true;
            }
        },

        /**
         * Only fatal messages are loggable.
         */
        FATAL {
            boolean isTraceEnabled() {
                return false;
            }

            boolean isDebugEnabled() {
                return false;
            }

            boolean isInfoEnabled() {
                return false;
            }

            boolean isWarnEnabled() {
                return false;
            }

            boolean isErrorEnabled() {
                return false;
            }

            boolean isFatalEnabled() {
                return true;
            }
        },

        /**
         * Nothing is loggable.
         */
        OFF {
            boolean isTraceEnabled() {
                return false;
            }

            boolean isDebugEnabled() {
                return false;
            }

            boolean isInfoEnabled() {
                return false;
            }

            boolean isWarnEnabled() {
                return false;
            }

            boolean isErrorEnabled() {
                return false;
            }

            boolean isFatalEnabled() {
                return false;
            }
        },

        /**
         * Unknown.
         */
        UNKNOWN {
            boolean isTraceEnabled() {
                return false;
            }

            boolean isDebugEnabled() {
                return false;
            }

            boolean isInfoEnabled() {
                return false;
            }

            boolean isWarnEnabled() {
                return false;
            }

            boolean isErrorEnabled() {
                return false;
            }

            boolean isFatalEnabled() {
                return false;
            }
        };

        abstract boolean isTraceEnabled();

        abstract boolean isDebugEnabled();

        abstract boolean isInfoEnabled();

        abstract boolean isWarnEnabled();

        abstract boolean isErrorEnabled();

        abstract boolean isFatalEnabled();
    }


    /**
     * Log engine name (can be null).
     */
    private static final String log2use = System.getProperty("com.wiley.tes.log");

    /**
     * Indicates that we're running under JBoss so its Logger must be used.
     */
    private static final boolean underJBoss = System.getProperty("jboss.server.name") != null;

    /**
     * Name of the logger.
     */
    protected final String name;

    /**
     * Indicates whether call details (class/method/thread names) must be shown.
     */
    protected boolean showCallDetails = true;

    /**
     * Defines minimal time delta for exiting() calls in milliseconds.
     */
    private static long minTraceTime = 3000;

    /**
     * Current log level.
     */
    private Level level = Level.UNKNOWN;

    /**
     * Creates the logger with the given name.
     *
     * @param name the name.
     */
    protected Logger(String name) {
        this.name = name;
    }

    /**
     * Returns Logger with the given name.
     *
     * @param name name of the Logger.
     * @return Logger with the given name.
     */
    public static Logger getLogger(String name) {
        if (log2use != null) {
            if (log2use.equals("java"))
                return new StdLogger(name);
            else if (log2use.equals("log4j"))
                return new Log4JLogger(name);
        }

        if (underJBoss)
            return new Log4JLogger(name);
        else
            return new StdLogger(name);
    }

    /**
     * Returns Logger with the name generated from the given class.
     *
     * @param cls the class.
     * @return Logger with the name generated from the given class.
     */
    public static Logger getLogger(Class cls) {
        return getLogger(cls.getName());
    }

    /**
     * Returns true if call details (class/method/thread names) will be shown
     * for this Logger.
     *
     * @return true if call details (class/method/thread names) will be shown
     *         for this Logger.
     */
    public final boolean getShowCallDetails() {
        return showCallDetails;
    }

    /**
     * Sets whether call details (class/method/thread names) should be shown for
     * this Logger.
     *
     * @param showCallDetails true to show, false to hide (default: true).
     */
    public final void setShowCallDetails(boolean showCallDetails) {
        this.showCallDetails = showCallDetails;
    }

    /**
     * Returns current log level.
     *
     * @return current log level.
     */
    public final Level getLevel() {
        defineLevel();
        return level;
    }

    /**
     * Returns true if the given level is enabled in the underlaying log engine.
     *
     * @param level the log level.
     * @return true if the given level is enabled in the underlaying log engine.
     */
    protected abstract boolean isEnabled(Level level);

    /**
     * Defines current log level in the underlaying log engine.
     */
    private void defineLevel() {
        if (level == Level.UNKNOWN) {
            if (isEnabled(Level.TRACE))
                level = Level.TRACE;
            else if (isEnabled(Level.DEBUG))
                level = Level.DEBUG;
            else if (isEnabled(Level.INFO))
                level = Level.INFO;
            else if (isEnabled(Level.WARN))
                level = Level.WARN;
            else if (isEnabled(Level.ERROR))
                level = Level.ERROR;
            else if (isEnabled(Level.FATAL))
                level = Level.FATAL;
            else
                level = Level.OFF;
        }
    }

    /**
     * (Re)sets the log level.
     *
     * @param level new log level.
     */
    public final void setLevel(Level level) {
        this.level = level;
        setLevelImpl(level);
    }

    /**
     * Implementation of the {@link #setLevel} method.
     *
     * @param level new log level.
     */
    protected abstract void setLevelImpl(Level level);

    /**
     * (Re)sets the minimal trace time in milliseconds (default: 3000). If the
     * logger level is <code>TRACE</code>, then <code>exiting()</code>
     * methods will always produce an output, otherwise the output will be
     * produced if the time delta is greater or equals the minimal trace time
     * only. Please note that this affects all Loggers regardles of their
     * names.
     *
     * @param newMinTraceTime new value for the minimal trace time in
     *                        milliseconds.
     * @see #exiting(long)
     * @see #exiting(Object, long)
     */
    public static void setMinTraceTime(long newMinTraceTime) {
        minTraceTime = newMinTraceTime;
    }

    /**
     * Returns true if this logger is in the trace enabled mode.
     *
     * @return true if this logger is in the trace enabled mode.
     */
    public final boolean isTraceEnabled() {
        defineLevel();
        return level.isTraceEnabled();
    }

    /**
     * Outputs the message with the trace priority.
     *
     * @param message the message.
     */
    public final void trace(Object message) {
        if (isTraceEnabled())
            traceImpl(message);
    }

    /**
     * Implementation of {@link #trace(Object) trace} method.
     *
     * @param message the message.
     */
    protected abstract void traceImpl(Object message);

    /**
     * Outputs the message and the exception info with the trace priority.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    public final void trace(Object message, Throwable t) {
        if (isTraceEnabled())
            traceImpl(message, t);
    }

    /**
     * Implementation of {@link #trace(Object, Throwable) trace} method.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    protected abstract void traceImpl(Object message, Throwable t);

    /**
     * Traces method enter. This method is best to be used when the logger is
     * allowed to show class/method names.
     *
     * @return current time in milliseconds.
     */
    public final long entering() {
        trace("entering");
        return System.currentTimeMillis();
    }

    /**
     * Traces method enter.
     *
     * @param msg the message.
     * @return current time in milliseconds.
     */
    public final long entering(Object msg) {
        if (isTraceEnabled())
            traceImpl("entering " + msg);
        return System.currentTimeMillis();
    }

    /**
     * Traces method exit. This method is best to be used when the logger is
     * allowed to show class/method names.
     */
    public final void exiting() {
        trace("exiting");
    }

    /**
     * Traces method exit.
     *
     * @param message the message.
     */
    public final void exiting(Object message) {
        if (isTraceEnabled())
            traceImpl("exiting " + message);
    }

    /**
     * Traces method exit. This method is best to be used when the logger is
     * allowed to show class/method names.
     *
     * @param startTime method start startTime returned by
     *                  <code>entering()</code> method. If the time difference between now and
     *                  this value greater than the minimal trace time, then the output will be
     *                  done at WARN level.
     * @see #setMinTraceTime(long)
     */
    public final void exiting(long startTime) {
        final long delta = System.currentTimeMillis() - startTime;
        if (delta > minTraceTime && isWarnEnabled())
            warnImpl("exiting (" + delta + " ms)");
        else if (isTraceEnabled())
            traceImpl("exiting (" + delta + " ms)");
    }

    /**
     * Traces method exit.
     *
     * @param message   the message.
     * @param startTime method start startTime returned by
     *                  <code>entering()</code> method. If the time difference between now and
     *                  this value greater than the minimal trace time, then the output will be
     *                  done at WARN level.
     * @see #setMinTraceTime(long)
     */
    public final void exiting(Object message, long startTime) {
        final long delta = System.currentTimeMillis() - startTime;
        if (delta > minTraceTime && isWarnEnabled())
            warnImpl("exiting (" + delta + " ms) " + message);
        else if (isTraceEnabled())
            traceImpl("exiting (" + delta + " ms) " + message);
    }

    /**
     * Returns true if this logger is in the debug enabled mode.
     *
     * @return true if this logger is in the debug enabled mode.
     */
    public final boolean isDebugEnabled() {
        defineLevel();
        return level.isDebugEnabled();
    }

    /**
     * Outputs the message with the debug priority.
     *
     * @param message the message.
     */
    public final void debug(Object message) {
        if (isDebugEnabled())
            debugImpl(message);
    }

    /**
     * Formats and outputs a message with arguments specified
     *
     * @param message the message
     * @param args    the arguments
     */
    public final void debug(String message, Object... args) {
        if (isDebugEnabled())
            debugImpl(String.format(message, args));
    }

    /**
     * Implementation of {@link #debug(Object) debug} method.
     *
     * @param message the message.
     */
    protected abstract void debugImpl(Object message);

    /**
     * Outputs the message and the exception info with the debug priority.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    public final void debug(Object message, Throwable t) {
        if (isDebugEnabled())
            debugImpl(message, t);
    }

    /**
     * Implementation of {@link #debug(Object, Throwable) debug} method.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    protected abstract void debugImpl(Object message, Throwable t);

    /**
     * Returns true if this logger is in the info enabled mode.
     *
     * @return true if this logger is in the info enabled mode.
     */
    public final boolean isInfoEnabled() {
        defineLevel();
        return level.isInfoEnabled();
    }

    /**
     * Outputs the message with the info priority.
     *
     * @param message the message.
     */
    public final void info(Object message) {
        if (isInfoEnabled())
            infoImpl(message);
    }

    /**
     * Formats and outputs a message with arguments specified
     *
     * @param message the message
     * @param args    the arguments
     */
    public final void info(String message, Object... args) {
        if (isInfoEnabled())
            infoImpl(String.format(message, args));
    }

    /**
     * Implementation of {@link #info(Object) info} method.
     *
     * @param message the message.
     */
    protected abstract void infoImpl(Object message);

    /**
     * Outputs the message and the exception info with the info priority.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    public final void info(Object message, Throwable t) {
        if (isInfoEnabled())
            infoImpl(message, t);
    }

    /**
     * Implementation of {@link #info(Object, Throwable) info} method.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    protected abstract void infoImpl(Object message, Throwable t);

    /**
     * Returns true if this logger is in the warn enabled mode.
     *
     * @return true if this logger is in the warn enabled mode.
     */
    public final boolean isWarnEnabled() {
        defineLevel();
        return level.isWarnEnabled();
    }

    /**
     * Outputs the message with the warning priority.
     *
     * @param message the message.
     */
    public final void warn(Object message) {
        if (isWarnEnabled())
            warnImpl(message);
    }

    /**
     * Formats and outputs a message with arguments specified
     *
     * @param message the message
     * @param args    the arguments
     */
    public final void warn(String message, Object... args) {
        if (isWarnEnabled())
            warnImpl(String.format(message, args));
    }

    /**
     * Implementation of {@link #warn(Object) warn} method.
     *
     * @param message the message.
     */
    protected abstract void warnImpl(Object message);

    /**
     * Outputs the message and the exception info with the warning priority.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    public final void warn(Object message, Throwable t) {
        if (isWarnEnabled())
            warnImpl(message, t);
    }

    /**
     * Implementation of {@link #warn(Object, Throwable) warn} method.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    protected abstract void warnImpl(Object message, Throwable t);

    /**
     * Returns true if this logger is in the error enabled mode.
     *
     * @return true if this logger is in the error enabled mode.
     */
    public final boolean isErrorEnabled() {
        defineLevel();
        return level.isErrorEnabled();
    }

    /**
     * Outputs the message with the error priority.
     *
     * @param message the message.
     */
    public final void error(Object message) {
        if (isErrorEnabled())
            errorImpl(message);
    }

    /**
     * Formats and outputs an error message with arguments specified
     *
     * @param message the error message
     * @param args    the arguments
     */
    public final void error(String message, Object... args) {
        if (isErrorEnabled())
            errorImpl(String.format(message, args));
    }

    /**
     * Implementation of {@link #error(Object) error} method.
     *
     * @param message the message.
     */
    protected abstract void errorImpl(Object message);

    /**
     * Outputs the message and the exception info with the error priority.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    public final void error(Object message, Throwable t) {
        if (isErrorEnabled())
            errorImpl(message, t);
    }

    /**
     * Implementation of {@link #error(Object, Throwable) error} method.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    protected abstract void errorImpl(Object message, Throwable t);

    /**
     * Returns true if this logger is in the fatal enabled mode.
     *
     * @return true if this logger is in the fatal enabled mode.
     */
    public final boolean isFatalEnabled() {
        defineLevel();
        return level.isFatalEnabled();
    }

    /**
     * Outputs the message with the fatal error priority.
     *
     * @param message the message.
     */
    public final void fatal(Object message) {
        if (isFatalEnabled())
            fatalImpl(message);
    }

    /**
     * Implementation of {@link #fatal(Object) fatal} method.
     *
     * @param message the message.
     */
    protected abstract void fatalImpl(Object message);

    /**
     * Outputs the message and the exception info with the fatal error priority.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    public final void fatal(Object message, Throwable t) {
        if (isFatalEnabled())
            fatalImpl(message, t);
    }

    /**
     * Implementation of {@link #fatal(Object, Throwable) fatal} method.
     *
     * @param message the message.
     * @param t       the Throwable object.
     */
    protected abstract void fatalImpl(Object message, Throwable t);

    /**
     * Returns array of strings containing stack details: 0 - class name,
     * 1 - method and thread names. Note, the array will contain null values if
     * the data cannot be retreived.
     *
     * @return array of strings containing stack details.
     */
    protected static String[] getStackDetails() {
        final String[] res = new String[2];
        boolean in = true;
        final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            final String className = frame.getClassName();
            if (in && className.equals(Logger.class.getName()))
                in = false;
            if (!in && !className.startsWith(Logger.class.getName())) {
                res[0] = frame.getClassName();
                final StringBuilder sb = new StringBuilder(1024);
                sb.append(frame.getMethodName());
// uncomment this if line numbers are needed
//                if (frame.getLineNumber() > 0)
//                    sb.append(" (").append(frame.getLineNumber()).append(')');
                sb.append(" [").append(Thread.currentThread().getName()).append(']');
                res[1] = sb.toString();
                break;
            }
        }
        return res;
    }

    /**
     * Standard Logger wrapper.
     *
     * @author <a href='mailto:azhukov@wiley.ru'>Alexey Zhukov</a>
     */
    private static final class StdLogger extends Logger {
        private final java.util.logging.Logger log;

        private StdLogger(String name) {
            super(name);
            log = java.util.logging.Logger.getLogger(name);
        }

        private java.util.logging.Level getNativeLevel(Level level) {
            switch (level) {
                case TRACE:
                    return java.util.logging.Level.FINEST;

                case DEBUG:
                    return java.util.logging.Level.FINE;

                case INFO:
                    return java.util.logging.Level.INFO;

                case WARN:
                    return java.util.logging.Level.WARNING;

                case ERROR:
                    return java.util.logging.Level.SEVERE;

                case FATAL:
                    return java.util.logging.Level.SEVERE;

                case OFF:
                    return java.util.logging.Level.OFF;

                default:
                    throw new IllegalArgumentException("Unknown log level: " +
                            level);
            }
        }

        protected void setLevelImpl(Level level) {
            log.setLevel(getNativeLevel(level));
        }

        protected boolean isEnabled(Level level) {
            return log.isLoggable(getNativeLevel(level));
        }

        protected void traceImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.FINEST, details[0], details[1],
                        message.toString());
            } else
                log.logp(java.util.logging.Level.FINEST, null, null,
                        message.toString());
        }

        protected void traceImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.FINEST, details[0], details[1],
                        message.toString(), t);
            } else
                log.logp(java.util.logging.Level.FINEST, null, null,
                        message.toString(), t);
        }

        protected void debugImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.FINE, details[0], details[1],
                        message.toString());
            } else
                log.logp(java.util.logging.Level.FINE, null, null,
                        message.toString());
        }

        protected void debugImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.FINE, details[0], details[1],
                        message.toString(), t);
            } else
                log.logp(java.util.logging.Level.FINE, null, null,
                        message.toString(), t);
        }

        protected void infoImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.INFO, details[0], details[1],
                        message.toString());
            } else
                log.logp(java.util.logging.Level.INFO, null, null,
                        message.toString());
        }

        protected void infoImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.INFO, details[0], details[1],
                        message.toString(), t);
            } else
                log.logp(java.util.logging.Level.INFO, null, null,
                        message.toString(), t);
        }

        protected void warnImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.WARNING, details[0], details[1],
                        message.toString());
            } else
                log.logp(java.util.logging.Level.WARNING, null, null,
                        message.toString());
        }

        protected void warnImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.WARNING, details[0], details[1],
                        message.toString(), t);
            } else
                log.logp(java.util.logging.Level.WARNING, null, null,
                        message.toString(), t);
        }

        protected void errorImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.SEVERE, details[0], details[1],
                        message.toString());
            } else
                log.logp(java.util.logging.Level.SEVERE, null, null,
                        message.toString());
        }

        protected void errorImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.SEVERE, details[0], details[1],
                        message.toString(), t);
            } else
                log.logp(java.util.logging.Level.SEVERE, null, null,
                        message.toString(), t);
        }

        protected void fatalImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.SEVERE, details[0], details[1],
                        message.toString());
            } else
                log.logp(java.util.logging.Level.SEVERE, null, null,
                        message.toString());
        }

        protected void fatalImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                log.logp(java.util.logging.Level.SEVERE, details[0], details[1],
                        message.toString(), t);
            } else
                log.logp(java.util.logging.Level.SEVERE, null, null,
                        message.toString(), t);
        }
    }

    /**
     * Log4J Logger wrapper.
     *
     * @author <a href='mailto:azhukov@wiley.ru'>Alexey Zhukov</a>
     */
    private static final class Log4JLogger extends Logger {
        private static boolean initialized;

        private static Class logClass;

        private static Method M_getLogger;

        private static Method M_setLevel;

        private static Object LVL_TRACE;
        private static Object LVL_DEBUG;
        private static Object LVL_INFO;
        private static Object LVL_WARN;
        private static Object LVL_ERROR;
        private static Object LVL_FATAL;
        private static Object LVL_OFF;

        private static Method M_isEnabledFor;

        private static Method M_log;
        private static Method M_log2;

        private Object logObj;

        private static void init() {
            try {
                logClass = Class.forName("org.apache.log4j.Logger");

                M_getLogger = logClass.getMethod("getLogger", new Class[]{String.class});

                final Class lvlClass = Class.forName("org.apache.log4j.Level");
                M_setLevel = logClass.getMethod("setLevel", new Class[]{lvlClass});

                LVL_TRACE = lvlClass.getField("TRACE").get(null);
                LVL_DEBUG = lvlClass.getField("DEBUG").get(null);
                LVL_INFO = lvlClass.getField("INFO").get(null);
                LVL_WARN = lvlClass.getField("WARN").get(null);
                LVL_ERROR = lvlClass.getField("ERROR").get(null);
                LVL_FATAL = lvlClass.getField("FATAL").get(null);
                LVL_OFF = lvlClass.getField("OFF").get(null);

                final Class prClass = Class.forName("org.apache.log4j.Priority");
                M_isEnabledFor = logClass.getMethod("isEnabledFor", new Class[]{prClass});

                M_log = logClass.getMethod("log", new Class[]{prClass, Object.class});
                M_log2 = logClass.getMethod("log", new Class[]{prClass, Object.class, Throwable.class});

                initialized = true;
            } catch (Exception e) {
                throw new RuntimeException("Cannot initialize logger", e);
            }
        }

        private Log4JLogger(String name) {
            super(name);

            if (!initialized) {
                synchronized (Log4JLogger.class) {
                    if (!initialized)
                        init();
                }
            }

            try {
                logObj = M_getLogger.invoke(logClass, name);
            } catch (Exception e) {
                throw new RuntimeException("Cannot initialize logger", e);
            }
        }

        private void doSetLevel(Object level) {
            try {
                M_setLevel.invoke(logObj, level);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private boolean doIsEnabledFor(Object level) {
            try {
                return (Boolean) M_isEnabledFor.invoke(logObj, level);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void doLog(Object level, Object msg) {
            try {
                M_log.invoke(logObj, level, msg);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void doLog(Object level, Object msg, Throwable t) {
            try {
                M_log2.invoke(logObj, level, msg, t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private Object getNativeLevel(Level level) {
            switch (level) {
                case TRACE:
                    return LVL_TRACE;

                case DEBUG:
                    return LVL_DEBUG;

                case INFO:
                    return LVL_INFO;

                case WARN:
                    return LVL_WARN;

                case ERROR:
                    return LVL_ERROR;

                case FATAL:
                    return LVL_FATAL;

                case OFF:
                    return LVL_OFF;

                default:
                    throw new IllegalArgumentException("Unknown log level: " +
                            level);
            }
        }

        protected void setLevelImpl(Level level) {
            doSetLevel(getNativeLevel(level));
        }

        protected boolean isEnabled(Level level) {
            return doIsEnabledFor(getNativeLevel(level));
        }

        protected void traceImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_TRACE, sb.toString());
                } else
                    doLog(LVL_TRACE, message);
            } else
                doLog(LVL_TRACE, message);
        }

        protected void traceImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_TRACE, sb.toString(), t);
                } else
                    doLog(LVL_TRACE, message, t);
            } else
                doLog(LVL_TRACE, message, t);
        }

        protected void debugImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_DEBUG, sb.toString());
                } else
                    doLog(LVL_DEBUG, message);
            } else
                doLog(LVL_DEBUG, message);
        }

        protected void debugImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_DEBUG, sb.toString(), t);
                } else
                    doLog(LVL_DEBUG, message, t);
            } else
                doLog(LVL_DEBUG, message, t);
        }

        protected void infoImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_INFO, sb.toString());
                } else
                    doLog(LVL_INFO, message);
            } else
                doLog(LVL_INFO, message);
        }

        protected void infoImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_INFO, sb.toString(), t);
                } else
                    doLog(LVL_INFO, message, t);
            } else
                doLog(LVL_INFO, message, t);
        }

        protected void warnImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_WARN, sb.toString());
                } else
                    doLog(LVL_WARN, message);
            } else
                doLog(LVL_WARN, message);
        }

        protected void warnImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_WARN, sb.toString(), t);
                } else
                    doLog(LVL_WARN, message, t);
            } else
                doLog(LVL_WARN, message, t);
        }

        protected void errorImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_ERROR, sb.toString());
                } else
                    doLog(LVL_ERROR, message);
            } else
                doLog(LVL_ERROR, message);
        }

        protected void errorImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_ERROR, sb.toString(), t);
                } else
                    doLog(LVL_ERROR, message, t);
            } else
                doLog(LVL_ERROR, message, t);
        }

        protected void fatalImpl(Object message) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_FATAL, sb.toString());
                } else
                    doLog(LVL_FATAL, message);
            } else
                doLog(LVL_FATAL, message);
        }

        protected void fatalImpl(Object message, Throwable t) {
            if (showCallDetails) {
                final String[] details = getStackDetails();
                if (details[0] != null) {
                    final StringBuilder sb = new StringBuilder(256);
                    if (!details[0].equals(name))
                        sb.append(details[0]).append(' ');
                    sb.append(details[1]).append(": ");
                    sb.append(message);
                    doLog(LVL_FATAL, sb.toString(), t);
                } else
                    doLog(LVL_FATAL, message, t);
            } else
                doLog(LVL_FATAL, message, t);
        }
    }
}
