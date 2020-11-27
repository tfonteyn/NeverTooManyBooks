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

import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
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
        extends JsonCoderBase<Book> {

    private final AuthorCoder mAuthorCoder = new AuthorCoder();
    private final PublisherCoder mPublisherCoder = new PublisherCoder();
    private final SeriesCoder mSeriesCoder = new SeriesCoder();
    private final TocEntryCoder mTocEntryCoder = new TocEntryCoder();
    private final BookshelfCoder mBookshelfCoder;

    public BookCoder(@NonNull final BooklistStyle defStyle) {
        mBookshelfCoder = new BookshelfCoder(defStyle);
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Book book) {
        final JSONObject data = new JSONObject();
        //noinspection SimplifyStreamApiCallChains
        book.keySet().stream().forEach(key -> encode(data, book, key));
        return data;
    }

    private void encode(@NonNull final JSONObject data,
                        @NonNull final Book book,
                        @NonNull final String key) {
        try {
            final Object o = book.get(key);

            if (o instanceof String && !((String) o).isEmpty()) {
                data.put(key, o);

            } else if (o instanceof Long && (Long) o != 0) {
                data.put(key, o);

            } else if (o instanceof Integer && (Integer) o != 0) {
                data.put(key, o);

            } else if (o instanceof Double && (Double) o != 0) {
                data.put(key, o);

            } else if (o instanceof Float && (Float) o != 0) {
                data.put(key, o);

            } else if (o instanceof Boolean) {
                // always write regardless of being 'false'
                data.put(key, o);

            } else if (o instanceof ArrayList) {
                switch (key) {
                    case Book.BKEY_AUTHOR_LIST: {
                        final ArrayList<Author> list = book.getParcelableArrayList(key);
                        if (!list.isEmpty()) {
                            data.put(key, mAuthorCoder.encode(list));
                        }
                        break;
                    }
                    case Book.BKEY_BOOKSHELF_LIST: {
                        final ArrayList<Bookshelf> list = book.getParcelableArrayList(key);
                        if (!list.isEmpty()) {
                            data.put(key, mBookshelfCoder.encode(list));
                        }
                        break;
                    }
                    case Book.BKEY_PUBLISHER_LIST: {
                        final ArrayList<Publisher> list = book.getParcelableArrayList(key);
                        if (!list.isEmpty()) {
                            data.put(key, mPublisherCoder.encode(list));
                        }
                        break;
                    }
                    case Book.BKEY_SERIES_LIST: {
                        final ArrayList<Series> list = book.getParcelableArrayList(key);
                        if (!list.isEmpty()) {
                            data.put(key, mSeriesCoder.encode(list));
                        }
                        break;
                    }
                    case Book.BKEY_TOC_LIST: {
                        final ArrayList<TocEntry> list = book.getParcelableArrayList(key);
                        if (!list.isEmpty()) {
                            data.put(key, mTocEntryCoder.encode(list));
                        }
                        break;
                    }

                    default:
                        throw new IllegalArgumentException("key=" + key + "|: " + o);
                }
            } else if (o instanceof Money) {
                // Only write the value. The currency will be covered as a plain String type key.
                final double d = ((Money) o).doubleValue();
                if (d != 0) {
                    data.put(key, d);
                }

            } else if (o instanceof Serializable) {
                throw new IllegalArgumentException("Serializable not implemented");

            } else if (o != null) {
                throw new IllegalArgumentException("key=" + key + "|o=" + o);
            }

        } catch (@NonNull final JSONException e) {
            throw new IllegalStateException(e);
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
