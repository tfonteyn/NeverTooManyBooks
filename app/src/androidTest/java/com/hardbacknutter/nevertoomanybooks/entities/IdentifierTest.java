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

package com.hardbacknutter.nevertoomanybooks.entities;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.network.UrlPatterns;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentifierTest
        extends BaseDBTest {

    private static final Pattern PATTERN = Pattern.compile("%s");

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    void createInitialList() {
        final Set<String> keys = new HashSet<>();
        Identifier.createInitialList(context).forEach(i -> {
            final String key = i.getKey();
            assertFalse(keys.contains(key), "Duplicate key: " + key);
            keys.add(key);

            i.getBookUri().ifPresent(bookUri -> assertEquals(
                    1,
                    PATTERN.split(bookUri, -1).length - 1,
                    "Invalid bookUri key: " + key));
        });
    }

    @Test
    void validateSiteUrl() {
        for (final Identifier identifier : Identifier.createInitialList(context)) {
            final String url = identifier.getSiteUrl();
            assertTrue(UrlPatterns.isBlankOrValidUrl(url), url);
        }
    }

    @Test
    void validateBookUri() {
        for (final Identifier identifier : Identifier.createInitialList(context)) {
            final String uri = identifier.getBookUri().orElse("");
            assertTrue(UrlPatterns.isBlankOrValidUriWith1s(uri), uri);
        }
    }

    @Test
    void validateAuthorUri() {
        for (final Identifier identifier : Identifier.createInitialList(context)) {
            final String uri = identifier.getAuthorUri().orElse("");
            assertTrue(UrlPatterns.isBlankOrValidUriWith1s(uri), uri);
        }
    }
}