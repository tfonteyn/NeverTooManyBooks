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

package com.eleybourn.bookcatalogue.tasks.taskqueue;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.adapters.BindableItemCursorAdapter;
import com.eleybourn.bookcatalogue.baseactivity.BindableItemListActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BindableItemCursor;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Listeners.OnTaskChangeListener;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Listeners.TaskActions;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity to display the available QueueManager Task object subclasses to the user.
 *
 * @author Philip Warner
 */
public class TaskQueueListActivity extends BindableItemListActivity {
    /**
     * Listener to handle Event add/change/delete.
     */
    private final OnTaskChangeListener mOnTaskChangeListener = new OnTaskChangeListener() {
        @Override
        public void onTaskChange(final @Nullable Task task, final @NonNull TaskActions action) {
            TaskQueueListActivity.this.refreshData();
        }
    };

    private CatalogueDBAdapter mDb = null;
    private TasksCursor mCursor;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_message_queue_list;
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(R.string.menu_background_tasks);

        mDb = new CatalogueDBAdapter(this);

        //When any Event is added/changed/deleted, update the list. Lazy, yes.
        QueueManager.getQueueManager().registerTaskListener(mOnTaskChangeListener);

        Button cleanupBtn = findViewById(R.id.cleanup);
        cleanupBtn.setText(R.string.btn_cleanup_old_tasks);
        cleanupBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                QueueManager.getQueueManager().cleanupOldTasks();
            }
        });

        if (savedInstanceState == null) {
            HintManager.displayHint(this.getLayoutInflater(), R.string.hint_background_tasks, null);
        }
        Tracker.exitOnCreate(this);
    }

    /**
     * Refresh data; some other activity may have changed relevant data (eg. a book)
     */
    @Override
    @CallSuper
    protected void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        refreshData();
        Tracker.exitOnResume(this);
    }

    /**
     * Build a context menu dialogue when an item is clicked.
     */
    @Override
    public void onListItemClick(@NonNull AdapterView<?> parent, final @NonNull View v, final int position, final long id) {
        Task task = ViewTagger.getTagOrThrow(v, R.id.TAG_TASK);
        List<ContextDialogItem> items = new ArrayList<>();

        items.add(new ContextDialogItem(getString(R.string.show_events_ellipsis), new Runnable() {
            @Override
            public void run() {
                doShowTaskEvents(id);
            }
        }));

        task.addContextMenuItems(this, parent, v, position, id, items, mDb);

        if (items.size() > 0) {
            showContextDialogue(R.string.title_select_an_action, items);
        }
    }

    private void doShowTaskEvents(final long taskId) {
        Intent intent = new Intent(this, EventQueueListActivity.class);
        intent.putExtra(EventQueueListActivity.REQUEST_BKEY_TASK_ID, taskId);
        startActivity(intent);
    }

    /**
     * Return the number of task types we might return. 50 is just paranoia.
     * RELEASE: Keep checking this value!
     */
    @Override
    public int getBindableItemTypeCount() {
        return 50;
    }

    /**
     * Pass binding off to the task object.
     */
    @Override
    public void bindViewToItem(final @NonNull Context context,
                               final @NonNull View view,
                               final @NonNull BindableItemCursor cursor,
                               final @NonNull BindableItemCursorAdapter.BindableItem bindable) {
        ViewTagger.setTag(view, R.id.TAG_TASK, bindable);
        bindable.bindView(view, context, cursor, mDb);
    }

    /**
     * Get a cursor returning the tasks we are interested in (in this case all tasks)
     *
     * Reads from {@link TaskQueueDBAdapter}
     */
    @NonNull
    @Override
    protected BindableItemCursor getBindableItemCursor(final @Nullable Bundle savedInstanceState) {
        mCursor = QueueManager.getQueueManager().getTasks();
        return mCursor;
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);

        try {
            QueueManager.getQueueManager().unregisterTaskListener(mOnTaskChangeListener);
        } catch (Exception ignore) {
        }

        try {
            if (mCursor != null) {
                mCursor.close();
            }
        } catch (Exception ignore) {
        }

        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }
}
