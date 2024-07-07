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
    public void parseMulti9787536692930()
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
    public void parse9787536692930()
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

    @Test
    public void parseMulti9787549641864()
            throws IOException {

        final String locationHeader = "https://search.douban.com/book/subject_search"
                                      + "?search_text=9787549641864&cat=1001";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_multi_9787549641864;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Optional<String> oUrl = searchEngine.parseMultiResult(document);

        assertTrue(oUrl.isPresent());
        assertEquals("https://book.douban.com/subject/36665775/", oUrl.get());
    }

    @Test
    public void parse9787549641864()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://book.douban.com/subject/36665775/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.douban_9787549641864;

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
        assertEquals("保罗·霍尔特", author.getFamilyName());
        assertEquals("[法]", author.getGivenNames());

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

}
