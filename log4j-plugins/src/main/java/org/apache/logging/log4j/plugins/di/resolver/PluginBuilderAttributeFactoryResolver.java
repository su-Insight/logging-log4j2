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
package org.apache.logging.log4j.plugins.di.resolver;

import java.lang.reflect.Type;
import org.apache.logging.log4j.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.plugins.convert.TypeConverter;
import org.apache.logging.log4j.plugins.di.spi.StringValueResolver;
import org.jspecify.annotations.Nullable;

/**
 * Factory resolver for {@link PluginBuilderAttribute}-annotated keys. This injects a plugin configuration option.
 */
public class PluginBuilderAttributeFactoryResolver<T>
        extends AbstractAttributeFactoryResolver<T, PluginBuilderAttribute> {
    public PluginBuilderAttributeFactoryResolver() {
        super(PluginBuilderAttribute.class);
    }

    @Override
    protected boolean isSensitive(final PluginBuilderAttribute annotation) {
        return annotation.sensitive();
    }

    @Override
    protected @Nullable T getDefaultValue(
            final PluginBuilderAttribute annotation,
            final StringValueResolver resolver,
            final Type type,
            final TypeConverter<T> typeConverter) {
        return null;
    }
}
