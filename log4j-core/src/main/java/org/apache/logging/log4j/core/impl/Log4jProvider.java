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
package org.apache.logging.log4j.core.impl;

import aQute.bnd.annotation.Resolution;
import aQute.bnd.annotation.spi.ServiceConsumer;
import aQute.bnd.annotation.spi.ServiceProvider;
import java.util.ServiceLoader;
import org.apache.logging.log4j.core.impl.internal.QueuedScopedContextProvider;
import org.apache.logging.log4j.spi.Provider;
import org.apache.logging.log4j.spi.ScopedContextProvider;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.ServiceLoaderUtil;

/**
 * Binding for the Log4j API.
 */
@ServiceProvider(value = Provider.class, resolution = Resolution.OPTIONAL)
@ServiceConsumer(value = ScopedContextProvider.class, resolution = Resolution.OPTIONAL)
public class Log4jProvider extends Provider {
    public Log4jProvider() {
        super(10, CURRENT_VERSION, Log4jContextFactory.class);
    }

    @Override
    public ScopedContextProvider getScopedContextProvider() {
        return ServiceLoaderUtil.safeStream(
                        ScopedContextProvider.class,
                        ServiceLoader.load(ScopedContextProvider.class),
                        StatusLogger.getLogger())
                .findFirst()
                .orElse(QueuedScopedContextProvider.INSTANCE);
    }
}
