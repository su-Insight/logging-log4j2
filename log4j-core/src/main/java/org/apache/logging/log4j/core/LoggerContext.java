/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.core;

import static org.apache.logging.log4j.core.util.ShutdownCallbackRegistry.SHUTDOWN_HOOK_MARKER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.LoggerContextAwarePostProcessor;
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.apache.logging.log4j.core.config.Reconfigurable;
import org.apache.logging.log4j.core.impl.Log4jPropertyKey;
import org.apache.logging.log4j.core.util.Cancellable;
import org.apache.logging.log4j.core.util.Constants;
import org.apache.logging.log4j.core.util.ExecutorServices;
import org.apache.logging.log4j.core.util.NetUtils;
import org.apache.logging.log4j.core.util.ShutdownCallbackRegistry;
import org.apache.logging.log4j.message.FlowMessageFactory;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.plugins.di.ConfigurableInstanceFactory;
import org.apache.logging.log4j.plugins.di.DI;
import org.apache.logging.log4j.plugins.di.InstanceFactory;
import org.apache.logging.log4j.plugins.di.Key;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.apache.logging.log4j.spi.LoggerContextShutdownAware;
import org.apache.logging.log4j.spi.LoggerContextShutdownEnabled;
import org.apache.logging.log4j.spi.LoggerRegistry;
import org.apache.logging.log4j.spi.LoggingSystem;
import org.apache.logging.log4j.spi.Terminable;
import org.apache.logging.log4j.spi.recycler.RecyclerFactory;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Lazy;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.PropertyEnvironment;

/**
 * The LoggerContext is the anchor for the logging system. It maintains a list of all the loggers requested by
 * applications and a reference to the Configuration. The Configuration will contain the configured loggers, appenders,
 * filters, etc. and will be atomically updated whenever a reconfigure occurs.
 */
