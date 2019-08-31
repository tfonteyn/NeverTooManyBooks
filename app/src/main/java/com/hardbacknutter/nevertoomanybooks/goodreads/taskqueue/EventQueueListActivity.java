/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager.TipOwner;

/**
 * Activity to display all Events in the QueueManager.
 */
public class EventQueueListActivity
        extends BindableItemListActivity {

    /** Key to store optional task id when activity is started. */
    public static final String REQ_BKEY_TASK_ID = "EventQueueListActivity.TaskId";

    /** Task ID, if provided in intent. */
    private long mTaskId;

    private void showContextMenu(@NonNull final AdapterView<?> parent,
                                 @NonNull final View v,
                                 @NonNull final Event event,
                                 final int position,
                                 final long id) {
        List<ContextDialogItem> items = new ArrayList<>();
        event.addContextMenuItems(this, v, id, items, mDb);
        ContextDialogItem.showContextDialog(this, items);
    }

    @Override
    public void bindView(@NonNull final Context context,
                         @NonNull final BindableItemCursor cursor,
                         @NonNull final View convertView,
                         @NonNull final BindableItemCursorAdapter.BindableItem item) {
        convertView.setTag(R.id.TAG_GR_EVENT, item);
        item.bindView(convertView, context, cursor, mDb);
    }

    /**
     * Get the EventsCursor relevant to this Activity.
     */
    @NonNull
    @Override
    protected BindableItemCursor getBindableItemCursor() {
        if (mTaskId == 0) {
            return QueueManager.getQueueManager().getAllEvents();
        } else {
            return QueueManager.getQueueManager().getTaskEvents(mTaskId);
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {

        mTaskId = getIntent().getLongExtra(REQ_BKEY_TASK_ID, 0);
        // Once we have the task id, call the parent
        super.onCreate(savedInstanceState);

        setTitle(R.string.gr_tq_title_task_errors);

        QueueManager.getQueueManager().registerEventListener(mChangeListener);

        Button cleanupBtn = findViewById(R.id.cleanup);
        cleanupBtn.setText(R.string.gr_tq_btn_cleanup_old_events);
        cleanupBtn.setOnClickListener(v -> QueueManager.getQueueManager().cleanupOldEvents());

        if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_background_task_events, null);
        }
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        QueueManager.getQueueManager().unregisterEventListener(mChangeListener);
        super.onDestroy();
    }

    /**
     * Build a context menu dialogue when an item is clicked.
     */
    @Override
    public void onListItemClick(@NonNull final AdapterView<?> parent,
                                @NonNull final View v,
                                final int position,
                                final long id) {

        final Event event = (Event) v.getTag(R.id.TAG_GR_EVENT);

        // If it owns a hint, display it first
        if (event instanceof TipOwner) {
            TipManager.display(this, ((TipOwner) event).getTip(),
                               () -> showContextMenu(parent, v, event, position, id));
        } else {
            showContextMenu(parent, v, event, position, id);
        }
    }
}
