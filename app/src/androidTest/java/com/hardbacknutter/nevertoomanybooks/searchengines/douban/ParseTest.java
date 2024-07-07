/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.douban;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";
    private static final String UTF_8 = "UTF-8";
    private DoubanSearchEngine searchEngine;

    private RealNumberParser realNumberParser;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (DoubanSearchEngine) EngineId.Douban.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        realNumberParser = new RealNumberParser(List.of(searchEngine.getLocale(context)));
    }

    @Test
    public void parseMulti()
            throws IOException {

        final String locationHeader = "https://search.douban.com/book/subject_search"
                                      + "?search_text=9787536692930";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_multi_9787536692930;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Optional<String> oUrl = searchEngine.parseMultiResult(document);

        assertTrue(oUrl.isPresent());
        assertEquals("https://book.douban.com/subject/36874304/", oUrl.get());
    }

    @Test
    public void parse01()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://book.douban.com/subject/36874304/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_9787536692930;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("三体", book.getString(DBKey.TITLE, null));
        assertEquals("zho", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2011-06", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("302", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("9787536692930", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("平装", book.getString(DBKey.FORMAT, null));

        assertEquals(23d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.CNY, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertEquals("36874304", book.getString(DBKey.SID_DOUBAN, null));

        //noinspection LongLine
        assertEquals(
                "<p>军方探寻外星文明的绝秘计划“红岸工程”取得了突破性进展。但在按下发射键的那一刻，历经劫难的叶文洁没有意识到，她彻底改变了人类的命运。地球文明向宇宙发出的第一声啼鸣，以太阳为中心，以光速向宇宙深处飞驰……</p>\n"
                + "<p>四光年外，“三体文明”正苦苦挣扎——三颗无规则运行的太阳主导下的百余次毁灭与重生逼迫他们逃离母星。而恰在此时。他们接收到了地球发来的信息。在运用超技术锁死地球人的基础科学之后。三体人庞大的宇宙舰队开始向地球进发……</p>\n"
                + "<p>人类的末日悄然来临。</p>",
                book.getString(DBKey.DESCRIPTION, null));


        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("重庆出版社", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("刘慈欣", authors.get(0).getFamilyName());
        assertEquals("", authors.get(0).getGivenNames());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());
        final Series expectedSeries;
        expectedSeries = new Series("科幻世界·中国科幻基石丛书");
        assertEquals(expectedSeries, series.get(0));

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Douban.getPreferenceKey()
                                          + "_9787536692930_0_.jpg"));

    }
}
