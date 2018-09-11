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
package com.eleybourn.bookcatalogue;

import android.support.annotation.NonNull;

import java.io.Serializable;

/**
 * Class to represent a single title within an anthology
 *
 * @author pjw
 */
public class AnthologyTitle implements Serializable {
    private static final long serialVersionUID = -8715364898312204329L;
    private Author mAuthor;
    private String mTitle;

    /**
     * Constructor
     *
     * @param author Author of title
     * @param title  Title
     */
    @SuppressWarnings("WeakerAccess")
    public AnthologyTitle(Author author, @NonNull final String title) {
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

    public Author getAuthor() {
        return mAuthor;
    }

    public void setAuthor(Author author) {
        mAuthor = author;
    }

}
