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
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.sync.SyncAction;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderProcessor;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void copyIfBlankOnListField()
            throws IOException {

        final Book localBook = new Book();
        localBook.setTitle("local-title");
        localBook.add(Author.from("local author"));
        localBook.add(Publisher.from("local publisher"));
        localBook.add(Series.from("local series"));
        // NO Identifiers

        final Book remoteBook = new Book();
        remoteBook.setTitle("remote-title");
        remoteBook.add(Author.from("remote author"));
        remoteBook.add(Publisher.from("remote publisher"));
        remoteBook.setColor("remote colour");
        remoteBook.setIdentifierValue(Identifier.SID_BNF, "remote-bnf");
        remoteBook.setIdentifierValue(Identifier.SID_GOODREADS, "remote-goodreads");

        final SearchBookUpdatesViewModel vm = new SearchBookUpdatesViewModel();
        vm.init(context, null);

        final Collection<SyncField> syncFields = vm.getSyncFields();

        syncFields.stream()
                  .filter(syncField -> Book.BKEY_IDENTIFIER_LIST.equals(syncField.getKey()))
                  .forEach(f -> f.setAction(SyncAction.CopyIfBlank));

        final SyncReaderProcessor processor = vm.getSyncProcessorBuilder().build(context);

        final Map<String, SyncField> fieldsWanted = processor.filter(localBook);
        Log.d(TAG, fieldsWanted.toString());

        final Book delta = processor.process(context, 123, localBook, remoteBook, fieldsWanted);
        assertNotNull(delta);

        assertEquals(123, delta.getId());

        // CopyIfBlank and local is-blank
        final List<Identifier.Value> identifiers = delta.getIdentifiers();
        assertEquals(2, identifiers.size());

        assertEquals("remote-bnf",
                     delta.getIdentifierValue(Identifier.SID_BNF).orElse(null));
        assertEquals("remote-goodreads",
                     delta.getIdentifierValue(Identifier.SID_GOODREADS).orElse(null));
    }

    @Test
    public void quick()
            throws IOException {

        final Book localBook = new Book();
        localBook.setTitle("local-title");
        localBook.add(Author.from("local author"));
        localBook.add(Publisher.from("local publisher"));
        localBook.add(Series.from("local series"));
        localBook.setIdentifierValue(Identifier.SID_BEDETHEQUE, "local-bedetheque");
        localBook.setIdentifierValue(Identifier.SID_GOODREADS, "local-goodreads");

        final Book remoteBook = new Book();
        remoteBook.setTitle("remote-title");
        remoteBook.add(Author.from("remote author"));
        remoteBook.add(Publisher.from("remote publisher"));
        remoteBook.setColor("remote colour");
        remoteBook.setIdentifierValue(Identifier.SID_BNF, "remote-bnf");
        remoteBook.setIdentifierValue(Identifier.SID_GOODREADS, "remote-goodreads");

        final SearchBookUpdatesViewModel vm = new SearchBookUpdatesViewModel();
        vm.init(context, null);

        final Collection<SyncField> syncFields = vm.getSyncFields();

        syncFields.stream()
                  .filter(syncField -> Book.BKEY_AUTHOR_LIST.equals(syncField.getKey()))
                  .forEach(f -> f.setAction(SyncAction.Append));

        syncFields.stream()
                  .filter(syncField -> Book.BKEY_PUBLISHER_LIST.equals(syncField.getKey()))
                  .forEach(f -> f.setAction(SyncAction.Overwrite));

        syncFields.stream()
                  .filter(syncField -> Book.BKEY_IDENTIFIER_LIST.equals(syncField.getKey()))
                  .forEach(f -> f.setAction(SyncAction.CopyIfBlank));

        final SyncReaderProcessor processor = vm.getSyncProcessorBuilder().build(context);

        final Map<String, SyncField> fieldsWanted = processor.filter(localBook);
        Log.d(TAG, fieldsWanted.toString());

        final Book delta = processor.process(context, 123, localBook, remoteBook, fieldsWanted);
        assertNotNull(delta);

        // _id=123,
        // author_list=[Author{id=0, familyName=`Author`, givenNames=`Real`, complete=false, type=0b0: Type{}, realAuthor=null}],
        // publisher_list=[Publisher{id=0, name=`Real Pub`}, Publisher{id=0, name=`MySelf`}]}]}
        assertEquals(123, delta.getId());

        // Added
        assertEquals("remote colour", delta.getString(DBKey.COLOR, null));

        // Append
        final List<Author> authors = delta.getAuthors();
        assertEquals(2, authors.size());
        Author author;
        author = authors.get(0);
        assertEquals("author", author.getFamilyName());
        assertEquals("local", author.getGivenNames());
        author = authors.get(1);
        assertEquals("author", author.getFamilyName());
        assertEquals("remote", author.getGivenNames());

        // Overwrite
        final List<Publisher> publishers = delta.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals("remote publisher", publishers.get(0).getName());

        // CopyIfBlank and local not-blank; the key must be removed from the delta
        assertFalse(delta.contains(Book.BKEY_IDENTIFIER_LIST));
    }
}