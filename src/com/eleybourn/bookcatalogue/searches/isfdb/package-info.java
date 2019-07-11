/**
 * ISFDB search engine using JSoup.org HTML screen scraping.
 * <p>
 * The reason for the screen scraping is due to the fact that the official web API
 * for ISFDB does not return the TOC information of a book which was the reason to create
 * this search engine in the first place.
 * <p>
 * 2019-07: it seems the site developers have started to make some changes in the output
 * which repeatedly breaks the scraping.
 * <p>
 * The short term plan:
 * - keep the TOC screen scraping. It can be triggered from the options menu on the books
 * edit screen, TOC tab.
 * - try to keep the general scraper up to date.
 *
 * Long term:
 * - create a new search engine using the web API for all non-TOC information.
 * Code it so it calls the TOC scraper when required.
 *
 * Notes on the URL format for scraping pages.
 * <p>
 *     Search for something:  http://www.isfdb.org/cgi-bin/se.cgi?arg=%s&type=ISBN
 * </p>
 * <ul>
 *     <li>Author: http://www.isfdb.org/cgi-bin/ea.cgi?69</li>
 *     <li>Title with editions: http://www.isfdb.org/cgi-bin/title.cgi?1825</li>
 *     <li>Book: http://www.isfdb.org/cgi-bin/pl.cgi?118921</li>
 * </ul>
 * <p>
 *     Web API: http://www.isfdb.org/wiki/index.php/Web_API
 * </p>
 *
 * Data not available from the Web API, but can be scraped:
 * <ul>
 *     <li>TOC</li>
 *     <li>Author, publisher, ... ISFDB internal id's</li>
 * </ul>
 */
package com.eleybourn.bookcatalogue.searches.isfdb;
