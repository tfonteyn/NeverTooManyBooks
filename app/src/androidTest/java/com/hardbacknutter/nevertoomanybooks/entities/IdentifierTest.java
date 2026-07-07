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

import android.util.Pair;

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

    /** A Uri MUST have this placeholder. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%s");

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    /**
     * Test key duplication and uri containing a placeholder.
     * Use the pattern from THIS class.
     *
     * @see #validateUri()
     */
    @Test
    void createInitialList() {
        final Set<Pair<String, Identifier.EntityType>> keys = new HashSet<>();
        for (final Identifier identifier : Identifier.createInitialList(context)) {
            final String key = identifier.getKey();
            final Identifier.EntityType type = identifier.getEntityType();

            final boolean isUnique = keys.add(new Pair<>(key, type));
            assertTrue(isUnique, "Duplicate key/type: " + key + ", " + type);

            identifier.getUri().ifPresent(uri -> assertEquals(
                    1,
                    PLACEHOLDER.split(uri, -1).length - 1,
                    "Invalid uri key: " + key));
        }
    }

    /** Test the site Uri, using the utility class. */
    @Test
    void validateSiteUrl() {
        for (final Identifier identifier : Identifier.createInitialList(context)) {
            final String url = identifier.getSiteUrl();
            assertTrue(UrlPatterns.isBlankOrValidUrl(url), url);
        }
    }

    /** Test the Uri, but using the utility class and NOT the local pattern. */
    @Test
    void validateUri() {
        for (final Identifier identifier : Identifier.createInitialList(context)) {
            final String uri = identifier.getUri().orElse("");
            assertTrue(UrlPatterns.isBlankOrValidUriWith1s(uri), uri);
        }
    }
}