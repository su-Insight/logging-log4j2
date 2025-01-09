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
package org.apache.logging.log4j.async.logger;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.message.FlowMessageFactory;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.plugins.di.ConfigurableInstanceFactory;
import org.apache.logging.log4j.spi.recycler.RecyclerFactory;
import org.apache.logging.log4j.status.StatusLogger;

/**
 * {@code LoggerContext} that creates {@code AsyncLogger} objects.
 */
public class AsyncLoggerContext extends LoggerContext {

    private final AsyncLoggerDisruptor loggerDisruptor;

    public AsyncLoggerContext(final String name) {
        super(name);
        loggerDisruptor = new AsyncLoggerDisruptor(name, this::createAsyncWaitStrategyFactory);
    }

    public AsyncLoggerContext(final String name, final Object externalContext) {
        super(name, externalContext);
        loggerDisruptor = new AsyncLoggerDisruptor(name, this::createAsyncWaitStrategyFactory);
    }

    public AsyncLoggerContext(final String name, final Object externalContext, final URI configLocn) {
        super(name, externalContext, configLocn);
        loggerDisruptor = new AsyncLoggerDisruptor(name, this::createAsyncWaitStrategyFactory);
    }

    public AsyncLoggerContext(
            final String name,
            final Object externalContext,
            final URI configLocn,
            final ConfigurableInstanceFactory instanceFactory) {
        super(name, externalContext, configLocn, instanceFactory);
        loggerDisruptor = new AsyncLoggerDisruptor(name, this::createAsyncWaitStrategyFactory);
    }

    public AsyncLoggerContext(final String name, final Object externalContext, final String configLocn) {
        super(name, externalContext, configLocn);
        loggerDisruptor = new AsyncLoggerDisruptor(name, this::createAsyncWaitStrategyFactory);
    }

    public AsyncLoggerContext(
            final String name,
            final Object externalContext,
            final String configLocn,
            final ConfigurableInstanceFactory instanceFactory) {
        super(name, externalContext, configLocn, instanceFactory);
        loggerDisruptor = new AsyncLoggerDisruptor(name, this::createAsyncWaitStrategyFactory);
    }

    private AsyncWaitStrategyFactory createAsyncWaitStrategyFactory() {
        final DisruptorConfiguration disruptorConfiguration =
                getConfiguration().getExtension(DisruptorConfiguration.class);
        return disruptorConfiguration != null ? disruptorConfiguration.getWaitStrategyFactory() : null;
    }

    @Override
    protected Logger newInstance(
            final LoggerContext ctx,
            final String name,
            final MessageFactory messageFactory,
            final FlowMessageFactory flowMessageFactory,
            final RecyclerFactory recyclerFactory,
            final org.apache.logging.log4j.Logger statusLogger) {
        return new AsyncLogger(
                ctx, name, messageFactory, flowMessageFactory, recyclerFactory, statusLogger, loggerDisruptor);
    }

    @Override
    public void setName(final String name) {
        super.setName("AsyncContext[" + name + "]");
        loggerDisruptor.setContextName(name);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.logging.log4j.core.LoggerContext#start()
     */
    @Override
    public void start() {
        loggerDisruptor.start();
        super.start();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.logging.log4j.core.LoggerContext#start(org.apache.logging.log4j.core.config.Configuration)
     */
    @Override
    public void start(final Configuration config) {
        maybeStartHelper(config);
        super.start(config);
    }

    private void maybeStartHelper(final Configuration config) {
        // If no log4j configuration was found, there are no loggers
        // and there is no point in starting the disruptor (which takes up
        // significant memory and starts a thread).
        if (config instanceof DefaultConfiguration) {
            StatusLogger.getLogger().debug("[{}] Not starting Disruptor for DefaultConfiguration.", getName());
        } else {
            loggerDisruptor.start();
        }
    }

    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        setStopping();
        // first stop Disruptor
        loggerDisruptor.stop(timeout, timeUnit);
        super.stop(timeout, timeUnit);
        return true;
    }

    @Override
    public boolean includeLocation() {
        return false;
    }

    // package-protected for tests
    AsyncLoggerDisruptor getAsyncLoggerDisruptor() {
        return loggerDisruptor;
    }
}