public class LoggerContext extends AbstractLifeCycle
        implements org.apache.logging.log4j.spi.LoggerContext,
                AutoCloseable,
                Terminable,
                Consumer<Reconfigurable>,
                LoggerContextShutdownEnabled {

    public static final Key<LoggerContext> KEY = Key.forClass(LoggerContext.class);

    private final LoggerRegistry<Logger> loggerRegistry = new LoggerRegistry<>();
    private final List<Consumer<Configuration>> configurationStartedListeners = new ArrayList<>();
    private final List<Consumer<Configuration>> configurationStoppedListeners = new ArrayList<>();
    private final Lazy<List<LoggerContextShutdownAware>> listeners = Lazy.relaxed(CopyOnWriteArrayList::new);
    private final ConfigurableInstanceFactory instanceFactory;
    private PropertiesUtil properties;

    /**
     * The Configuration is volatile to guarantee that initialization of the Configuration has completed before the
     * reference is updated.
     */
    private volatile Configuration configuration = new DefaultConfiguration();

    private final Configuration nullConfiguration;
    private static final String EXTERNAL_CONTEXT_KEY = "__EXTERNAL_CONTEXT_KEY__";
    private final ConcurrentMap<String, Object> externalMap = new ConcurrentHashMap<>();
    private String contextName;
    private volatile URI configLocation;
    private Cancellable shutdownCallback;

    private final Lock configLock = new ReentrantLock();

    /**
     * Constructor used to create an InternalLoggerContext.
     */
    protected LoggerContext() {
        setStarted();
        instanceFactory = null;
        this.nullConfiguration = null;
    }

    /**
     * Constructor taking only a name.
     *
     * @param name The context name.
     */
    public LoggerContext(final String name) {
        this(name, null, (URI) null);
    }

    /**
     * Constructor taking a name and a reference to an external context.
     *
     * @param name The context name.
     * @param externalContext The external context.
     */
    public LoggerContext(final String name, final Object externalContext) {
        this(name, externalContext, (URI) null);
    }

    /**
     * Constructor taking a name, external context and a configuration URI.
     *
     * @param name The context name.
     * @param externalContext The external context.
     * @param configLocn The location of the configuration as a URI.
     */
    public LoggerContext(final String name, final Object externalContext, final URI configLocn) {
        this(name, externalContext, configLocn, DI.createInitializedFactory());
    }

    /**
     * Constructs a LoggerContext with a name, external context, configuration URI, and a ConfigurableInstanceFactory.
     *
     * @param name context name
     * @param externalContext external context or null
     * @param configLocn location of configuration as a URI
     * @param instanceFactory initialized ConfigurableInstanceFactory
     */
    public LoggerContext(
            final String name,
            final Object externalContext,
            final URI configLocn,
            final ConfigurableInstanceFactory instanceFactory) {
        this.contextName = name;
        if (externalContext != null) {
            externalMap.put(EXTERNAL_CONTEXT_KEY, externalContext);
        }
        this.configLocation = configLocn;
        this.instanceFactory = instanceFactory.newChildInstanceFactory();
        initializeInstanceFactory();
        this.nullConfiguration = new NullConfiguration(this);
    }

    /**
     * Constructor taking a name external context and a configuration location String. The location must be resolvable
     * to a File.
     *
     * @param name The configuration location.
     * @param externalContext The external context.
     * @param configLocn The configuration location.
     */
    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "The configLocn comes from a secure source (Log4j properties)")
    public LoggerContext(final String name, final Object externalContext, final String configLocn) {
        this(name, externalContext, configLocn, DI.createInitializedFactory());
    }

    /**
     * Constructs a LoggerContext with a name, external context, configuration location string, and an instance factory.
     * The location must be resolvable to a File.
     *
     * @param name context name
     * @param externalContext external context or null
     * @param configLocn configuration location
     * @param instanceFactory initialized ConfigurableInstanceFactory
     */
    public LoggerContext(
            final String name,
            final Object externalContext,
            final String configLocn,
            final ConfigurableInstanceFactory instanceFactory) {
        this.contextName = name;
        if (externalContext != null) {
            externalMap.put(EXTERNAL_CONTEXT_KEY, externalContext);
        }
        if (configLocn != null) {
            URI uri;
            try {
                uri = new File(configLocn).toURI();
            } catch (final Exception ex) {
                uri = null;
            }
            configLocation = uri;
        } else {
            configLocation = null;
        }
        this.instanceFactory = instanceFactory.newChildInstanceFactory();
        initializeInstanceFactory();
        this.nullConfiguration = new NullConfiguration(this);
    }

    /**
     * Checks that the message factory a logger was created with is the same as the given messageFactory. If they are
     * different log a warning to the {@linkplain StatusLogger}. A null MessageFactory translates to the default
     * MessageFactory {@link LoggingSystem#getMessageFactory()}.
     *
     * @param logger The logger to check
     * @param messageFactory The message factory to check.
     */
    public static void checkMessageFactory(final ExtendedLogger logger, final MessageFactory messageFactory) {
        final String name = logger.getName();
        final MessageFactory loggerMessageFactory = logger.getMessageFactory();
        final MessageFactory currentMessageFactory = LoggingSystem.getMessageFactory();
        if (messageFactory != null && !loggerMessageFactory.equals(messageFactory)) {
            StatusLogger.getLogger()
                    .warn(
                            "The Logger {} was created with the message factory {} and is now requested with the "
                                    + "message factory {}, which may create log events with unexpected formatting.",
                            name,
                            loggerMessageFactory,
                            messageFactory);
        } else if (messageFactory == null && loggerMessageFactory != currentMessageFactory) {
            StatusLogger.getLogger()
                    .warn(
                            "The Logger {} was created with the message factory {} and is now requested with a null "
                                    + "message factory (defaults to {}), which may create log events with unexpected "
                                    + "formatting.",
                            name,
                            loggerMessageFactory,
                            currentMessageFactory.getClass().getName());
        }
    }

    private void initializeInstanceFactory() {
        final Lazy<LoggerContext> ref = Lazy.weak(this);
        instanceFactory.registerBinding(KEY, ref);
        instanceFactory.registerInstancePostProcessor(new LoggerContextAwarePostProcessor(this));
    }

    public void setProperties(final PropertiesUtil properties) {
        this.properties = properties;
    }

    @Override
    public PropertyEnvironment getProperties() {
        return properties;
    }

    @Override
    public void addShutdownListener(final LoggerContextShutdownAware listener) {
        listeners.get().add(listener);
    }

    @Override
    public List<LoggerContextShutdownAware> getListeners() {
        return listeners.get();
    }

    /**
     * Returns the current LoggerContext.
     * <p>
     * Avoids the type cast for:
     * </p>
     *
     * <pre>
     * (LoggerContext) LogManager.getContext();
     * </pre>
     *
     * <p>
     * WARNING - The LoggerContext returned by this method may not be the LoggerContext used to create a Logger for the
     * calling class.
     * </p>
     *
     * @return The current LoggerContext.
     * @see LogManager#getContext()
     */
    public static LoggerContext getContext() {
        final org.apache.logging.log4j.spi.LoggerContext context = LogManager.getContext();
        if (context instanceof final LoggerContext ctx) {
            return ctx;
        }
        throw new IllegalStateException(
                "Expected instance of " + LoggerContext.class + " but got " + context.getClass());
    }

    /**
     * Returns a LoggerContext.
     * <p>
     * Avoids the type cast for:
     * </p>
     *
     * <pre>
     * (LoggerContext) LogManager.getContext(currentContext);
     * </pre>
     *
     * @param currentContext if false the LoggerContext appropriate for the caller of this method is returned. For
     *            example, in a web application if the caller is a class in WEB-INF/lib then one LoggerContext may be
     *            returned and if the caller is a class in the container's classpath then a different LoggerContext may
     *            be returned. If true then only a single LoggerContext will be returned.
     * @return a LoggerContext.
     * @see LogManager#getContext(boolean)
     */
    public static LoggerContext getContext(final boolean currentContext) {
        final org.apache.logging.log4j.spi.LoggerContext context = LogManager.getContext(currentContext);
        if (context instanceof final LoggerContext ctx) {
            return ctx;
        }
        throw new IllegalStateException(
                "Expected instance of " + LoggerContext.class + " but got " + context.getClass());
    }

    /**
     * Returns a LoggerContext.
     * <p>
     * Avoids the type cast for:
     * </p>
     *
     * <pre>
     * (LoggerContext) LogManager.getContext(loader, currentContext, configLocation);
     * </pre>
     *
     * @param loader The ClassLoader for the context. If null the context will attempt to determine the appropriate
     *            ClassLoader.
     * @param currentContext if false the LoggerContext appropriate for the caller of this method is returned. For
     *            example, in a web application if the caller is a class in WEB-INF/lib then one LoggerContext may be
     *            returned and if the caller is a class in the container's classpath then a different LoggerContext may
     *            be returned. If true then only a single LoggerContext will be returned.
     * @param configLocation The URI for the configuration to use.
     * @return a LoggerContext.
     * @see LogManager#getContext(ClassLoader, boolean, URI)
     */
    public static LoggerContext getContext(
            final ClassLoader loader, final boolean currentContext, final URI configLocation) {
        final org.apache.logging.log4j.spi.LoggerContext context =
                LogManager.getContext(loader, currentContext, configLocation);
        if (context instanceof final LoggerContext ctx) {
            return ctx;
        }
        throw new IllegalStateException(
                "Expected instance of " + LoggerContext.class + " but got " + context.getClass());
    }

    @Override
    public void start() {
        LOGGER.debug("Starting {}...", this);
        if (getProperties().getBooleanProperty(Log4jPropertyKey.STACKTRACE_ON_START, false)) {
            LOGGER.debug(
                    "Stack trace to locate invoker",
                    new Exception("Not a real error, showing stack trace to locate invoker"));
        }
        if (configLock.tryLock()) {
            try {
                if (this.isInitialized() || this.isStopped()) {
                    this.setStarting();
                    reconfigure();
                    if (this.configuration.isShutdownHookEnabled()) {
                        setUpShutdownHook();
                    }
                    this.setStarted();
                }
            } finally {
                configLock.unlock();
            }
        }
        LOGGER.debug("{} started OK.", this);
    }

    /**
     * Starts with a specific configuration.
     *
     * @param config The new Configuration.
     */
    public void start(final Configuration config) {
        LOGGER.debug("Starting {} with configuration {}...", this, config);
        if (configLock.tryLock()) {
            try {
                if (this.isInitialized() || this.isStopped()) {
                    if (config.isShutdownHookEnabled()) {
                        setUpShutdownHook();
                    }
                    this.setStarted();
                }
            } finally {
                configLock.unlock();
            }
        }
        setConfiguration(config);
        LOGGER.debug("{} started OK with configuration {}.", this, config);
    }

    private void setUpShutdownHook() {
        if (shutdownCallback == null) {
            final LoggerContextFactory factory = LogManager.getFactory();
            if (factory instanceof ShutdownCallbackRegistry) {
                LOGGER.debug(SHUTDOWN_HOOK_MARKER, "Shutdown hook enabled. Registering a new one.");
                // LOG4J2-1642 preload ExecutorServices as it is used in shutdown hook
                ExecutorServices.ensureInitialized();
                try {
                    final long shutdownTimeoutMillis = this.configuration.getShutdownTimeoutMillis();
                    this.shutdownCallback = ((ShutdownCallbackRegistry) factory).addShutdownCallback(new Runnable() {
                        @Override
                        public void run() {
                            final LoggerContext context = LoggerContext.this;
                            LOGGER.debug(SHUTDOWN_HOOK_MARKER, "Stopping {}", context);
                            context.stop(shutdownTimeoutMillis, TimeUnit.MILLISECONDS);
                        }

                        @Override
                        public String toString() {
                            return "Shutdown callback for LoggerContext[name=" + LoggerContext.this.getName() + ']';
                        }
                    });
                } catch (final IllegalStateException e) {
                    throw new IllegalStateException(
                            "Unable to register Log4j shutdown hook because JVM is shutting down.", e);
                } catch (final SecurityException e) {
                    LOGGER.error(
                            SHUTDOWN_HOOK_MARKER, "Unable to register shutdown hook due to security restrictions", e);
                }
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public void terminate() {
        stop();
    }

    /**
     * Blocks until all Log4j tasks have completed execution after a shutdown request and all appenders have shut down,
     * or the timeout occurs, or the current thread is interrupted, whichever happens first.
     * <p>
     * Not all appenders will honor this, it is a hint and not an absolute guarantee that the this method not block longer.
     * Setting timeout too low increase the risk of losing outstanding log events not yet written to the final
     * destination.
     * <p>
     * Log4j can start threads to perform certain actions like file rollovers, calling this method with a positive timeout will
     * block until the rollover thread is done.
     *
     * @param timeout the maximum time to wait, or 0 which mean that each apppender uses its default timeout, and don't wait for background
     * tasks
     * @param timeUnit
     *            the time unit of the timeout argument
     * @return {@code true} if the logger context terminated and {@code false} if the timeout elapsed before
     *         termination.
     * @since 2.7
     */
    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        LOGGER.debug("Stopping {}...", this);
        configLock.lock();
        try {
            if (this.isStopped()) {
                return true;
            }

            this.setStopping();

            if (shutdownCallback != null) {
                shutdownCallback.cancel();
                shutdownCallback = null;
            }
            final Configuration prev = configuration;
            configuration = nullConfiguration;
            updateLoggers();
            prev.stop(timeout, timeUnit);
            externalMap.clear();
            LogManager.getFactory().removeContext(this);
        } finally {
            configLock.unlock();
            this.setStopped();
        }
        if (listeners.isInitialized()) {
            listeners.get().forEach(listener -> {
                try {
                    listener.contextShutdown(this);
                } catch (final RuntimeException ignored) {
                }
            });
        }
        LOGGER.debug("Stopped {} with status {}", this, true);
        return true;
    }

    /**
     * Gets the name.
     *
     * @return the name.
     */
    @Override
    public String getName() {
        return contextName;
    }

    /**
     * Gets the root logger.
     *
     * @return the root logger.
     */
    public Logger getRootLogger() {
        return getLogger(LogManager.ROOT_LOGGER_NAME);
    }

    /**
     * Sets the name.
     *
     * @param name the new LoggerContext name
     * @throws NullPointerException if the specified name is {@code null}
     */
    public void setName(final String name) {
        contextName = Objects.requireNonNull(name);
    }

    @Override
    public Object getObject(final String key) {
        return externalMap.get(key);
    }

    @Override
    public Object putObject(final String key, final Object value) {
        return externalMap.put(key, value);
    }

    @Override
    public Object putObjectIfAbsent(final String key, final Object value) {
        return externalMap.putIfAbsent(key, value);
    }

    @Override
    public Object removeObject(final String key) {
        return externalMap.remove(key);
    }

    @Override
    public boolean removeObject(final String key, final Object value) {
        return externalMap.remove(key, value);
    }

    /**
     * Sets the external context.
     *
     * @param context The external context.
     */
    public void setExternalContext(final Object context) {
        if (context != null) {
            this.externalMap.put(EXTERNAL_CONTEXT_KEY, context);
        } else {
            this.externalMap.remove(EXTERNAL_CONTEXT_KEY);
        }
    }

    /**
     * Returns the external context.
     *
     * @return The external context.
     */
    @Override
    public Object getExternalContext() {
        return this.externalMap.get(EXTERNAL_CONTEXT_KEY);
    }

    /**
     * Gets a Logger from the Context.
     *
     * @param name The name of the Logger to return.
     * @return The Logger.
     */
    @Override
    public Logger getLogger(final String name) {
        return getLogger(name, null);
    }

    /**
     * Gets a collection of the current loggers.
     * <p>
     * Whether this collection is a copy of the underlying collection or not is undefined. Therefore, modify this
     * collection at your own risk.
     * </p>
     *
     * @return a collection of the current loggers.
     */
    public Collection<Logger> getLoggers() {
        return loggerRegistry.getLoggers();
    }

    /**
     * Obtains a Logger from the Context.
     *
     * @param name The name of the Logger to return.
     * @param messageFactory The message factory is used only when creating a logger, subsequent use does not change the
     *            logger but will log a warning if mismatched.
     * @return The Logger.
     */
    @Override
    public Logger getLogger(final String name, final MessageFactory messageFactory) {
        // Note: This is the only method where we add entries to the 'loggerRegistry' ivar.
        Logger logger = loggerRegistry.getLogger(name, messageFactory);
        if (logger != null) {
            checkMessageFactory(logger, messageFactory);
            return logger;
        }
        final MessageFactory actualMessageFactory =
                messageFactory != null ? messageFactory : instanceFactory.getInstance(MessageFactory.class);
        final FlowMessageFactory flowMessageFactory = instanceFactory.getInstance(FlowMessageFactory.class);
        final RecyclerFactory recyclerFactory = instanceFactory.getInstance(RecyclerFactory.class);
        final org.apache.logging.log4j.Logger statusLogger =
                instanceFactory.getInstance(Constants.DEFAULT_STATUS_LOGGER_KEY);
        logger = newInstance(this, name, actualMessageFactory, flowMessageFactory, recyclerFactory, statusLogger);
        loggerRegistry.putIfAbsent(name, actualMessageFactory, logger);
        return loggerRegistry.getLogger(name, actualMessageFactory);
    }

    /**
     * Gets the LoggerRegistry.
     *
     * @return the LoggerRegistry.
     * @since 2.17.2
     */
    @Override
    public LoggerRegistry<Logger> getLoggerRegistry() {
        return loggerRegistry;
    }

    /**
     * Gets the InstanceFactory.
     *
     * @return the InstanceFactory
     * @since 3.0.0
     */
    public InstanceFactory getInstanceFactory() {
        return instanceFactory;
    }

    /**
     * Creates a child instance factory. This allows unrelated Configurations to register their own factories.
     *
     * @return new ConfigurableInstanceFactory
     * @since 3.0.0
     */
    public ConfigurableInstanceFactory newChildInstanceFactory() {
        return instanceFactory.newChildInstanceFactory();
    }

    /**
     * Determines if the specified Logger exists.
     *
     * @param name The Logger name to search for.
     * @return True if the Logger exists, false otherwise.
     */
    @Override
    public boolean hasLogger(final String name) {
        return loggerRegistry.hasLogger(name);
    }

    /**
     * Determines if the specified Logger exists.
     *
     * @param name The Logger name to search for.
     * @return True if the Logger exists, false otherwise.
     */
    @Override
    public boolean hasLogger(final String name, final MessageFactory messageFactory) {
        return loggerRegistry.hasLogger(name, messageFactory);
    }

    /**
     * Determines if the specified Logger exists.
     *
     * @param name The Logger name to search for.
     * @return True if the Logger exists, false otherwise.
     */
    @Override
    public boolean hasLogger(final String name, final Class<? extends MessageFactory> messageFactoryClass) {
        return loggerRegistry.hasLogger(name, messageFactoryClass);
    }

    /**
     * Returns the current Configuration. The Configuration will be replaced when a reconfigure occurs.
     *
     * @return The current Configuration, never {@code null}, but may be
     * {@link org.apache.logging.log4j.core.config.NullConfiguration}.
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    public Configuration getConfiguration(final ConfigurationSource source) {
        return getConfigurationFactory().getConfiguration(this, source);
    }

    public Configuration getConfiguration(final String name, final URI configLocation) {
        return getConfigurationFactory().getConfiguration(this, name, configLocation);
    }

    public Configuration getConfiguration(final String name, final URI configLocation, final ClassLoader loader) {
        return getConfigurationFactory().getConfiguration(this, name, configLocation, loader);
    }

    private ConfigurationFactory getConfigurationFactory() {
        return instanceFactory.getInstance(ConfigurationFactory.KEY);
    }

    /**
     * Adds a Filter to the Configuration. Filters that are added through the API will be lost when a reconfigure
     * occurs.
     *
     * @param filter The Filter to add.
     */
    public void addFilter(final Filter filter) {
        configuration.addFilter(filter);
    }

    /**
     * Removes a Filter from the current Configuration.
     *
     * @param filter The Filter to remove.
     */
    public void removeFilter(final Filter filter) {
        configuration.removeFilter(filter);
    }

    /**
     * Sets the Configuration to be used.
     *
     * @param config The new Configuration.
     * @return The previous Configuration.
     */
    public Configuration setConfiguration(final Configuration config) {
        if (config == null) {
            LOGGER.error("No configuration found for context '{}'.", contextName);
            // No change, return the current configuration.
            return this.configuration;
        }
        configLock.lock();
        try {
            final Configuration prev = this.configuration;
            config.addListener(this);

            final ConcurrentMap<String, String> map = config.getComponent(Configuration.CONTEXT_PROPERTIES);

            try { // LOG4J2-719 network access may throw android.os.NetworkOnMainThreadException
                // LOG4J2-2808 don't block unless necessary
                map.computeIfAbsent("hostName", s -> NetUtils.getLocalHostname());
            } catch (final Exception ex) {
                LOGGER.debug("Ignoring {}, setting hostName to 'unknown'", ex.toString());
                map.putIfAbsent("hostName", "unknown");
            }
            map.putIfAbsent("contextName", contextName);
            config.start();
            this.configuration = config;
            updateLoggers();
            if (prev != null) {
                prev.removeListener(this);
                prev.stop();
                notifyConfigurationStopped(prev);
            }

            return prev;
        } finally {
            configLock.unlock();
        }
    }

    public void addConfigurationStartedListener(final Consumer<Configuration> listener) {
        configLock.lock();
        try {
            configurationStartedListeners.add(listener);
        } finally {
            configLock.unlock();
        }
    }

    public void removeConfigurationStartedListener(final Consumer<Configuration> listener) {
        configLock.lock();
        try {
            configurationStartedListeners.remove(listener);
        } finally {
            configLock.unlock();
        }
    }

    private void notifyConfigurationStarted(final Configuration configuration) {
        for (final Consumer<Configuration> listener : configurationStartedListeners) {
            try {
                listener.accept(configuration);
            } catch (final Throwable t) {
                LOGGER.error(
                        "Caught exception while notifying listener {} of configuration start {}",
                        listener,
                        configuration,
                        t);
            }
        }
    }

    public void addConfigurationStoppedListener(final Consumer<Configuration> listener) {
        configLock.lock();
        try {
            configurationStoppedListeners.add(listener);
        } finally {
            configLock.unlock();
        }
    }

    public void removeConfigurationStoppedListener(final Consumer<Configuration> listener) {
        configLock.lock();
        try {
            configurationStoppedListeners.remove(listener);
        } finally {
            configLock.unlock();
        }
    }

    private void notifyConfigurationStopped(final Configuration configuration) {
        for (final Consumer<Configuration> listener : configurationStoppedListeners) {
            try {
                listener.accept(configuration);
            } catch (final Throwable t) {
                LOGGER.error(
                        "Caught exception while notifying listener {} of configuration stop {}",
                        listener,
                        configuration,
                        t);
            }
        }
    }

    /**
     * Returns the initial configuration location or {@code null}. The returned value may not be the location of the
     * current configuration. Use {@link #getConfiguration()}.{@link Configuration#getConfigurationSource()
     * getConfigurationSource()}.{@link ConfigurationSource#getLocation() getLocation()} to get the actual source of the
     * current configuration.
     *
     * @return the initial configuration location or {@code null}
     */
    public URI getConfigLocation() {
        return configLocation;
    }

    /**
     * Sets the configLocation to the specified value and reconfigures this context.
     *
     * @param configLocation the location of the new configuration
     */
    public void setConfigLocation(final URI configLocation) {
        this.configLocation = configLocation;
        reconfigure(configLocation);
    }

    /**
     * Reconfigures the context.
     */
    private void reconfigure(final URI configURI) {
        final Object externalContext = externalMap.get(EXTERNAL_CONTEXT_KEY);
        final ClassLoader cl = externalContext instanceof ClassLoader ? (ClassLoader) externalContext : null;
        LOGGER.debug("Reconfiguration started for {} at URI {} with optional ClassLoader: {}", this, configURI, cl);
        boolean setProperties = false;
        if (properties != null && !PropertiesUtil.hasThreadProperties()) {
            PropertiesUtil.setThreadProperties(properties);
            setProperties = true;
        }
        try {
            final Configuration instance = getConfiguration(contextName, configURI, cl);
            if (instance == null) {
                LOGGER.error(
                        "Reconfiguration failed: No configuration found for '{}' at '{}' in '{}'",
                        contextName,
                        configURI,
                        cl);
            } else {
                setConfiguration(instance);
                /*
                 * instance.start(); Configuration old = setConfiguration(instance); updateLoggers(); if (old != null) {
                 * old.stop(); }
                 */
                final String location =
                        configuration == null ? "?" : String.valueOf(configuration.getConfigurationSource());
                LOGGER.debug(
                        "Reconfiguration complete for {} at URI {} with optional ClassLoader: {}", this, location, cl);
            }
        } finally {
            if (setProperties) {
                PropertiesUtil.clearThreadProperties();
            }
        }
    }

    /**
     * Reconfigures the context. Log4j does not remove Loggers during a reconfiguration. Log4j will create new
     * LoggerConfig objects and Log4j will point the Loggers at the new LoggerConfigs. Log4j will free the old
     * LoggerConfig, along with old Appenders and Filters.
     */
    public void reconfigure() {
        reconfigure(configLocation);
    }

    public void reconfigure(final Configuration configuration) {
        setConfiguration(configuration);
        final ConfigurationSource source = configuration.getConfigurationSource();
        if (source != null) {
            final URI uri = source.getURI();
            if (uri != null) {
                configLocation = uri;
            }
        }
    }

    /**
     * Causes all Loggers to be updated against the current Configuration.
     */
    public void updateLoggers() {
        updateLoggers(this.configuration);
    }

    /**
     * Causes all Logger to be updated against the specified Configuration.
     *
     * @param config The Configuration.
     */
    public void updateLoggers(final Configuration config) {
        for (final Logger logger : loggerRegistry.getLoggers()) {
            logger.updateConfiguration(config);
        }
        notifyConfigurationStarted(config);
    }

    /**
     * Causes a reconfiguration to take place when the underlying configuration file changes.
     *
     * @param reconfigurable The Configuration that can be reconfigured.
     */
    @Override
    public void accept(final Reconfigurable reconfigurable) {
        configLock.lock();
        try {
            final long startMillis = System.currentTimeMillis();
            LOGGER.debug("Reconfiguration started for context {} ({})", contextName, this);
            ThreadContext.init();
            final Configuration newConfig = reconfigurable.reconfigure();
            if (newConfig != null) {
                setConfiguration(newConfig);
                LOGGER.debug(
                        "Reconfiguration completed for {} ({}) in {} milliseconds.",
                        contextName,
                        this,
                        System.currentTimeMillis() - startMillis);
            } else {
                LOGGER.debug(
                        "Reconfiguration failed for {} ({}) in {} milliseconds.",
                        contextName,
                        this,
                        System.currentTimeMillis() - startMillis);
            }
        } finally {
            configLock.unlock();
        }
    }

    @Override
    public String toString() {
        return "LoggerContext[" + contextName + "]";
    }

    // LOG4J2-151: changed visibility from private to protected
    protected Logger newInstance(
            final LoggerContext ctx,
            final String name,
            final MessageFactory messageFactory,
            final FlowMessageFactory flowMessageFactory,
            final RecyclerFactory recyclerFactory,
            final org.apache.logging.log4j.Logger statusLogger) {
        return new Logger(ctx, name, messageFactory, flowMessageFactory, recyclerFactory, statusLogger);
    }

    /**
     * If {@code true} loggers will include location by default.
     */
    public boolean includeLocation() {
        return true;
    }
}
