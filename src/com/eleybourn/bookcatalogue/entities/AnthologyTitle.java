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

import com.eleybourn.bookcatalogue.backup.CsvExporter;
import com.eleybourn.bookcatalogue.backup.CsvImporter;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Class to represent a single title within an anthology
 *
 * The table has a limitation right now... an AnthologyTitle can only exist in ONE book
 * -> TODO split "anthology" table into "anthology" table without book id, and a new table "anthology_book" linking
 *
 * @author pjw
 */
public class AnthologyTitle implements Serializable ,Utils.ItemWithIdFixup {
    private static final long serialVersionUID = -8715364898312204329L;

    public long id;
    private long mBookId;
    private Author mAuthor;
    private String mTitle;

    /**
     * Constructor that will attempt to parse a single string into an AnthologyTitle name.
     */
    public AnthologyTitle(String name) {
        id = 0;
        mBookId = 0;
        fromString(name);
    }

    /**
     * Constructor
     *
     * @param author Author of title
     * @param title  Title
     */
    @SuppressWarnings("WeakerAccess")
    public AnthologyTitle(Long bookId, @NonNull final Author author, @NonNull final String title) {
        this(0L,bookId, author, title);
    }

    /**
     * Constructor
     *
     * @param author Author of title
     * @param title  Title
     */
    @SuppressWarnings("WeakerAccess")
    public AnthologyTitle(Long id, Long bookId, @NonNull final Author author, @NonNull final String title) {
        this.id = id;
        this.mBookId = bookId;
        mAuthor = author;
        mTitle = title.trim();
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

    public void setAuthor( @NonNull final Author author) {
        mAuthor = author;
    }

    public long getBookId() {
        return mBookId;
    }

    public void setBookId(final long bookId) {
        this.mBookId = bookId;
    }

    /**
     *  Support for encoding to a text file
     *
     *  TODO: the * was taken from how {@link CsvExporter} and {@link CsvImporter} do the Anthology titles
     *  -> make the * a constant
     */
    @Override
    @NonNull
    public String toString() {
        return ArrayUtils.encodeListItem(',', mTitle) + " * " + mAuthor;
    }

    private void fromString(@NonNull final String s) {
        ArrayList<String> tit_aut = ArrayUtils.decodeList('*', s);
        mTitle = tit_aut.get(0);
        mAuthor = new Author(tit_aut.get(1));
    }

    /**
     * Replace local details from another author
     *
     * @param source AnthologyTitle to copy
     */
    @SuppressWarnings("unused")
    void copyFrom(@NonNull final AnthologyTitle source) {
        mAuthor = source.getAuthor();
        mTitle = source.getTitle();
        id = source.id;
    }

    @Override
    public long fixupId(@NonNull final CatalogueDBAdapter db) {
        this.id = db.getAnthologyTitleId(this);
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

}
