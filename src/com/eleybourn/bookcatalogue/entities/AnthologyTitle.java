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
import com.eleybourn.bookcatalogue.utils.RTE;
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

    /**
     * Used by:
     * - ISFDB import of anthology titles
     * - export/import
     *
     * find the publication year in a string like "some title (1960)"
     *
     * pattern finds (1960), group 1 will then contain the pure 1960
     */
    private static final Pattern YEAR_FROM_STRING = Pattern.compile("\\(([1|2]\\d\\d\\d)\\)");
    public static final char SEPARATOR = ',';

    /**
     * V82:
     * private static final long serialVersionUID = -8715364898312204329L;
     * V83 changed the format
     *
     * TODO: see if this matters, I don't think we serialize this to a binary stream (only to a String)
     */
    private static final long serialVersionUID = 2L;
    private long id = 0;
    private Author mAuthor;
    private String mTitle;
    @NonNull
    private String mFirstPublicationDate = "";

    @Deprecated
    private long mBookId = 0;
    @Deprecated
    private long mPosition = 0;     // order in the book, [1..x]

    /**
     * Constructor that will attempt to parse a single string into an AnthologyTitle.
     */
    public AnthologyTitle(@NonNull final String fromString) {
        fromString(fromString);
    }

    /**
     * Constructor
     *
     * @param author Author of title
     * @param title  Title
     */
    public AnthologyTitle(@NonNull final Author author,
                          @NonNull final String title,
                          @NonNull final String publicationDate) {
        mAuthor = author;
        mTitle = title.trim();
        mFirstPublicationDate = publicationDate;
    }

    /**
     * Helper to check if all titles in a list have the same author.
     */
    public static boolean isSingleAuthor(@NonNull final List<AnthologyTitle> results) {
        // check if its all the same author or not
        boolean sameAuthor = true;
        if (results.size() > 1) {
            Author author = results.get(0).getAuthor();
            for (AnthologyTitle t : results) { // yes, we check 0 twice.. oh well.
                sameAuthor = author.equals(t.getAuthor());
                if (!sameAuthor) {
                    break;
                }
            }
        }
        return sameAuthor;
    }

    /**
     * Support for decoding from a text file
     */
    private void fromString(@NonNull final String encodedString) {
        // V82: Giants In The Sky * Blish, James
        // V83: Giants In The Sky (1952) * Blish, James

        List<String> list = ArrayUtils.decodeList(TITLE_AUTHOR_DELIM, encodedString);
        mAuthor = new Author(list.get(1));
        String title = list.get(0);

        //TOMF FIXME: fine for now, but should be made foolproof for full dates (via DateUtils) instead of just the 4 digit year
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
     * Support for Serializable/encoding to a text file
     *
     * @return the object encoded as a String.
     *
     * If the year is known:
     * "Giants In The Sky (1952) * Blish, James"
     * else:
     * "Giants In The Sky * Blish, James"
     */
    @Override
    @NonNull
    public String toString() {
        String yearStr;
        if (!mFirstPublicationDate.isEmpty()) {
            // start with a space !
            yearStr = " (" + mFirstPublicationDate + ")";
        } else {
            yearStr = "";
        }
        return ArrayUtils.encodeListItem(SEPARATOR, mTitle) + yearStr + " " + TITLE_AUTHOR_DELIM + " " + mAuthor;
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

    @Deprecated
    public long getBookId() {
        return mBookId;
    }

    @Deprecated
    public void setBookId(final long mBookId) {
        this.mBookId = mBookId;
    }

    @Deprecated
    public long getPosition() {
        return mPosition;
    }

    @Deprecated
    public void setPosition(final long mPosition) {
        this.mPosition = mPosition;
    }

    @NonNull
    public String getFirstPublication() {
        return mFirstPublicationDate;
    }

    public void setFirstPublication(@NonNull final String publicationDate) {
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
    public boolean equals(@Nullable final Object o) {
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

    //ENHANCE use enum for ANTHOLOGY_BITMASK
    public enum Type {
        no, singleAuthor, multipleAuthors;

        public int getBitmask() {
            switch (this) {
                case no:
                    return 0x00;
                case singleAuthor:
                    return 0x01;
                case multipleAuthors:
                    return 0x11;
                default:
                    return 0x00;
            }
        }

        public Type get(final int bitmask) {
            switch (bitmask) {
                case 0x00:
                    return no;
                case 0x01:
                    return singleAuthor;
                case 0x11:
                    return multipleAuthors;
                default:
                    throw new RTE.IllegalTypeException("" + bitmask);
            }
        }
    }
}
