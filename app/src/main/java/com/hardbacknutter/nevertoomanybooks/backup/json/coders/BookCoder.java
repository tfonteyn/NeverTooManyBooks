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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import android.content.Context;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * Supports all types from {@link DataManager#put} with the exception of {@link Serializable}.
 * <p>
 * {@link #encode} omits {@code null} values, numeric {@code 0} values and empty lists.
 */
public class BookCoder
        implements JsonCoder<Book> {

    private final JsonCoder<Author> authorCoder = new AuthorCoder();
    @NonNull
    private final JsonCoder<Bookshelf> bookshelfCoder;
    @NonNull
    private final JsonCoder<CalibreLibrary> calibreLibraryCoder;
    private final JsonCoder<Publisher> publisherCoder = new PublisherCoder();
    private final JsonCoder<Series> seriesCoder = new SeriesCoder();
    private final JsonCoder<TocEntry> tocEntryCoder = new TocEntryCoder();
    private final RealNumberParser realNumberParser;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param defaultStyle the default style to use for {@link Bookshelf}s
     */
    public BookCoder(@NonNull final Context context,
                     @NonNull final Style defaultStyle) {

        bookshelfCoder = new BookshelfCoder(context, defaultStyle);
        calibreLibraryCoder = new CalibreLibraryCoder(context, defaultStyle);
        this.realNumberParser = new RealNumberParser(context);
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Book book)
            throws JSONException {
        final JSONObject out = new JSONObject();
        for (final String key : book.keySet()) {
            encode(out, book, key);
        }
        return out;
    }

    private void encode(@NonNull final JSONObject out,
                        @NonNull final Book book,
                        @NonNull final String key)
            throws JSONException {

        final Object element = book.get(key, realNumberParser);

        // Special keys first.

        // The presence of FK_CALIBRE_LIBRARY (a row id) indicates there IS a calibre
        // library for this book but there is no other/more library data on the book itself.
        // We need to explicitly load the library and encode a reference for it.
        if (DBKey.FK_CALIBRE_LIBRARY.equals(key)) {
            final CalibreLibrary library = book.getCalibreLibrary();
            if (library != null) {
                // FK as it's a reference
                out.put(DBKey.FK_CALIBRE_LIBRARY,
                        calibreLibraryCoder.encodeReference(library));
            }

        } else if (element instanceof String) {
            if (!((String) element).isEmpty()) {
                out.put(key, element);
            }

        } else if (element instanceof Money) {
            // Only write the value. The currency will be handled as a plain String type key.
            final Money money = (Money) element;
            if (!money.isZero()) {
                out.put(key, money.getValue());
            }
        } else if (element instanceof Number) {
            if (((Number) element).doubleValue() != 0) {
                out.put(key, element);
            }
        } else if (element instanceof Boolean) {
            // always write regardless of being 'false'
            out.put(key, element);

        } else if (element instanceof ArrayList) {
            switch (key) {
                case Book.BKEY_AUTHOR_LIST: {
                    final List<Author> list = book.getAuthors();
                    if (!list.isEmpty()) {
                        out.put(key, authorCoder.encode(list));
                    }
                    break;
                }
                case Book.BKEY_BOOKSHELF_LIST: {
                    final List<Bookshelf> list = book.getBookshelves();
                    if (!list.isEmpty()) {
                        // FK as it's a reference
                        out.put(DBKey.FK_BOOKSHELF, bookshelfCoder.encodeReference(list));
                    }
                    break;
                }
                case Book.BKEY_PUBLISHER_LIST: {
                    final List<Publisher> list = book.getPublishers();
                    if (!list.isEmpty()) {
                        out.put(key, publisherCoder.encode(list));
                    }
                    break;
                }
                case Book.BKEY_SERIES_LIST: {
                    final List<Series> list = book.getSeries();
                    if (!list.isEmpty()) {
                        out.put(key, seriesCoder.encode(list));
                    }
                    break;
                }
                case Book.BKEY_TOC_LIST: {
                    final List<TocEntry> list = book.getToc();
                    if (!list.isEmpty()) {
                        out.put(key, tocEntryCoder.encode(list));
                    }
                    break;
                }

                default:
                    throw new IllegalArgumentException("key=" + key + "|: " + element);
            }

        } else if (element instanceof Parcelable) {
            // skip, 2023-02-23: the only one in use for now is the Calibre Library
            // which is already handled above.

        } else if (element instanceof Serializable) {
            throw new IllegalArgumentException("Serializable not implemented for: " + element);

        } else if (element != null) {
            throw new IllegalArgumentException("key=" + key + "|o=" + element);
        }
    }

    @Override
    @NonNull
    public Book decode(@NonNull final JSONObject data)
            throws JSONException {
        final Book book = new Book();
        final Iterator<String> it = data.keys();
        while (it.hasNext()) {
            final String key = it.next();
            switch (key) {
                case Book.BKEY_BOOKSHELF_LIST:
                    // Full object
                    book.setBookshelves(bookshelfCoder.decode(data.getJSONArray(key)));
                    break;

                case DBKey.FK_BOOKSHELF:
                    // Reference; if the reference is not found,
                    // the book will be put on the preferred (or default) Bookshelf.
                    book.setBookshelves(bookshelfCoder.decodeReference(data.getJSONArray(key)));
                    break;

                case Book.BKEY_CALIBRE_LIBRARY:
                    // Full object
                    book.setCalibreLibrary(calibreLibraryCoder.decode(data.getJSONObject(key)));
                    break;

                case DBKey.FK_CALIBRE_LIBRARY:
                    // Reference; if the reference is not found,
                    // the Calibre data is removed from the book
                    book.setCalibreLibrary(
                            calibreLibraryCoder.decodeReference(data.getJSONObject(key))
                                               .orElse(null));
                    break;


                case Book.BKEY_AUTHOR_LIST:
                    book.setAuthors(authorCoder.decode(data.getJSONArray(key)));
                    break;

                case Book.BKEY_PUBLISHER_LIST:
                    book.setPublishers(publisherCoder.decode(data.getJSONArray(key)));
                    break;

                case Book.BKEY_SERIES_LIST:
                    book.setSeries(seriesCoder.decode(data.getJSONArray(key)));
                    break;

                case Book.BKEY_TOC_LIST:
                    book.setToc(tocEntryCoder.decode(data.getJSONArray(key)));
                    break;


                default: {
                    final Object o = data.get(key);
                    if (o instanceof String) {
                        book.putString(key, (String) o);
                    } else if (o instanceof Long) {
                        book.putLong(key, (Long) o);
                    } else if (o instanceof Integer) {
                        book.putInt(key, (Integer) o);
                    } else if (o instanceof Double) {
                        // Double covers 'Money'. The currency is done with a separate String type.
                        book.putDouble(key, (Double) o);
                    } else if (o instanceof Float) {
                        book.putFloat(key, (Float) o);
                    } else if (o instanceof Boolean) {
                        book.putBoolean(key, (Boolean) o);

                    } else if (o instanceof BigInteger) {
                        // added since org.json:json:20201115
                        book.putLong(key, ((BigInteger) o).longValue());
                    } else if (o instanceof BigDecimal) {
                        // added since org.json:json:20201115
                        book.putDouble(key, ((BigDecimal) o).doubleValue());

                    } else {
                        throw new IllegalArgumentException("key=" + key
                                                           + "|type=" + o.getClass().getName()
                                                           + "|o=" + o);
                    }
                }
            }
        }

        return book;
    }
}
