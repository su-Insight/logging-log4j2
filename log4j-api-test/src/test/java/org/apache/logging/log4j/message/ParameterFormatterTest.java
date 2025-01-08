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
package org.apache.logging.log4j.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.message.ParameterFormatter.MessagePatternAnalysis;
import org.apache.logging.log4j.test.junit.UsingStatusLoggerMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link ParameterFormatter}.
 */
@UsingStatusLoggerMock
class ParameterFormatterTest {

    @ParameterizedTest
    @CsvSource({
        "0,,false,",
        "0,,false,aaa",
        "0,,true,\\{}",
        "1,0,false,{}",
        "1,0,true,{}\\{}",
        "1,2,true,\\\\{}",
        "2,8:10,true,foo \\{} {}{}",
        "2,8:10,true,foo {\\} {}{}",
        "2,0:2,false,{}{}",
        "3,0:2:4,false,{}{}{}",
        "4,0:2:4:8,false,{}{}{}aa{}",
        "4,0:2:4:10,false,{}{}{}a{]b{}",
        "5,0:2:4:7:10,false,{}{}{}a{}b{}"
    })
    void test_pattern_analysis(
            final int placeholderCount,
            final String placeholderCharIndicesString,
            final boolean escapedPlaceholderFound,
            final String pattern) {
        MessagePatternAnalysis analysis = ParameterFormatter.analyzePattern(pattern, placeholderCount);
        assertThat(analysis.placeholderCount).isEqualTo(placeholderCount);
        if (placeholderCount > 0) {
            final int[] placeholderCharIndices = Arrays.stream(placeholderCharIndicesString.split(":"))
                    .mapToInt(Integer::parseInt)
                    .toArray();
            assertThat(analysis.placeholderCharIndices).startsWith(placeholderCharIndices);
            assertThat(analysis.escapedCharFound).isEqualTo(escapedPlaceholderFound);
        }
    }

    @ParameterizedTest
    @CsvSource({"1,foo {}", "2,bar {}{}"})
    void format_should_fail_on_insufficient_args(final int placeholderCount, final String pattern) {
        final int argCount = placeholderCount - 1;
        assertThatThrownBy(() -> ParameterFormatter.format(pattern, new Object[argCount], argCount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "found %d argument placeholders, but provided %d for pattern `%s`",
                        placeholderCount, argCount, pattern);
    }

    @ParameterizedTest
    @MethodSource("messageFormattingTestCases")
    void format_should_work(
            final String pattern, final Object[] args, final int argCount, final String expectedFormattedMessage) {
        final String actualFormattedMessage = ParameterFormatter.format(pattern, args, argCount);
        assertThat(actualFormattedMessage).isEqualTo(expectedFormattedMessage);
    }

    static Object[][] messageFormattingTestCases() {
        return new Object[][] {
            new Object[] {"Test message {}{} {}", new Object[] {"a", "b", "c"}, 3, "Test message ab c"},
            new Object[] {
                "Test message {} {} {} {} {} {}",
                new Object[] {"a", null, "c", null, null, null},
                6,
                "Test message a null c null null null"
            },
            new Object[] {
                "Test message {}{} {}",
                new Object[] {"a", "b", "c", "unnecessary", "superfluous"},
                5,
                "Test message ab c"
            },
            new Object[] {"Test message \\{}{} {}", new Object[] {"a", "b", "c"}, 3, "Test message {}a b"},
            new Object[] {"Test message {}{} {}\\", new Object[] {"a", "b", "c"}, 3, "Test message ab c\\"},
            new Object[] {"Test message {}{} {}\\\\", new Object[] {"a", "b", "c"}, 3, "Test message ab c\\"},
            new Object[] {"Test message \\\\{}{} {}", new Object[] {"a", "b", "c"}, 3, "Test message \\ab c"},
            new Object[] {"Test message {}{} {}", new Object[] {"a", "b", "c"}, 3, "Test message ab c"},
            new Object[] {
                "Test message {} {} {} {} {} {}",
                new Object[] {"a", null, "c", null, null, null},
                6,
                "Test message a null c null null null"
            },
            new Object[] {
                "Test message {}{} {}",
                new Object[] {"a", "b", "c", "unnecessary", "superfluous"},
                5,
                "Test message ab c"
            },
            new Object[] {"Test message \\{}{} {}", new Object[] {"a", "b", "c"}, 3, "Test message {}a b"},
            new Object[] {"Test message {}{} {}\\", new Object[] {"a", "b", "c"}, 3, "Test message ab c\\"},
            new Object[] {"Test message {}{} {}\\\\", new Object[] {"a", "b", "c"}, 3, "Test message ab c\\"},
            new Object[] {"Test message \\\\{}{} {}", new Object[] {"a", "b", "c"}, 3, "Test message \\ab c"},
            new Object[] {"foo \\\\\\{} {}", new Object[] {"bar"}, 1, "foo \\{} bar"},
            new Object[] {"missing arg {} {}", new Object[] {1, 2}, 1, "missing arg 1 {}"},
            new Object[] {"foo {\\} {}", new Object[] {"bar"}, 1, "foo {\\} bar"}
        };
    }

    @Test
    public void testDeepToString() {
        final List<Object> list = new ArrayList<>();
        list.add(1);
        // noinspection CollectionAddedToSelf
        list.add(list);
        list.add(2);
        final String actual = ParameterFormatter.deepToString(list);
        final String expected = "[1, [..." + ParameterFormatter.identityToString(list) + "...], 2]";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testDeepToStringUsingNonRecursiveButConsequentObjects() {
        final List<Object> list = new ArrayList<>();
        final Object item = Collections.singletonList(0);
        list.add(1);
        list.add(item);
        list.add(2);
        list.add(item);
        list.add(3);
        final String actual = ParameterFormatter.deepToString(list);
        final String expected = "[1, [0], 2, [0], 3]";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testIdentityToString() {
        final List<Object> list = new ArrayList<>();
        list.add(1);
        // noinspection CollectionAddedToSelf
        list.add(list);
        list.add(2);
        final String actual = ParameterFormatter.identityToString(list);
        final String expected = list.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(list));
        assertThat(actual).isEqualTo(expected);
    }
}
