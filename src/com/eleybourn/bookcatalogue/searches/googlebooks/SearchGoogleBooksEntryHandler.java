/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.Locale;

/*
 * An XML handler for the Google Books entry return
 *
 * <?xml version='1.0' encoding='UTF-8'?>
 * <entry xmlns='http://www.w3.org/2005/Atom'
 *      xmlns:gbs='http://schemas.google.com/books/2008'
 *      xmlns:dc='http://purl.org/dc/terms'
 *      xmlns:batch='http://schemas.google.com/gdata/batch'
 *      xmlns:gd='http://schemas.google.com/g/2005'>
 *
 * 		<id>http://www.google.com/books/feeds/volumes/A4NDPgAACAAJ</id>
 * 		<updated>2010-02-28T10:49:24.000Z</updated>
 * 		<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/books/2008#volume'/>
 * 		<title type='text'>The trigger</title>
 * 		<link rel='http://schemas.google.com/books/2008/info' type='text/html' href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;ie=ISO-8859-1&amp;source=gbs_gdata'/>
 * 		<link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml' href='http://www.google.com/books/feeds/users/me/volumes'/>
 * 		<link rel='alternate' type='text/html' href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;ie=ISO-8859-1'/>
 * 		<link rel='self' type='application/atom+xml' href='http://www.google.com/books/feeds/volumes/A4NDPgAACAAJ'/>
 * 		<gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 * 		<gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 * 		<gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 * 		<dc:creator>Arthur Charles Clarke</dc:creator>
 * 		<dc:creator>Michael P. Kube-McDowell</dc:creator>
 * 		<dc:date>2000-01-01</dc:date>
 * 		<dc:format>Dimensions 11.0x18.0x3.6 cm</dc:format>
 * 		<dc:format>550 pages</dc:format>
 * 		<dc:format>book</dc:format>
 * 		<dc:identifier>A4NDPgAACAAJ</dc:identifier>
 * 		<dc:identifier>ISBN:0006483836</dc:identifier>
 * 		<dc:identifier>ISBN:9780006483830</dc:identifier>
 * 		<dc:language>en</dc:language>
 * 		<dc:publisher>Voyager</dc:publisher>
 * 		<dc:subject>Fiction / Science Fiction / General</dc:subject>
 * 		<dc:subject>Fiction / Technological</dc:subject>
 * 		<dc:subject>Fiction / War &amp; Military</dc:subject>
 * 		<dc:title>The trigger</dc:title>
 * </entry>
 *
 * <?xml version='1.0' encoding='UTF-8'?>
 * <entry xmlns='http://www.w3.org/2005/Atom'
 *      xmlns:gbs='http://schemas.google.com/books/2008'
 *      xmlns:dc='http://purl.org/dc/terms'
 *      xmlns:batch='http://schemas.google.com/gdata/batch'
 *      xmlns:gd='http://schemas.google.com/g/2005'>
 *
 * 		<id>http://www.google.com/books/feeds/volumes/lf2EMetoLugC</id>
 * 		<updated>2010-03-01T07:31:23.000Z</updated>
 * 		<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/books/2008#volume'/>
 * 		<title type='text'>The Geeks' Guide to World Domination</title>
 * 		<link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown' href='http://bks3.books.google.com/books?id=lf2EMetoLugC&amp;printsec=frontcover&amp;img=1&amp;zoom=5&amp;sig=ACfU3U1hcfy_NvWZbH46OzWwmQQCDV46lA&amp;source=gbs_gdata'/>
 * 		<link rel='http://schemas.google.com/books/2008/info' type='text/html' href='http://books.google.com/books?id=lf2EMetoLugC&amp;ie=ISO-8859-1&amp;source=gbs_gdata'/>
 * 		<link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml' href='http://www.google.com/books/feeds/users/me/volumes'/>
 * 		<link rel='alternate' type='text/html' href='http://books.google.com/books?id=lf2EMetoLugC&amp;ie=ISO-8859-1'/>
 * 		<link rel='self' type='application/atom+xml' href='http://www.google.com/books/feeds/volumes/lf2EMetoLugC'/>
 * 		<gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 * 		<gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 * 		<gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 * 		<dc:creator>Garth Sundem</dc:creator>
 * 		<dc:date>2009-03-10</dc:date>
 * 		<dc:description>These days, from blah blah ....the Geek Wars have</dc:description>
 * 		<dc:format>Dimensions 13.2x20.1x2.0 cm</dc:format>
 * 		<dc:format>288 pages</dc:format>
 * 		<dc:format>book</dc:format>
 * 		<dc:identifier>lf2EMetoLugC</dc:identifier>
 * 		<dc:identifier>ISBN:0307450341</dc:identifier>
 * 		<dc:identifier>ISBN:9780307450340</dc:identifier>
 * 		<dc:language>en</dc:language>
 * 		<dc:publisher>Three Rivers Press</dc:publisher>
 * 		<dc:subject>Curiosities and wonders/ Humor</dc:subject>
 * 		<dc:subject>Geeks (Computer enthusiasts)/ Humor</dc:subject>
 * 		<dc:subject>Curiosities and wonders</dc:subject>
 * 		<dc:subject>Geeks (Computer enthusiasts)</dc:subject>
 * 		<dc:subject>Humor / Form / Parodies</dc:subject>
 * 		<dc:subject>Humor / General</dc:subject>
 * 		<dc:subject>Humor / General</dc:subject>
 * 		<dc:subject>Humor / Form / Comic Strips &amp; Cartoons</dc:subject>
 * 		<dc:subject>Humor / Form / Essays</dc:subject>
 * 		<dc:subject>Humor / Form / Parodies</dc:subject>
 * 		<dc:subject>Reference / General</dc:subject>
 * 		<dc:subject>Reference / Curiosities &amp; Wonders</dc:subject>
 * 		<dc:subject>Reference / Encyclopedias</dc:subject>
 * 		<dc:title>The Geeks' Guide to World Domination</dc:title>
 * 		<dc:title>Be Afraid, Beautiful People</dc:title>
 * </entry>
 *
 */
