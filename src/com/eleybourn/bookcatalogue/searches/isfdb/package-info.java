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
 *      <li>pe.cgi - This displays titles associated with a particular series. </li>
 *      <li>pl.cgi - This displays the bibliographic information for one publication.</li>
 * </ul>
 * <p>
 *     The advanced search returns a URL with "adv_search_results.cgi" and <br>
 *         (if searching for 'publications') a content the same as "title.cgi"
 * </p>
 * <p>
 * Web API: http://www.isfdb.org/wiki/index.php/Web_API
 * </p>
 * <p>
 * Data not available from the Web API, but can be scraped:
 * <ul>
 * <li>TOC</li>
 * <li>Author, publisher, ... ISFDB internal id's</li>
 * </ul>
 */
package com.eleybourn.bookcatalogue.searches.isfdb;
