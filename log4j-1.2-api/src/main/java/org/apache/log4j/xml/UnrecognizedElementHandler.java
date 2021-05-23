/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
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
package org.apache.log4j.xml;

import org.w3c.dom.Element;
import java.util.Properties;

/**
 * When implemented by an object configured by DOMConfigurator,
 * the handle method will be called when an unrecognized child
 * element is encountered.  Unrecognized child elements of
 * the log4j:configuration element will be dispatched to
 * the logger repository if it supports this interface.
 *
 * @since 1.2.15
 */
public interface UnrecognizedElementHandler {
    /**
     * Called to inform a configured object when
     * an unrecognized child element is encountered.
     * @param element element, may not be null.
     * @param props properties in force, may be null.
     * @return true if configured object recognized the element
     * @throws Exception throw an exception to prevent activation
     * of the configured object.
     */
    boolean parseUnrecognizedElement(Element element, Properties props) throws Exception;
}
