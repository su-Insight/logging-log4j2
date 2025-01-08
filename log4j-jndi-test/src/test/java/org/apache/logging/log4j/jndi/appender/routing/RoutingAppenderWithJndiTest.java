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
package org.apache.logging.log4j.jndi.appender.routing;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Collections;
import javax.naming.Context;
import javax.naming.NamingException;
import org.apache.logging.log4j.EventLogger;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.apache.logging.log4j.core.test.junit.LoggerContextRule;
import org.apache.logging.log4j.jndi.JndiManager;
import org.apache.logging.log4j.jndi.test.junit.JndiRule;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * RoutingAppenderWithJndiTest
 */
public class RoutingAppenderWithJndiTest {

    public static final String JNDI_CONTEXT_NAME = "java:comp/env/logging/context-name";
    private ListAppender listAppender1;
    private ListAppender listAppender2;

    public static LoggerContextRule loggerContextRule = new LoggerContextRule(getLocation());

    private static String getLocation() {
        System.setProperty("log4j2.*.JNDI.enableLookup", "true");
        return "log4j-routing-by-jndi.xml";
    }

    @ClassRule
    public static RuleChain rules =
            RuleChain.outerRule(new JndiRule(Collections.emptyMap())).around(loggerContextRule);

    @Before
    public void before() throws NamingException {
        listAppender1 = RoutingAppenderWithJndiTest.loggerContextRule.getListAppender("List1");
        listAppender2 = RoutingAppenderWithJndiTest.loggerContextRule.getListAppender("List2");
    }

    @After
    public void after() {
        listAppender1.clear();
        listAppender2.clear();
    }

    @Test
    @SuppressWarnings("BanJNDI")
    public void routingTest() throws NamingException {
        // default route when there's no jndi resource
        StructuredDataMessage msg =
                new StructuredDataMessage("Test", "This is a message from unknown context", "Context");
        EventLogger.logEvent(msg);
        final File defaultLogFile = new File("target/routingbyjndi/routingbyjnditest-unknown.log");
        assertTrue("The default log file was not created", defaultLogFile.exists());

        // now set jndi resource to Application1
        final Context context = JndiManager.getDefaultManager().getContext();
        assertNotNull(context);
        context.bind(JNDI_CONTEXT_NAME, "Application1");

        msg = new StructuredDataMessage("Test", "This is a message from Application1", "Context");
        EventLogger.logEvent(msg);
        assertNotNull("No events generated", listAppender1.getEvents());
        assertTrue(
                "Incorrect number of events. Expected 1, got "
                        + listAppender1.getEvents().size(),
                listAppender1.getEvents().size() == 1);

        // now set jndi resource to Application2
        context.rebind(JNDI_CONTEXT_NAME, "Application2");

        msg = new StructuredDataMessage("Test", "This is a message from Application2", "Context");
        EventLogger.logEvent(msg);
        assertNotNull("No events generated", listAppender2.getEvents());
        assertTrue(
                "Incorrect number of events. Expected 1, got "
                        + listAppender2.getEvents().size(),
                listAppender2.getEvents().size() == 1);
        assertTrue(
                "Incorrect number of events. Expected 1, got "
                        + listAppender1.getEvents().size(),
                listAppender1.getEvents().size() == 1);

        msg = new StructuredDataMessage("Test", "This is another message from Application2", "Context");
        EventLogger.logEvent(msg);
        assertNotNull("No events generated", listAppender2.getEvents());
        assertTrue(
                "Incorrect number of events. Expected 2, got "
                        + listAppender2.getEvents().size(),
                listAppender2.getEvents().size() == 2);
        assertTrue(
                "Incorrect number of events. Expected 1, got "
                        + listAppender1.getEvents().size(),
                listAppender1.getEvents().size() == 1);

        // now set jndi resource to Application3.
        // The context name, 'Application3', will be used as log file name by the default route.
        context.rebind("java:comp/env/logging/context-name", "Application3");
        msg = new StructuredDataMessage("Test", "This is a message from Application3", "Context");
        EventLogger.logEvent(msg);
        final File application3LogFile = new File("target/routingbyjndi/routingbyjnditest-Application3.log");
        assertTrue("The Application3 log file was not created", application3LogFile.exists());

        // now set jndi resource to Application4
        // The context name, 'Application4', will be used as log file name by the default route.
        context.rebind("java:comp/env/logging/context-name", "Application4");
        msg = new StructuredDataMessage("Test", "This is a message from Application4", "Context");
        EventLogger.logEvent(msg);
        final File application4LogFile = new File("target/routingbyjndi/routingbyjnditest-Application4.log");
        assertTrue("The Application3 log file was not created", application4LogFile.exists());
    }
}