class SearchGoogleBooksEntryHandler extends DefaultHandler {

    /** file suffix for cover files */
    private static final String FILENAME_SUFFIX = "_GB";

    /** XML tags we look for */
//	private static final String ID = "id";
    private static final String XML_AUTHOR = "creator";
    private static final String XML_TITLE = "title";
    private static final String XML_ISBN = "identifier";
    private static final String XML_DATE_PUBLISHED = "date";
    private static final String XML_PUBLISHER = "publisher";
    private static final String XML_FORMAT = "format";
    private static final String XML_LINK = "link";
    private static final String XML_GENRE = "subject";
    private static final String XML_DESCRIPTION = "description";
    private static final String XML_LANGUAGE = "language";

    private static boolean mFetchThumbnail;
    @NonNull
    private final Bundle mValues;
    private StringBuilder builder;

    SearchGoogleBooksEntryHandler(final @NonNull Bundle values, final boolean fetchThumbnail) {
        mValues = values;
        mFetchThumbnail = fetchThumbnail;
    }

    @Override
    @CallSuper
    public void characters(final @NonNull char[] ch, final int start, final int length) throws SAXException {
        super.characters(ch, start, length);
        builder.append(ch, start, length);
    }

    private void addIfNotPresent(final @NonNull String key, final @NonNull String value) {
        String test = mValues.getString(key);
        if (test == null || test.isEmpty()) {
            mValues.putString(key, value);
        }
    }
    @Override
    @CallSuper
    public void endElement(final @NonNull String uri,
                           final @NonNull String localName,
                           final @NonNull String name) throws SAXException {
        super.endElement(uri, localName, name);

        switch(localName.toLowerCase()) {
            case XML_TITLE: {
                // there can be multiple listed, but only one 'primary'
                addIfNotPresent(UniqueId.KEY_TITLE, builder.toString());
                break;
            }
            case XML_ISBN: {
                String tmp = builder.toString();
                if (tmp.indexOf("ISBN:") == 0) {
                    tmp = tmp.substring(5);
                    String isbn = mValues.getString(UniqueId.KEY_BOOK_ISBN);
                    // store the 'longest' isbn
                    if (isbn == null || tmp.length() > isbn.length()) {
                        mValues.putString(UniqueId.KEY_BOOK_ISBN, tmp);
                    }
                }
                break;
            }
            case XML_LANGUAGE: {
                String lang = builder.toString();
                if (!lang.isEmpty()) {
                    Locale loc = new Locale(lang);
                    addIfNotPresent(UniqueId.KEY_BOOK_LANGUAGE, loc.getDisplayLanguage());
                }
                break;
            }
            case XML_AUTHOR: {
                StringList.addOrAppend(mValues, UniqueId.BKEY_AUTHOR_STRING_LIST, builder.toString());
                break;
            }
            case XML_PUBLISHER: {
                addIfNotPresent(UniqueId.KEY_BOOK_PUBLISHER, builder.toString());
                break;
            }
            case XML_DATE_PUBLISHED: {
                addIfNotPresent(UniqueId.KEY_BOOK_DATE_PUBLISHED, builder.toString());
                break;
            }
            case XML_FORMAT: {
                /*
                 * 		<dc:format>Dimensions 13.2x20.1x2.0 cm</dc:format>
                 * 		<dc:format>288 pages</dc:format>
                 * 		<dc:format>book</dc:format>
                 */
                String tmp = builder.toString();
                int index = tmp.indexOf(" pages");
                if (index > -1) {
                    tmp = tmp.substring(0, index).trim();
                    mValues.putString(UniqueId.KEY_BOOK_PAGES, tmp);
                }
                break;
            }
            case XML_GENRE: {
                //ENHANCE: only the 'last' genre is taken, but a book/genre needs to be restructured to have a separate 'genres' table
                mValues.putString(UniqueId.KEY_BOOK_GENRE, builder.toString());
                break;
            }
            case XML_DESCRIPTION: {
                addIfNotPresent(UniqueId.KEY_DESCRIPTION, builder.toString());
                break;
            }
        }

        builder.setLength(0);
    }

    @Override
    @CallSuper
    public void startDocument() throws SAXException {
        super.startDocument();
        builder = new StringBuilder();
    }

    @Override
    @CallSuper
    public void startElement(final @NonNull String uri,
                             final @NonNull String localName,
                             final @NonNull String name,
                             final @NonNull Attributes attributes) throws SAXException {
        super.startElement(uri, localName, name, attributes);
        if (mFetchThumbnail && XML_LINK.equalsIgnoreCase(localName)) {
            if (("http://schemas.google.com/books/2008/thumbnail").equals(attributes.getValue("", "rel"))) {
                String thumbnail = attributes.getValue("", "href");
                String fileSpec = ImageUtils.saveThumbnailFromUrl(thumbnail, FILENAME_SUFFIX);
                if (!fileSpec.isEmpty()) {
                    StringList.addOrAppend(mValues, UniqueId.BKEY_THUMBNAIL_FILE_SPEC, fileSpec);
                }
            }
        }
    }
}
