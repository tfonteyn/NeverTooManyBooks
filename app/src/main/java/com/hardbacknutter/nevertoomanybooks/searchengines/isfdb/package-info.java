/*
 * @Copyright 2018-2022 HardBackNutter
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

/**
 * ISFDB search engine using JSoup.org HTML screen scraping.
 * <p>
 * The reason for the screen scraping is due to the fact that the official web API
 * for ISFDB does not return the TOC information of a book which was the reason to create
 * this search engine in the first place.
 * https://sourceforge.net/p/isfdb/code-svn/HEAD/tree/trunk/rest/
 * Their code to produce the Web API output:
 * https://sourceforge.net/p/isfdb/code-svn/HEAD/tree/trunk/rest/pub_output.py
 * <p>
 * The underlying reason is that their SQL basically does a 'select * from pubs...'
 * without joins:
 * https://sourceforge.net/p/isfdb/code-svn/HEAD/tree/trunk/common/SQLparsing.py#l576
 * https://sourceforge.net/p/isfdb/code-svn/HEAD/tree/trunk/common/SQLparsing.py#l1254
 * <p>
 * 2019-07: it seems the site developers have started to make some changes in the output
 * which repeatedly breaks the scraping.
 * <p>
 * The short term plan:
 * - keep the TOC screen scraping. It can be triggered from the options menu on the books
 * edit screen, TOC tab.
 * - try to keep the general scraper up to date.
 * <p>
 * Long term:
 * - create a new search engine using the web API for all non-TOC information.
 * Code it so it calls the TOC scraper when required.
 * {@link com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbSearchEngine#xmlSearchByExternalId}
 * <p>
 * Notes on the URL format for scraping pages.
 * <p>
 * Search for something:  http://www.isfdb.org/cgi-bin/se.cgi?arg=%s&type=ISBN
 * </p>
 * http://www.isfdb.org/wiki/index.php/ISFDB:FAQ#What_ISFDB_URLs_are_stable_and_safe_to_use_when_linking.3F
 * <ul>
 *      <li>ea.cgi - This displays the ISFDB bibliography for one author.</li>
 *      <li>title.cgi - This displays bibliographic information for one title.</li>
 *      <li>pe.cgi - This displays titles associated with a particular Series. </li>
 *      <li>pl.cgi - This displays the bibliographic information for one publication.</li>
 * </ul>
 * <p>
 * The advanced search returns a URL with "adv_search_results.cgi" and <br>
 * (if searching for 'publications') a content the same as "title.cgi"
 * </p>
 * <p>
 * <a href="http://www.isfdb.org/wiki/index.php/Web_API">Web API</a>
 * </p>
 * <p>
 * Data not available from the Web API, but can be scraped:
 * <ul>
 *      <li>TOC</li>
 *      <li>Author, publisher, ... ISFDB internal IDs</li>
 * </ul>
 * <p>
 * -----------------------------------------------------------------------------------
 * Some reference tables:
 * <pre>
 *     // INSERT INTO `identifier_types` VALUES
 *     // (1,'ASIN','Amazon Standard Identification Number'),
 *     // (2,'BL','The British Library'),
 *     // (3,'BNB','The British National Bibliography'),
 *     // (4,'BNF','BibliothÃ¨que nationale de France'),
 *     // (5,'COPAC (defunct)','UK/Irish union catalog'),
 *     // (6,'DNB','Deutsche Nationalbibliothek'),
 *     // (7,'FantLab','Laboratoria Fantastiki'),
 *     // (8,'Goodreads','Goodreads social cataloging site'),
 *     // (9,'JNB/JPNO','The Japanese National Bibliography'),
 *     // (10,'LCCN','Library of Congress Control Number'),
 *     // (11,'NDL','National Diet Library'),
 *     // (12,'OCLC/WorldCat','Online Computer Library Center'),
 *     // (13,'Open Library','Open Library'),
 *     // (14,'SFBG','Catalog of books published in Bulgaria'),
 *     // (15,'BN','Barnes and Noble'),
 *     // (16,'PPN','De Nederlandse Bibliografie Pica Productie Nummer'),
 *     // (17,'Audible-ASIN','Audible ASIN'),
 *     // (18,'LTF','La Tercera Fundaci&#243;n'),
 *     // (19,'KBR','De Belgische Bibliografie/La Bibliographie de Belgique'),
 *     // (20,'Reginald-1','R. Reginald. Science Fiction and Fantasy Literature: ...'),
 *     // (21,'Reginald-3','Robert Reginald. Science Fiction and Fantasy Literature, ...'),
 *     // (22,'Bleiler Gernsback','Everett F. Bleiler, Richard Bleiler. Science-Fiction: ...'),
 *     // (23,'Bleiler Supernatural','Everett F. Bleiler. The Guide to Supernatural Fiction. ...'),
 *     // (24,'Bleiler Early Years','Richard Bleiler, Everett F. Bleiler. Science-Fiction: ...'),
 *     // (25,'NILF','Numero Identificativo della Letteratura Fantastica / Fantascienza'),
 *     // (26,'NooSFere','NooSFere'),
 *     // (27,'SF-Leihbuch','Science Fiction-Leihbuch-Datenbank'),
 *     // (28,'NLA','National Library of Australia'),
 *     // (29,'PORBASE','Biblioteca Nacional de Portugal'),
 *     // (30,'Libris','Libris - National Library of Sweden'),
 *     // (31,'Libris XL','Libris XL - National Library of Sweden (new interface)');
 * </pre>
 */
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;
