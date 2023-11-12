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

package com.hardbacknutter.nevertoomanybooks.utils.mappers;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
class MapperTest {

    @Test
    void colorMappingMustUseLowerCaseKeys() {
        final Map<String, Integer> mappings = new ColorMapper()
                .getMappings();
        mustUseLowerCaseKeys(mappings);
    }

    @Test
    void formatMappingMustUseLowerCaseKeys() {
        final Map<String, Integer> mappings = new FormatMapper()
                .getMappings();
        mustUseLowerCaseKeys(mappings);
    }

    private void mustUseLowerCaseKeys(@NonNull final Map<String, Integer> mappings) {
        final List<String> failures = mappings
                .keySet()
                .stream()
                .filter(key -> !key.equals(key.toLowerCase()))
                .collect(Collectors.toList());
        if (!failures.isEmpty()) {
            fail("Keys which are not all lowercase: " + failures);
        }
    }
}
