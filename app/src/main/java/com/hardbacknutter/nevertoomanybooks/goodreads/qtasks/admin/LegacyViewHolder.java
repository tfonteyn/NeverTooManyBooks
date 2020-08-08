/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQItem;

public class LegacyViewHolder
        extends BaseViewHolder {

    private final TextView tv1;
    private final TextView tv2;

    LegacyViewHolder(@NonNull final View itemView) {
        super(itemView);

        tv1 = itemView.findViewById(R.id.LEGACY_VIEW_1);
        tv2 = itemView.findViewById(R.id.LEGACY_VIEW_2);
    }

    /**
     * View constructor. Hardcoded instead of using a layout.
     *
     * @param context Current context
     *
     * @return new View
     */
    static View createView(@NonNull final Context context) {
        final LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);

        final ViewGroup.LayoutParams margins = new LinearLayout.LayoutParams(
                ViewGroup.MarginLayoutParams.MATCH_PARENT,
                ViewGroup.MarginLayoutParams.WRAP_CONTENT);

        final TextView tv1 = new TextView(context);
        tv1.setId(R.id.LEGACY_VIEW_1);
        view.addView(tv1, margins);

        final TextView tv2 = new TextView(context);
        tv2.setId(R.id.LEGACY_VIEW_2);
        view.addView(tv2, margins);

        return view;
    }

    public void bind(@NonNull final TQItem item) {
        final Context context = itemView.getContext();
        tv1.setText(context.getString(R.string.legacy_record, item.getId()));
        tv2.setText(context.getString(R.string.legacy_description));
    }
}
