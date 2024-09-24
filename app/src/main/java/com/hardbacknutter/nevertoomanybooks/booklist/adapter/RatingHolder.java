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

import android.view.View;
import android.widget.RatingBar;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;

public class RatingHolder
        extends RowViewHolder
        implements BindableViewHolder<DataHolder> {

    /**
     * Key of the related data column.
     * It's ok to store this as it's intrinsically linked with the ViewType.
     */
    @NonNull
    private final String key;
    @NonNull
    private final RatingBar ratingBar;

    /**
     * Constructor.
     *
     * @param itemView the view specific for this holder
     * @param style    to use
     */
    RatingHolder(@NonNull final View itemView,
                 final Style style) {
        super(itemView);
        key = style.requireGroupById(BooklistGroup.RATING)
                   .getDisplayDomainExpression()
                   .getDomain()
                   .getName();
        ratingBar = itemView.findViewById(R.id.rating);
    }

    @Override
    public void onBind(@NonNull final DataHolder rowData) {
        ratingBar.setRating(rowData.getInt(key));
    }
}
