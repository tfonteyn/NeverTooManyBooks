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
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.eleybourn.bookcatalogue.dialogs.HintManager.HintOwner;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Listeners.EventActions;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Listeners.OnEventChangeListener;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity to display all Events in the QueueManager.
 *
 * @author Philip Warner
 */
public class EventQueueListActivity extends BindableItemListActivity {

    /** Key to store optional task ID when activity is started */
    public static final String REQUEST_BKEY_TASK_ID = "EventQueueListActivity.TaskId";

    /** DB connection */
    private CatalogueDBAdapter mDb = null;
    private BindableItemCursor mCursor;

    /**
     * Listener to handle Event add/change/delete.
     */
    private final OnEventChangeListener mOnEventChangeListener = new OnEventChangeListener() {
        @Override
        public void onEventChange(@Nullable Event event, @NonNull EventActions action) {
            //When any Event is added/changed/deleted, update the list. Lazy, yes.
            EventQueueListActivity.this.refreshData();
        }
    };

    /** Task ID, if provided in intent */
    private long mTaskId = 0;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_message_queue_list;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        mDb = new CatalogueDBAdapter(this);

        Intent intent = getIntent();
        if (intent != null) {
            mTaskId = intent.getLongExtra(REQUEST_BKEY_TASK_ID, 0);
        }
        // Once the basic criteria have been setup, call the parent
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_task_errors);

        QueueManager.getQueueManager().registerEventListener(mOnEventChangeListener);

        Button cleanupBtn = findViewById(R.id.cleanup);
        cleanupBtn.setText(R.string.btn_cleanup_old_events);
        cleanupBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                QueueManager.getQueueManager().cleanupOldEvents();
            }
        });

        if (savedInstanceState == null) {
            HintManager.displayHint(this.getLayoutInflater(), R.string.hint_background_task_events, null);
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
    public void onListItemClick(final @NonNull AdapterView<?> parent,
                                final @NonNull View v,
                                final int position, final long id) {
        // get the event object
        final Event event = ViewTagger.getTag(v, R.id.TAG_EVENT);

        // If it owns a hint, display it
        if (event instanceof HintOwner) {
            // Show the hint if necessary; fall through to the runnable
            HintManager.displayHint(this.getLayoutInflater(), ((HintOwner) event).getHint(), new Runnable() {
                @Override
                public void run() {
                    doContextMenu(parent, v, position, id);
                }
            });
        } else {
            // Just display context menu
            doContextMenu(parent, v, position, id);
        }
    }

    private void doContextMenu(final @NonNull AdapterView<?> parent,
                               final @NonNull View v,
                               final int position, final long id) {
        final Event event = ViewTagger.getTagOrThrow(v, R.id.TAG_EVENT);
        final List<ContextDialogItem> items = new ArrayList<>();

        event.addContextMenuItems(this, parent, v, position, id, items, mDb);

        if (items.size() > 0) {
            showContextDialogue(R.string.title_select_an_action, items);
        }
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);

        try {
            if (mCursor != null) {
                mCursor.close();
            }
        } catch (Exception ignore) {
        }

        try {
            QueueManager.getQueueManager().unregisterEventListener(mOnEventChangeListener);
        } catch (Exception ignore) {
        }

        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }

    /**
     * Paranoid overestimate of the number of event types we use.
     */
    @Override
    public int getBindableItemTypeCount() {
        return 50;
    }

    /**
     * Let the Event bind itself.
     */
    @Override
    public void bindViewToItem(final @NonNull Context context,
                               final @NonNull View view,
                               final @NonNull BindableItemCursor cursor,
                               final @NonNull BindableItemCursorAdapter.BindableItem item) {
        ViewTagger.setTag(view, R.id.TAG_EVENT, item);
        item.bindView(view, context, cursor, mDb);
    }

    /**
     * Get the EventsCursor relevant to this Activity
     */
    @NonNull
    @Override
    protected BindableItemCursor getBindableItemCursor(final @Nullable Bundle savedInstanceState) {
        if (mTaskId == 0) {
            mCursor = QueueManager.getQueueManager().getAllEvents();
        } else {
            mCursor = QueueManager.getQueueManager().getTaskEvents(mTaskId);
        }

        return mCursor;
    }
}
