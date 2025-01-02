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
package org.apache.logging.log4j.tojul;

import aQute.bnd.annotation.Resolution;
import aQute.bnd.annotation.spi.ServiceProvider;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.apache.logging.log4j.spi.Provider;

/**
 * Bind the Log4j API to JUL.
 *
 * @author <a href="http://www.vorburger.ch">Michael Vorburger.ch</a> for Google
 */
@ServiceProvider(value = Provider.class, resolution = Resolution.OPTIONAL)
public class JULProvider extends Provider {
    private static final LoggerContextFactory CONTEXT_FACTORY = new JULLoggerContextFactory();

    public JULProvider() {
        super(20, CURRENT_VERSION);
    }

    @Override
    public LoggerContextFactory getLoggerContextFactory() {
        return CONTEXT_FACTORY;
    }

    @Override
    public String getThreadContextMap() {
        // JUL does not provide an MDC implementation
        return NO_OP_CONTEXT_MAP;
    }
}
