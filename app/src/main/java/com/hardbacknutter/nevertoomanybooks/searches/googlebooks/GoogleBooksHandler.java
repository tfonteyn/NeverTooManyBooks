/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches.googlebooks;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 2019-11: this needs scrapping. See {@link GoogleBooksManager} class doc.
 *
 * THIS CLASS IS OBSOLETE (but still in use and working fine).
 *
 * An XML handler for the Google Books return.
 * Gets the total number of books found, and their id (which is a URL)
 * <p>
 * VERY IMPORTANT:
 * The response gives us a "feed" with multiple "entry" elements.
 * What our code does it get the first "entry" and read the "id" element,
 * which is a URL to the entry.
 * We then pass this URL to {@link GoogleBooksEntryHandler} which FETCHES THAT URL
 * and parses it.
 * <p>
 * rewrite the GoogleBooks xml handlers by merging them.
 * <p>
 * - So we do NOT parse the "entry" as delivered in the "feed"
 * ==> waste of bandwidth.
 * - a search for "9780575109735" shows that the data in the first "entry" of the "feed"
 * CAN BE DIFFERENT from the data fetched from the entry url!
 * ==> so google delivers (potentially) different results for "fee/entry1" versus directly
 * fetching "entry1" ??
 * <p>
 * Speculation: this could be historical... perhaps when the code was first written,
 * the feed/entry only contained the url and not the actual data?
 *
 * <p>
 * Example responses:
 * <pre>
 *     {@code
 * <?xml version='1.0' encoding='UTF-8'?>
 * <feed xmlns='http://www.w3.org/2005/Atom'
 *       xmlns:openSearch='http://a9.com/-/spec/opensearchrss/1.0/'
 *       xmlns:gbs='http://schemas.google.com/books/2008'
 *       xmlns:dc='http://purl.org/dc/terms'
 *       xmlns:batch='http://schemas.google.com/gdata/batch'
 *       xmlns:gd='http://schemas.google.com/g/2005'>
 *   <id>http://www.google.com/books/feeds/volumes</id>
 *   <updated>2010-02-28T03:28:09.000Z</updated>
 *   <category scheme='http://schemas.google.com/g/2005#kind'
 *             term='http://schemas.google.com/books/2008#volume'/>
 *   <title type='text'>Search results for ISBN9780006483830</title>
 *   <link rel='alternate' type='text/html'
 *         href='http://www.google.com'/>
 *   <link rel='http://schemas.google.com/g/2005#feed' type='application/atom+xml'
 *         href='http://www.google.com/books/feeds/volumes'/>
 *   <link rel='self' type='application/atom+xml'
 *         href='http://www.google.com/books/feeds/volumes?q=ISBN9780006483830'/>
 *   <author>
 *     <name>Google Books Search</name>
 *     <uri>http://www.google.com</uri>
 *   </author>
 *   <generator version='beta'>Google Book Search data API</generator>
 *   <openSearch:totalResults>1</openSearch:totalResults>
 *   <openSearch:startIndex>1</openSearch:startIndex>
 *   <openSearch:itemsPerPage>1</openSearch:itemsPerPage>
 *   <entry>
 *     <id>http://www.google.com/books/feeds/volumes/A4NDPgAACAAJ</id>
 *     <updated>2010-02-28T03:28:09.000Z</updated>
 *     <category scheme='http://schemas.google.com/g/2005#kind'
 *               term='http://schemas.google.com/books/2008#volume'/>
 *     <title type='text'>The trigger</title>
 *     <link rel='http://schemas.google.com/books/2008/info'
 *           type='text/html'
 *           href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;dq=ISBN9780006483830
 *           &amp;ie=ISO-8859-1&amp;source=gbs_gdata'/>
 *     <link rel='http://schemas.google.com/books/2008/preview'
 *           type='text/html'
 *           href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;dq=ISBN9780006483830
 *           &amp;ie=ISO-8859-1&amp;cd=1&amp;source=gbs_gdata'/>
 *     <link rel='http://schemas.google.com/books/2008/annotation'
 *           type='application/atom+xml'
 *           href='http://www.google.com/books/feeds/users/me/volumes'/>
 *     <link rel='alternate'
 *           type='text/html'
 *           href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;dq=ISBN9780006483830
 *           &amp;ie=ISO-8859-1'/>
 *     <link rel='self'
 *           type='application/atom+xml'
 *           href='http://www.google.com/books/feeds/volumes/A4NDPgAACAAJ'/>
 *     <gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 *     <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *     <gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 *     <dc:creator>Arthur Charles Clarke</dc:creator>
 *     <dc:creator>Michael P. Kube-McDowell</dc:creator>
 *     <dc:date>2000-01-01</dc:date>
 *     <dc:format>550 pages</dc:format>
 *     <dc:format>book</dc:format>
 *     <dc:identifier>A4NDPgAACAAJ</dc:identifier>
 *     <dc:identifier>ISBN:0006483836</dc:identifier>
 *     <dc:identifier>ISBN:9780006483830</dc:identifier>
 *     <dc:subject>Fiction</dc:subject>
 *     <dc:title>The trigger</dc:title>
 *   </entry>
 * </feed>
 *
 * <?xml version='1.0' encoding='UTF-8'?>
 * <feed xmlns='http://www.w3.org/2005/Atom'
 *       xmlns:openSearch='http://a9.com/-/spec/opensearchrss/1.0/'
 *       xmlns:gbs='http://schemas.google.com/books/2008'
 *       xmlns:dc='http://purl.org/dc/terms'
 *       xmlns:batch='http://schemas.google.com/gdata/batch'
 *       xmlns:gd='http://schemas.google.com/g/2005'>
 *  <id>http://www.google.com/books/feeds/volumes</id>
 *  <updated>2010-03-01T07:27:49.000Z</updated>
 *  <category scheme='http://schemas.google.com/g/2005#kind'
 *            term='http://schemas.google.com/books/2008#volume'/>
 *  <title type='text'>Search results for ISBN9780307450340</title>
 *  <link rel='alternate'
 *        type='text/html'
 *        href='http://www.google.com'/>
 *  <link rel='http://schemas.google.com/g/2005#feed'
 *        type='application/atom+xml'
 *        href='http://www.google.com/books/feeds/volumes'/>
 *  <link rel='self'
 *        type='application/atom+xml'
 *        href='http://www.google.com/books/feeds/volumes?q=ISBN9780307450340'/>
 *  <author>
 *    <name>Google Books Search</name>
 *    <uri>http://www.google.com</uri>
 *  </author>
 *  <generator version='beta'>Google Book Search data API</generator>
 *  <openSearch:totalResults>1</openSearch:totalResults>
 *  <openSearch:startIndex>1</openSearch:startIndex>
 *  <openSearch:itemsPerPage>1</openSearch:itemsPerPage>
 *  <entry>
 *    <id>http://www.google.com/books/feeds/volumes/lf2EMetoLugC</id>
 *    <updated>2010-03-01T07:27:49.000Z</updated>
 *    <category scheme='http://schemas.google.com/g/2005#kind'
 *                term='http://schemas.google.com/books/2008#volume'/>
 *    <title type='text'>The Geeks' Guide to World Domination</title>
 *    <link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown'
 *      href='http://bks3.books.google.com/books?id=lf2EMetoLugC&amp;printsec=frontcover
 *      &amp;img=1&amp;zoom=5&amp;sig=ACfU3U1hcfy_NvWZbH46OzWwmQQCDV46lA&amp;source=gbs_gdata'/>
 *    <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *      href='http://books.google.com/books?id=lf2EMetoLugC&amp;dq=ISBN9780307450340
 *      &amp;ie=ISO-8859-1&amp;source=gbs_gdata'/>
 *    <link rel='http://schemas.google.com/books/2008/preview' type='text/html'
 *      href='http://books.google.com/books?id=lf2EMetoLugC&amp;dq=ISBN9780307450340
 *      &amp;ie=ISO-8859-1&amp;cd=1&amp;source=gbs_gdata'/>
 *    <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *      href='http://www.google.com/books/feeds/users/me/volumes'/>
 *    <link rel='alternate' type='text/html'
 *      href='http://books.google.com/books?id=lf2EMetoLugC&amp;dq=ISBN9780307450340
 *      &amp;ie=ISO-8859-1'/>
 *    <link rel='self' type='application/atom+xml'
 *      href='http://www.google.com/books/feeds/volumes/lf2EMetoLugC'/>
 *    <gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 *    <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *    <gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 *    <dc:creator>Garth Sundem</dc:creator>
 *    <dc:date>2009-03-10</dc:date>
 *    <dc:description>These days, from blah blah ....the Geek Wars have</dc:description>
 *    <dc:format>245 pages</dc:format>
 *    <dc:format>book</dc:format>
 *    <dc:identifier>lf2EMetoLugC</dc:identifier>
 *    <dc:identifier>ISBN:0307450341</dc:identifier>
 *    <dc:identifier>ISBN:9780307450340</dc:identifier>
 *    <dc:publisher>Three Rivers Pr</dc:publisher>
 *    <dc:subject>Humor</dc:subject>
 *    <dc:title>The Geeks' Guide to World Domination</dc:title>
 *    <dc:title>Be Afraid, Beautiful People</dc:title>
 *  </entry>
 * </feed>
 * }
 * </pre>
 *
 *
 * <pre>
 *     {@code
 *     https://books.google.com/books/feeds/volumes?q=ISBN%3C0340198273%3E
 *
 * <?xml version='1.0' encoding='UTF-8'?>
 *  <feed xmlns='http://www.w3.org/2005/Atom'
 *    xmlns:openSearch='http://a9.com/-/spec/opensearchrss/1.0/'
 *    xmlns:gbs='http://schemas.google.com/books/2008'
 *    xmlns:gd='http://schemas.google.com/g/2005'
 *    xmlns:batch='http://schemas.google.com/gdata/batch'
 *    xmlns:dc='http://purl.org/dc/terms'>
 *    <id>http://www.google.com/books/feeds/volumes</id>
 *    <updated>2019-07-17T09:02:42.000Z</updated>
 *    <category scheme='http://schemas.google.com/g/2005#kind'
 *              term='http://schemas.google.com/books/2008#volume'/>
 *    <title type='text'>Search results for ISBN&lt;0340198273&gt;</title>
 *    <link rel='alternate' type='text/html' href='http://www.google.com'/>
 *    <link rel='http://schemas.google.com/g/2005#feed' type='application/atom+xml'
 *      href='https://www.google.com/books/feeds/volumes'/>
 *    <link rel='self' type='application/atom+xml'
 *      href='https://www.google.com/books/feeds/volumes?q=ISBN%3C0340198273%3E'/>
 *    <link rel='next' type='application/atom+xml'
 *      href='https://www.google.com/books/feeds/volumes?q=ISBN%3C0340198273%3E
 *      &amp;start-index=11&amp;max-results=10'/>
 *    <author>
 *      <name>Google Books Search</name>
 *      <uri>http://www.google.com</uri>
 *    </author>
 *    <generator version='beta'>Google Book Search data API</generator>
 *    <openSearch:totalResults>427</openSearch:totalResults>
 *    <openSearch:startIndex>1</openSearch:startIndex>
 *    <openSearch:itemsPerPage>10</openSearch:itemsPerPage>
 *    <entry>
 *      <id>http://www.google.com/books/feeds/volumes/IVnpNAAACAAJ</id>
 *      <updated>2019-07-17T09:02:42.000Z</updated>
 *      <category scheme='http://schemas.google.com/g/2005#kind'
 *                term='http://schemas.google.com/books/2008#volume'/>
 *      <title type='text'>The Anome</title>
 *      <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *            href='http://books.google.com/books?id=IVnpNAAACAAJ&amp;dq=ISBN%3C0340198273%3E
 *            &amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/preview' type='text/html'
 *            href='http://books.google.com/books?id=IVnpNAAACAAJ&amp;dq=ISBN%3C0340198273%3E
 *            &amp;cd=1&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *            href='http://www.google.com/books/feeds/users/me/volumes'/>
 *      <link rel='alternate' type='text/html'
 *            href='http://books.google.com/books?id=IVnpNAAACAAJ&amp;dq=ISBN%3C0340198273%3E'/>
 *      <link rel='self' type='application/atom+xml'
 *            href='https://www.google.com/books/feeds/volumes/IVnpNAAACAAJ'/>
 *      <gbs:contentVersion>preview-1.0.0</gbs:contentVersion>
 *      <gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 *      <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *      <gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 *      <dc:creator>Jack Vance</dc:creator>
 *      <dc:date>1977</dc:date>
 *      <dc:format>206 pages</dc:format>
 *      <dc:format>book</dc:format>
 *      <dc:identifier>IVnpNAAACAAJ</dc:identifier>
 *      <dc:identifier>ISBN:0340198273</dc:identifier>
 *      <dc:identifier>ISBN:9780340198278</dc:identifier>
 *      <dc:language>en</dc:language>
 *      <dc:publisher>Coronet</dc:publisher>
 *      <dc:subject>English fiction</dc:subject>
 *      <dc:title>The Anome</dc:title>
 *    </entry>
 *
 *    <entry>
 *      <id>http://www.google.com/books/feeds/volumes/AYeaGwAACAAJ</id>
 *      <updated>2019-07-17T09:02:42.000Z</updated>
 *      <category scheme='http://schemas.google.com/g/2005#kind'
 *      term='http://schemas.google.com/books/2008#volume'/>
 *      <title type='text'>The Faceless Man</title>
 *      <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *          href='http://books.google.com/books?id=AYeaGwAACAAJ&amp;dq=ISBN%3C0340198273%3E&
 *          amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/preview' type='text/html'
 *          href='http://books.google.com/books?id=AYeaGwAACAAJ&amp;dq=ISBN%3C0340198273%3E
 *          &amp;cd=2&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *          href='http://www.google.com/books/feeds/users/me/volumes'/>
 *      <link rel='alternate' type='text/html'
 *          href='http://books.google.com/books?id=AYeaGwAACAAJ&amp;dq=ISBN%3C0340198273%3E'/>
 *      <link rel='self' type='application/atom+xml'
 *          href='https://www.google.com/books/feeds/volumes/AYeaGwAACAAJ'/>
 *      <gbs:contentVersion>preview-1.0.0</gbs:contentVersion>
 *      <gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 *      <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *      <gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 *      <dc:creator>Jack Vance</dc:creator>
 *      <dc:date>1983</dc:date>
 *      <dc:format>224 pages</dc:format>
 *      <dc:format>book</dc:format>
 *      <dc:identifier>AYeaGwAACAAJ</dc:identifier>
 *      <dc:identifier>ISBN:0934438854</dc:identifier>
 *      <dc:identifier>ISBN:9780934438858</dc:identifier>
 *      <dc:language>en</dc:language>
 *      <dc:publisher>Underwood Books</dc:publisher>
 *      <dc:subject>Fiction</dc:subject>
 *      <dc:title>The Faceless Man</dc:title>
 *    </entry>
 *
 *    <entry>
 *      <id>http://www.google.com/books/feeds/volumes/sWuaFofonmIC</id>
 *      <updated>2019-07-17T09:02:42.000Z</updated>
 *      <category scheme='http://schemas.google.com/g/2005#kind'
 *          term='http://schemas.google.com/books/2008#volume'/>
 *      <title type='text'>The Brave Free Men</title>
 *      <link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown'
 *          href='http://books.google.com/books/content?id=sWuaFofonmIC&amp;printsec=frontcover
 *          &amp;img=1&amp;zoom=5&amp;edge=curl&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *          href='http://books.google.com/books?id=sWuaFofonmIC&amp;dq=ISBN%3C0340198273%3E
 *          &amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/preview' type='text/html'
 *          href='http://books.google.com/books?id=sWuaFofonmIC&amp;printsec=frontcover
 *          &amp;dq=ISBN%3C0340198273%3E&amp;cd=3&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *          href='http://www.google.com/books/feeds/users/me/volumes'/>
 *      <link rel='http://schemas.google.com/books/2008/buylink' type='text/html'
 *          href='https://play.google.com/store/books/details?id=sWuaFofonmIC
 *          &amp;rdid=book-sWuaFofonmIC&amp;rdot=1&amp;source=gbs_gdata'/>
 *      <link rel='alternate' type='text/html'
 *          href='http://books.google.com/books?id=sWuaFofonmIC&amp;dq=ISBN%3C0340198273%3E'/>
 *      <link rel='self' type='application/atom+xml'
 *          href='https://www.google.com/books/feeds/volumes/sWuaFofonmIC'/>
 *      <gbs:contentVersion>0.1.1.0.preview.2</gbs:contentVersion>
 *      <gbs:embeddability value='http://schemas.google.com/books/2008#embeddable'/>
 *      <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *      <gbs:viewability value='http://schemas.google.com/books/2008#view_partial'/>
 *      <dc:creator>Jack Vance</dc:creator>
 *      <dc:date>2011-12-19</dc:date>
 *      <dc:description>If they were to fight the people  blah blah...</dc:description>
 *      <dc:format>87 pages</dc:format>
 *      <dc:format>book</dc:format>
 *      <dc:identifier>sWuaFofonmIC</dc:identifier>
 *      <dc:identifier>ISBN:9780575109735</dc:identifier>
 *      <dc:identifier>ISBN:0575109734</dc:identifier>
 *      <dc:language>en</dc:language>
 *      <gbs:price type='SuggestedRetailPrice'>
 *        <gd:money amount='3.99' currencyCode='GBP'/>
 *      </gbs:price>
 *      <gbs:price type='RetailPrice'>
 *        <gd:money amount='3.99' currencyCode='GBP'/>
 *      </gbs:price>
 *      <dc:publisher>Hachette UK</dc:publisher>
 *      <dc:subject>Fiction</dc:subject>
 *      <dc:title>The Brave Free Men</dc:title>
 *    </entry>
 *
 *    <entry>
 *      <id>http://www.google.com/books/feeds/volumes/O_Ik7L1VSMsC</id>
 *      <updated>2019-07-17T09:02:42.000Z</updated>
 *      <category scheme='http://schemas.google.com/g/2005#kind'
 *      term='http://schemas.google.com/books/2008#volume'/>
 *      <title type='text'>The Asutra</title>
 *      <link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown'
 *          href='http://books.google.com/books/content?id=O_Ik7L1VSMsC&amp;printsec=frontcover
 *          &amp;img=1&amp;zoom=5&amp;edge=curl&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *          href='http://books.google.com/books?id=O_Ik7L1VSMsC&amp;dq=ISBN%3C0340198273%3E
 *          &amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/preview' type='text/html'
 *          href='http://books.google.com/books?id=O_Ik7L1VSMsC&amp;printsec=frontcover
 *          &amp;dq=ISBN%3C0340198273%3E&amp;cd=4&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *          href='http://www.google.com/books/feeds/users/me/volumes'/>
 *      <link rel='http://schemas.google.com/books/2008/buylink' type='text/html'
 *          href='https://play.google.com/store/books/details?id=O_Ik7L1VSMsC
 *          &amp;rdid=book-O_Ik7L1VSMsC&amp;rdot=1&amp;source=gbs_gdata'/>
 *      <link rel='alternate' type='text/html'
 *          href='http://books.google.com/books?id=O_Ik7L1VSMsC&amp;dq=ISBN%3C0340198273%3E'/>
 *      <link rel='self' type='application/atom+xml'
 *          href='https://www.google.com/books/feeds/volumes/O_Ik7L1VSMsC'/>
 *      <gbs:contentVersion>0.1.1.0.preview.2</gbs:contentVersion>
 *      <gbs:embeddability value='http://schemas.google.com/books/2008#embeddable'/>
 *      <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *      <gbs:viewability value='http://schemas.google.com/books/2008#view_partial'/>
 *      <dc:creator>Jack Vance</dc:creator>
 *      <dc:date>2011-12-19</dc:date>
 *      <dc:description>For wild rumours come out of the hidden land blah blah...</dc:description>
 *      <dc:format>86 pages</dc:format>
 *      <dc:format>book</dc:format>
 *      <dc:identifier>O_Ik7L1VSMsC</dc:identifier>
 *      <dc:identifier>ISBN:9780575109742</dc:identifier>
 *      <dc:identifier>ISBN:0575109742</dc:identifier>
 *      <dc:language>en</dc:language>
 *      <gbs:price type='SuggestedRetailPrice'>
 *        <gd:money amount='3.99' currencyCode='GBP'/>
 *      </gbs:price>
 *      <gbs:price type='RetailPrice'>
 *        <gd:money amount='3.99' currencyCode='GBP'/>
 *      </gbs:price>
 *      <dc:publisher>Hachette UK</dc:publisher>
 *      <dc:subject>Fiction</dc:subject>
 *      <dc:title>The Asutra</dc:title>
 *    </entry>
 *
 *    <entry>
 *      <id>http://www.google.com/books/feeds/volumes/3Dm6V9QOPkoC</id>
 *      <updated>2019-07-17T09:02:42.000Z</updated>
 *      <category scheme='http://schemas.google.com/g/2005#kind'
 *          term='http://schemas.google.com/books/2008#volume'/>
 *      <title type='text'>The Magnificent Showboats</title>
 *      <link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown'
 *          href='http://books.google.com/books/content?id=3Dm6V9QOPkoC&amp;printsec=frontcover
 *          &amp;img=1&amp;zoom=5&amp;edge=curl&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *          href='http://books.google.com/books?id=3Dm6V9QOPkoC&amp;dq=ISBN%3C0340198273%3E
 *          &amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/preview' type='text/html'
 *          href='http://books.google.com/books?id=3Dm6V9QOPkoC&amp;printsec=frontcover
 *          &amp;dq=ISBN%3C0340198273%3E&amp;cd=5&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *          href='http://www.google.com/books/feeds/users/me/volumes'/>
 *      <link rel='http://schemas.google.com/books/2008/buylink' type='text/html'
 *          href='https://play.google.com/store/books/details?id=3Dm6V9QOPkoC
 *          &amp;rdid=book-3Dm6V9QOPkoC&amp;rdot=1&amp;source=gbs_gdata'/>
 *      <link rel='alternate' type='text/html'
 *          href='http://books.google.com/books?id=3Dm6V9QOPkoC&amp;dq=ISBN%3C0340198273%3E'/>
 *      <link rel='self' type='application/atom+xml'
 *          href='https://www.google.com/books/feeds/volumes/3Dm6V9QOPkoC'/>
 *      <gbs:contentVersion>1.3.3.0.preview.2</gbs:contentVersion>
 *      <gbs:embeddability value='http://schemas.google.com/books/2008#embeddable'/>
 *      <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *      <gbs:viewability value='http://schemas.google.com/books/2008#view_partial'/>
 *      <dc:creator>Jack Vance</dc:creator>
 *      <dc:date>2011-12-19</dc:date>
 *      <dc:description>The Magnificent Showboats follows the blah blah... </dc:description>
 *      <dc:format>102 pages</dc:format>
 *      <dc:format>book</dc:format>
 *      <dc:identifier>3Dm6V9QOPkoC</dc:identifier>
 *      <dc:identifier>ISBN:9780575109599</dc:identifier>
 *      <dc:identifier>ISBN:0575109599</dc:identifier>
 *      <dc:language>en</dc:language>
 *      <gbs:price type='SuggestedRetailPrice'>
 *        <gd:money amount='2.99' currencyCode='GBP'/>
 *      </gbs:price>
 *      <gbs:price type='RetailPrice'>
 *        <gd:money amount='2.99' currencyCode='GBP'/>
 *      </gbs:price>
 *      <dc:publisher>Hachette UK</dc:publisher>
 *      <dc:subject>Fiction</dc:subject>
 *      <dc:title>The Magnificent Showboats</dc:title>
 *    </entry>
 *
 * <entry>
 * <id>http://www.google.com/books/feeds/volumes/6RySDQAAQBAJ</id>
 * <updated>2019-07-17T09:02:42.000Z</updated>
 * <category scheme='http://schemas.google.com/g/2005#kind'
 *      term='http://schemas.google.com/books/2008#volume'/>
 * <title type='text'>The Golden Amazon</title>
 *      <link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown'
 *          href='http://books.google.com/books/content?id=6RySDQAAQBAJ&amp;printsec=frontcover
 *          &amp;img=1&amp;zoom=5&amp;edge=curl&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *          href='http://books.google.com/books?id=6RySDQAAQBAJ&amp;dq=ISBN%3C0340198273%3E
 *          &amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/preview' type='text/html'
 *          href='http://books.google.com/books?id=6RySDQAAQBAJ&amp;printsec=frontcover
 *          &amp;dq=ISBN%3C0340198273%3E&amp;cd=6&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *          href='http://www.google.com/books/feeds/users/me/volumes'/>
 *      <link rel='alternate' type='text/html'
 *          href='http://books.google.com/books?id=6RySDQAAQBAJ&amp;dq=ISBN%3C0340198273%3E'/>
 *      <link rel='self' type='application/atom+xml'
 *          href='https://www.google.com/books/feeds/volumes/6RySDQAAQBAJ'/>
 *      <gbs:contentVersion>preview-1.0.0</gbs:contentVersion>
 *      <gbs:embeddability value='http://schemas.google.com/books/2008#embeddable'/>
 *      <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *      <gbs:viewability value='http://schemas.google.com/books/2008#view_partial'/>
 *      <dc:creator>John Russell Fearn</dc:creator>
 *      <dc:date>2016-11-12</dc:date>
 *      <dc:description>Here is an interplanetary story that will .. blah blah...</dc:description>
 *      <dc:format>180 pages</dc:format>
 *      <dc:format>book</dc:format>
 *      <dc:identifier>6RySDQAAQBAJ</dc:identifier>
 *      <dc:identifier>ISBN:9781365528965</dc:identifier>
 *      <dc:identifier>ISBN:1365528960</dc:identifier>
 *      <dc:language>en</dc:language>
 *      <dc:publisher>Lulu.com</dc:publisher>
 *      <dc:subject>Fiction</dc:subject>
 *      <dc:title>The Golden Amazon</dc:title>
 * </entry>
 * <entry>
 * <id>http://www.google.com/books/feeds/volumes/NsdMAAAACAAJ</id>
 * <updated>2019-07-17T09:02:42.000Z</updated>
 * <category scheme='http://schemas.google.com/g/2005#kind'
 *      term='http://schemas.google.com/books/2008#volume'/>
 * <title type='text'>Space Time and Nathaniel</title>
 *      <link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown'
 *          href='http://books.google.com/books/content?id=NsdMAAAACAAJ&amp;printsec=frontcover
 *          &amp;img=1&amp;zoom=5&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *          href='http://books.google.com/books?id=NsdMAAAACAAJ&amp;dq=ISBN%3C0340198273%3E
 *          &amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/preview' type='text/html'
 *          href='http://books.google.com/books?id=NsdMAAAACAAJ&amp;dq=ISBN%3C0340198273%3E
 *          &amp;cd=7&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *          href='http://www.google.com/books/feeds/users/me/volumes'/>
 *      <link rel='alternate' type='text/html'
 *          href='http://books.google.com/books?id=NsdMAAAACAAJ&amp;dq=ISBN%3C0340198273%3E'/>
 *      <link rel='self' type='application/atom+xml'
 *          href='https://www.google.com/books/feeds/volumes/NsdMAAAACAAJ'/>
 *      <gbs:contentVersion>preview-1.0.0</gbs:contentVersion>
 *      <gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 *      <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *      <gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 *      <dc:creator>Brian W. Aldiss</dc:creator>
 *      <dc:date>2001-01-28</dc:date>
 *      <dc:format>212 pages</dc:format>
 *      <dc:format>book</dc:format>
 *      <dc:identifier>NsdMAAAACAAJ</dc:identifier>
 *      <dc:identifier>ISBN:0755100557</dc:identifier>
 *      <dc:identifier>ISBN:9780755100552</dc:identifier>
 *      <dc:language>en</dc:language>
 *      <dc:publisher>House of Stratus Limited</dc:publisher>
 *      <dc:subject>Fiction</dc:subject>
 *      <dc:title>Space Time and Nathaniel</dc:title>
 *    </entry>
 *
 *    <entry>
 *      <id>http://www.google.com/books/feeds/volumes/Tp0sOVqefewC</id>
 *      <updated>2019-07-17T09:02:42.000Z</updated>
 *      <category scheme='http://schemas.google.com/g/2005#kind'
 *              term='http://schemas.google.com/books/2008#volume'/>
 *      <title type='text'>Kavin's World</title>
 *      <link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown'
 *          href='http://books.google.com/books/content?id=Tp0sOVqefewC&amp;printsec=frontcover
 *          &amp;img=1&amp;zoom=5&amp;edge=curl&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *          href='http://books.google.com/books?id=Tp0sOVqefewC&amp;dq=ISBN%3C0340198273%3E
 *          &amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/preview' type='text/html'
 *          href='http://books.google.com/books?id=Tp0sOVqefewC&amp;printsec=frontcover
 *          &amp;dq=ISBN%3C0340198273%3E&amp;cd=8&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *          href='http://www.google.com/books/feeds/users/me/volumes'/>
 *      <link rel='http://schemas.google.com/books/2008/buylink' type='text/html'
 *          href='https://play.google.com/store/books/details?id=Tp0sOVqefewC
 *          &amp;rdid=book-Tp0sOVqefewC&amp;rdot=1&amp;source=gbs_gdata'/>
 *      <link rel='alternate' type='text/html'
 *          href='http://books.google.com/books?id=Tp0sOVqefewC&amp;dq=ISBN%3C0340198273%3E'/>
 *      <link rel='self' type='application/atom+xml'
 *          href='https://www.google.com/books/feeds/volumes/Tp0sOVqefewC'/>
 *      <gbs:contentVersion>0.1.1.0.preview.1</gbs:contentVersion>
 *      <gbs:embeddability value='http://schemas.google.com/books/2008#embeddable'/>
 *      <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *      <gbs:viewability value='http://schemas.google.com/books/2008#view_partial'/>
 *      <dc:creator>David Mason</dc:creator>
 *      <dc:date>1999-12-01</dc:date>
 *      <dc:format>221 pages</dc:format>
 *      <dc:format>book</dc:format>
 *      <dc:identifier>Tp0sOVqefewC</dc:identifier>
 *      <dc:identifier>ISBN:9781587150654</dc:identifier>
 *      <dc:identifier>ISBN:1587150654</dc:identifier>
 *      <dc:language>en</dc:language>
 *      <gbs:price type='SuggestedRetailPrice'>
 *        <gd:money amount='6.48' currencyCode='GBP'/>
 *      </gbs:price>
 *      <gbs:price type='RetailPrice'>
 *        <gd:money amount='4.34' currencyCode='GBP'/>
 *      </gbs:price>
 *      <dc:publisher>Wildside Press LLC</dc:publisher>
 *      <dc:subject>Fiction</dc:subject>
 *      <dc:title>Kavin's World</dc:title>
 *    </entry>
 *
 *    <entry>
 *      <id>http://www.google.com/books/feeds/volumes/4ZCy30OedPMC</id>
 *      <updated>2019-07-17T09:02:42.000Z</updated>
 *      <category scheme='http://schemas.google.com/g/2005#kind'
 *              term='http://schemas.google.com/books/2008#volume'/>
 *      <title type='text'>Eighty Minute Hour</title>
 *      <link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown'
 *          href='http://books.google.com/books/content?id=4ZCy30OedPMC&amp;printsec=frontcover
 *          &amp;img=1&amp;zoom=5&amp;edge=curl&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *          href='http://books.google.com/books?id=4ZCy30OedPMC&amp;dq=ISBN%3C0340198273%3E
 *          &amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/preview' type='text/html'
 *          href='http://books.google.com/books?id=4ZCy30OedPMC&amp;printsec=frontcover
 *          &amp;dq=ISBN%3C0340198273%3E&amp;cd=9&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *          href='http://www.google.com/books/feeds/users/me/volumes'/>
 *      <link rel='http://schemas.google.com/books/2008/buylink' type='text/html'
 *          href='https://play.google.com/store/books/details?id=4ZCy30OedPMC
 *          &amp;rdid=book-4ZCy30OedPMC&amp;rdot=1&amp;source=gbs_gdata'/>
 *      <link rel='alternate' type='text/html'
 *          href='http://books.google.com/books?id=4ZCy30OedPMC&amp;dq=ISBN%3C0340198273%3E'/>
 *      <link rel='self' type='application/atom+xml'
 *          href='https://www.google.com/books/feeds/volumes/4ZCy30OedPMC'/>
 *      <gbs:contentVersion>1.2.2.0.preview.2</gbs:contentVersion>
 *      <gbs:embeddability value='http://schemas.google.com/books/2008#embeddable'/>
 *      <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *      <gbs:viewability value='http://schemas.google.com/books/2008#view_partial'/>
 *      <dc:creator>Brian Aldiss</dc:creator>
 *      <dc:date>2013-10-24</dc:date>
 *      <dc:description>A Space Opera. An ambitious, incredible - Space Opera!</dc:description>
 *      <dc:format>201 pages</dc:format>
 *      <dc:format>book</dc:format>
 *      <dc:identifier>4ZCy30OedPMC</dc:identifier>
 *      <dc:identifier>ISBN:9780007482450</dc:identifier>
 *      <dc:identifier>ISBN:0007482450</dc:identifier>
 *      <dc:language>en</dc:language>
 *      <gbs:price type='SuggestedRetailPrice'>
 *        <gd:money amount='2.99' currencyCode='GBP'/>
 *      </gbs:price>
 *      <gbs:price type='RetailPrice'>
 *        <gd:money amount='2.99' currencyCode='GBP'/>
 *      </gbs:price>
 *      <dc:publisher>HarperCollins UK</dc:publisher>
 *      <dc:subject>Fiction</dc:subject>
 *      <dc:title>Eighty Minute Hour</dc:title>
 *    </entry>
 *
 *    <entry>
 *      <id>http://www.google.com/books/feeds/volumes/BccqAwAAQBAJ</id>
 *      <updated>2019-07-17T09:02:42.000Z</updated>
 *      <category scheme='http://schemas.google.com/g/2005#kind'
 *              term='http://schemas.google.com/books/2008#volume'/>
 *      <title type='text'>The Beast That Shouted Love at the Heart of the World</title>
 *      <link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown'
 *          href='http://books.google.com/books/content?id=BccqAwAAQBAJ&amp;printsec=frontcover
 *          &amp;img=1&amp;zoom=5&amp;edge=curl&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *          href='http://books.google.com/books?id=BccqAwAAQBAJ&amp;dq=ISBN%3C0340198273%3E
 *          &amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/preview' type='text/html'
 *          href='http://books.google.com/books?id=BccqAwAAQBAJ&amp;printsec=frontcover
 *          &amp;dq=ISBN%3C0340198273%3E&amp;cd=10&amp;source=gbs_gdata'/>
 *      <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *          href='http://www.google.com/books/feeds/users/me/volumes'/>
 *      <link rel='http://schemas.google.com/books/2008/buylink' type='text/html'
 *          href='https://play.google.com/store/books/details?id=BccqAwAAQBAJ
 *          &amp;rdid=book-BccqAwAAQBAJ&amp;rdot=1&amp;source=gbs_gdata'/>
 *      <link rel='alternate' type='text/html'
 *          href='http://books.google.com/books?id=BccqAwAAQBAJ&amp;dq=ISBN%3C0340198273%3E'/>
 *      <link rel='self' type='application/atom+xml'
 *          href='https://www.google.com/books/feeds/volumes/BccqAwAAQBAJ'/>
 *      <gbs:contentVersion>1.19.19.0.preview.3</gbs:contentVersion>
 *      <gbs:embeddability value='http://schemas.google.com/books/2008#embeddable'/>
 *      <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *      <gbs:viewability value='http://schemas.google.com/books/2008#view_partial'/>
 *      <dc:creator>Harlan Ellison</dc:creator>
 *      <dc:date>2014-04-01</dc:date>
 *      <dc:description>This groundbreaking collection brings .. bla bla ...</dc:description>
 *      <dc:format>300 pages</dc:format>
 *      <dc:format>book</dc:format>
 *      <dc:identifier>BccqAwAAQBAJ</dc:identifier>
 *      <dc:identifier>ISBN:9781497604896</dc:identifier>
 *      <dc:identifier>ISBN:1497604893</dc:identifier>
 *      <dc:language>en</dc:language>
 *      <gbs:price type='SuggestedRetailPrice'>
 *        <gd:money amount='10.79' currencyCode='GBP'/>
 *      </gbs:price>
 *      <gbs:price type='RetailPrice'>
 *        <gd:money amount='7.34' currencyCode='GBP'/>
 *      </gbs:price>
 *      <dc:publisher>Open Road Media</dc:publisher>
 *      <dc:subject>Fiction</dc:subject>
 *      <dc:title>The Beast That Shouted Love at the Heart of the World</dc:title>
 *      <dc:title>Stories</dc:title>
 *    </entry>
 *  </feed>
 *     }
 * </pre>
 */
