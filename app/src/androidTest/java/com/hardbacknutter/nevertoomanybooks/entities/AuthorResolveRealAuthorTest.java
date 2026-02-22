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

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("LongLine")
class AuthorResolveRealAuthorTest
        extends BaseDBTest {

    private static final String TAG = "AuthorResolveRealAuthor";
    private final Author[] a = new Author[11];
    private AuthorDao authorDao;
    private Logger logger;
    private Locale locale;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        logger = LoggerFactory.getLogger();
        authorDao = serviceLocator.getAuthorDao();
        locale = context.getResources().getConfiguration().getLocales().get(0);
    }

    private void createAuthor(@NonNull final Author author)
            throws DaoWriteException {
        logger.d(TAG, "create: " + author.getFamilyName());
        authorDao.fixId(context, author, locale);
        if (author.getId() > 0) {
            authorDao.delete(context, author);
        }
        authorDao.insert(context, author, locale);
    }

    private void createAuthorLists()
            throws DaoWriteException {
        int i = 0;

        // root
        a[i] = new Author("f0", "g0");
        createAuthor(a[i]);

        i++;

        // ok: a1 -> a0
        a[i] = new Author("f1", "g1");
        a[i].setRealAuthor(a[0]);
        createAuthor(a[i]);

        i++;

        // ok: a2 -> a1 -> a0
        a[i] = new Author("f2", "g2");
        a[i].setRealAuthor(a[1]);
        createAuthor(a[i]);

        i++;

        // 1:1 circular
        a[i] = new Author("f3", "g3");
        a[i].setRealAuthor(a[i]);
        createAuthor(a[i]);

        i++;

        // 3-way circular

        // create as a root
        a[i] = new Author("f4", "g4");
        createAuthor(a[i]);

        i++;

        // a5 -> a4
        a[i] = new Author("f5", "g5");
        a[i].setRealAuthor(a[4]);
        createAuthor(a[i]);

        i++;

        // a6 -> a5 -> a4
        a[i] = new Author("f6", "g6");
        a[i].setRealAuthor(a[5]);
        createAuthor(a[i]);
    }

    @Test
    void resolve()
            throws DaoWriteException {

        createAuthorLists();

        logger.d(TAG, "before", Arrays.asList(a));
        // Author{id=1, familyName=`f0`, givenNames=`g0`,  realAuthorId=0, realAuthor=null},
        // Author{id=2, familyName=`f1`, givenNames=`g1`,  realAuthorId=1, realAuthor=Author{id=1, familyName=`f0`, givenNames=`g0`,  realAuthorId=0, realAuthor=null}},
        // Author{id=3, familyName=`f2`, givenNames=`g2`,  realAuthorId=1, realAuthor=Author{id=1, familyName=`f0`, givenNames=`g0`,  realAuthorId=0, realAuthor=null}},
        // Author{id=4, familyName=`f3`, givenNames=`g3`,  realAuthorId=0, realAuthor=null},
        // Author{id=5, familyName=`f4`, givenNames=`g4`,  realAuthorId=0, realAuthor=null},
        // Author{id=6, familyName=`f5`, givenNames=`g5`,  realAuthorId=5, realAuthor=Author{id=5, familyName=`f4`, givenNames=`g4`,  realAuthorId=0, realAuthor=null}},
        // Author{id=7, familyName=`f6`, givenNames=`g6`,  realAuthorId=5, realAuthor=Author{id=5, familyName=`f4`, givenNames=`g4`,  realAuthorId=0, realAuthor=null}}

        // root
        assertEquals(0, a[0].realAuthorId);
        // simple reference
        assertEquals(a[0].getId(), a[1].realAuthorId);
        // a[1] removed from the chain
        assertEquals(a[0].getId(), a[2].realAuthorId);

        // self-reference 0'd
        assertEquals(0, a[3].realAuthorId);

        // root
        assertEquals(0, a[4].realAuthorId);
        // normal reference
        assertEquals(a[4].getId(), a[5].realAuthorId);
        // a[5] removed from the chain
        assertEquals(a[4].getId(), a[6].realAuthorId);

        // Now force circular
        a[4].setRealAuthor(a[6]);
        // rejected, and set back to root
        assertEquals(0, a[4].realAuthorId);
    }
}
