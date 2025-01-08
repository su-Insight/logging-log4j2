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
package org.apache.log4j.config;

import static org.apache.logging.log4j.core.config.ConfigurationFactory.LOG4J1_CONFIGURATION_FILE_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.test.junit.TempLoggingDir;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RestoreSystemProperties;

/**
 * Test configuration from Properties.
 */
class PropertiesRollingWithPropertiesTest {

    @TempLoggingDir
    private static Path loggingPath;

    @Test
    @RestoreSystemProperties
    void testProperties() {
        System.setProperty("test.directory", loggingPath.toString());
        System.setProperty(LOG4J1_CONFIGURATION_FILE_PROPERTY, "log4j1-rolling-properties.properties");

        try (final LoggerContext context = LoggerContext.getContext(false)) {
            final Logger logger = context.getLogger("test");
            logger.debug("This is a test of the root logger");
            assertThat(loggingPath.resolve("somefile.log")).exists().isNotEmptyFile();
        }
    }
}