class GoogleBooksHandler
        extends DefaultHandler {

    /** XML tags/attrs we look for. */
    private static final String XML_ID = "id";
    private static final String XML_ENTRY = "entry";
    private static final Pattern HTTP_PATTERN = Pattern.compile("http:", Pattern.LITERAL);

    private final StringBuilder mBuilder = new StringBuilder();
    @NonNull
    private final ArrayList<String> url = new ArrayList<>();

    private boolean mInEntry;
    private boolean mEntryDone;

    /**
     * Return the id of the first book found.
     *
     * @return The book url list (to be passed to the entry handler), can be empty.
     */
    @NonNull
    ArrayList<String> getUrlList() {
        return url;
    }

    /**
     * Start each XML element. Specifically identify when we are in the item
     * element and set the appropriate flag.
     */
    @Override
    @CallSuper
    public void startElement(@NonNull final String uri,
                             @NonNull final String localName,
                             @NonNull final String qName,
                             @NonNull final Attributes attributes) {
        if (!mEntryDone && localName.equalsIgnoreCase(XML_ENTRY)) {
            mInEntry = true;
        }
    }

    /**
     * Populate the results Bundle for each appropriate element.
     */
    @Override
    @CallSuper
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName) {
        /* the bits we want:
         *     <entry>
         *       <id>http://www.google.com/books/feeds/volumes/lf2EMetoLugC</id>
         */
        if (localName.equalsIgnoreCase(XML_ENTRY)) {
            mInEntry = false;
            mEntryDone = true;
        } else if (mInEntry) {
            if (localName.equalsIgnoreCase(XML_ID)) {
                // This url comes back as http, and we must use https... so replace it.
                url.add(HTTP_PATTERN.matcher(mBuilder.toString())
                                    .replaceAll(Matcher.quoteReplacement("https:")));
            }
        }

        // Always reset the length. This is not entirely the right thing to do, but works
        // because we always want strings from the lowest level (leaf) XML elements.
        // To be completely correct, we should maintain a stack of builders that are pushed and
        // popped as each startElement/endElement is called. But lets not be pedantic for now.
        mBuilder.setLength(0);
    }

    @Override
    @CallSuper
    public void characters(@NonNull final char[] ch,
                           final int start,
                           final int length) {
        mBuilder.append(ch, start, length);
    }
}
