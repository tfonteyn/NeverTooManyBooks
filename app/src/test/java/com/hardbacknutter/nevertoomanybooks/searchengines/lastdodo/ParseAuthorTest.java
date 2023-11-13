/*
 * @Copyright 2018-2023 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */

package com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo;

import androidx.annotation.NonNull;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParseAuthorTest {

    @NonNull
    static Stream<Arguments> readArgs() {
        return Stream.of(
                Arguments.of("Robert Velter",
                             new String[]{"Robert Velter"}),
                Arguments.of("Robert Velter (Rob Vel)",
                             new String[]{"Robert Velter",
                                     "Rob Vel"}),
                Arguments.of("Robert Velter (Rob-Vel,Bozz)",
                             new String[]{"Robert Velter",
                                     "Rob-Vel", "Bozz"}),
                Arguments.of("Don (*3)",
                             new String[]{"Don (*3)"})
        );
    }

    @ParameterizedTest
    @MethodSource("readArgs")
    void names01(@NonNull final String input,
                 @NonNull final String[] expected) {

        final String[] names = LastDodoSearchEngine.parseAuthorNames(input);
        assertEquals(expected.length, names.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], names[i]);
        }
    }
}
