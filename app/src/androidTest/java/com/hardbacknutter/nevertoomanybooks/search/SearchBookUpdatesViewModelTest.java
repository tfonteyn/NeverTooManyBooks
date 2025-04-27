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

package com.hardbacknutter.nevertoomanybooks.search;

import android.util.Log;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.sync.SyncAction;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SearchBookUpdatesViewModelTest
        extends BaseDBTest {

    private static final String TAG = "SearchBookUpdatesVM";

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    public void quick()
            throws IOException {
        final SearchBookUpdatesViewModel vm = new SearchBookUpdatesViewModel();
        vm.init(context, null);

        final Collection<SyncField> syncFields = vm.getSyncFields();

        syncFields.stream()
                  .filter(syncField -> Book.BKEY_AUTHOR_LIST.equals(syncField.getKey()))
                  .forEach(f -> f.setAction(SyncAction.Append));

        syncFields.stream()
                  .filter(syncField -> Book.BKEY_PUBLISHER_LIST.equals(syncField.getKey()))
                  .forEach(f -> f.setAction(SyncAction.Overwrite));

        final Book localBook = new Book();
        localBook.setTitle("blah");
        localBook.add(Author.from("Me Myself"));
        localBook.add(Publisher.from("MySelf"));
        localBook.add(Series.from("MySeries"));

        final Book remoteBook = new Book();
        remoteBook.setTitle("Actual");
        remoteBook.add(Author.from("Real Author"));
        remoteBook.add(Publisher.from("Real Pub"));
        remoteBook.putString(DBKey.COLOR, "monochromie");

        final SyncReaderProcessor processor = vm.getSyncProcessorBuilder().build(context);

        final Map<String, SyncField> fieldsWanted = processor.filter(localBook);
        Log.d(TAG, fieldsWanted.toString());

        final Book delta = processor.process(context, 123, localBook, remoteBook, fieldsWanted);
        assertNotNull(delta);

        // _id=123,
        // author_list=[Author{id=0, familyName=`Author`, givenNames=`Real`, complete=false, type=0b0: Type{}, realAuthor=null}],
        // publisher_list=[Publisher{id=0, name=`Real Pub`}, Publisher{id=0, name=`MySelf`}]}]}
        assertEquals(123, delta.getId());

        // Append
        final List<Author> authors = delta.getAuthors();
        assertEquals(2, authors.size());
        Author author;
        author = authors.get(0);
        assertEquals("Myself", author.getFamilyName());
        assertEquals("Me", author.getGivenNames());
        author = authors.get(1);
        assertEquals("Author", author.getFamilyName());
        assertEquals("Real", author.getGivenNames());

        // Overwrite
        final List<Publisher> publishers = delta.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals("Real Pub", publishers.get(0).getName());

        assertEquals("Black & white", delta.getString(DBKey.COLOR));
    }
}