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
package org.apache.logging.log4j.spi;

import org.apache.logging.log4j.core.impl.CoreProperties.ThreadContextProperties;
import org.apache.logging.log4j.kit.env.PropertyEnvironment;
import org.apache.logging.log4j.perf.nogc.OpenHashStringMap;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.StringMap;

/**
 * {@code OpenHashStringMap}-based implementation of the {@code ThreadContextMap} interface that creates a copy of
 * the data structure on every modification. Any particular instance of the data structure is a snapshot of the
 * ThreadContext at some point in time and can safely be passed off to other threads
 *
 * @since 2.7
 */
public class CopyOnWriteOpenHashMapThreadContextMap extends CopyOnWriteSortedArrayThreadContextMap {

    /** Constant used in benchmark code */
    public static final Class<? extends ThreadContextMap> SUPER = CopyOnWriteSortedArrayThreadContextMap.class;

    private final int initialCapacity = PropertyEnvironment.getGlobal()
            .getProperty(ThreadContextProperties.class)
            .initialCapacity();

    @Override
    protected StringMap createStringMap() {
        return new OpenHashStringMap<>(initialCapacity);
    }

    @Override
    protected StringMap createStringMap(final ReadOnlyStringMap original) {
        return new OpenHashStringMap<>(original);
    }
}
