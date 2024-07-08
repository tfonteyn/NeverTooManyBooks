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

import androidx.preference.PreferenceManager;

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
import static org.junit.Assert.assertFalse;
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

    private void setOrderNewestFirst(final boolean value) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit().putBoolean(DoubanSearchEngine.PK_USE_NEWEST_RESULT, value).apply();
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
    public void parseMulti9787536692930()
            throws IOException {

        final String locationHeader = "https://search.douban.com/book/subject_search"
                                      + "?search_text=9787536692930";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_multi_9787536692930;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        Optional<String> oUrl;

        setOrderNewestFirst(false);
        oUrl = searchEngine.parseMultiResult(context, document, "9787536692930");
        assertTrue(oUrl.isPresent());
        assertEquals("https://book.douban.com/subject/36874304/", oUrl.get());

        setOrderNewestFirst(true);
        oUrl = searchEngine.parseMultiResult(context, document, "9787536692930");
        assertTrue(oUrl.isPresent());
        assertEquals("https://book.douban.com/subject/36892731/", oUrl.get());
    }

    @Test
    public void parse9787536692930_36892731()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://book.douban.com/subject/36892731/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_9787536692930_36892731;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("三体", book.getString(DBKey.TITLE, null));
        assertEquals("zho", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2021-01-01", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("300", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("9787536692930", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("精装", book.getString(DBKey.FORMAT, null));

        assertFalse(book.contains(DBKey.PRICE_LISTED));
        assertFalse(book.contains(DBKey.PRICE_LISTED_CURRENCY));

        assertEquals("36892731", book.getString(DBKey.SID_DOUBAN, null));

        //noinspection LongLine
        assertEquals(
                "<p>文化大革命如火如荼进行的同时。军方探寻外星文明的绝秘计划“红岸工程”取得了突破性进展。但在按下发射键的那一刻，历经劫难的叶文洁没有意识到，她彻底改变了人类的命运。地球文明向宇宙发出的第一声啼鸣，以太阳为中心，以光速向宇宙深处飞驰……</p>\n" +
                "<p>四光年外，“三体文明”正苦苦挣扎——三颗无规则运行的太阳主导下的百余次毁灭与重生逼迫他们逃离母星。而恰在此时。他们接收到了地球发来的信息。在运用超技术锁死地球人的基础科学之后。三体人庞大的宇宙舰队开始向地球进发……</p>\n" +
                "<p>人类的末日悄然来临。</p>",
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
        assertEquals(0, series.size());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Douban.getPreferenceKey()
                                          + "_9787536692930_0_.jpg"));
    }

    @Test
    public void parse9787536692930_36874304()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://book.douban.com/subject/36874304/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_9787536692930_36874304;

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

    @Test
    public void parseMulti9787549641864()
            throws IOException {

        final String locationHeader = "https://search.douban.com/book/subject_search"
                                      + "?search_text=9787549641864&cat=1001";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_multi_9787549641864;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        // There is only one result.
        final Optional<String> oUrl =
                searchEngine.parseMultiResult(context, document, "9787549641864");
        assertTrue(oUrl.isPresent());
        assertEquals("https://book.douban.com/subject/36665775/", oUrl.get());
    }

    @Test
    public void parse9787549641864()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://book.douban.com/subject/36665775/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_9787549641864_36665775;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("第七重解答", book.getString(DBKey.TITLE, null));
        assertEquals("La Septième Hypothèse", book.getString(DBKey.TITLE_ORIGINAL_LANG, null));
        assertEquals("zho", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2024-04-30", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("288", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("9787549641864", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("平装", book.getString(DBKey.FORMAT, null));
        assertEquals(4f, book.getFloat(DBKey.RATING, realNumberParser), 0.1);

        assertEquals(45d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.CNY, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertEquals("36665775", book.getString(DBKey.SID_DOUBAN, null));

        //noinspection LongLine
        assertEquals(
                "<p>反转反转反转，再反转再反转，再神级反转的绝世推理经典!</p>\n" +
                "<p>-----------------------------------------------------------------------------------</p>\n" +
                "<p>\uD83D\uDCD6编辑推荐</p>\n" +
                "<p>◎ 不可能犯罪之王保罗·霍尔特绝版名作</p>\n" +
                "<p>◎ 入选“豆瓣推理小说Top 100”，豆瓣评分8.6！</p>\n" +
                "<p>◎ 教科书级反转神作，旧版价格炒到10倍+</p>\n" +
                "<p>◎ 鸟嘴医生之谜×垃圾桶藏尸之谜×人体瞬移之谜×谋杀决斗之谜</p>\n" +
                "<p>◎ 融推理三巨头之长，诡计与逻辑的惊艳结合</p>\n" +
                "<p>◎ 让岛田庄司、绫辻行人推崇备至的推理大师</p>\n" +
                "<p>◎ 翻开本书，共赴一场反转反转再反转的推理盛宴</p>\n" +
                "<p>-----------------------------------------------------------------------------------</p>\n" +
                "<p>\uD83D\uDCD6内容简介</p>\n" +
                "<p>午夜时分，中世纪的鸟嘴医生游荡在现代街头。</p>\n" +
                "<p>幽暗小巷，无名男尸凭空消失又凭空出现。</p>\n" +
                "<p>密闭走廊，染病房客在众目睽睽之下人间蒸发。</p>\n" +
                "<p>诡异书房，昔日好友发起致命的杀人对决。</p>\n" +
                "<p>…………</p>\n" +
                "<p>1938年的伦敦城内，怪事一桩接着一桩发生。</p>\n" +
                "<p>侦探二人组穷举所有可能性，却始终不得其解。</p>\n" +
                "<p>案件或许要历经六重反转，</p>\n" +
                "<p>才能迎来揭露真相的第七重解答！</p>\n" +
                "<p>-----------------------------------------------------------------------------------</p>\n" +
                "<p>\uD83D\uDCD6内文金句</p>\n" +
                "<p>1. 在绝对诚实的人和长期撒谎的人之间，存在着不同程度的撒谎者。</p>\n" +
                "<p>2. 在我们面前的是一张拼图，其中显然没有任何一块碎片能拼得起来，而且随着事件的发酵，碎片的数量还在不断增加。</p>\n" +
                "<p>3. 我们太过沉迷于这种组合罪犯的把戏，反而忽略了最重要的因素一一人的因素。</p>\n" +
                "<p>-----------------------------------------------------------------------------------</p>\n" +
                "<p>\uD83D\uDCD6媒体评价</p>\n" +
                "<p>1.“传统”“反潮流”是对霍尔特作品的常用定义。他积累了近40本小说的疯狂谜题和不可能犯罪，成为了约翰·迪克森·卡尔的唯一继承人。——霍尔特官方主页</p>\n" +
                "<p>2. 保罗·霍尔特的小说融合了灵异的艺术氛围和高超的经典情节，这些特质让他的小说充满令人难以抗拒的魅力。——《埃勒里·奎因推理杂志》</p>\n" +
                "<p>3. 当您进入霍尔特笔下的故事时，千万当心！他会让您接受难以置信的传说和离奇的故事，让您相信不可能的事物和疯狂的解释。——罗兰·拉库布（法国知名作家、编辑）</p>",
                book.getString(DBKey.DESCRIPTION, null));


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

        author = authors.get(1);
        assertEquals("朱寒依", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_TRANSLATOR, author.getType());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());
        final Series expectedSeries;
        expectedSeries = new Series("读客悬疑文库从书");
        assertEquals(expectedSeries, series.get(0));

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Douban.getPreferenceKey()
                                          + "_9787549641864_0_.jpg"));
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
    public void parseMulti9787532190294()
            throws IOException {

        final String locationHeader = "https://search.douban.com/book/subject_search" +
                                      "?search_text=9787532190294&cat=1001";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_multi_9787532190294;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        Optional<String> oUrl;

        // The first item with id="25930607" is an 'empty' book and will be rejected.
        // Instead we'll should return the second item
        setOrderNewestFirst(false);
        oUrl = searchEngine.parseMultiResult(context, document, "9787532190294");
        assertTrue(oUrl.isPresent());
        assertEquals("https://book.douban.com/subject/36897178/", oUrl.get());

        // The newest is a valid book
        setOrderNewestFirst(true);
        oUrl = searchEngine.parseMultiResult(context, document, "9787532190294");
        assertTrue(oUrl.isPresent());
        assertEquals("https://book.douban.com/subject/36897178/", oUrl.get());
    }

    @Test
    public void parse9787532190294_36897178()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://book.douban.com/subject/36897178/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_9787532190294_36897178;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("伽利略的错误", book.getString(DBKey.TITLE, null));
        assertEquals("Galileo's Error: Foundations for a New Science of Consciousness",
                     book.getString(DBKey.TITLE_ORIGINAL_LANG, null));
        assertEquals("zho", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2024-06", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("226", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("9787532190294", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("平装", book.getString(DBKey.FORMAT, null));
        assertFalse(book.contains(DBKey.RATING));

        assertEquals(58d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.CNY, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertEquals("36897178", book.getString(DBKey.SID_DOUBAN, null));

        //noinspection LongLine
        assertEquals(
                "<p>自然科学在解释意识上的失败，似乎让我们不得不在物理主义和某种二元论之间做出选择，前者意味着要否认我们的意识和主观体验的实在性，后者则带来了更多问题。作为第三种方案的“泛心论”近年来备受哲学家和神经科学家关注，不少人认为它离解决意识问题的目标更近；根据这一理论，意识是 物理世界中基本的、普遍存在的特征，我们的意识在物理世界中看起来之所以独特，是因为它是我们目前唯一拥有的通达物质内在本质的窗口。本书回顾了三种路径在解决意识问题方面的优势与缺陷，以简明扼要的哲学论证为泛心论做出了有力的辩护，是一部通达意识科学研究前沿的入门佳作。</p>\n" +
                "<p>----------------------------</p>\n" +
                "<p>\uD83E\uDDE0我怎么知道我是否存在？我等于我的大脑吗？200页无门槛直抵心灵哲学核心议题</p>\n" +
                "<p>\uD83D\uDD34我们看到的红色从何而来？疼痛的感觉能否还原为特定神经元放电？深入分析“解释鸿沟”三种方案</p>\n" +
                "<p>\uD83E\uDD16人类意识是否独一无二？人工智能会有自我意识吗？从“泛心论”出发重新观察世界</p>\n" +
                "<p>----------------------------</p>\n" +
                "<p>“本书是新一代哲学家的宣言，他们认为我们需要修正对物理世界的看法，以适应意识。伽利略把心灵从物质中抽离出来，这对物质科学来说是好事，但对心灵科学来说不见得如此。菲利普·高夫认为，要解释意识，我们必须把心灵放回物质中。他的想法很激进，但他的论证很严谨，这本书读起来很愉快。我向任何想要了解意识之谜的人推荐这本书。”</p>\n" +
                "<p>——大卫·查莫斯，《有意识的心灵》作者、纽约大学哲学教授</p>\n" +
                "<p>“菲利普·高夫在本书中提出了一种科学研究意识的新方法。他以通俗易懂、引人入胜的方式分析了为什么我们的感觉经验仍然无法得到科学解释，为什么将意识描述为物质基本特征的理论一直被忽视，以及为什么这些理论现在值得认真考虑。谁要是对意识研究的未来感兴趣，这就是他的必读书。”</p>\n" +
                "<p>——安娜卡·哈里斯，科普作家</p>\n" +
                "<p>“菲利普·高夫写了一本非常通俗易懂、趣味横生的书，介绍并捍卫了一种日渐流行（虽然乍看之下有些古怪）的观点：意识无处不在，物质不会因其排列组合突然间闪现出意识，意识一开始就存在。关于这一引人入胜的话题，再没有比本书更好的导论了。”</p>\n" +
                "<p>——史蒂芬·劳，牛津大学继续教育系哲学主任</p>",
                book.getString(DBKey.DESCRIPTION, null));


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

        author = authors.get(1);
        assertEquals("傅星源", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_TRANSLATOR, author.getType());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(0, series.size());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Douban.getPreferenceKey()
                                          + "_9787532190294_0_.jpg"));
    }
}
