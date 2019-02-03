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
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.HintManager.HintOwner;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity to display all Events in the QueueManager.
 *
 * @author Philip Warner
 */
public class EventQueueListActivity
        extends BindableItemListActivity {

    /** Key to store optional task ID when activity is started. */
    public static final String REQUEST_BKEY_TASK_ID = "EventQueueListActivity.TaskId";

    /** Task ID, if provided in intent. */
    private long mTaskId;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_message_queue_list;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {

        Intent intent = getIntent();
        if (intent != null) {
            mTaskId = intent.getLongExtra(REQUEST_BKEY_TASK_ID, 0);
        }
        // Once the basic criteria have been setup, call the parent
        super.onCreate(savedInstanceState);
        setTitle(R.string.gr_tq_title_task_errors);

        QueueManager.getQueueManager().registerEventListener(mOnChangeListener);

        Button cleanupBtn = findViewById(R.id.cleanup);
        cleanupBtn.setText(R.string.gr_tq_btn_cleanup_old_events);
        cleanupBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                QueueManager.getQueueManager().cleanupOldEvents();
            }
        });

        if (savedInstanceState == null) {
            HintManager.displayHint(this.getLayoutInflater(),
                                    R.string.hint_background_task_events,
                                    null);
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

        final Event event = ViewTagger.getTagOrThrow(v, R.id.TAG_EVENT);

        // If it owns a hint, display it first
        if (event instanceof HintOwner) {
            HintManager.displayHint(this.getLayoutInflater(), ((HintOwner) event).getHint(),
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            doContextMenu(parent, v, event, position, id);
                                        }
                                    });
        } else {
            // Just display context menu
            doContextMenu(parent, v, event, position, id);
        }
    }

    private void doContextMenu(@NonNull final AdapterView<?> parent,
                               @NonNull final View v,
                               @NonNull final Event event,
                               final int position,
                               final long id) {
        List<ContextDialogItem> items = new ArrayList<>();
        event.addContextMenuItems(this, parent, v, position, id, items, mDb);
        ContextDialogItem.showContextDialog(this, R.string.title_select_an_action, items);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        QueueManager.getQueueManager().unregisterEventListener(mOnChangeListener);
        super.onDestroy();
    }

    /**
     * Let the Event bind itself.
     */
    @Override
    public void bindViewToItem(@NonNull final Context context,
                               @NonNull final View convertView,
                               @NonNull final BindableItemCursor cursor,
                               @NonNull final BindableItemCursorAdapter.BindableItem item) {
        ViewTagger.setTag(convertView, R.id.TAG_EVENT, item);
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
}
