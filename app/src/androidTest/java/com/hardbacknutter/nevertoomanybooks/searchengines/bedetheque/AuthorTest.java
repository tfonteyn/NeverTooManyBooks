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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Does live lookups to the website !
 */
public class AuthorTest
        extends BaseDBTest {

    private AuthorResolver resolver;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup();

        resolver = new BedethequeAuthorResolver(context, new NotCancellable());

        ServiceLocator.getInstance().getBedethequeCacheDao().clearCache();
    }

    @Test
    public void lookup01()
            throws SearchException, CredentialsException {

        final boolean lookup;
        final Author author;
        final Author realAuthor;

        author = new Author("Leloup", "Roger");
        lookup = resolver.resolve(author);
        // no pen-name
        Assert.assertFalse(lookup);
        Assert.assertEquals("Leloup", author.getFamilyName());
        Assert.assertEquals("Roger", author.getGivenNames());
        realAuthor = author.getRealAuthor();
        Assert.assertNull(realAuthor);
    }

    @Test
    public void lookup02()
            throws SearchException, CredentialsException {

        final boolean lookup;
        final Author author;
        final Author realAuthor;

        author = new Author("<Indéterminé>", "");
        lookup = resolver.resolve(author);
        // no pen-name
        Assert.assertFalse(lookup);
        Assert.assertEquals("<Indéterminé>", author.getFamilyName());
        realAuthor = author.getRealAuthor();
        Assert.assertNull(realAuthor);
    }

    @Test
    public void lookup10()
            throws SearchException, CredentialsException {

        final boolean lookup;
        final Author author;
        final Author realAuthor;

        author = new Author("Jije", "");
        lookup = resolver.resolve(author);
        Assert.assertTrue(lookup);
        Assert.assertEquals("Jijé", author.getFamilyName());
        realAuthor = author.getRealAuthor();
        Assert.assertNotNull(realAuthor);
        Assert.assertEquals("Gillain", realAuthor.getFamilyName());
        Assert.assertEquals("Joseph", realAuthor.getGivenNames());
        Assert.assertNull(realAuthor.getRealAuthor());
    }

    @Test
    public void lookup11()
            throws SearchException, CredentialsException {

        final boolean lookup;
        final Author author;
        final Author realAuthor;

        author = new Author("61Chi", "");
        lookup = resolver.resolve(author);
        Assert.assertTrue(lookup);
        Assert.assertEquals("61Chi", author.getFamilyName());
        realAuthor = author.getRealAuthor();
        Assert.assertNotNull(realAuthor);
        Assert.assertEquals("Liu", realAuthor.getFamilyName());
        Assert.assertEquals("Yi-chi", realAuthor.getGivenNames());
        Assert.assertNull(realAuthor.getRealAuthor());
    }



    private static class NotCancellable
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
