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
import android.content.Intent;
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

/**
 * Activity to display the available QueueManager Task object subclasses to the user.
 */
public class TaskQueueListActivity
        extends BindableItemListActivity {

    /**
     * Pass binding off to the task object.
     */
    @Override
    public void bindView(@NonNull final Context context,
                         @NonNull final BindableItemCursor cursor,
                         @NonNull final View convertView,
                         @NonNull final BindableItemCursorAdapter.BindableItem item) {
        convertView.setTag(R.id.TAG_GR_TASK, item);
        item.bindView(convertView, context, cursor, mDb);
    }

    /**
     * Get a cursor returning the tasks we are interested in (in this case all tasks).
     * <p>
     * Reads from {@link TaskQueueDAO}
     */
    @NonNull
    @Override
    protected BindableItemCursor getBindableItemCursor() {
        return QueueManager.getQueueManager().getTasks();
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.gr_tq_menu_background_tasks);

        //When any task is added/changed/deleted, update the list. Lazy, yes.
        QueueManager.getQueueManager().registerTaskListener(mChangeListener);

        Button cleanupBtn = findViewById(R.id.cleanup);
        cleanupBtn.setText(R.string.gr_tq_btn_cleanup_old_tasks);
        cleanupBtn.setOnClickListener(v -> QueueManager.getQueueManager().cleanupOldTasks());

        if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_background_tasks, null);
        }
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        QueueManager.getQueueManager().unregisterTaskListener(mChangeListener);
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
        List<ContextDialogItem> items = new ArrayList<>();
        items.add(new ContextDialogItem(
                getString(R.string.gr_tq_show_events_ellipsis),
                () -> {
                    Intent intent = new Intent(TaskQueueListActivity.this,
                                               EventQueueListActivity.class)
                                            .putExtra(EventQueueListActivity.REQ_BKEY_TASK_ID, id);
                    startActivity(intent);
                }));

        Task task = (Task) v.getTag(R.id.TAG_GR_TASK);
        task.addContextMenuItems(this, v, id, items, mDb);
        ContextDialogItem.showContextDialog(this, items);
    }
}
