/*
 * @Copyright 2020 HardBackNutter
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

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * Supports all types from {@link DataManager#put} with the exception of {@link Serializable}.
 * <p>
 * {@link #encode} omits {@code null} values, numeric {@code 0} values and empty lists.
 */
public class BookCoder
        implements JsonCoder<Book> {

    /** JSON Object tag for the array of books. */
    public static final String BOOK_LIST = "book_list";

    private final JsonCoder<Author> mAuthorCoder = new AuthorCoder();
    private final JsonCoder<Publisher> mPublisherCoder = new PublisherCoder();
    private final JsonCoder<Series> mSeriesCoder = new SeriesCoder();
    private final JsonCoder<TocEntry> mTocEntryCoder = new TocEntryCoder();
    private final JsonCoder<Bookshelf> mBookshelfCoder;

    public BookCoder(@NonNull final ListStyle defStyle) {
        mBookshelfCoder = new BookshelfCoder(defStyle);
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

        final Object element = book.get(key);

        if (element instanceof String) {
            if (!((String) element).isEmpty()) {
                out.put(key, element);
            }

        } else if (element instanceof Money) {
            // Only write the value. The currency will be covered as a plain String type key.
            // We could just treat Money as a Number (which it is) but JSONStringer uses
            // 'toString' which caused some issues... so keeping this as a reminder.
            final double d = ((Money) element).doubleValue();
            if (d != 0) {
                out.put(key, d);
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
                    final ArrayList<Author> list = book.getParcelableArrayList(key);
                    if (!list.isEmpty()) {
                        out.put(key, mAuthorCoder.encode(list));
                    }
                    break;
                }
                case Book.BKEY_BOOKSHELF_LIST: {
                    final ArrayList<Bookshelf> list = book.getParcelableArrayList(key);
                    if (!list.isEmpty()) {
                        out.put(key, mBookshelfCoder.encode(list));
                    }
                    break;
                }
                case Book.BKEY_PUBLISHER_LIST: {
                    final ArrayList<Publisher> list = book.getParcelableArrayList(key);
                    if (!list.isEmpty()) {
                        out.put(key, mPublisherCoder.encode(list));
                    }
                    break;
                }
                case Book.BKEY_SERIES_LIST: {
                    final ArrayList<Series> list = book.getParcelableArrayList(key);
                    if (!list.isEmpty()) {
                        out.put(key, mSeriesCoder.encode(list));
                    }
                    break;
                }
                case Book.BKEY_TOC_LIST: {
                    final ArrayList<TocEntry> list = book.getParcelableArrayList(key);
                    if (!list.isEmpty()) {
                        out.put(key, mTocEntryCoder.encode(list));
                    }
                    break;
                }

                default:
                    throw new IllegalArgumentException("key=" + key + "|: " + element);
            }

        } else if (element instanceof Serializable) {
            throw new IllegalArgumentException("Serializable not implemented o=" + element);

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
                case Book.BKEY_AUTHOR_LIST:
                    book.putParcelableArrayList(
                            key, mAuthorCoder.decode(data.getJSONArray(key)));
                    break;

                case Book.BKEY_BOOKSHELF_LIST:
                    book.putParcelableArrayList(
                            key, mBookshelfCoder.decode(data.getJSONArray(key)));
                    break;

                case Book.BKEY_PUBLISHER_LIST:
                    book.putParcelableArrayList(
                            key, mPublisherCoder.decode(data.getJSONArray(key)));
                    break;

                case Book.BKEY_SERIES_LIST:
                    book.putParcelableArrayList(
                            key, mSeriesCoder.decode(data.getJSONArray(key)));
                    break;

                case Book.BKEY_TOC_LIST:
                    book.putParcelableArrayList(
                            key, mTocEntryCoder.decode(data.getJSONArray(key)));
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
                    } else {
                        throw new IllegalArgumentException("key=" + key + "|o=" + o);
                    }
                }
            }
        }

        return book;
    }
}
