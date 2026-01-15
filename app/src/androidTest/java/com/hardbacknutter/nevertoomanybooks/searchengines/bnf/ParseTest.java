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

package com.hardbacknutter.nevertoomanybooks.searchengines.bnf;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private static final String UTF_8 = "UTF-8";

    private BnfSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (BnfSearchEngine) EngineId.Bnf.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);
    }

    @Test
    public void parse9782266341417()
            throws SearchException {

        final Book book = new Book();

        final String txt =
                "000 cam 22 450\n" +
                "001 FRBNF475921580000008\n" +
                "003 http://catalogue.bnf.fr/ark:/12148/cb475921587\n" +
                "010 .. $a 978-2-266-34141-7 $b br. $d 7,30 EUR\n" +
                "020 .. $a FR $b 02464257\n" +
                "073 .0 $a 9782266341417\n" +
                "100 .. $a 20241104d2024 m y0frey50 ba\n" +
                "101 1. $a fre $c eng\n" +
                "102 .. $a FR\n" +
                "105 .. $a ||||z 00|y|\n" +
                "106 .. $a r\n" +
                "181 .0 $6 01 $a i $b xxxe\n" +
                "181 .. $6 02 $c txt $2 rdacontent\n" +
                "182 .0 $6 01 $a n\n" +
                "182 .. $6 02 $c n $2 rdamedia\n" +
                "200 1. $a Les étymologies $b Texte imprimé $f J. R. R. Tolkien $g édition de Christopher Tolkien $g traduit de l'anglais par Daniel Lauzon\n" +
                "214 .0 $a Paris $c Pocket $d DL 2024\n" +
                "214 .3 $a impr. en Espagne\n" +
                "215 .. $a 1 vol. (152 p.) $d 18 cm\n" +
                "225 |. $a Imaginaire\n" +
                "225 |. $a Pocket $v 7375\n" +
                "330 .. $a Un dictionnaire pour les nommer tous ! (A l'origine des noms dans"
                + " l'œuvre de Tolkien) Les lecteurs trouveront dans ce \" dictionnaire"
                + " étymologique \" elfique l'explication de nombreux noms et le point de départ"
                + " de bien des récits imaginés par J.R.R. Tolkien. Les Étymologies permettent de"
                + " suivre l'évolution d'une langue \" vivante \", de saisir son importance."
                + " Leur création a accompagné, si ce n'est guidé, la naissance du Seigneur des"
                + " Anneaux et de La Terre du Milieu. $2 éditeur\n"
                +
                "410 .0 $0 34228117 $t Presses pocket (Paris) $x 0244-6405 $v 7375\n" +
                "410 .0 $0 45156268 $t Imaginaire (Paris) $x 2497-7284 $d 2024\n" +
                "454 .1 $t The etymologies\n" +
                "604 .. $3 11939715 $a Tolkien $b John Ronald Reuel $f 1892-1973 $t The Lord of the rings $3 11959172 $x Langues $2 rameau\n" +
                "608 .. $3 11976221 $a Glossaires et lexiques $2 rameau\n" +
                "676 .. $a 823.914 (critique) $v 23\n" +
                "686 .. $a 800 $2 Cadre de classement de la Bibliographie nationale française\n" +
                "700 .| $3 11926763 $o ISNI0000000121441970 $a Tolkien $b John Ronald Reuel $f 1892-1973 $4 070\n" +
                "702 .| $3 11926762 $o ISNI0000000071393949 $a Tolkien $b Christopher $f 1924-2020 $4 340\n" +
                "702 .| $3 15069993 $o ISNI0000000072518950 $a Lauzon $b Daniel $f 1979-.... $4 730\n" +
                "801 .0 $a FR $b FR-751131015 $c 20241104 $g AFNOR $h FRBNF475921580000008 $2 intermrc\n" +
                "856 .2 $u 955048 $b Première de couverture\n" +
                "930 .. $5 FR-751131010:47592158001001 $a 2024-238377 $b 759999999 $c Tolbiac - Rez de Jardin - Littérature et art - Magasin $d O\n";

        final List<String> rows = List.of(txt.split("\n"));

        searchEngine.parse(context, rows, book);
        Log.d(TAG, book.toString());

        assertEquals("Les étymologies", book.getString(DBKey.TITLE, null));
        assertEquals("9782266341417", book.getString(DBKey.ISBN, null));
        assertEquals("cb475921587", book.requireIdentifierValue(Identifier.SID_BNF));

        assertEquals("fre", book.getString(DBKey.LANGUAGE, null));

        assertEquals("The etymologies", book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("eng", book.getString(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, null));

        assertEquals("2024", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("152", book.getString(DBKey.PAGES, null));

        assertEquals("7.3", book.getString(DBKey.PRICE_LISTED, null));
        assertEquals("EUR", book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertEquals(
                "Un dictionnaire pour les nommer tous ! (A l'origine des noms dans"
                + " l'œuvre de Tolkien) Les lecteurs trouveront dans ce \" dictionnaire"
                + " étymologique \" elfique l'explication de nombreux noms et le point de départ"
                + " de bien des récits imaginés par J.R.R. Tolkien. Les Étymologies permettent de"
                + " suivre l'évolution d'une langue \" vivante \", de saisir son importance."
                + " Leur création a accompagné, si ce n'est guidé, la naissance du Seigneur des"
                + " Anneaux et de La Terre du Milieu.",
                book.getString(DBKey.DESCRIPTION, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Pocket", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(3, authors.size());

        Author author;
        Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Tolkien", author.getFamilyName());
        assertEquals("John Ronald Reuel", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000121441970", oIv.get());

        author = authors.get(1);
        assertEquals("Tolkien", author.getFamilyName());
        assertEquals("Christopher", author.getGivenNames());
        assertEquals(AuthorRole.EDITOR, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000071393949", oIv.get());

        author = authors.get(2);
        assertEquals("Lauzon", author.getFamilyName());
        assertEquals("Daniel", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000072518950", oIv.get());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series;
        series = allSeries.get(0);
        assertEquals("Imaginaire", series.getTitle());
        assertEquals("", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Pocket", series.getTitle());
        assertEquals("7375", series.getNumber());
    }

    @Test
    public void parse9782756078311()
            throws IOException, CoverStorageException, SearchException {

        final String locationHeader = "https://catalogue.bnf.fr/ark:/12148/cb458421422.public";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bnf_9782756078311;

        final Book book = new Book();

        final String txt =
                "000 cam 22 450\n" +
                "001 FRBNF458421420000004\n" +
                "003 http://catalogue.bnf.fr/ark:/12148/cb458421422\n" +
                "010 .. $a 978-2-7560-7831-1 $b rel. $d 14,50 EUR\n" +
                "020 .. $a FR $b 02004153\n" +
                "073 .0 $a 9782756078311\n" +
                "100 .. $a 20191118d2019 m y0frey50 ba\n" +
                "101 0. $a fre\n" +
                "102 .. $a FR\n" +
                "105 .. $a ||||t 00|a|\n" +
                "106 .. $a r\n" +
                "181 .0 $6 01 $a i $b xxxe $a b $b xb2e\n" +
                "181 .. $6 02 $c txt $c sti $2 rdacontent\n" +
                "182 .0 $6 01 $a n\n" +
                "182 .. $6 02 $c n $2 rdamedia\n" +
                "200 1. $a Mise à jour $b Texte imprimé" +
                " $f scénario, JD Morvan $g dessin et couleur, Philippe Buchet\n" +
                "214 .0 $a [Paris] $c Delcourt $d DL 2019\n" +
                "214 .3 $a impr. en Belgique\n" +
                "215 .. $a 1 vol. (46 p.) $c ill. en coul. $d 32 cm\n" +
                "225 19 $a Sillage $v 20\n" +
                "225 |. $a Néopolis\n" +
                "330 .. $a Alors que Nävis est sur la piste d'un de ces mystérieux métamorphes,"
                + " celle-ci met fortuitement la main sur un dossier la concernant. Intriguée,"
                + " elle décide de le dérober et de le cracker avec l'aide de Juliette."
                + " Ce dossier est un document d'archives récupéré sur le « Juniville 08 »,"
                + " vaisseau où vivait notre héroïne avant d'être enrôlée par Sillage. "
                + "Qui est-elle vraiment et d'où vient elle ? Cet épisode nous le révèlera."
                + " $2 éditeur\n" +
                "410 .0 $0 34280292 $t Neopolis (Paris) $x 1248-5152 $d 2019\n" +
                "461 .0 $0 36133793 $t Sillage $v 20\n" +
                "676 .. $a 741.5 $v 23\n" +
                "686 .. $a 805 $2 Cadre de classement de la Bibliographie nationale française\n" +
                "700 .| $3 12950081 $o ISNI0000000121449219 $a Morvan $b Jean-David $f 1969-.... $4 070\n" +
                "702 .| $3 13205924 $o ISNI0000000121225055 $a Buchet $b Philippe $f 1962-.... $4 440\n" +
                "801 .0 $a FR $b FR-751131015 $c 20191118 $g AFNOR $h FRBNF458421420000004 $2 intermrc\n" +
                "856 .2 $u 414352 $b Première de couverture\n" +
                "930 .. $5 FR-751131010:45842142001001 $a 2019-271270 $b 759999999 $c Tolbiac - Rez de Jardin - Littérature et art - Magasin $d O\n";

        final List<String> rows = List.of(txt.split("\n"));

        searchEngine.parse(context, rows, book);
        Log.d(TAG, book.toString());

        assertEquals("Mise à jour", book.getString(DBKey.TITLE, null));
        assertEquals("9782756078311", book.getString(DBKey.ISBN, null));
        assertEquals("cb458421422", book.requireIdentifierValue(Identifier.SID_BNF));

        assertEquals("fre", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2019", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("46", book.getString(DBKey.PAGES, null));

        assertEquals("14.5", book.getString(DBKey.PRICE_LISTED, null));
        assertEquals("EUR", book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertEquals(
                "Alors que Nävis est sur la piste d'un de ces mystérieux métamorphes,"
                + " celle-ci met fortuitement la main sur un dossier la concernant. Intriguée,"
                + " elle décide de le dérober et de le cracker avec l'aide de Juliette. Ce dossier"
                + " est un document d'archives récupéré sur le « Juniville 08 », vaisseau où"
                + " vivait notre héroïne avant d'être enrôlée par Sillage. Qui est-elle vraiment"
                + " et d'où vient elle ? Cet épisode nous le révèlera.",
                book.getString(DBKey.DESCRIPTION, null));


        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Delcourt", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;
        Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Morvan", author.getFamilyName());
        assertEquals("Jean-David", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000121449219", oIv.get());

        author = authors.get(1);
        assertEquals("Buchet", author.getFamilyName());
        assertEquals("Philippe", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000121225055", oIv.get());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series;
        series = allSeries.get(0);
        assertEquals("Sillage", series.getTitle());
        assertEquals("20", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Néopolis", series.getTitle());
        assertEquals("", series.getNumber());

        // Fetch the public page, where we only parse the cover image
        final Document document = loadDocument(resId, UTF_8, locationHeader);
        searchEngine.parseCovers(context, document, book);

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;
        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9782756078311_0_.jpg"));
    }
}
