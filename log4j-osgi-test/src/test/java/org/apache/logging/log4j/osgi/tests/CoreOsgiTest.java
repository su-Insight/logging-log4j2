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
package org.apache.logging.log4j.osgi.tests;

import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.linkBundle;
import static org.ops4j.pax.exam.CoreOptions.options;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.Constants;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class CoreOsgiTest {

    @org.ops4j.pax.exam.Configuration
    public Option[] config() {
        return options(
                linkBundle("org.apache.logging.log4j.api"),
                linkBundle("org.apache.logging.log4j.plugins"),
                linkBundle("org.apache.logging.log4j.kit"),
                linkBundle("org.apache.logging.log4j.core"),
                linkBundle("org.apache.logging.log4j.1.2.api").start(false),
                // required by Pax Exam's logging
                linkBundle("org.objectweb.asm"),
                linkBundle("org.objectweb.asm.commons"),
                linkBundle("org.objectweb.asm.tree"),
                linkBundle("org.objectweb.asm.tree.analysis"),
                linkBundle("org.objectweb.asm.util"),
                linkBundle("org.apache.aries.spifly.dynamic.bundle").startLevel(2),
                linkBundle("slf4j.api"),
                linkBundle("ch.qos.logback.classic"),
                linkBundle("ch.qos.logback.core"),
                junitBundles());
    }

    @ProbeBuilder
    public TestProbeBuilder probeConfiguration(final TestProbeBuilder builder) {
        // Register `Log4jPlugins` manually with the Service Loader Mediator
        builder.setHeader(
                Constants.PROVIDE_CAPABILITY,
                "osgi.serviceloader;osgi.serviceloader=\"org.apache.logging.log4j.plugins.model.PluginService\";"
                        + "register:=\"org.apache.logging.log4j.osgi.tests.plugins.Log4jPlugins\"");
        builder.setHeader(
                Constants.REQUIRE_CAPABILITY,
                "osgi.extender;filter:=\"(&(osgi.extender=osgi.serviceloader.registrar)(version>=1.0)(!(version>=2.0)))\"");
        return builder;
    }

    @Test
    public void testSimpleLogInAnOsgiContext() {
        final CustomConfiguration custom = getConfiguration();
        // Logging
        final Logger logger = LogManager.getLogger(getClass());
        logger.info("Hello OSGI from Log4j2!");
        assertEquals(1, custom.getEvents().size());
        final LogEvent event = custom.getEvents().get(0);
        assertEquals("Hello OSGI from Log4j2!", event.getMessage().getFormattedMessage());
        assertEquals(Level.INFO, event.getLevel());
        custom.clearEvents();
    }

    private static CustomConfiguration getConfiguration() {
        final LoggerContextFactory factory = LogManager.getFactory();
        assertEquals(Log4jContextFactory.class, factory.getClass());
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        assertEquals(CustomConfiguration.class, config.getClass());
        return (CustomConfiguration) config;
    }
}
