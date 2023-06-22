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

package com.hardbacknutter.nevertoomanybooks.searchengines.bertrandpt;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BertrandPtTest
        extends JSoupBase {

    private static final String TAG = "BertrandPtTest";

    private static final String UTF_8 = "UTF-8";

    private BertrandPtSearchEngine searchEngine;
    private Book book;

    @BeforeEach
    public void setup()
            throws Exception {
        super.setup();
        book = new Book(BundleMock.create());
        searchEngine = (BertrandPtSearchEngine) EngineId.BertrandPt.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.UK);
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        final String locationHeader =
                "https://www.bertrand.pt/livro/eu-ja-acreditei-em-ti-barbara-corby/28660380";
        final String filename = "/bertrandpt/9789899087774_book.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{true, false}, book);
        //System.out.println(book);

        assertEquals("Eu Já Acreditei em Ti", book.getString(DBKey.TITLE, null));
        assertEquals("9789899087774", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2023-07", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("Capa mole", book.getString(DBKey.FORMAT, null));
        assertEquals("Português", book.getString(DBKey.LANGUAGE, null));
        assertEquals("184", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals(14.31d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser));
        assertEquals("EUR", book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
        assertEquals("Livros > Livros em Português > Literatura > Jovem Adulto",
                     book.getString(DBKey.GENRE, null));

        assertEquals("Dizem que o tempo cura tudo. Mas e quando nem o tempo é capaz de" +
                     " curar um coração partido? Passaram-se vários meses desde que Fred partiu" +
                     " para Barcelona. A Manu está finalmente a viver em Lisboa, rodeada pelos" +
                     " seus melhores amigos, e não podia estar mais contente com a faculdade," +
                     " o part-time no Rio 23 e… o vizinho charmoso do quinto esquerdo que está" +
                     " sempre a tropeçar no seu caminho. <br><br>\n" +
                     " Só que Fred deixou um vazio no seu coração, e a tristeza ainda é" +
                     " demasiado profunda. Agora que as férias de verão se aproximam, o" +
                     " reencontro é inevitável. E a Manu não sabe como vai reagir quando" +
                     " voltar a ver o <i>irmão</i> que, no ano passado, descobriu que" +
                     " amava.<br><br>\n" +
                     " Depois do sucesso de <i>Eu Já Devia Saber</i>, Bárbara Corby está de" +
                     " volta com a continuação de uma história de amor que nunca deveria" +
                     " ter acontecido. Num romance cheio de reviravoltas e surpresas," +
                     " segredos do passado emergem. Mas uma tragédia pode mudar tudo.",
                     book.getString(DBKey.DESCRIPTION));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Manuscrito Editora", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Corby", authors.get(0).getFamilyName());
        assertEquals("Bárbara", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        final List<String> covers = book.getCoverFileSpecList(0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.BertrandPt.getPreferenceKey()
                                          + "_9789899087774_0_.jpg"));
    }

    @Test
    void parse02()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.UK);
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        final String locationHeader =
                "https://www.bertrand.pt/livro/o-ano-da-morte-de-ricardo-reis-jose-saramago/18493509";
        final String filename = "/bertrandpt/9789720048820.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{true, false}, book);
        //System.out.println(book);

        assertEquals("O Ano da Morte de Ricardo Reis", book.getString(DBKey.TITLE, null));
        assertEquals("978-972-0-04882-0", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2016-09", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("Capa mole", book.getString(DBKey.FORMAT, null));
        assertEquals("Português", book.getString(DBKey.LANGUAGE, null));
        assertEquals("496", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals(14.2d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser));
        assertEquals("EUR", book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
        assertEquals("Livros > Livros em Português > Literatura > Romance",
                     book.getString(DBKey.GENRE, null));
        assertEquals(4f, book.getFloat(DBKey.RATING, realNumberParser));

        assertEquals("Leitura recomendada para o 12.º ano de escolaridade.<br><br>\n" +
                     " Um tempo múltiplo. Labiríntico. As histórias das sociedades humanas." +
                     " Ricardo Reis chega a Lisboa em finais de dezembro de 1935." +
                     " Fica até setembro de 1936. Uma personagem vinda de uma outra ficção," +
                     " a da heteronímia de Fernando Pessoa. E um movimento inverso, logo" +
                     " a começar: «Aqui onde o mar se acaba e a terra principia»; o virar ao" +
                     " contrário o verso de Camões: «Onde a terra acaba e o mar começa.»" +
                     " Em Camões, o movimento é da terra para o mar; no livro de Saramago" +
                     " temos Ricardo Reis a regressar a Portugal por mar. É substituído o" +
                     " movimento épico da partida. Mais uma vez, a história na escrita de" +
                     " Saramago. E as relações entre a vida e a morte. Ricardo Reis chega" +
                     " a Lisboa em finais de dezembro e Fernando Pessoa morreu a 30 de" +
                     " novembro. Ricardo Reis visita-o ao cemitério. Um tempo complexo." +
                     " O fascismo consolida-se em Portugal. <br><br>" +
                     "<strong>Caligrafia da capa por CARLOS REIS</strong> <br><br>",
                     book.getString(DBKey.DESCRIPTION));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Porto Editora", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Saramago", authors.get(0).getFamilyName());
        assertEquals("José", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        final List<String> covers = book.getCoverFileSpecList(0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.BertrandPt.getPreferenceKey()
                                          + "_978-972-0-04882-0_0_.jpg"));
    }
}
