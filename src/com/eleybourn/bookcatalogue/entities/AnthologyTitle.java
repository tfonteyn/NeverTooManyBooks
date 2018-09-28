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
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Class to represent a single title within an anthology
 *
 * Note:
 * these are always insert/update'd ONLY when a book is insert/update'd
 * Hence writes are always a List<AnthologyTitle> in one go. This circumvents the 'position' column
 * as the update will simply insert in-order and auto increment position.
 *
 * The table has some limitations right now
 * 1. can only exist in ONE book
 * 2. can only have one author
 *
 * @author pjw
 */
public class AnthologyTitle implements Serializable, Utils.ItemWithIdFixup {
    /**
     * import/export etc...
     *
     * "anthology title * author "
     */
    public static final char TITLE_AUTHOR_DELIM = '*';
    //TODO: see if this matters, I don't think we serialise this to external/blob
    //private static final long serialVersionUID = -8715364898312204329L;
    private static final long serialVersionUID = 2L;
    private long id = 0;
    private Author mAuthor;
    private String mTitle;
    private String mPublicationDate;

    private long mBookId = 0;
    private long mPosition = 0;     // order in the book, [1..x]

    /**
     * Constructor that will attempt to parse a single string into an AnthologyTitle name.
     */
    public AnthologyTitle(@NonNull final String encodedString) {
        authorFromName(encodedString);
    }

    /**
     * Constructor
     *
     * @param author Author of title
     * @param title  Title
     */
    public AnthologyTitle(@NonNull final Author author,
                          @NonNull final String title,
                          @Nullable final String publicationDate,

                          final long bookId) {
        mAuthor = author;
        mTitle = title.trim();
        mPublicationDate = publicationDate;

        mBookId = bookId;
    }

    private void authorFromName(@NonNull final String encodedString) {
        List<String> data = ArrayUtils.decodeList(TITLE_AUTHOR_DELIM, encodedString);
        mTitle = data.get(0);
        mAuthor = new Author(data.get(1));
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

    public String getPublicationDate() {
        return mPublicationDate;
    }

    /**
     * Support for encoding to a text file
     * TODO: V83 import/export
     */
    @Override
    @NonNull
    public String toString() {
        return ArrayUtils.encodeListItem(',', mTitle) + " " + TITLE_AUTHOR_DELIM + " " + mAuthor;
    }

    @Override
    public long fixupId(@NonNull final CatalogueDBAdapter db) {
        this.mAuthor.id = db.getAuthorIdByName(mAuthor.familyName, mAuthor.givenNames);
        this.id = db.getAnthologyTitleId(mAuthor.id, mTitle);
        return this.id;
    }

    /**
     * Each AnthologyTitle is defined exactly by a unique ID.
     */
    @Override
    public boolean isUniqueById() {
        return true;
    }

    /**
     * Two are the same if:
     * - one or both are 'new' (id==0)  and author + title is the same
     * - their id's are the same
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnthologyTitle that = (AnthologyTitle) o;
        if (id == 0 || that.id == 0) {
            return Objects.equals(mAuthor, that.mAuthor) && Objects.equals(mTitle, that.mTitle);
        }
        return (id == that.id);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id, mAuthor, mTitle);
    }
}
