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

package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.TypedCursor;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookRepository;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BookDaoImplTest
        extends BaseDBTest {

    private AuthorDao authorDao;
    private PublisherDao publisherDao;
    private BookDao bookDao;
    private BookRepository bookRepository;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        authorDao = serviceLocator.getAuthorDao();
        publisherDao = serviceLocator.getPublisherDao();
        bookDao = serviceLocator.getBookDao();
        bookRepository = new BookRepository(context);

        final List<Long> toRemove = new ArrayList<>();
        try(final TypedCursor fetch = bookDao.fetch(List.of(ISBN.parse("9783956405136")))) {
            while (fetch.moveToFirst()) {
                toRemove.add(fetch.getLong(0));
            }
        }
        if (!toRemove.isEmpty()) {
            toRemove.forEach(bookId -> bookDao.delete(bookId));
        }

        authorDao.findByName(context, new Author("Kondoh", "Akino"),
                             Locale.GERMANY)
                 .ifPresent(a -> authorDao.delete(context, a));

        authorDao.findByName(context, new Author("Maser", "Verena"),
                             Locale.GERMANY)
                 .ifPresent(a -> authorDao.delete(context, a));

        publisherDao.findByName(context, new Publisher("Reprodukt"), Locale.GERMANY)
                    .ifPresent(p -> publisherDao.delete(context, p));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void insertDuplicate()
            throws DaoWriteException {

        final Publisher publisher = Publisher.from("Reprodukt");
        final long p1 = publisherDao.insert(context, publisher, Locale.GERMANY);

        Author author;
        author = new Author("Kondoh", "Akino");
        author.setIdentifierValue(Identifier.SID_ISNI, "0000000002217394");
        final long a1 = authorDao.insert(context, author, Locale.GERMANY);

        author = new Author("Maser", "Verena");
        final long a2 = authorDao.insert(context, author, Locale.GERMANY);

        // bookshelf_list=[Bookshelf{id=5, name=`Literatur`,  identifier_list=[
        // Value{key=oclc, sid=`1558583930`},
        // Value{key=dnb, sid=`1382937814`}, Value{key=de-599, sid=`DNB1382937814`}]}]}

        final Book book = new Book();
        book.ensureBookshelf();

        book.setTitle("Streifzüge durch New York 1");
        book.setLanguage("deu");
        book.setPublicationDate("2026");
        book.putString(DBKey.DATE_ACQUIRED, "2026-08-21");
        book.setRawProductCode("9783956405136");
        book.setPages("164");
        book.setPriceListed(new Money(BigDecimal.valueOf(16), Money.EURO));

        final Author author1 = authorDao.findById(a1).get();
        author1.setRole(AuthorRole.WRITER);
        book.add(author1);

        final Author author2 = authorDao.findById(a2).get();
        author2.setRole(AuthorRole.TRANSLATOR);
        book.add(author2);

        final Publisher publisher1 = publisherDao.findById(p1).get();
        book.add(publisher1);

        final long bookId1 = bookRepository.insert(context, book, Set.of());

        assertEquals(bookId1, book.getId());

        final long bookId2 = bookRepository.insert(context, book, Set.of());
        assertEquals(bookId2, book.getId());

        assertNotEquals(bookId1, bookId2);
    }
}
