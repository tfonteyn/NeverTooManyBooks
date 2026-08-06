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

package com.hardbacknutter.nevertoomanybooks.searchengines.douban;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"LongLine", "UnnecessaryUnicodeEscape", "JavadocLinkAsPlainText"})
class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";
    private static final String UTF_8 = "UTF-8";
    private DoubanSearchEngine searchEngine;

    private RealNumberParser ratingNumberParser;
    private MoneyParser moneyParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (DoubanSearchEngine) EngineId.Douban.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);

        final Locale siteLocale = searchEngine.getLocale(context);
        final List<Locale> allLocales = List.of(siteLocale);
        ratingNumberParser = new RealNumberParser(allLocales);
        moneyParser = new MoneyParser(siteLocale, allLocales);
    }

    private void setFetchMostRecent(final boolean value) {
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit().putBoolean(DoubanSearchEngine.PK_FETCH_MOST_RECENT, value).apply();
    }

    /**
     * <pre>
     *         window.__DATA__ = {
     *         "count": 15,
     *         "error_info": "",
     *         "items": [
     *             {
     *                 "abstract": "\u5218\u6148\u6b23 / \u91cd\u5e86\u51fa\u7248\u793e / 2011-6 / 23.00\u5143",
     *                 "abstract_2": "",
     *                 "cover_url": "https://img1.doubanio.com/view/subject/m/public/s34850048.jpg",
     *                 "extra_actions": [],
     *                 "id": 36874304,
     *                 "interest": null,
     *                 "label_actions": [],
     *                 "labels": [],
     *                 "more_url": "onclick=\"moreurl(this,{from:'book_subject_search',subject_id:'36874304',query:'9787536692930',i:'0',cat_id:'1001'})\"",
     *                 "rating": {
     *                     "count": 0,
     *                     "rating_info": "\u8bc4\u4ef7\u4eba\u6570\u4e0d\u8db3",
     *                     "star_count": 0,
     *                     "value": 0
     *                 },
     *                 "title": "\u4e09\u4f53",
     *                 "topics": [],
     *                 "tpl_name": "search_subject",
     *                 "url": "https://book.douban.com/subject/36874304/"
     *             },
     *             {
     *                 "abstract": "\u5218\u6148\u6b23 / \u91cd\u5e86\u51fa\u7248\u793e / 2021-1-1",
     *                 "abstract_2": "",
     *                 "cover_url": "https://img3.doubanio.com/view/subject/m/public/s34863232.jpg",
     *                 "extra_actions": [],
     *                 "id": 36892731,
     *                 "interest": null,
     *                 "label_actions": [],
     *                 "labels": [],
     *                 "more_url": "onclick=\"moreurl(this,{from:'book_subject_search',subject_id:'36892731',query:'9787536692930',i:'1',cat_id:'1001'})\"",
     *                 "rating": {
     *                     "count": 0,
     *                     "rating_info": "\u8bc4\u4ef7\u4eba\u6570\u4e0d\u8db3",
     *                     "star_count": 0,
     *                     "value": 0
     *                 },
     *                 "title": "\u4e09\u4f53",
     *                 "topics": [],
     *                 "tpl_name": "search_subject",
     *                 "url": "https://book.douban.com/subject/36892731/"
     *             },
     *             {
     *                 "abstract": "\u5218\u6148\u6b23 / \u91cd\u5e86\u51fa\u7248\u793e / 2008-1 / 23.00",
     *                 "abstract_2": "",
     *                 "cover_url": "https://img1.doubanio.com/view/subject/m/public/s2768378.jpg",
     *                 "extra_actions": [],
     *                 "id": 2567698,
     *                 "interest": null,
     *                 "label_actions": [],
     *                 "labels": [],
     *                 "more_url": "onclick=\"moreurl(this,{from:'book_subject_search',subject_id:'2567698',query:'9787536692930',i:'2',cat_id:'1001'})\"",
     *                 "rating": {
     *                     "count": 497927,
     *                     "rating_info": "",
     *                     "star_count": 4.5,
     *                     "value": 8.9
     *                 },
     *                 "title": "\u4e09\u4f53 : \u201c\u5730\u7403\u5f80\u4e8b\u201d\u4e09\u90e8\u66f2\u4e4b\u4e00",
     *                 "topics": [],
     *                 "tpl_name": "search_subject",
     *                 "url": "https://book.douban.com/subject/2567698/"
     *             }
     *         ],
     *         "report": {
     *             "qtype": "195",
     *             "tags": "\u8bfb\u4e66"
     *         },
     *         "start": 0,
     *         "text": "9787536692930",
     *         "total": 3
     *     };
     * </pre>
     */
    @Test
    void parseMulti9787536692930()
            throws IOException {

        final String locationHeader = "https://search.douban.com/book/subject_search?search_text=9787536692930";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_multi_9787536692930;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        String url;

        setFetchMostRecent(false);
        url = searchEngine.parseMultiResult(document);
        assertNotNull(url);
        assertEquals("https://book.douban.com/subject/36874304/", url);

        setFetchMostRecent(true);
        url = searchEngine.parseMultiResult(document);
        assertNotNull(url);
        assertEquals("https://book.douban.com/subject/36892731/", url);
    }

    @Test
    void parse9787536692930_36892731()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://book.douban.com/subject/36892731/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_9787536692930_36892731;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("三体", book.getString(DBKey.TITLE, null));
        assertEquals("9787536692930", book.getString(DBKey.ISBN, null));
        assertEquals("36892731", book.requireIdentifierValue(Identifier.SID_DOUBAN));

        assertEquals("zho", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2021-01-01", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("300", book.getString(DBKey.PAGES, null));
        assertEquals("精装", book.getString(DBKey.FORMAT, null));

        assertFalse(book.contains(DBKey.PRICE_LISTED));
        assertFalse(book.contains(DBKey.PRICE_LISTED_CURRENCY));

        final String description = book.getString(DBKey.DESCRIPTION, null);
        assertNotNull(description);
        assertTrue(description.startsWith("<p>文化大革命如火如荼进行的同时。"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("重庆出版社", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        final Author author;

        author = authors.get(0);
        assertEquals("刘慈欣", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertTrue(author.getIdentifierValue(Identifier.SID_DOUBAN).isEmpty());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(0, series.size());

        final String preferenceKey = EngineId.Douban.getPreferenceKey();
        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9787536692930_0_.jpg"));
    }

    @Test
    void parse9787536692930_36874304()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://book.douban.com/subject/36874304/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_9787536692930_36874304;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("三体", book.getString(DBKey.TITLE, null));
        assertEquals("9787536692930", book.getString(DBKey.ISBN, null));
        assertEquals("36874304", book.requireIdentifierValue(Identifier.SID_DOUBAN));

        assertEquals("zho", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2011-06", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("302", book.getString(DBKey.PAGES, null));
        assertEquals("平装", book.getString(DBKey.FORMAT, null));

        assertPriceListed(book, "23", MoneyParser.CNY, moneyParser);

        final String description = book.getString(DBKey.DESCRIPTION, null);
        assertNotNull(description);
        assertTrue(
                description.startsWith("<p>军方探寻外星文明的绝秘计划“红岸工程”取得了突破性进展。"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("重庆出版社", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        final Author author;

        author = authors.get(0);
        assertEquals("刘慈欣", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        //oIv = author.getIdentifierValue(Identifier.SID_DOUBAN);
        //assertTrue(oIv.isPresent());
        //assertEquals("4561353", oIv.get());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("科幻世界·中国科幻基石丛书", series.getTitle());
        assertEquals("38", series.getIdentifierValue(Identifier.SID_DOUBAN).orElse(null));

        final String preferenceKey = EngineId.Douban.getPreferenceKey();
        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9787536692930_0_.jpg"));
    }

    @Test
    void parseMulti9787549641864()
            throws IOException {

        final String locationHeader = "https://search.douban.com/book/subject_search"
                                      + "?search_text=9787549641864&cat=1001";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_multi_9787549641864;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        // There is only one result.
        final String url = searchEngine.parseMultiResult(document);
        assertNotNull(url);
        assertEquals("https://book.douban.com/subject/36665775/", url);
    }

    @Test
    void parse9787549641864()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://book.douban.com/subject/36665775/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_9787549641864_36665775;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("第七重解答", book.getString(DBKey.TITLE, null));
        assertEquals("La Septième Hypothèse",
                     book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("9787549641864", book.getString(DBKey.ISBN, null));
        assertEquals("36665775", book.requireIdentifierValue(Identifier.SID_DOUBAN));

        assertEquals("zho", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2024-04-30", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("288", book.getString(DBKey.PAGES, null));
        assertEquals("平装", book.getString(DBKey.FORMAT, null));
        assertEquals(4.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);

        assertPriceListed(book, "45", MoneyParser.CNY, moneyParser);

        final String description = book.getString(DBKey.DESCRIPTION, null);
        assertNotNull(description);
        assertTrue(
                description.startsWith("<p>反转反转反转，再反转再反转，再神级反转的绝世推理经典!"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("文汇出版社", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;

        author = authors.get(0);
        assertEquals("保罗·霍尔特 [法]", author.getFamilyName());
        assertEquals("", author.getGivenNames());
//        oIv = author.getIdentifierValue(Identifier.SID_DOUBAN);
//        assertTrue(oIv.isPresent());
//        assertEquals("322717", oIv.get());

        author = authors.get(1);
        assertEquals("朱寒依", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        assertTrue(author.getIdentifierValue(Identifier.SID_DOUBAN).isEmpty());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("读客悬疑文库从书", series.getTitle());
        assertEquals("51250", series.getIdentifierValue(Identifier.SID_DOUBAN).orElse(null));

        final String preferenceKey = EngineId.Douban.getPreferenceKey();
        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9787549641864_0_.jpg"));
    }

    /**
     * <pre>
     *         window.__DATA__ = {
     *         "count": 15,
     *         "error_info": "",
     *         "items": [
     *             {
     *                 "abstract": "",
     *                 "abstract_2": "",
     *                 "cover_url": "https://img1.doubanio.com/cuphead/book-static/pics/book-default-lpic.gif",
     *                 "extra_actions": [],
     *                 "id": 25930607,
     *                 "interest": null,
     *                 "label_actions": [],
     *                 "labels": [],
     *                 "more_url": "onclick=\"moreurl(this,{from:'book_subject_search',subject_id:'25930607',query:'9787532190294',i:'0',cat_id:'1001'})\"",
     *                 "rating": {
     *                     "count": 0,
     *                     "rating_info": "\u76ee\u524d\u65e0\u4eba\u8bc4\u4ef7",
     *                     "star_count": 0,
     *                     "value": 0
     *                 },
     *                 "title": "9787539190594",
     *                 "topics": [],
     *                 "tpl_name": "search_subject",
     *                 "url": "https://book.douban.com/subject/25930607/"
     *             },
     *             {
     *                 "abstract": "[\u82f1] \u83f2\u5229\u666e\u00b7\u9ad8\u592b / \u5085\u661f\u6e90 / \u4e0a\u6d77\u6587\u827a\u51fa\u7248\u793e / 2024-6 / 58",
     *                 "abstract_2": "",
     *                 "cover_url": "https://img1.doubanio.com/view/subject/m/public/s34875559.jpg",
     *                 "extra_actions": [],
     *                 "id": 36897178,
     *                 "interest": null,
     *                 "label_actions": [],
     *                 "labels": [],
     *                 "more_url": "onclick=\"moreurl(this,{from:'book_subject_search',subject_id:'36897178',query:'9787532190294',i:'1',cat_id:'1001'})\"",
     *                 "rating": {
     *                     "count": 0,
     *                     "rating_info": "\u8bc4\u4ef7\u4eba\u6570\u4e0d\u8db3",
     *                     "star_count": 0,
     *                     "value": 0
     *                 },
     *                 "title": "\u4f3d\u5229\u7565\u7684\u9519\u8bef : \u4e3a\u4e00\u79cd\u65b0\u7684\u610f\u8bc6\u79d1\u5b66\u5960\u57fa",
     *                 "topics": [],
     *                 "tpl_name": "search_subject",
     *                 "url": "https://book.douban.com/subject/36897178/"
     *             }
     *         ],
     *         "report": {
     *             "qtype": "195",
     *             "tags": "\u8bfb\u4e66"
     *         },
     *         "start": 0,
     *         "text": "9787532190294",
     *         "total": 2
     *     };
     * </pre>
     */
    @Test
    void parseMulti9787532190294()
            throws IOException {

        final String locationHeader = "https://search.douban.com/book/subject_search" +
                                      "?search_text=9787532190294&cat=1001";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_multi_9787532190294;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        String url;

        // The first item with id="25930607" is an 'empty' book and will be rejected.
        // Instead, we'll should return the second item
        setFetchMostRecent(false);
        url = searchEngine.parseMultiResult(document);
        assertNotNull(url);
        assertEquals("https://book.douban.com/subject/36897178/", url);

        // The most recent one is a valid book
        setFetchMostRecent(true);
        url = searchEngine.parseMultiResult(document);
        assertNotNull(url);
        assertEquals("https://book.douban.com/subject/36897178/", url);
    }

    @Test
    void parse9787532190294_36897178()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://book.douban.com/subject/36897178/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_9787532190294_36897178;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("伽利略的错误", book.getString(DBKey.TITLE, null));
        assertEquals("Galileo's Error: Foundations for a New Science of Consciousness",
                     book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("9787532190294", book.getString(DBKey.ISBN, null));
        assertEquals("36897178", book.requireIdentifierValue(Identifier.SID_DOUBAN));

        assertEquals("zho", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2024-06", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("226", book.getString(DBKey.PAGES, null));
        assertEquals("平装", book.getString(DBKey.FORMAT, null));
        assertFalse(book.contains(DBKey.RATING));

        assertPriceListed(book, "58", MoneyParser.CNY, moneyParser);

        final String description = book.getString(DBKey.DESCRIPTION, null);
        assertNotNull(description);
        assertTrue(description.startsWith("<p>自然科学在解释意识上的失败，似乎让我们不"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("上海文艺出版社", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;

        author = authors.get(0);
        assertEquals("菲利普·高夫 [英]", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertTrue(author.getIdentifierValue(Identifier.SID_DOUBAN).isEmpty());

        author = authors.get(1);
        assertEquals("傅星源", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        assertTrue(author.getIdentifierValue(Identifier.SID_DOUBAN).isEmpty());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(0, series.size());

        final String preferenceKey = EngineId.Douban.getPreferenceKey();
        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9787532190294_0_.jpg"));
    }
}
