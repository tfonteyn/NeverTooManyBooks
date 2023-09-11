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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * ViewHolder for an Author.
 */
public class AuthorHolder
        extends GenericStringHolder {

    @NonNull
    private final ImageView completeView;

    /**
     * Constructor.
     *
     * @param itemView  the view specific for this holder
     * @param style     to use
     * @param level     the level in the Booklist tree
     * @param formatter to use
     */
    AuthorHolder(@NonNull final View itemView,
                 @NonNull final Style style,
                 @IntRange(from = 1) final int level,
                 @NonNull final FormatFunction formatter) {
        super(itemView, style, BooklistGroup.AUTHOR, level, formatter);
        completeView = itemView.findViewById(R.id.cbx_is_complete);
    }

    @Override
    public void onBind(@NonNull final DataHolder rowData) {
        super.onBind(rowData);

        completeView.setVisibility(rowData.getBoolean(DBKey.AUTHOR_IS_COMPLETE)
                                   ? View.VISIBLE : View.GONE);
    }
}
