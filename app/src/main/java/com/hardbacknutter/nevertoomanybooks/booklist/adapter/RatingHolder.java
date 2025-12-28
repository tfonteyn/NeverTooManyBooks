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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;

public class RatingHolder
        extends RowViewHolder
        implements BindableViewHolder<DataHolder> {

    /**
     * Key of the related data column.
     * It's ok to store this as it's intrinsically linked with the ViewType.
     * And yes, we could just hardcode it in this class, but it's
     * easier this way to protect ourselves against changes.
     */
    @NonNull
    private final String key;
    @NonNull
    private final RatingBar ratingBar;
    @NonNull
    private final TextView bookCountView;
    @NonNull
    private final RealNumberParser realNumberParser;
    private final boolean showGroupBookCount;

    /**
     * Constructor.
     *
     * @param itemView         the view specific for this holder
     * @param style            to use
     * @param realNumberParser the shared parser
     */
    RatingHolder(@NonNull final View itemView,
                 @NonNull final Style style,
                 @NonNull final RealNumberParser realNumberParser) {
        super(itemView);
        this.realNumberParser = realNumberParser;

        key = style.requireGroupById(BooklistGroup.RATING)
                   .getDisplayDomainExpression()
                   .getDomain()
                   .getName();
        showGroupBookCount = style.isShowGroupBookCount();

        ratingBar = itemView.findViewById(R.id.rating);
        bookCountView = itemView.findViewById(R.id.level_book_count);
        bookCountView.setVisibility(showGroupBookCount ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBind(@NonNull final DataHolder rowData) {
        ratingBar.setRating(rowData.getFloat(key, realNumberParser));
        if (showGroupBookCount) {
            bookCountView.setText(String.valueOf(rowData.getLong(DBKey.FK_BOOK)));
        }
    }
}
