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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The {@link CoverFileSpecArray#BKEY_FILE_SPEC_ARRAY} keys are not used here,
 * as the accumulator checks for the real files (which won't be there).
 * It is assumed that if Authors/Series/etc... get processed ok, then so will the fileSpecs.
 */
public class ResultsAccumulatorTest
        extends BaseDBTest {

    private static final String SEARCH_ISBN = "9780552574471";

    private final Locale systemLocale = Locale.US;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
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
        //                                  type=0b0: Type{},
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
        //                     type=0b0: Type{},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Kidby`,
        //                     givenNames=`Paul`,
        //                     complete=false,
        //                     type=0b100000000: Type{TYPE_COVER_ARTIST},
        //                     realAuthor=null}],
        // publisher_list=[Publisher{id=0, name=`Corgi`}],
        // isfdb_book_id=568139,
        // __ISFDB_ISBN2=0552574473,
        // list_price_currency=GBP}]}

        final Book book = new Book();
        book.putString(DBKey.DESCRIPTION, "Data from Amazon UK as of 2016-09-07");
        book.putString(DBKey.LANGUAGE, "eng");

        book.setToc(List.of(
                new TocEntry(new Author("Pratchett", "Terry", false),
                             "The Shepherd's Crown",
                             "2015-01-01")));

        book.putString(DBKey.FORMAT, "tp");
        book.putString(DBKey.FIRST_PUBLICATION__DATE, "2015");
        book.putString(DBKey.BOOK_PUBLICATION__DATE, "2016-06-02");

//        final ArrayList<String> fileSpecs = new ArrayList<>();
//        fileSpecs.add(
//                "/storage/emulated/0/Android/data/com.hardbacknutter.nevertoomanybooks.debug/files/Pictures/tmp/1701443226640_isfdb_9780552574471_0_.jpg");
//        book.putStringArrayList(CoverFileSpecArray.BKEY_FILE_SPEC_ARRAY[0], fileSpecs);

        book.putString("__ISFDB_BOOK_TYPE", "NOVEL");
        book.putString(DBKey.BOOK_ISBN, "9780552574471");
        book.putString(DBKey.PAGE_COUNT, "332");
        book.putString(DBKey.TITLE, "The Shepherd's Crown");
        book.putString(DBKey.PRICE_LISTED, "7.99");

        book.setAuthors(List.of(
                new Author("Pratchett", "Terry", false),
                new Author("Kidby", "Paul", false)
                        .setType(Author.TYPE_ARTIST)
        ));

        book.setPublishers(List.of(new Publisher("Corgi")));

        book.putString(DBKey.SID_ISFDB, "568139");
        book.putString("__ISFDB_ISBN2", "0552574473");
        book.putString(DBKey.PRICE_LISTED_CURRENCY, "GBP");

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
        //                     type=0b1: Type{TYPE_WRITER},
        //                     realAuthor=null}],
        // publisher_list=[Publisher{id=0,
        //                           name=`Corgi Childrens`}],
        // list_price_currency=GBP}]}

        final Book book = new Book();
        book.setSeries(List.of(
                new Series("Discworld Novels", false)
        ));
        book.putString(DBKey.LANGUAGE, "English");
        book.putString(DBKey.FORMAT, "Paperback");
        book.putString(DBKey.SID_ASIN, "0552574473");
        book.putString(DBKey.BOOK_ISBN, "978-0552574471");
        book.putString(DBKey.PAGE_COUNT, "336");
        book.putString(DBKey.TITLE, "The Shepherd's Crown: A Discworld Novel, Volume 41");
        book.putString(DBKey.PRICE_LISTED, "7.29");
        book.setAuthors(List.of(
                new Author("Pratchett", "Terry", false)
                        .setType(Author.TYPE_WRITER)));
        book.setPublishers(List.of(new Publisher("Corgi Childrens")));
        book.putString(DBKey.PRICE_LISTED_CURRENCY, "GBP");

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
        //                     type=0b1: Type{TYPE_WRITER},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Kidby`,
        //                     givenNames=`Paul`,
        //                     complete=false,
        //                     type=0b1000000000000: Type{TYPE_ARTIST},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Ellen Andersen`,
        //                     givenNames=`Laura`,
        //                     complete=false,
        //                     type=0b1000000000000: Type{TYPE_ARTIST},
        //                     realAuthor=null}],
        // publisher_list=[Publisher{id=0,
        //                           name=`Corgi Childrens`}],
        // list_price_currency=EUR}]}

        final Book book = new Book();
        book.setSeries(List.of(
                new Series("Discworld Novels", false)));
        book.putString(DBKey.DESCRIPTION,
                       "An old enemy is gathering strength. This is a time of endings and beginnings, old friends and new, a blurring of edges and a shifting of power. Now Tiffany stands between the light and the dark, the good and the bad. As the fairy horde prepares for invasion, Tiffany must summon all the witches to stand with her.");
        book.putString(DBKey.LANGUAGE, "en");
        book.putString(DBKey.FORMAT, "Paperback");
        book.putFloat(DBKey.RATING, 4.8f);
        book.putString(DBKey.BOOK_PUBLICATION__DATE, "2016-06-02");

//        final ArrayList<String> fileSpecs = new ArrayList<>();
//        fileSpecs.add(
//                "/storage/emulated/0/Android/data/com.hardbacknutter.nevertoomanybooks.debug/files/Pictures/tmp/1701443226896_bol_9780552574471_0_.jpg");
//        book.putStringArrayList(CoverFileSpecArray.BKEY_FILE_SPEC_ARRAY[0], fileSpecs);

        book.putString(DBKey.BOOK_ISBN, "9780552574471");
        book.putString(DBKey.PAGE_COUNT, "335");
        book.putString(DBKey.TITLE, "Shepherds Crown");
        book.putString(DBKey.PRICE_LISTED, "8.87");

        book.setAuthors(List.of(
                new Author("Pratchett", "Terry", false)
                        .setType(Author.TYPE_WRITER),
                new Author("Kidby", "Paul", false)
                        .setType(Author.TYPE_ARTIST),
                new Author("Ellen Andersen", "Laura", false)
                        .setType(Author.TYPE_ARTIST)
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
        //                                  type=0b0: Type{},
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
        //                     type=0b0: Type{},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Kidby`,
        //                     givenNames=`Paul`,
        //                     complete=false,
        //                     type=0b100000000: Type{TYPE_COVER_ARTIST},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Pratchett`,
        //                     givenNames=`Terry`,
        //                     complete=false,
        //                     type=0b1: Type{TYPE_WRITER},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Pratchett`,
        //                     givenNames=`Terry`,
        //                     complete=false,
        //                     type=0b1: Type{TYPE_WRITER},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Kidby`,
        //                     givenNames=`Paul`,
        //                     complete=false,
        //                     type=0b1000000000000: Type{TYPE_ARTIST},
        //                     realAuthor=null},
        //              Author{id=0,
        //                     familyName=`Ellen Andersen`,
        //                     givenNames=`Laura`,
        //                     complete=false,
        //                     type=0b1000000000000: Type{TYPE_ARTIST},
        //                     realAuthor=null}],
        // publisher_list=[Publisher{id=0, name=`Corgi`},
        //                 Publisher{id=0, name=`Corgi Childrens`},
        //                 Publisher{id=0, name=`Corgi Childrens`}],
        // isfdb_book_id=568139,
        // __ISFDB_ISBN2=0552574473,
        // list_price_currency=GBP}]}

        final Book book = new Book();
        book.setSeries(List.of(
                new Series("Discworld Novels", false),
                new Series("Discworld Novels", false)));
        book.putString(DBKey.DESCRIPTION, "Data from Amazon UK as of 2016-09-07");
        book.putString(DBKey.LANGUAGE, "eng");
        book.setToc(List.of(
                new TocEntry(new Author("Pratchett", "Terry", false),
                             "The Shepherd's Crown",
                             "2015-01-01")));

        book.putString(DBKey.FORMAT, "Trade Paperback");
        book.putFloat(DBKey.RATING, 4.8f);
        book.putString(DBKey.FIRST_PUBLICATION__DATE, "2015");
        book.putString(DBKey.BOOK_PUBLICATION__DATE, "2016-06-02");

//        final ArrayList<String> fileSpecs = new ArrayList<>();
//        fileSpecs.add(
//                "/storage/emulated/0/Android/data/com.hardbacknutter.nevertoomanybooks.debug/files/Pictures/tmp/1701443472874_bol_9780552574471_0_.jpg");
//        book.putStringArrayList(CoverFileSpecArray.BKEY_FILE_SPEC_ARRAY[0], fileSpecs);

        book.putString("__ISFDB_BOOK_TYPE", "NOVEL");
        book.putString(DBKey.SID_ASIN, "0552574473");
        book.putString(DBKey.BOOK_ISBN, "9780552574471");
        book.putString(DBKey.PAGE_COUNT, "332");
        book.putString(DBKey.TITLE, "The Shepherd's Crown");
        book.putString(DBKey.PRICE_LISTED, "7.99");

        book.setAuthors(List.of(
                new Author("Pratchett", "Terry", false),
                new Author("Kidby", "Paul", false)
                        .setType(Author.TYPE_ARTIST),
                new Author("Pratchett", "Terry", false)
                        .setType(Author.TYPE_WRITER),
                new Author("Pratchett", "Terry", false)
                        .setType(Author.TYPE_WRITER),
                new Author("Kidby", "Paul", false)
                        .setType(Author.TYPE_ARTIST),
                new Author("Ellen Andersen", "Laura", false)
                        .setType(Author.TYPE_ARTIST)
        ));

        book.setPublishers(List.of(
                new Publisher("Corgi"),
                new Publisher("Corgi Childrens"),
                new Publisher("Corgi Childrens")
        ));
        book.putString(DBKey.SID_ISFDB, "568139");
        book.putString("__ISFDB_ISBN2", "0552574473");
        book.putString(DBKey.PRICE_LISTED_CURRENCY, "GBP");

        return book;
    }

    @Test
    public void process() {
        final ResultsAccumulator resultsAccumulator =
                new ResultsAccumulator(context, systemLocale,
                                       serviceLocator::getLanguages);

        final Map<Locale, Book> results = new LinkedHashMap<>();
        Pair<Locale, Book> localeBookPair;

        localeBookPair = create01();
        results.put(localeBookPair.first, localeBookPair.second);
        localeBookPair = create02();
        results.put(localeBookPair.first, localeBookPair.second);
        localeBookPair = create03();
        results.put(localeBookPair.first, localeBookPair.second);

        final Book book = new Book();
        book.putString(DBKey.BOOK_ISBN, SEARCH_ISBN);

        resultsAccumulator.process(context, results, book);

        final Book expected = createResult_01_02_03();

        assertEquals(expected.getAuthors(), book.getAuthors());
        assertEquals(expected.getSeries(), book.getSeries());
        assertEquals(expected.getPublishers(), book.getPublishers());
        assertEquals(expected.getToc(), book.getToc());

        expected.setAuthors(List.of());
        expected.setSeries(List.of());
        expected.setPublishers(List.of());
        expected.setToc(List.of());

        book.setAuthors(List.of());
        book.setSeries(List.of());
        book.setPublishers(List.of());
        book.setToc(List.of());

        assertEquals(expected, book);
    }
}
