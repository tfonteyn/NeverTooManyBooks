/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils.mappers;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatMapperTest
        extends BaseDBTest {

    private Book book;
    private FormatMapper mapper;
    private String key;

    @NonNull
    static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of("en", "pb", "Paperback"),
                Arguments.of("en", "Dimensions 5x4", "Dimensions 5x4"),
                Arguments.of("en", "some-string", "some-string"),
                Arguments.of("fr", "pb", "Livre de poche")
        );
    }

    /**
     * We're not using the {@code setup()} as usual, as we need
     * to update the super's Locale for EACH set of test parameters.
     *
     * @param localeCode to use
     */
    private void setupThisTest(@NonNull final String localeCode)
            throws StorageException {
        super.setup(localeCode);

        book = new Book();
        mapper = new FormatMapper(Locale.UK);
        key = mapper.getKey();
    }

    @ParameterizedTest
    @MethodSource("data")
    void basic(@NonNull final String fLocaleCode,
               @NonNull final String fInput,
               @NonNull final String fExpected)
            throws StorageException {
        setupThisTest(fLocaleCode);

        book.putString(key, fInput);
        mapper.map(context, book);

        assertEquals(fExpected, book.getString(key, null));
    }
}
