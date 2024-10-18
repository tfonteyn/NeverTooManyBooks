/*
 * @Copyright 2018-2024 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;


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
     * @param args Bundle with arguments
     */
    void init(@NonNull final Bundle args) {
        if (style == null) {
            // Lookup the provided style or use the default if not found.
            final String styleUuid = args.getString(Style.BKEY_UUID);
            final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
            style = stylesHelper.getStyle(styleUuid).orElseGet(stylesHelper::getDefault);
        }
    }

    @NonNull
    Style getStyle() {
        return style;
    }
}
