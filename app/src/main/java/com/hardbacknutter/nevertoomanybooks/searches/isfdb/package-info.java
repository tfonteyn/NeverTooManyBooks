/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
 * Their code to produce the Web API output:
 * https://sourceforge.net/p/isfdb/code-svn/HEAD/tree/trunk/rest/pub_output.py
 *
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
 */
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;
