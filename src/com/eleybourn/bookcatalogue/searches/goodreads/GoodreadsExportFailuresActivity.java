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

package com.eleybourn.bookcatalogue.searches.goodreads;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BindableItemListActivity;
import com.eleybourn.bookcatalogue.taskqueue.BindableItemCursor;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.HintManager.HintOwner;
import com.eleybourn.bookcatalogue.taskqueue.Event;
import com.eleybourn.bookcatalogue.taskqueue.Listeners.EventActions;
import com.eleybourn.bookcatalogue.taskqueue.Listeners.OnEventChangeListener;
import com.eleybourn.bookcatalogue.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.utils.ViewTagger;
import com.eleybourn.bookcatalogue.widgets.BindableItemCursorAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity to display all Events in the QueueManager.
 *
 * TODO: Decide if this should be renamed, and consider adding event selection methods.
 *
 * @author Philip Warner
 */
public class GoodreadsExportFailuresActivity extends BindableItemListActivity {
    /** Key to store optional task ID hen activity is started */
    private static final String LOCAL_BKEY_TASK_ID = "GoodreadsExportFailuresActivity.TaskId";
    /** DB connection */
    private CatalogueDBAdapter mDb = null;
    private BindableItemCursor mCursor;
    /**
     * Listener to handle Event add/change/delete.
     */
    private final OnEventChangeListener m_OnEventChangeListener = new OnEventChangeListener() {
        @Override
        public void onEventChange(Event event, EventActions action) {
            GoodreadsExportFailuresActivity.this.refreshData();
        }
    };
    /** Task ID, if provided in intent */
    private long mTaskId = 0;

    /**
     * Constructor. Tell superclass the resource for the list.
     */
    public GoodreadsExportFailuresActivity() {
        super(com.eleybourn.bookcatalogue.R.layout.goodreads_event_list);
    }

    /**
     * @return the unique ID associated with this activity. Used in activity results.
     */
    @SuppressWarnings("SameReturnValue")
    private static int getActivityId() {
        return UniqueId.ACTIVITY_GOODREADS_EXPORT_FAILURES;
    }

    /**
     * Utility routine to start this activity on behalf of the passed activity.
     */
    public static void start(@NonNull final Activity from, final long taskId) {
        Intent i = new Intent(from, GoodreadsExportFailuresActivity.class);
        i.putExtra(LOCAL_BKEY_TASK_ID, taskId);
        from.startActivityForResult(i, getActivityId());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        // Get a DB adapter
        mDb = new CatalogueDBAdapter(this);
        mDb.open();

        Intent i = getIntent();
        if (i != null && i.hasExtra(LOCAL_BKEY_TASK_ID)) {
            mTaskId = i.getLongExtra(LOCAL_BKEY_TASK_ID, 0);
        } else {
            mTaskId = 0;
        }

        // Once the basic criteria have been setup, call the parent
        super.onCreate(savedInstanceState);

        //When any Event is added/changed/deleted, update the list. Lazy, yes.
        BookCatalogueApp.getQueueManager().registerEventListener(m_OnEventChangeListener);
        // Update the header.
        updateHeader();

        // Handle the 'cleanup' button.
        {
            View v = this.findViewById(R.id.cleanup);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    QueueManager.getQueueManager().cleanupOldEvents(7);
                }
            });
        }

        this.setTitle(R.string.task_errors);

        if (savedInstanceState == null) {
            HintManager.displayHint(this, R.string.hint_background_task_events, null);
        }
    }

    /**
     * Update the header to reflect current cursor size.
     */
    private void updateHeader() {
        TextView head = this.findViewById(R.id.events_found);
        head.setText(getResources().getString(R.string.events_found, mCursor.getCount()));
    }

    /**
     * Refresh data; some other activity may have changed relevant data (eg. a book)
     */
    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    /**
     * Build a context menu dialogue when an item is clicked.
     */
    @Override
    public void onListItemClick(@NonNull final AdapterView<?> parent, @NonNull final View v, final int position, final long id) {
        // get the event object
        final Event event = ViewTagger.getTag(v, R.id.TAG_EVENT);

        // If it owns a hint, display it
        if (event instanceof HintOwner) {
            // Show the hint if necessary; fall through to the runnable
            HintManager.displayHint(this, ((HintOwner) event).getHint(), new Runnable() {
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

    private void doContextMenu(@NonNull final AdapterView<?> parent, final View v, final int position, final long id) {
        final Event event = ViewTagger.getTag(v, R.id.TAG_EVENT);
        final List<ContextDialogItem> items = new ArrayList<>();

        if (event == null) {
            Logger.logError("event was null");
            return;
        }
        event.addContextMenuItems(this, parent, v, position, id, items, mDb);

        if (items.size() > 0) {
            showContextDialogue(getResources().getString(R.string.select_an_action), items);
        }
    }

    /**
     * Capture calls to refreshData() so we can update the header.
     */
    @Override
    protected void refreshData() {
        super.refreshData();
        updateHeader();
    }

    /**
     * Cleanup
     */
    @Override
    protected void onDestroy() {
        try {
            super.onDestroy();
        } catch (Exception ignore) {}
        try {
            if (mCursor != null) {
                mCursor.close();
                mCursor = null;
            }
        } catch (Exception ignore) {}
        try {
            if (mDb != null)
                mDb.close();
        } catch (Exception ignore) {}
        try {
            BookCatalogueApp.getQueueManager().unregisterEventListener(m_OnEventChangeListener);
        } catch (Exception ignore) {}
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
    public void bindViewToItem(@NonNull final Context context,
                               @NonNull final View view,
                               @NonNull final BindableItemCursor cursor,
                               @NonNull final BindableItemCursorAdapter.BindableItem item) {
        ViewTagger.setTag(view, R.id.TAG_EVENT, item);
        item.bindView(view, context, cursor, mDb);
    }

    /**
     * Get the EventsCursor relevant to this Activity
     */
    @Override
    protected BindableItemCursor getBindableItemCursor(@Nullable final Bundle savedInstanceState) {
        if (mTaskId == 0)
            mCursor = BookCatalogueApp.getQueueManager().getAllEvents();
        else
            mCursor = BookCatalogueApp.getQueueManager().getTaskEvents(mTaskId);

        return mCursor;
    }

}
