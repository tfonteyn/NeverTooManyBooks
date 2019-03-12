/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.goodreads.taskqueue;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;

import java.util.List;

/**
 * Class to wrap events that can not be de-serialized so that the EventsCursor *always*
 * returns a valid Event.
 *
 * @author Philip Warner
 */
public class LegacyEvent
        extends Event {

    private static final int TEXT_FIELD_1 = 1;
    private static final int TEXT_FIELD_2 = 2;
    private static final long serialVersionUID = 8961033939258102422L;

    LegacyEvent() {
        super("Legacy Event");
    }

    @NonNull
    @Override
    public View getView(@NonNull final Context context,
                        @NonNull final BindableItemCursor cursor,
                        @NonNull final ViewGroup parent) {
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
                         @NonNull final DBA db) {
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
                                    @NonNull final DBA db) {

        items.add(new ContextDialogItem(context.getString(R.string.gr_tq_menu_delete_event),
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                QueueManager.getQueueManager()
                                                            .deleteEvent(getId());
                                            }
                                        }));

    }
}
