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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * Used by:
     * - ISFDB import of anthology titles
     * - export/import
     *
     *  find the publication year in a string like "some title (1960)"
     *
     *  pattern finds (1960), group 1 will then contain the pure 1960
     */
    public static final Pattern YEAR_FROM_STRING = Pattern.compile("\\(([1|2]\\d\\d\\d)\\)");

    private long id = 0;
    private Author mAuthor;
    private String mTitle;
    private String mFirstPublicationDate;

    private long mBookId;
    private long mPosition = 0;     // order in the book, [1..x]

    /**
     * Constructor that will attempt to parse a single string into an AnthologyTitle.
     */
    public AnthologyTitle(@NonNull final String fromString,  final long bookId) {
        fromString(fromString);
        mBookId = bookId;
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
        mFirstPublicationDate = publicationDate;

        mBookId = bookId;
    }

    /**
     * Support for decoding from a text file
     */
    private void fromString(@NonNull final String encodedString) {
        // V82: Giants In The Sky * Blish, James
        // V83: Giants In The Sky (1952) * Blish, James

        List<String> data = ArrayUtils.decodeList(TITLE_AUTHOR_DELIM, encodedString);
        mAuthor = new Author(data.get(1));
        String title = data.get(0);

        Matcher matcher = AnthologyTitle.YEAR_FROM_STRING.matcher(title);
        if (matcher.find()) {
            mFirstPublicationDate = matcher.group(1);
            mTitle = title.replace(matcher.group(0), "").trim();
        } else {
            mFirstPublicationDate = "";
            mTitle = title;
        }
    }
    /**
     * Support for encoding to a text file
     */
    @Override
    @NonNull
    public String toString() {
        String yearStr;
        if (mFirstPublicationDate != null && !mFirstPublicationDate.isEmpty()) {
            // start with a space !
            yearStr = " (" + mFirstPublicationDate + ")";
        } else {
            yearStr = "";
        }
        // V83: Giants In The Sky (1952) * Blish, James
        return ArrayUtils.encodeListItem(',', mTitle) + yearStr + " " + TITLE_AUTHOR_DELIM + " " + mAuthor;
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

    public String getFirstPublication() {
        return mFirstPublicationDate;
    }

    public void setFirstPublication(final String publicationDate) {
        mFirstPublicationDate = publicationDate;
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
