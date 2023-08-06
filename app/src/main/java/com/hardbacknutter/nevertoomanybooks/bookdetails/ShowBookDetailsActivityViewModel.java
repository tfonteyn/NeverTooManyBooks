/*
 * @Copyright 2018-2023 HardBackNutter
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

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.searchengines.MenuHandlerFactory;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;


public class ShowBookDetailsActivityViewModel
        extends ViewModel {

    private List<MenuHandler> menuHandlers;

    private boolean modified;

    private Style style;

    /**
     * Part of the fragment result data.
     * This informs the BoB whether it should rebuild its list.
     *
     * @return {@code true} if the book was changed and successfully saved.
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * Unconditionally set the modification flag to {@code true}.
     */
    void setDataModified() {
        modified = true;
    }

    /**
     * Set the modification flag to {@code true} <strong>if the given data was modified</strong>.
     * Does NOT change the flag if the data was not modified.
     *
     * @param data to check for being modified
     */
    void setDataModified(@NonNull final EditBookOutput data) {
        if (data.isModified()) {
            modified = true;
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param args Bundle with arguments
     */
    public void init(@NonNull final Bundle args) {
        if (style == null) {
            // the style can be 'null' here. If so, the default one will be used.
            final String styleUuid = args.getString(Style.BKEY_UUID);
            style = ServiceLocator.getInstance().getStyles().getStyleOrDefault(styleUuid);

            menuHandlers = MenuHandlerFactory.create();
        }
    }

    @NonNull
    List<MenuHandler> getMenuHandlers() {
        return menuHandlers;
    }

    @NonNull
    public Style getStyle() {
        return style;
    }
}
