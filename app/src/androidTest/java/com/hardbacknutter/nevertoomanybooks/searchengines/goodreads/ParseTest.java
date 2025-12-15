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

package com.hardbacknutter.nevertoomanybooks.searchengines.goodreads;

import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONObject;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings({"MissingJavadoc", "UnnecessaryUnicodeEscape", "LongLine"})
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";
    private static final String UTF_8 = "UTF-8";

    private GoodreadsSearchEngine searchEngine;
    private RealNumberParser realNumberParser;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (GoodreadsSearchEngine) EngineId.Goodreads.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);

        realNumberParser = new RealNumberParser(List.of(searchEngine.getLocale(context)));

        // The Goodreads resolver is by default always true,
        // but the wikidata one is by default false.
        // For these tests we want both
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                         .putBoolean(EngineId.Goodreads.getPreferenceKey()
                                     + ".resolve.authors.wikidata", true)
                         .apply();
    }

    @Test
    public void parseNextDataJson9789604419197()
            throws IOException, StorageException, SearchException, CredentialsException {
        final Book book = new Book();
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.goodreads_next_data_9789604419197);
        searchEngine.parse(context, document, book, new boolean[]{true, false, false, false});
        Log.d(TAG, book.toString());

        assertEquals("Ο νόμος ποτέ δε κοιμάται...", book.getString(DBKey.TITLE, null));
        assertEquals("9789604419197", book.getString(DBKey.ISBN, null));
        assertEquals("ell", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("48", book.getString(DBKey.PAGES, null));
        assertEquals("L'Agent 212, Tome 01 : 24 heures sur 24",
                     book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("2007-12-01", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals(new PartialDate(1981, 1, 1), book.getFirstPublicationDate());
        assertEquals(4.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);

        assertEquals("9604419196", book.requireIdentifierValue(Identifier.SID_ASIN));
        assertEquals("33838921", book.requireIdentifierValue(Identifier.SID_GOODREADS));

        assertEquals("Indonesian edition of <i>BD Pirate :"
                     + " Agent 212, tome 1 : 24 heures sur 24</i>",
                     book.getString(DBKey.DESCRIPTION, null));

        final List<Tag> bookTags = book.getTags();
        assertEquals(6, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Comics"));
        assertTrue(tags.contains("Komik"));
        assertTrue(tags.contains("Bande Dessinée"));
        assertTrue(tags.contains("Indonesian Literature"));
        assertTrue(tags.contains("Humor"));
        assertTrue(tags.contains("Comedy"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Modern Times", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(3, authors.size());
        Author author;
        Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Cauvin", author.getFamilyName());
        assertEquals("Raoul", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1938-09-26", author.getBirthDate().orElse(null));
        assertEquals("2021-08-19", author.getDeathDate().orElse(null));
        assertEquals(11, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_GOODREADS);
        assertTrue(oIv.isPresent());
        assertEquals("115105", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_OPEN_LIBRARY);
        assertTrue(oIv.isPresent());
        assertEquals("OL1559643A", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q2037254", oIv.get());
        // We don't check all of them...

        author = authors.get(1);
        assertEquals("Kox", author.getFamilyName());
        assertEquals("Daniel", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertEquals("1952-02-04", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));
        assertEquals(9, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_GOODREADS);
        assertTrue(oIv.isPresent());
        assertEquals("120410", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q3014263", oIv.get());
        // We don't check all of them...

        author = authors.get(2);
        assertEquals("Γαλάτουλα", author.getFamilyName());
        assertEquals("Τατιάνα", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        assertNull(author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));
        assertEquals(1, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_GOODREADS);
        assertTrue(oIv.isPresent());
        assertEquals("7048501", oIv.get());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());
        assertEquals("L'Agent 212", series.get(0).getTitle());
        assertEquals("1", series.get(0).getNumber());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Goodreads.getPreferenceKey()
                                          + "_9789604419197_0_.jpg"));
    }

    @Test
    public void parseNextDataJson9789028453807()
            throws IOException, StorageException, SearchException, CredentialsException {

        final Book book = new Book();
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.goodreads_next_data_9789028453807);
        searchEngine.parse(context, document, book, new boolean[]{true, false, false, false});
        Log.d(TAG, book.toString());

        assertEquals("De chocoladewinkel van verloren liefdes", book.getString(DBKey.TITLE, null));
        assertEquals("9789028453807", book.getString(DBKey.ISBN, null));
        assertEquals("Dutch", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Kindle Edition", book.getString(DBKey.FORMAT, null));
        assertNull(book.getString(DBKey.PAGES, null));
        assertNull(book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("2024-10-21", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals(new PartialDate(2022, 10, 28), book.getFirstPublicationDate());
        assertEquals(3.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);

        assertEquals("B0D79GXMVR", book.requireIdentifierValue(Identifier.SID_ASIN));
        assertEquals("215846772", book.requireIdentifierValue(Identifier.SID_GOODREADS));

        assertEquals(
                "Johoo heeft een kleine chocoladewinkel in een steegje in Seoel."
                + " Haar klanten komen niet alleen voor overheerlijke chocolade, maar ook"
                + " voor het speciale liefdesadvies waar Johoo om bekendstaat. Ze luistert"
                + " geduldig naar alle verhalen, en heeft voor iedereen precies het soort"
                + " chocola dat diegene nodig heeft. Van een zevenjarige jongen die verliefd"
                + " is op een meisje uit zijn klas, tot een oude man die door zijn eigen vrouw"
                + " niet meer herkend wordt. Maar zou er ook een recept te vinden zijn voor de"
                + " verloren liefde van Johoo zelf?",
                book.getString(DBKey.DESCRIPTION, null));

        final List<Tag> tags = book.getTags();
        assertEquals(0, tags.size());

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Wereldbibliotheek", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());
        Optional<String> oIv;
        Author author;
        author = authors.get(0);
        assertEquals("Ye-eun", author.getFamilyName());
        assertEquals("Kim", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_GOODREADS);
        assertTrue(oIv.isPresent());
        assertEquals("50820550", oIv.get());

        author = authors.get(1);
        assertEquals("Nuanxed", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_GOODREADS);
        assertTrue(oIv.isPresent());
        assertEquals("40652983", oIv.get());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(0, series.size());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Goodreads.getPreferenceKey()
                                          + "_9789028453807_0_.jpg"));
    }

    @Test
    public void parseNextDataJson9780062683250()
            throws IOException, StorageException, SearchException, CredentialsException {

        final Book book = new Book();
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.goodreads_next_data_9780062683250);
        searchEngine.parse(context, document, book, new boolean[]{true, false, false, false});
        Log.d(TAG, book.toString());

        assertEquals("The Left-Handed Booksellers of London", book.getString(DBKey.TITLE, null));
        assertEquals("9780062683250", book.getString(DBKey.ISBN, null));
        assertEquals("English", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("416", book.getString(DBKey.PAGES, null));
        assertEquals("2020-09-22", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals(new PartialDate(2020, 9, 22), book.getFirstPublicationDate());
        assertEquals(4.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);

        assertEquals("006268325X", book.requireIdentifierValue(Identifier.SID_ASIN));
        assertEquals("49867186", book.requireIdentifierValue(Identifier.SID_GOODREADS));

        assertEquals("<strong>A girl’s quest to find her father leads her to an extended"
                     + " family of magical fighting booksellers who police the mythical Old World"
                     + " of England when it intrudes on the modern world."
                     + " From the bestselling master of teen fantasy, Garth Nix.</strong>"
                     + "<br /><br />In a slightly alternate London in 1983, Susan Arkshaw"
                     + " is looking for her father, a man she has never met."
                     + " Crime boss Frank Thringley might be able to help"
                     + " her, but Susan doesn’t get time to ask Frank any questions before he is"
                     + " turned to dust by the prick of a silver hatpin in the hands of the"
                     + " outrageously attractive Merlin.<br /><br />Merlin is a young left-handed"
                     + " bookseller (one of the fighting ones), who with the right-handed"
                     + " booksellers (the intellectual ones), are an extended family of magical"
                     + " beings who police the mythic and legendary Old World when it intrudes on"
                     + " the modern world, in addition to running several bookshops.<br /><br />"
                     + "Susan’s search for her father begins with her mother’s possibly"
                     + " misremembered or misspelt surnames, a reading room ticket, and a silver"
                     + " cigarette case engraved with something that might be a coat of arms."
                     + "<br /><br />Merlin has a quest of his own, to find the Old World entity"
                     + " who used ordinary criminals to kill his mother. As he and his sister,"
                     + " the right-handed bookseller Vivien, tread in the path of a botched or"
                     + " covered-up police investigation from years past, they find this quest"
                     + " strangely overlaps with Susan’s. Who or what was her father?"
                     + " Susan, Merlin, and Vivien must find out, as the Old World erupts"
                     + " dangerously into the New.",
                     book.getString(DBKey.DESCRIPTION, null));

        final List<Tag> bookTags = book.getTags();
        assertEquals(10, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Fantasy"));
        assertTrue(tags.contains("Young Adult"));
        assertTrue(tags.contains("Fiction"));
        assertTrue(tags.contains("Urban Fantasy"));
        assertTrue(tags.contains("Historical Fiction"));
        assertTrue(tags.contains("Audiobook"));
        assertTrue(tags.contains("Books About Books"));
        assertTrue(tags.contains("Magic"));
        assertTrue(tags.contains("Historical"));
        assertTrue(tags.contains("Young Adult Fantasy"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Katherine Tegen Books", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        final Author author;
        Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Nix", author.getFamilyName());
        assertEquals("Garth", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1963-07-19", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));
        assertEquals(16, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_GOODREADS);
        assertTrue(oIv.isPresent());
        assertEquals("8347", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_OPEN_LIBRARY);
        assertTrue(oIv.isPresent());
        assertEquals("OL382982A", oIv.get());
        // We don't check all of them...

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());

        assertEquals("Left-Handed Booksellers of London", series.get(0).getTitle());
        assertEquals("1", series.get(0).getNumber());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Goodreads.getPreferenceKey()
                                          + "_9780062683250_0_.jpg"));
    }

    @Test
    public void parseNextDataJson9780553803723()
            throws IOException, StorageException, SearchException, CredentialsException {

        final Book book = new Book();
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.goodreads_next_data_9780553803723);
        searchEngine.parse(context, document, book, new boolean[]{true, false, false, false});
        Log.d(TAG, book.toString());

        assertEquals("Foundation and Empire", book.getString(DBKey.TITLE, null));
        assertNull(book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("9780553803723", book.getString(DBKey.ISBN, null));
        assertEquals("English", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("256", book.getString(DBKey.PAGES, null));
        assertEquals("2004-06-01", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals(new PartialDate(1952, 1, 1), book.getFirstPublicationDate());
        assertEquals(4.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);

        assertEquals("0553803727", book.requireIdentifierValue(Identifier.SID_ASIN));
        assertEquals("29581", book.requireIdentifierValue(Identifier.SID_GOODREADS));

        assertEquals("Foundation and Empire tells the incredible story of a new breed"
                     + " of man who create a new force for galactic government. Thus, the"
                     + " Foundation hurtles into conflict with the decadent, decrepit First"
                     + " Empire. In this struggle for power amid the chaos of the stars, man"
                     + " stands at the threshold of a new, enlightened life which could easily"
                     + " be put aside for the old forces of barbarism. The Foundation novels"
                     + " of Isaac Asimov constitute what is very likely the most famed epic in"
                     + " all of science-fiction",
                     book.getString(DBKey.DESCRIPTION, null));

        final List<Tag> bookTags = book.getTags();
        assertEquals(10, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Audiobook"));
        assertTrue(tags.contains("Classics"));
        assertTrue(tags.contains("Fantasy"));
        assertTrue(tags.contains("Fiction"));
        assertTrue(tags.contains("Novels"));
        assertTrue(tags.contains("Science Fiction Fantasy"));
        assertTrue(tags.contains("Science Fiction"));
        assertTrue(tags.contains("Space Opera"));
        assertTrue(tags.contains("Space"));
        assertTrue(tags.contains("Speculative Fiction"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Spectra", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        final Author author;
        Optional<String> oIv;

        author = authors.get(0);

        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1920-01-02", author.getBirthDate().orElse(null));
        assertEquals("1992-04-06", author.getDeathDate().orElse(null));
        assertTrue(author.getTmpPictureFileSpec().get().endsWith("_goodreads_16667_0_.jpg"));
        assertEquals(21, author.getIdentifiers().size());

        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q34981", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000122590564", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_VIAF);
        assertTrue(oIv.isPresent());
        assertEquals("24597135", oIv.get());
        // We don't check all of them...

        final List<Series> seriesList = book.getSeries();
        assertNotNull(seriesList);
        assertEquals(3, seriesList.size());

        Series series;
        series = seriesList.get(0);
        assertEquals("Foundation (Publication Order)", series.getTitle());
        assertEquals("2", series.getNumber());
        series = seriesList.get(1);
        assertEquals("Foundation (Chronological Order)", series.getTitle());
        assertEquals("4", series.getNumber());
        series = seriesList.get(2);
        assertEquals("Greater Foundation Universe", series.getTitle());
        assertEquals("12", series.getNumber());

        final String preferenceKey = EngineId.Goodreads.getPreferenceKey();
        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9780553803723_0_.jpg"));
    }

    /**
     * There are several/literal {@code null} values in the json source.
     * Make sure by testing for correctly missing fields.
     */
    @Test
    public void withNulls()
            throws IOException, StorageException, SearchException, CredentialsException {

        final Book book = new Book();
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.goodreads_with_nulls);
        searchEngine.parse(context, document, book, new boolean[]{true, false, false, false});
        Log.d(TAG, book.toString());

        assertEquals("The Aenied", book.getString(DBKey.TITLE, null));
        assertEquals("Aeneis", book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertNull(book.getString(DBKey.ISBN, null));
        assertNull(book.getString(DBKey.LANGUAGE, null));
        assertNull(book.getString(DBKey.FORMAT, null));
        assertNull(book.getString(DBKey.PAGES, null));
        assertNull(book.getString(DBKey.PUBLICATION_DATE, null));
        // yes, that's the date on goodreads
        assertEquals(new PartialDate(-19, 1, 1), book.getFirstPublicationDate());
        assertEquals(4.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);

        assertTrue(book.getIdentifierValue(Identifier.SID_ASIN).isEmpty());
        assertEquals("37557163", book.requireIdentifierValue(Identifier.SID_GOODREADS));

        assertEquals("The Aeneid is an epic poem, written by Virgil between 29 and"
                     + " 19 BC, that tells the legendary story of Aeneas, a Trojan who travelled"
                     + " to Italy, where he became the ancestor of the Romans. It comprises"
                     + " 9,896 lines in dactylic hexameter. The first six of the poem's twelve"
                     + " books tell the story of Aeneas's wanderings from Troy to Italy, and"
                     + " the poem's second half tells of the Trojans' ultimately victorious war"
                     + " upon the Latins, under whose name Aeneas and his Trojan followers are"
                     + " destined to be subsumed. The hero Aeneas was already known to Greco-Roman"
                     + " legend and myth, having been a character in the Iliad. Virgil took the"
                     + " disconnected tales of Aeneas's wanderings, his vague association with"
                     + " the foundation of Rome and a personage of no fixed characteristics other"
                     + " than a scrupulous pietas, and fashioned this into a compelling founding"
                     + " myth or national epic that at once tied Rome to the legends of Troy,"
                     + " explained the Punic Wars, glorified traditional Roman virtues, and"
                     + " legitimized the Julio-Claudian dynasty as descendants of the founders,"
                     + " heroes, and gods of Rome and Troy. The Aeneid is widely regarded as"
                     + " Virgil's masterpiece and one of the greatest works of Latin literature."
                     + " (less)",
                     book.getString(DBKey.DESCRIPTION, null));

        final List<Tag> bookTags = book.getTags();
        assertEquals(10, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Literature"));
        assertTrue(tags.contains("Classics"));
        assertTrue(tags.contains("School"));
        assertTrue(tags.contains("Epic"));
        assertTrue(tags.contains("Ancient"));
        assertTrue(tags.contains("Mythology"));
        assertTrue(tags.contains("Classic Literature"));
        assertTrue(tags.contains("Poetry"));
        assertTrue(tags.contains("Fantasy"));
        assertTrue(tags.contains("Fiction"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(0, allPublishers.size());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        final Author author;
        Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Virgil", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());

        // the lookup on OPEN_LIBRARY fails as they have this author listed as "Virgil Virgil"
        assertEquals(2, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_GOODREADS);
        assertTrue(oIv.isPresent());
        assertEquals("919", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q17505763", oIv.get());

        final List<Series> seriesList = book.getSeries();
        assertNotNull(seriesList);
        assertEquals(0, seriesList.size());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(0, covers.size());
    }

    @Test
    public void parseHtml9780062683250()
            throws IOException, SearchException, CredentialsException, StorageException {

        final String locationHeader = "https://www.goodreads.com/book/show/49867186-the-left-handed-booksellers-of-london?ac=1&from_search=true&qid=ubH6XArsmP&rank=2";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.goodreads_9780062683250;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("The Left-Handed Booksellers of London", book.getString(DBKey.TITLE, null));
        // full assert is already tested in parseNextDataJson9780062683250()
    }

    @Test
    public void parseMultiResultFoundationAndEmpire()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://www.goodreads.com/search?q=foundation+and+empire&qid=rMtPCIQx9m";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.goodreads_multi_result_foundation_and_empire;
        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parseMultiResult(context, document, new boolean[]{true, false, false, false},
                                      book);
        Log.d(TAG, book.toString());

        // live download, results may be different
        assertEquals("Foundation and Empire", book.getString(DBKey.TITLE, null));
        assertEquals("9780553803723", book.getString(DBKey.ISBN, null));
        assertEquals("Spectra", book.getPrimaryPublisher().get().getName());
        // full assert is already tested in parseNextDataJson9780553803723()
    }
}
