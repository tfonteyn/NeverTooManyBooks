/*
 * @Copyright 2018-2022 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import android.content.Context;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Does live lookups to the website !
 */
public class AuthorTest
        extends BaseDBTest {

    private Context context;

    private AuthorResolver resolver;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup();

        context = serviceLocator.getLocalizedAppContext();
        resolver = new AuthorResolver(context, new FakeCancellable());

        serviceLocator.getCacheDb().execSQL("DELETE FROM " + CacheDbHelper.TBL_BDT_AUTHORS);
    }

    @Test
    public void lookup01()
            throws SearchException, CredentialsException {

        final boolean lookup;
        final Author author;
        final Author realAuthor;

        author = Author.from("Leloup, Roger");
        lookup = resolver.resolve(context, author);
        // no pen-name
        Assert.assertFalse(lookup);
        Assert.assertEquals("Leloup", author.getFamilyName());
        Assert.assertEquals("Roger", author.getGivenNames());
        realAuthor = author.getRealAuthor();
        Assert.assertNotNull(realAuthor);
    }

    @Test
    public void lookup02()
            throws SearchException, CredentialsException {

        final boolean lookup;
        final Author author;
        final Author realAuthor;

        author = Author.from("Jije");
        lookup = resolver.resolve(context, author);
        Assert.assertTrue(lookup);
        Assert.assertEquals("Jijé", author.getFamilyName());
        realAuthor = author.getRealAuthor();
        Assert.assertNotNull(realAuthor);
        Assert.assertEquals("Gillain", realAuthor.getFamilyName());
        Assert.assertEquals("Joseph", realAuthor.getGivenNames());
        Assert.assertNull(realAuthor.getRealAuthor());
    }

    private static class FakeCancellable
            implements Cancellable {
        @Override
        public void cancel() {

        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
