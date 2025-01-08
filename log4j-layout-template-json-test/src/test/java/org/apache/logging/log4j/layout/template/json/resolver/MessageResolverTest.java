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
package org.apache.logging.log4j.layout.template.json.resolver;

import static org.apache.logging.log4j.layout.template.json.TestHelpers.CONFIGURATION;
import static org.apache.logging.log4j.layout.template.json.TestHelpers.asMap;
import static org.apache.logging.log4j.layout.template.json.TestHelpers.usingSerializedLogEventAccessor;
import static org.apache.logging.log4j.layout.template.json.TestHelpers.writeJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.layout.template.json.JsonTemplateLayout;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.message.StringMapMessage;
import org.junit.jupiter.api.Test;

class MessageResolverTest {

    @Test
    void test_message_fallbackKey() {

        // Create the event template.
        final String eventTemplate = writeJson(asMap(
                "message",
                asMap(
                        "$resolver", "message",
                        "fallbackKey", "formattedMessage")));

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout.newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        // Create a log event with a MapMessage.
        final Message mapMessage = new StringMapMessage().with("key1", "val1");
        final LogEvent mapMessageLogEvent = Log4jLogEvent.newBuilder()
                .setMessage(mapMessage)
                .setTimeMillis(System.currentTimeMillis())
                .build();

        // Check the serialized MapMessage.
        usingSerializedLogEventAccessor(
                layout, mapMessageLogEvent, accessor -> assertThat(accessor.getString(new String[] {"message", "key1"}))
                        .isEqualTo("val1"));

        // Create a log event with a SimpleMessage.
        final Message simpleMessage = new SimpleMessage("simple");
        final LogEvent simpleMessageLogEvent = Log4jLogEvent.newBuilder()
                .setMessage(simpleMessage)
                .setTimeMillis(System.currentTimeMillis())
                .build();

        // Check the serialized MapMessage.
        usingSerializedLogEventAccessor(layout, simpleMessageLogEvent, accessor -> assertThat(
                        accessor.getString(new String[] {"message", "formattedMessage"}))
                .isEqualTo("simple"));
    }

    @Test
    void test_StringMapMessage() {

        // Create the log event.
        final StringMapMessage message = new StringMapMessage();
        message.put("message", "Hello, World!");
        message.put("bottle", "Kickapoo Joy Juice");
        final LogEvent logEvent = Log4jLogEvent.newBuilder().setMessage(message).build();

        // Create the event template.
        final String eventTemplate = writeJson(asMap("message", asMap("$resolver", "message")));

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout.newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        usingSerializedLogEventAccessor(layout, logEvent, accessor -> {
            assertThat(accessor.getString(new String[] {"message", "message"})).isEqualTo("Hello, World!");
            assertThat(accessor.getString(new String[] {"message", "bottle"})).isEqualTo("Kickapoo Joy Juice");
        });
    }

    @Test
    void test_ObjectMessage() {

        // Create the log event.
        final int id = 0xDEADBEEF;
        final String name = "name-" + id;
        final Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("id", id);
        attachment.put("name", name);
        final ObjectMessage message = new ObjectMessage(attachment);
        final LogEvent logEvent = Log4jLogEvent.newBuilder().setMessage(message).build();

        // Create the event template.
        final String eventTemplate = writeJson(asMap("message", asMap("$resolver", "message")));

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout.newBuilder()
                .setConfiguration(CONFIGURATION)
                .setStackTraceEnabled(true)
                .setEventTemplate(eventTemplate)
                .build();

        // Check the serialized event.
        usingSerializedLogEventAccessor(layout, logEvent, accessor -> {
            assertThat(accessor.getInteger(new String[] {"message", "id"})).isEqualTo(id);
            assertThat(accessor.getString(new String[] {"message", "name"})).isEqualTo(name);
        });
    }

    @Test
    void test_MapMessage_serialization() {

        // Create the event template.
        final String eventTemplate = writeJson(asMap("message", asMap("$resolver", "message")));

        // Create the layout.
        final JsonTemplateLayout layout = JsonTemplateLayout.newBuilder()
                .setConfiguration(CONFIGURATION)
                .setEventTemplate(eventTemplate)
                .build();

        // Create the log event with a MapMessage.
        final StringMapMessage mapMessage = new StringMapMessage()
                .with("key1", "val1")
                .with("key2", 0xDEADBEEF)
                .with("key3", Collections.singletonMap("key3.1", "val3.1"));
        final LogEvent logEvent = Log4jLogEvent.newBuilder()
                .setMessage(mapMessage)
                .setTimeMillis(System.currentTimeMillis())
                .build();

        // Check the serialized event.
        usingSerializedLogEventAccessor(layout, logEvent, accessor -> {
            assertThat(accessor.getString(new String[] {"message", "key1"})).isEqualTo("val1");
            assertThat(accessor.getInteger(new String[] {"message", "key2"})).isEqualTo(0xDEADBEEF);
            assertThat(accessor.getString(new String[] {"message", "key3", "key3.1"}))
                    .isEqualTo("val3.1");
        });
    }
}
