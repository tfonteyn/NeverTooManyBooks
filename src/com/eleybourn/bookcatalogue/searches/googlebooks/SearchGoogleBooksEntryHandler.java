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
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/*
 * An XML handler for the Google Books entry return
 *
 * <?xml version='1.0' encoding='UTF-8'?>
 * <entry xmlns='http://www.w3.org/2005/Atom' xmlns:gbs='http://schemas.google.com/books/2008' xmlns:dc='http://purl.org/dc/terms' xmlns:batch='http://schemas.google.com/gdata/batch' xmlns:gd='http://schemas.google.com/g/2005'>
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
 * <entry xmlns='http://www.w3.org/2005/Atom' xmlns:gbs='http://schemas.google.com/books/2008' xmlns:dc='http://purl.org/dc/terms' xmlns:batch='http://schemas.google.com/gdata/batch' xmlns:gd='http://schemas.google.com/g/2005'>
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
 * 		<dc:description>TUNE IN. TURN ON. GEEK OUT.Sorry, beautiful people. These days, from government to business to technology to Hollywood, geeks rule the world. Finally, here’s the book no self-respecting geek can live without–a guide jam-packed with 314.1516 short entries both useful and fun. Science, pop-culture trivia, paper airplanes, and pure geek-ish nostalgia coexist as happily in these pages as they do in their natural habitat of the geek brain.In short, dear geek, here you’ll find everything you need to achieve nirvana. And here, for you pathetic non-geeks, is the last chance to save yourselves: Love this book, live this book, and you too can join us in the experience of total world domination. • become a sudoku god• brew your own beer• build a laser beam• classify all living things• clone your pet• exorcise demons• find the world’s best corn mazes• grasp the theory of relativity• have sex on Second Life• injure a fish• join the Knights Templar• kick ass with sweet martial-arts moves• learn ludicrous emoticons• master the Ocarina of Time• pimp your cubicle• program a remote control• quote He-Man and Che Guevara• solve fiendish logic puzzles• touch Carl Sagan • unmask Linus Torvalds• visit Beaver Lick, Kentucky• win bar bets• write your name in ElvishJoin us or die, you will.Begun, the Geek Wars have</dc:description>
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
    //	private static final String ID = "id";
//	private static final String TOTAL_RESULTS = "totalResults";
//	private static final String ENTRY = "entry";
    private static final String AUTHOR = "creator";
    private static final String TITLE = "title";
    private static final String ISBN = "identifier";
    private static final String DATE_PUBLISHED = "date";
    private static final String PUBLISHER = "publisher";
    private static final String PAGES = "format"; // yes, 'format'
    private static final String LINK = "link";
    private static final String GENRE = "subject";
    private static final String DESCRIPTION = "description";
    private static boolean mFetchThumbnail;
    @NonNull
    private final Bundle mValues;
    private StringBuilder builder;

    SearchGoogleBooksEntryHandler(@NonNull final Bundle values, final boolean fetchThumbnail) {
        mValues = values;
        mFetchThumbnail = fetchThumbnail;
    }

    @Override
    @CallSuper
    public void characters(@NonNull final char[] ch, final int start, final int length) throws SAXException {
        super.characters(ch, start, length);
        builder.append(ch, start, length);
    }

    private void addIfNotPresent(@NonNull final String key) {
        String test = mValues.getString(key);
        if (test == null || test.isEmpty()) {
            mValues.putString(key, builder.toString());
        }
    }

    @Override
    @CallSuper
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String name) throws SAXException {
        super.endElement(uri, localName, name);

        switch(localName.toLowerCase()) {
            case TITLE: {
                addIfNotPresent(UniqueId.KEY_TITLE);
                break;
            }
            case ISBN: {
                String tmp = builder.toString();
                if (tmp.indexOf("ISBN:") == 0) {
                    tmp = tmp.substring(5);
                    String isbn = mValues.getString(UniqueId.KEY_BOOK_ISBN);
                    if (isbn == null || tmp.length() > isbn.length()) {
                        mValues.putString(UniqueId.KEY_BOOK_ISBN, tmp);
                    }
                }
                break;
            }
            case AUTHOR: {
                ArrayUtils.addOrAppend(mValues, UniqueId.BKEY_AUTHOR_STRING_LIST, builder.toString());
                break;
            }
            case PUBLISHER: {
                addIfNotPresent(UniqueId.KEY_BOOK_PUBLISHER);
                break;
            }
            case DATE_PUBLISHED: {
                addIfNotPresent(UniqueId.KEY_BOOK_DATE_PUBLISHED);
                break;
            }
            case PAGES: {
                String tmp = builder.toString();
                int index = tmp.indexOf(" pages");
                if (index > -1) {
                    tmp = tmp.substring(0, index).trim();
                    mValues.putString(UniqueId.KEY_BOOK_PAGES, tmp);
                }
                break;
            }
            case GENRE: {
                //ENHANCE: only the 'last' genre is taken, but a book/genre needs to be restructured to have a separate 'genres' table
                mValues.putString(UniqueId.KEY_BOOK_GENRE, builder.toString());
                break;
            }
            case DESCRIPTION: {
                addIfNotPresent(UniqueId.KEY_DESCRIPTION);
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
    public void startElement(@NonNull final String uri,
                             @NonNull final String localName,
                             @NonNull final String name,
                             @NonNull final Attributes attributes) throws SAXException {
        super.startElement(uri, localName, name, attributes);
        if (mFetchThumbnail && LINK.equalsIgnoreCase(localName)) {
            if (("http://schemas.google.com/books/2008/thumbnail").equals(attributes.getValue("", "rel"))) {
                String thumbnail = attributes.getValue("", "href");
                String fileSpec = ImageUtils.saveThumbnailFromUrl(thumbnail, "_GB");
                if (!fileSpec.isEmpty()) {
                    ArrayUtils.addOrAppend(mValues, UniqueId.BKEY_THUMBNAIL_FILES_SPEC, fileSpec);
                }
            }
        }
    }
}
