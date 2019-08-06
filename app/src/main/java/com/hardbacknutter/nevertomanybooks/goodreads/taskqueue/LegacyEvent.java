/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.goodreads.taskqueue;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.database.DAO;

/**
 * Class to wrap events that can not be de-serialized so that the EventsCursor *always*
 * returns a valid Event.
 */
public class LegacyEvent
        extends Event {

    private static final int TEXT_FIELD_1 = 1;
    private static final int TEXT_FIELD_2 = 2;
    private static final long serialVersionUID = 3505922919300986308L;

    LegacyEvent() {
        super("Legacy Event");
    }

    @NonNull
    @Override
    public View getView(@NonNull final LayoutInflater inflater,
                        @NonNull final BindableItemCursor cursor,
                        @NonNull final ViewGroup parent) {
        Context context = inflater.getContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        ViewGroup.LayoutParams margins = new LinearLayout.LayoutParams(
                ViewGroup.MarginLayoutParams.MATCH_PARENT,
                ViewGroup.MarginLayoutParams.WRAP_CONTENT);

        TextView tv = new TextView(context);
        tv.setId(TEXT_FIELD_1);
        root.addView(tv, margins);

        tv = new TextView(context);
        tv.setId(TEXT_FIELD_2);
        root.addView(tv, margins);

        return root;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void bindView(@NonNull final View view,
                         @NonNull final Context context,
                         @NonNull final BindableItemCursor cursor,
                         @NonNull final DAO db) {
        ((TextView) view.findViewById(TEXT_FIELD_1))
                .setText("Legacy Placeholder for Event #" + getId());
        ((TextView) view.findViewById(TEXT_FIELD_2))
                .setText("This event is obsolete and can not be recovered."
                         + " It is probably advisable to delete it.");
    }

    @Override
    public void addContextMenuItems(@NonNull final Context context,
                                    @NonNull final AdapterView<?> parent,
                                    @NonNull final View view,
                                    final int position,
                                    final long id,
                                    @NonNull final List<ContextDialogItem> items,
                                    @NonNull final DAO db) {

        items.add(new ContextDialogItem(context.getString(R.string.gr_tq_menu_delete_event),
                                        () -> QueueManager.getQueueManager()
                                                          .deleteEvent(getId())));

    }
}
