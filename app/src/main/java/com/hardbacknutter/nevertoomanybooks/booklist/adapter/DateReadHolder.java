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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.content.Context;
import android.view.View;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * This holder fixes an issue that when we have one ore more of the groups
 * DATE_READ_YEAR/DATE_READ_MONTH/DATE_READ_DAY
 * there will be a duplicate "(Year not set)" (and similar for the others).
 * Reproduce this by creating a style with DATE_READ_YEAR, DATE_READ_MONTH, AUTHOR
 * <p>
 * This is caused by those domains have .addGroupDomain(BD_BOOK_IS_READ);
 * In theory this is CORRECT !
 * <p>
 * The first "(Year not set)" contains books which have been 'read'
 * but for which the date-read is NOT set.
 * The second "(Year not set)" contains books which have NOT been 'read'
 * But this is confusing to the user (and myself...)
 * <p>
 * Solution 1: remove the BD_BOOK_IS_READ from those 3 group definitions now we
 * have a single "(Year not set)"
 * Problem: that group now has both read/not-read books intermixed.
 * <p>
 * Solution 2: keep those BD_BOOK_IS_READ in the group definition,
 * but explicitly prevent BD_BOOK_IS_READ from being added to the whereClause
 * ... NOT a solution: this will remove the "(Year not set)" and "(Month not set)"
 * ... but the Author heading will now be duplicated.
 * This is worse than solution 1
 * <p>
 * Solution 3: don't change anything, but create a RowViewHolder for
 * those groups and change the label to include the 'Unread' status
 */
public class DateReadHolder
        extends GenericStringHolder {
    /**
     * Constructor.
     *
     * @param itemView  the view specific for this holder
     * @param style     to use
     * @param groupId   the group this holder represents
     * @param level     the level in the Booklist tree
     * @param formatter to use
     */
    DateReadHolder(@NonNull final View itemView,
                   @NonNull final Style style,
                   @BooklistGroup.Id final int groupId,
                   @IntRange(from = 1) final int level,
                   @NonNull final FormatFunction formatter) {
        super(itemView, style, groupId, level, formatter);
    }

    @Override
    public void onBind(@NonNull final DataHolder rowData) {
        CharSequence text = formatter.format(groupId, rowData, key);

        // Check presence first, and only then test on 'false'
        if (rowData.contains(DBKey.READ__BOOL)) {
            if (!rowData.getBoolean(DBKey.READ__BOOL)) {
                final Context context = textView.getContext();
                text = context.getString(R.string.a_space_b, text,
                                         context.getString(R.string.lbl_unread));
            }
        }

        textView.setText(text);

        if (BuildConfig.DEBUG) {
            dbgPosition(rowData);
        }
    }
}
