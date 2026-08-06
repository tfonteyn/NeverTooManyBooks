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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import androidx.annotation.NonNull;
import androidx.core.util.Function;
import androidx.core.util.Pair;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link CoverFileSpecArray#BKEY_FILE_SPEC_ARRAY} keys are not used here,
 * as the accumulator checks for the real files (which won't be there).
 * It is assumed that if Authors/Series/etc... get processed OK, then so will the fileSpecs.
 */
@SuppressWarnings("LongLine")
class ResultsAccumulatorTest
        extends BaseDBTest {

    private static final String SEARCH_ISBN = "9780552574471";

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @NonNull
    private Pair<Locale, Book> create01() {
        //DataManager{rawData=Bundle[{
        // description=Data from Amazon UK as of 2016-09-07.,
        // language=eng,
        // toc_list=[TocEntry{id=0,
        //                    author=Author{id=0,
        //                                  familyName=`Pratchett`,
        //                                  givenNames=`Terry`,
        //                                  complete=false,
        //                                  role=0b0: Role{},
        //                                  realAuthor=null},
        //                    title=`The Shepherd's Crown`,
        //                    firstPublicationDate=`PartialDate{localDate=2015-01-01,
        //                                                      yearSet=true,
        //                                                      monthSet=false,
        //                                                      daySet=false}`,
        //                    bookCount=`1`}],
        // format=tp,
        // first_publication=2015,
        // date_published=2016-06-02,
        // fileSpec_array:0=[/storage/emulated/0/Android/data/com.hardbacknutter.nevertoomanybooks.debug/files/Pictures/tmp/1701443226640_isfdb_9780552574471_0_.jpg],
        // __ISFDB_BOOK_TYPE=NOVEL,
        // isbn=9780552574471,
        // pages=332,
        // title=The Shepherd's Crown,
        // list_price=7.99,
        // author_list=[Author{id=0,
        //                     familyName=`Pratchett`,
        //                     givenNames=`Terry`,
        //                     complete=false,
        //                     role=0b0: Role{},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Kidby`,
        //                     givenNames=`Paul`,
        //                     complete=false,
        //                     role=0b100000000: Role{COVER_ARTIST},
        //                     realAuthor=null}],
        // publisher_list=[Publisher{id=0, name=`Corgi`}],
        // isfdb_book_id=568139,
        // __ISFDB_ISBN2=0552574473,
        // list_price_currency=GBP}]}

        final Book book = new Book();
        book.setDescription("Data from Amazon UK as of 2016-09-07");
        book.setLanguage("eng");

        book.setToc(List.of(
                new TocEntry(new Author("Pratchett", "Terry"),
                             "The Shepherd's Crown",
                             new PartialDate(2015, 1, 1))));

        book.setFormat("tp");
        book.setFirstPublicationDate(2015);
        book.setPublicationDate("2016-06-02");

//        final ArrayList<String> fileSpecs = new ArrayList<>();
//        fileSpecs.add(
//                "/storage/emulated/0/Android/data/com.hardbacknutter.nevertoomanybooks.debug/files/Pictures/tmp/1701443226640_isfdb_9780552574471_0_.jpg");
//        book.putStringArrayList(CoverFileSpecArray.BKEY_FILE_SPEC_ARRAY[0], fileSpecs);

        book.putString(IsfdbSearchEngine.SiteField.BOOK_TYPE, "NOVEL");
        book.setRawProductCode("9780552574471");
        book.setPages(332);
        book.setTitle("The Shepherd's Crown");
        book.setPriceListed(new Money(new BigDecimal("7.99"),
                                      Currency.getInstance(MoneyParser.GBP)));

        book.setAuthors(List.of(
                new Author("Pratchett", "Terry"),
                new Author("Kidby", "Paul")
                        .setRole(AuthorRole.ARTIST)
        ));

        book.setPublishers(List.of(new Publisher("Corgi")));

        book.setIdentifiers(List.of(new Identifier.Value(Identifier.SID_ISFDB, "568139")));

        book.putString(IsfdbSearchEngine.SiteField.ISBN_2, "0552574473");

        return new Pair<>(Locale.US, book);
    }

    @NonNull
    private Pair<Locale, Book> create02() {
        //en_GB
        //DataManager{rawData=Bundle[{
        // series_list=[Series{id=0,
        //                     title=`Discworld Novels`,
        //                     complete=false,
        //                     number=``}],
        // language=English,
        // format=Paperback,
        // asin=0552574473,
        // isbn=978-0552574471,
        // pages=336,
        // title=The Shepherd's Crown: A Discworld Novel, Volume 41 ,
        // list_price=7.29,
        // author_list=[Author{id=0,
        //                     familyName=`Pratchett`,
        //                     givenNames=`Terry`,
        //                     complete=false,
        //                     role=0b1: Role{TYPE_WRITER},
        //                     realAuthor=null}],
        // publisher_list=[Publisher{id=0,
        //                           name=`Corgi Childrens`}],
        // list_price_currency=GBP}]}

        final Book book = new Book();
        book.setSeries(List.of(new Series("Discworld Novels")));
        book.setLanguage("English");
        book.setFormat("Paperback");
        book.setRawProductCode("978-0552574471");
        book.setPages(336);
        book.setTitle("The Shepherd's Crown: A Discworld Novel, Volume 41");
        book.setPriceListed(new Money(new BigDecimal("7.29"),
                                      Currency.getInstance(MoneyParser.GBP)));
        book.setAuthors(List.of(
                new Author("Pratchett", "Terry")
                        .setRole(AuthorRole.WRITER)));
        book.setPublishers(List.of(new Publisher("Corgi Childrens")));

        book.setIdentifiers(List.of(new Identifier.Value(Identifier.SID_ASIN, "0552574473")));

        return new Pair<>(Locale.UK, book);
    }

    @NonNull
    private Pair<Locale, Book> create03() {
        //nl_NL
        //DataManager{rawData=Bundle[{
        // series_list=[Series{id=0,
        //              title=`Discworld Novels`,
        //              complete=false, number=``}],
        // description=An old enemy is gathering strength. This is a time of endings and beginnings, old friends and new, a blurring of edges and a shifting of power. Now Tiffany stands between the light and the dark, the good and the bad. As the fairy horde prepares for invasion, Tiffany must summon all the witches to stand with her.,
        // language=en,
        // format=Paperback,
        // rating=4.8,
        // date_published=2016-06-02,
        // fileSpec_array:0=[/storage/emulated/0/Android/data/com.hardbacknutter.nevertoomanybooks.debug/files/Pictures/tmp/1701443226896_bol_9780552574471_0_.jpg],
        // isbn=9780552574471,
        // pages=335,
        // title=Shepherds Crown,
        // list_price=8.87,
        // author_list=[Author{id=0,
        //                     familyName=`Pratchett`,
        //                     givenNames=`Terry`,
        //                     complete=false,
        //                     role=0b1: Role{WRITER},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Kidby`,
        //                     givenNames=`Paul`,
        //                     complete=false,
        //                     role=0b1000000000000: Role{ARTIST},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Ellen Andersen`,
        //                     givenNames=`Laura`,
        //                     complete=false,
        //                     role=0b1000000000000: Role{ARTIST},
        //                     realAuthor=null}],
        // publisher_list=[Publisher{id=0,
        //                           name=`Corgi Childrens`}],
        // list_price_currency=EUR}]}

        final Book book = new Book();
        book.setSeries(List.of(new Series("Discworld Novels")));
        book.setDescription(
                "An old enemy is gathering strength. This is a time of endings"
                + " and beginnings, old friends and new, a blurring of edges and a"
                + " shifting of power. Now Tiffany stands between the light and the dark,"
                + " the good and the bad. As the fairy horde prepares for invasion,"
                + " Tiffany must summon all the witches to stand with her.");
        book.setLanguage("en");
        book.setFormat("Paperback");
        book.setRating(4.8f);
        book.setPublicationDate("2016-06-02");

//        final ArrayList<String> fileSpecs = new ArrayList<>();
//        fileSpecs.add(
//                "/storage/emulated/0/Android/data/com.hardbacknutter.nevertoomanybooks.debug/files/Pictures/tmp/1701443226896_bol_9780552574471_0_.jpg");
//        book.putStringArrayList(CoverFileSpecArray.BKEY_FILE_SPEC_ARRAY[0], fileSpecs);

        book.setRawProductCode("9780552574471");
        book.setPages(335);
        book.setTitle("Shepherds Crown");
        book.setPriceListed(new Money(new BigDecimal("8.87"), Money.EURO));

        book.setAuthors(List.of(
                new Author("Pratchett", "Terry")
                        .setRole(AuthorRole.WRITER),
                new Author("Kidby", "Paul")
                        .setRole(AuthorRole.ARTIST),
                new Author("Ellen Andersen", "Laura")
                        .setRole(AuthorRole.ARTIST)
        ));

        book.setPublishers(List.of(new Publisher("Corgi Childrens")));

        return new Pair<>(new Locale("nl", "NL"), book);
    }

    private Book createResult_01_02_03() {
        // result:
        //DataManager{rawData=Bundle[{
        // series_list=[Series{id=0,
        //                     title=`Discworld Novels`,
        //                     complete=false,
        //                     number=``},
        //              Series{id=0,
        //                     title=`Discworld Novels`,
        //                     complete=false,
        //                     number=``}],
        // description=Data from Amazon UK as of 2016-09-07.,
        // language=eng,
        // toc_list=[TocEntry{id=0,
        //                    author=Author{id=0,
        //                                  familyName=`Pratchett`,
        //                                  givenNames=`Terry`,
        //                                  complete=false,
        //                                  role=0b0: Role{},
        //                                  realAuthor=null},
        //                                  title=`The Shepherd's Crown`,
        //                                  firstPublicationDate=`PartialDate{localDate=2015-01-01,
        //                                                                     yearSet=true,
        //                                                                     monthSet=false,
        //                                                                     daySet=false}`,
        //                                  bookCount=`1`}],
        // format=Trade Paperback,
        // rating=4.8,
        // first_publication=2015,
        // date_published=2016-06-02,
        // Book:fileSpec:0=/storage/emulated/0/Android/data/com.hardbacknutter.nevertoomanybooks.debug/files/Pictures/tmp/1701443472874_bol_9780552574471_0_.jpg,
        // __ISFDB_BOOK_TYPE=NOVEL,
        // asin=0552574473,
        // isbn=9780552574471,
        // pages=332,
        // title=The Shepherd's Crown,
        // list_price=7.99,
        // author_list=[Author{id=0,
        //                     familyName=`Pratchett`,
        //                     givenNames=`Terry`,
        //                     complete=false,
        //                     role=0b0: Role{},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Kidby`,
        //                     givenNames=`Paul`,
        //                     complete=false,
        //                     role=0b100000000: Role{COVER_ARTIST},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Pratchett`,
        //                     givenNames=`Terry`,
        //                     complete=false,
        //                     role=0b1: Role{WRITER},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Pratchett`,
        //                     givenNames=`Terry`,
        //                     complete=false,
        //                     role=0b1: Role{WRITER},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Kidby`,
        //                     givenNames=`Paul`,
        //                     complete=false,
        //                     role=0b1000000000000: Role{ARTIST},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Ellen Andersen`,
        //                     givenNames=`Laura`,
        //                     complete=false,
        //                     role=0b1000000000000: Role{ARTIST},
        //                     realAuthor=null}],
        // publisher_list=[Publisher{id=0, name=`Corgi`},
        //                 Publisher{id=0, name=`Corgi Childrens`},
        //                 Publisher{id=0, name=`Corgi Childrens`}],
        // isfdb_book_id=568139,
        // __ISFDB_ISBN2=0552574473,
        // list_price_currency=GBP}]}

        final Book book = new Book();
        book.setSeries(List.of(
                new Series("Discworld Novels"),
                new Series("Discworld Novels")));
        book.setDescription("Data from Amazon UK as of 2016-09-07");
        book.setLanguage("eng");
        book.setToc(List.of(
                new TocEntry(new Author("Pratchett", "Terry"),
                             "The Shepherd's Crown",
                             new PartialDate(2015, 1, 1))));

        book.setFormat("Trade Paperback");
        book.setRating(4.8f);
        book.setFirstPublicationDate(2015);
        book.setPublicationDate("2016-06-02");

//        final ArrayList<String> fileSpecs = new ArrayList<>();
//        fileSpecs.add(
//                "/storage/emulated/0/Android/data/com.hardbacknutter.nevertoomanybooks.debug/files/Pictures/tmp/1701443472874_bol_9780552574471_0_.jpg");
//        book.putStringArrayList(CoverFileSpecArray.BKEY_FILE_SPEC_ARRAY[0], fileSpecs);

        book.putString(IsfdbSearchEngine.SiteField.BOOK_TYPE, "NOVEL");
        book.setRawProductCode("9780552574471");
        book.setPages(332);
        book.setTitle("The Shepherd's Crown");
        book.setPriceListed(new Money(new BigDecimal("7.99"),
                                      Currency.getInstance(MoneyParser.GBP)));

        book.setAuthors(List.of(
                new Author("Pratchett", "Terry"),
                new Author("Kidby", "Paul")
                        .setRole(AuthorRole.ARTIST),
                new Author("Pratchett", "Terry")
                        .setRole(AuthorRole.WRITER),
                new Author("Pratchett", "Terry")
                        .setRole(AuthorRole.WRITER),
                new Author("Kidby", "Paul")
                        .setRole(AuthorRole.ARTIST),
                new Author("Ellen Andersen", "Laura")
                        .setRole(AuthorRole.ARTIST)
        ));

        book.setPublishers(List.of(
                new Publisher("Corgi"),
                new Publisher("Corgi Childrens"),
                new Publisher("Corgi Childrens")
        ));
        book.setIdentifiers(List.of(
                new Identifier.Value(Identifier.SID_ASIN, "0552574473"),
                new Identifier.Value(Identifier.SID_ISFDB, "568139")
        ));

        book.putString(IsfdbSearchEngine.SiteField.ISBN_2, "0552574473");

        return book;
    }

    @Test
    void process() {
        final ResultsAccumulator resultsAccumulator =
                new ResultsAccumulator(context, serviceLocator::getLanguages);

        final List<Pair<Locale, Book>> results = new ArrayList<>();
        results.add(create01());
        results.add(create02());
        results.add(create03());

        final Book book = new Book();
        book.setRawProductCode(SEARCH_ISBN);

        resultsAccumulator.process(context, results, book);

        final Book expected = createResult_01_02_03();

        // compare the special list types first
        cmpList(expected, book, Book::getAuthors, b -> b.setAuthors(List.of()));
        cmpList(expected, book, Book::getSeries, b -> b.setSeries(List.of()));
        cmpList(expected, book, Book::getPublishers, b -> b.setPublishers(List.of()));
        cmpList(expected, book, Book::getToc, b -> b.setToc(List.of()));
        cmpList(expected, book, Book::getTags, b -> b.setTags(List.of()));
        cmpList(expected, book, Book::getIdentifiers, b -> b.setIdentifiers(List.of()));

        // finally compare all 'simple' fields
        assertEquals(expected, book);
    }

    private void cmpList(@NonNull final Book expected,
                         @NonNull final Book book,
                         @NonNull final Function<Book, List<?>> getList,
                         @NonNull final Consumer<Book> emptyList) {
        final List<?> eList = getList.apply(expected);
        final List<?> bList = getList.apply(book);
        assertEquals(eList.size(), bList.size());
        //noinspection SuspiciousMethodCalls
        assertTrue(eList.containsAll(bList));
        //noinspection SuspiciousMethodCalls
        assertTrue(bList.containsAll(eList));
        emptyList.accept(expected);
        emptyList.accept(book);
    }
}
