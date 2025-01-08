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
package org.apache.logging.log4j.plugins.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.apache.logging.log4j.plugins.internal.util.AnnotationUtil;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class AnnotationUtilTest {

    @Retention(RetentionPolicy.RUNTIME)
    @interface MetaAnnotation {}

    @Retention(RetentionPolicy.RUNTIME)
    @MetaAnnotation
    @interface StereotypeAnnotation {}

    @Retention(RetentionPolicy.RUNTIME)
    @StereotypeAnnotation
    @interface AliasedStereotypeAnnotation {}

    @StereotypeAnnotation
    static class HasMetaAnnotation {}

    @AliasedStereotypeAnnotation
    static class HasAliasedMetaAnnotation {}

    @TestFactory
    Stream<DynamicTest> isMetaAnnotationPresent() {
        return Stream.of(HasMetaAnnotation.class, HasAliasedMetaAnnotation.class)
                .map(clazz -> DynamicTest.dynamicTest(
                        "isMetaAnnotationPresent(" + clazz.getSimpleName() + ", MetaAnnotation.class)",
                        () -> assertTrue(AnnotationUtil.isMetaAnnotationPresent(clazz, MetaAnnotation.class))));
    }

    @TestFactory
    Stream<DynamicTest> getMetaAnnotation() {
        return Stream.of(HasMetaAnnotation.class, HasAliasedMetaAnnotation.class)
                .map(clazz -> DynamicTest.dynamicTest(
                        "getMetaAnnotation(" + clazz.getSimpleName() + ", MetaAnnotation.class)", () -> {
                            final Annotation annotation = AnnotationUtil.getElementAnnotationHavingMetaAnnotation(
                                    clazz, MetaAnnotation.class);
                            assertNotNull(annotation);
                            assertEquals(StereotypeAnnotation.class, annotation.annotationType());
                        }));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface LogicalAnnotation {}

    @Retention(RetentionPolicy.RUNTIME)
    @LogicalAnnotation
    @interface AliasedAnnotation {}

    @Retention(RetentionPolicy.RUNTIME)
    @AliasedAnnotation
    @interface MetaAliasedAnnotation {}

    @LogicalAnnotation
    static class HasLogicalAnnotation {}

    @AliasedAnnotation
    static class HasAliasedAnnotation {}

    @MetaAliasedAnnotation
    static class HasMetaAliasedAnnotation {}

    @TestFactory
    Stream<DynamicTest> getLogicalAnnotation() {
        return Stream.of(HasLogicalAnnotation.class, HasAliasedAnnotation.class, HasMetaAliasedAnnotation.class)
                .map(clazz -> DynamicTest.dynamicTest(clazz.getSimpleName(), () -> {
                    final LogicalAnnotation annotation =
                            AnnotationUtil.getLogicalAnnotation(clazz, LogicalAnnotation.class);
                    assertNotNull(annotation);
                }));
    }

    interface AnnotatedMethod {
        void method();
    }

    static class FirstMethod implements AnnotatedMethod {
        @Override
        @LogicalAnnotation
        public void method() {}
    }

    static class SecondMethod implements AnnotatedMethod {
        @Override
        @AliasedAnnotation
        public void method() {}
    }

    static class ThirdMethod implements AnnotatedMethod {
        @Override
        @MetaAliasedAnnotation
        public void method() {}
    }

    @TestFactory
    Stream<DynamicTest> getLogicalAnnotationOnMethod() {
        return Stream.of(FirstMethod.class, SecondMethod.class, ThirdMethod.class)
                .map(clazz -> DynamicTest.dynamicTest(clazz.getSimpleName(), () -> {
                    final Method method = assertDoesNotThrow(() -> clazz.getMethod("method"));
                    final LogicalAnnotation annotation =
                            AnnotationUtil.getLogicalAnnotation(method, LogicalAnnotation.class);
                    assertNotNull(annotation);
                }));
    }

    static class HasAnnotatedField {
        @LogicalAnnotation
        String field;
    }

    static class HasAliasedAnnotatedField {
        @AliasedAnnotation
        String field;
    }

    static class HasMetaAliasedAnnotatedField {
        @MetaAliasedAnnotation
        String field;
    }

    @TestFactory
    Stream<DynamicTest> getLogicalAnnotationOnField() {
        return Stream.of(HasAnnotatedField.class, HasAliasedAnnotatedField.class, HasMetaAliasedAnnotatedField.class)
                .map(clazz -> DynamicTest.dynamicTest(clazz.getSimpleName(), () -> {
                    final Field field = assertDoesNotThrow(() -> clazz.getDeclaredField("field"));
                    final LogicalAnnotation annotation =
                            AnnotationUtil.getLogicalAnnotation(field, LogicalAnnotation.class);
                    assertNotNull(annotation);
                }));
    }
}
