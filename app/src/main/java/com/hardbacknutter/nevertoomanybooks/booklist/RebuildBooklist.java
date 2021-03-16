/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * id values for state preservation property.
 * See {@link Mode}.
 */
public final class RebuildBooklist {

    public static final int FROM_SAVED_STATE = 0;
    public static final int EXPANDED = 1;
    static final int COLLAPSED = 2;
    static final int FROM_PREFERRED_STATE = 3;

    private RebuildBooklist() {
    }

    /**
     * Get the current preferred rebuild mode for the list.
     *
     * @return Mode
     */
    @Mode
    public static int getPreferredMode() {
        final String value = ServiceLocator.getGlobalPreferences()
                                           .getString(Prefs.pk_booklist_rebuild_state, null);
        if (value != null && !value.isEmpty()) {
            return Integer.parseInt(value);
        }
        return FROM_SAVED_STATE;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({FROM_SAVED_STATE,
             EXPANDED,
             COLLAPSED,
             FROM_PREFERRED_STATE})
    public @interface Mode {

    }
}
