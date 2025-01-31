/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.LegacyUpgrades;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoCollectionData;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.TagMapper;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * Supports all types from {@link DataManager#put} with the exception of {@link Serializable}.
 * <p>
 * {@link #encode} omits {@code null} values, numeric {@code 0} values and empty lists.
 * <p>
 * For historical reasons some boolean flags are encoded as {@code 1} for {@code true},
 * or {@code 0} for {@code false}.
 * Decoding will read and store the numeric value in the {@link Book}.
 * When this value is send to the database all is well because SQLite uses {@code 0/1} for
 * booleans anyhow. When fetched with {@link Book#getBoolean(String)} the internal parser
 * will convert to booleans.
 */
public class BookCoder
        implements JsonCoder<Book> {


    private final JsonCoder<Author> authorCoder = new AuthorCoder();
    @NonNull
    private final JsonCoder<Bookshelf> bookshelfCoder;
    @NonNull
    private final JsonCoder<CalibreLibrary> calibreLibraryCoder;
    private final JsonCoder<StripInfoCollectionData> stripInfoDataCoder = new StripInfoDataCoder();
    private final JsonCoder<Publisher> publisherCoder = new PublisherCoder();
    private final JsonCoder<Series> seriesCoder = new SeriesCoder();
    private final JsonCoder<TocEntry> tocEntryCoder = new TocEntryCoder();
    private final JsonCoder<Tag> tagCoder = new TagCoder();
    private final JsonCoder<Identifier.Value> identifierCoder = new IdentifierValueCoder();
    @NonNull
    private final RealNumberParser realNumberParser;
    @NonNull
    private final Context context;

    private final TagMapper tagMapper;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param defaultStyle the default style to use for {@link Bookshelf}s
     */
    public BookCoder(@NonNull final Context context,
                     @NonNull final Style defaultStyle) {

        this.context = context;

        bookshelfCoder = new BookshelfCoder(context, defaultStyle);
        calibreLibraryCoder = new CalibreLibraryCoder(context, defaultStyle);
        realNumberParser = new RealNumberParser(LocaleListUtils.asList(context));

        tagMapper = new TagMapper(context);
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Book book)
            throws JSONException {

        final JSONObject out = new JSONObject();
        book.keySet().forEach(key -> encode(out, book, key));
        return out;
    }

    private void encode(@NonNull final JSONObject out,
                        @NonNull final Book book,
                        @NonNull final String key)
            throws JSONException {

        switch (key) {
            case Book.BKEY_BOOKSHELF_LIST: {
                final List<Bookshelf> list = book.getBookshelves();
                if (!list.isEmpty()) {
                    // FK as it's a reference
                    out.put(DBKey.FK_BOOKSHELF, bookshelfCoder.encodeReference(list));
                }
                return;
            }
            case Book.BKEY_AUTHOR_LIST: {
                final List<Author> list = book.getAuthors();
                if (!list.isEmpty()) {
                    out.put(key, authorCoder.encode(list));
                }
                return;
            }
            case Book.BKEY_PUBLISHER_LIST: {
                final List<Publisher> list = book.getPublishers();
                if (!list.isEmpty()) {
                    out.put(key, publisherCoder.encode(list));
                }
                return;
            }
            case Book.BKEY_SERIES_LIST: {
                final List<Series> list = book.getSeries();
                if (!list.isEmpty()) {
                    out.put(key, seriesCoder.encode(list));
                }
                return;
            }
            case Book.BKEY_TOC_LIST: {
                final List<TocEntry> list = book.getToc();
                if (!list.isEmpty()) {
                    out.put(key, tocEntryCoder.encode(list));
                }
                return;
            }
            case Book.BKEY_TAG_LIST: {
                final List<Tag> list = book.getTags();
                if (!list.isEmpty()) {
                    out.put(key, tagCoder.encode(list));
                }
                return;
            }
            case Book.BKEY_IDENTIFIER_LIST: {
                final List<Identifier.Value> list = book.getIdentifiers();
                if (!list.isEmpty()) {
                    out.put(key, identifierCoder.encode(list));
                }
                return;
            }
            case DBKey.FK_CALIBRE_LIBRARY: {
                // The presence of FK_CALIBRE_LIBRARY indicates there IS a calibre library
                // for this book but there is no actual library data on the book itself.
                // We need to explicitly load the library and encode a reference for it.
                // FK as it's a reference
                book.getCalibreLibrary().ifPresent(library -> out
                        .put(DBKey.FK_CALIBRE_LIBRARY,
                             calibreLibraryCoder.encodeReference(library)));
                return;
            }
            case StripInfoCollectionData.BKEY: {
                book.getStripInfoCollectionData().ifPresent(scd -> out.put(
                        StripInfoCollectionData.BKEY, stripInfoDataCoder.encode(scd)));
                return;

            }
        }

        final Object element = book.get(key, realNumberParser);

        if (element instanceof CharSequence) {
            if (((CharSequence) element).length() > 0) {
                out.put(key, element);
            }
        } else if (element instanceof Money) {
            // Only write the value.
            // The currency will be handled as a plain String type key.
            final Money money = (Money) element;
            if (!money.isZero()) {
                out.put(key, money.getValue());
            }
        } else if (element instanceof Number) {
            if (((Number) element).doubleValue() != 0) {
                out.put(key, element);
            }
        } else if (element instanceof Boolean) {
            out.put(key, element);

            // } else if (element instanceof ArrayList) {
            // } else if (element instanceof Parcelable) {
            // } else if (element instanceof Serializable) {
            //    throw new IllegalArgumentException("Serializable not implemented: "
            //                                       + element);

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
                case Book.BKEY_AUTHOR_LIST: {
                    book.setAuthors(authorCoder.decode(data.getJSONArray(key)));
                    break;
                }
                case DBKey.FK_BOOKSHELF: {
                    // Reference as written by archive version 4+
                    // If the reference is not found,
                    // the book will be put on the preferred (or default) Bookshelf.
                    book.setBookshelves(bookshelfCoder.decodeReference(data.getJSONArray(key)));
                    break;
                }
                case Book.BKEY_IDENTIFIER_LIST: {
                    book.setIdentifiers(identifierCoder.decode(data.getJSONArray(key)));
                    break;
                }
                case Book.BKEY_PUBLISHER_LIST: {
                    book.setPublishers(publisherCoder.decode(data.getJSONArray(key)));
                    break;
                }
                case Book.BKEY_SERIES_LIST: {
                    book.setSeries(seriesCoder.decode(data.getJSONArray(key)));
                    break;
                }
                case Book.BKEY_TAG_LIST: {
                    book.setTags(tagCoder.decode(data.getJSONArray(key)));
                    break;
                }
                case Book.BKEY_TOC_LIST: {
                    book.setToc(tocEntryCoder.decode(data.getJSONArray(key)));
                    break;
                }

                case DBKey.FK_CALIBRE_LIBRARY: {
                    // Reference as written by archive version 4+
                    // If the reference is not found,
                    // the Calibre data is removed from the book
                    book.setCalibreLibrary(
                            calibreLibraryCoder.decodeReference(data.getJSONObject(key))
                                               .orElse(null));
                    break;
                }
                case StripInfoCollectionData.BKEY: {
                    book.setStripInfoCollectionData(
                            stripInfoDataCoder.decode(data.getJSONObject(key)));
                    break;
                }
                default: {
                    if (!decodeLegacyKeys(context, key, data, book)) {
                        // All other keys
                        book.put(key, data.get(key));
                    }
                    break;
                }
            }
        }
        return book;
    }

    /**
     * Decode/migrate data from older archive version.
     *
     * @param context Current context
     * @param key     to handle
     * @param data    to decode
     * @param book    to update
     *
     * @return {@code true} if the key was handled
     */
    private boolean decodeLegacyKeys(@NonNull final Context context,
                                     @NonNull final String key,
                                     @NonNull final JSONObject data,
                                     @NonNull final Book book) {
        switch (key) {
            case "bookshelf_list": {
                // Full object as written by archive version 2..3
                book.setBookshelves(bookshelfCoder.decode(data.getJSONArray(key)));
                return true;
            }
            case "calibre_library": {
                // Full object as written by archive version 2..3
                book.setCalibreLibrary(calibreLibraryCoder.decode(data.getJSONObject(key)));
                return true;
            }
            case "genre": {
                // Archive v7 and older used a single string for the genre
                final String genre = data.getString(key);
                book.getTags().addAll(LegacyUpgrades.migrateGenre(genre));
                tagMapper.map(context, book);
                return true;
            }
            default: {
                // Archive v7 and older used individual Identifier keys
                final String identifierKey = LegacyUpgrades.IDENTIFIERS.get(key);
                if (identifierKey != null) {
                    final String sid = data.optString(key, null);
                    book.setIdentifierValue(identifierKey, sid);
                    return true;
                }
            }
        }
        return false;
    }
}
