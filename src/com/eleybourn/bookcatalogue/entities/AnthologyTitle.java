/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.entities;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Class to represent a single title within an anthology
 *
 * Note:
 *   these are always insert/update'd ONLY when a book is insert/update'd
 *   Hence writes are always a List<AnthologyTitle> in one go. This circumvents the 'position' column
 *   as the update will simply insert in-order and auto increment position.
 *
 * The table has some limitations right now
 * 1. can only exist in ONE book
 * -> TODO split "anthology" table into "anthology" table without bookid/position, and "anthology_book_weak"
 * 2. can only have one author
 * -> TODO? might be overkill
 *
 * "anthology_book_weak" must then have:
 * - id
 * - book_id
 * - anthology_id
 * - anthology_position_in_book
 *
 * @author pjw
 */
public class AnthologyTitle implements Serializable, Utils.ItemWithIdFixup {
    //TODO: see if this matters, I don't think we serialise this to external/blob
    //private static final long serialVersionUID = -8715364898312204329L;
    private static final long serialVersionUID = 2L;

    /**
     * import/export etc...
     *
     * "anthology title * author "
     */
    public static final char TITLE_AUTHOR_DELIM = '*';

    public long id;
    private Author mAuthor;
    private String mTitle;

    private long mBookId;
    private long mPosition;     // order in the book, [1..x]

    /**
     * Constructor that will attempt to parse a single string into an AnthologyTitle name.
     */
    public AnthologyTitle(@NonNull final String name) {
        id = 0;
        mBookId = 0;
        mPosition = 0;
        authorFromName(name);
    }

    private void authorFromName(String name) {
        ArrayList<String> data = ArrayUtils.decodeList(TITLE_AUTHOR_DELIM, name);
        mTitle = data.get(0);
        mAuthor = new Author(data.get(1));
    }

    /**
     * Constructor
     *
     * @param author Author of title
     * @param title  Title
     */
    public AnthologyTitle(final long bookId, @NonNull final Author author, @NonNull final String title) {
        this(0, bookId, author, title, 0);
    }

    /**
     * Constructor
     *
     * @param author Author of title
     * @param title  Title
     */
    @SuppressWarnings("WeakerAccess")
    public AnthologyTitle(final long id, final long bookId, @NonNull final Author author, @NonNull final String title, final int position) {
        this.id = id;
        mBookId = bookId;
        mAuthor = author;
        mTitle = title.trim();
        mPosition = position;
    }

    @NonNull
    public String getTitle() {
        return mTitle;
    }

    public void setTitle(@NonNull final String title) {
        mTitle = title;
    }

    @NonNull
    public Author getAuthor() {
        return mAuthor;
    }

    public void setAuthor(@NonNull final Author author) {
        mAuthor = author;
    }

    public long getBookId() {
        return mBookId;
    }

    public void setBookId(final long mBookId) {
        this.mBookId = mBookId;
    }

    public long getPosition() {
        return mPosition;
    }

    public void setPosition(final long mPosition) {
        this.mPosition = mPosition;
    }

    /**
     * Support for encoding to a text file
     */
    @Override
    @NonNull
    public String toString() {
        return ArrayUtils.encodeListItem(',', mTitle) + " " + TITLE_AUTHOR_DELIM + " " + mAuthor;
    }

    @Override
    public long fixupId(@NonNull final CatalogueDBAdapter db) {
        this.id = db.getAnthologyTitleId(mBookId, mAuthor.getId(), mTitle);
        return this.id;
    }

    @Override
    public long getId() {
        return id;
    }

    /**
     * Each AnthologyTitle is defined exactly by a unique ID.
     */
    @Override
    public boolean isUniqueById() {
        return true;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnthologyTitle that = (AnthologyTitle) o;
        if ( id == 0 || that.id == 0) {
            return Objects.equals(mAuthor, that.mAuthor) && Objects.equals(mTitle, that.mTitle);
        }
        return (id == that.id);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id, mAuthor, mTitle);
    }
}
