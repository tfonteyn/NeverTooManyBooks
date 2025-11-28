/*
 * @Copyright 2018-2025 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.UrlPatterns;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("MissingJavadoc")
public class IdentifierTest
        extends BaseDBTest {

    private static final Pattern PATTERN = Pattern.compile("%s");

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    public void createInitialList() {
        final Set<String> keys = new HashSet<>();
        Identifier.createInitialList(context).forEach(i -> {
            final String key = i.getKey();
            assertFalse("Duplicate key: " + key, keys.contains(key));
            keys.add(key);

            i.getBookUri().ifPresent(bookUri -> assertEquals(
                    "Invalid bookUri key: " + key,
                    1,
                    PATTERN.split(bookUri, -1).length - 1));
        });
    }

    @Test
    public void validateSiteUrl() {
        for (final Identifier identifier : Identifier.createInitialList(context)) {
            final String url = identifier.getSiteUrl();
            assertTrue(url, UrlPatterns.isBlankOrValidUrl(url));
        }
    }

    @Test
    public void validateBookUri() {
        for (final Identifier identifier : Identifier.createInitialList(context)) {
            final String uri = identifier.getBookUri().orElse("");
            assertTrue(uri, UrlPatterns.isBlankOrValidUriWith1s(uri));
        }
    }

    @Test
    public void validateAuthorUri() {
        for (final Identifier identifier : Identifier.createInitialList(context)) {
            final String uri = identifier.getAuthorUri().orElse("");
            assertTrue(uri, UrlPatterns.isBlankOrValidUriWith1s(uri));
        }
    }
}