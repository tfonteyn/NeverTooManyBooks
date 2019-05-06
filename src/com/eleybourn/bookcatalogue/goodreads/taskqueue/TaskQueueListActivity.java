/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.goodreads.taskqueue;

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

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.HintManager;

/**
 * Activity to display the available QueueManager Task object subclasses to the user.
 *
 * @author Philip Warner
 */
public class TaskQueueListActivity
        extends BindableItemListActivity {

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.gr_tq_menu_background_tasks);

        //When any task is added/changed/deleted, update the list. Lazy, yes.
        QueueManager.getQueueManager().registerTaskListener(mOnChangeListener);

        Button cleanupBtn = findViewById(R.id.cleanup);
        cleanupBtn.setText(R.string.gr_tq_btn_cleanup_old_tasks);
        cleanupBtn.setOnClickListener(v -> QueueManager.getQueueManager().cleanupOldTasks());

        if (savedInstanceState == null) {
            HintManager.displayHint(getLayoutInflater(), R.string.hint_background_tasks, null);
        }
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
        task.addContextMenuItems(this, parent, v, position, id, items, mDb);
        ContextDialogItem.showContextDialog(this, items);
    }

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
     * Reads from {@link TaskQueueDBAdapter}
     */
    @NonNull
    @Override
    protected BindableItemCursor getBindableItemCursor() {
        return QueueManager.getQueueManager().getTasks();
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        QueueManager.getQueueManager().unregisterTaskListener(mOnChangeListener);
        super.onDestroy();
    }
}
