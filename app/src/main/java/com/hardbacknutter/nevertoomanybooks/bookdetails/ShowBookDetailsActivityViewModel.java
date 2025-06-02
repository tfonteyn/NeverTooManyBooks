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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;


/**
 * Shared data between pager, details and TOC fragments.
 * <p>
 * REMINDER: this is used by the ViewPager and MUST NOT contain Book information.
 * The ViewPager handles multiple child fragments, each of which represents a Book.
 */
@SuppressWarnings("WeakerAccess")
public class ShowBookDetailsActivityViewModel
        extends ViewModel {

    private boolean modified;

    private Bookshelf bookshelf;
    private Style style;

    /**
     * Part of the fragment result data.
     * This informs the BoB whether it should rebuild its list.
     *
     * @return {@code true} if the book was changed and successfully saved.
     */
    boolean isModified() {
        return modified;
    }

    /**
     * Unconditionally set the modification flag to {@code true}.
     */
    void setDataModified() {
        modified = true;
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link DBKey#FK_BOOKSHELF} the parceled Bookshelf
     */
    void init(@NonNull final Bundle args) {
        if (style == null) {
            bookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                               DBKey.FK_BOOKSHELF);
            style = bookshelf.getStyle();
        }
    }

    @NonNull
    Bookshelf getBookshelf() {
        return bookshelf;
    }

    @NonNull
    Style getStyle() {
        return style;
    }
}
