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

import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueManager;

/**
 * Activity to display all Events in the QueueManager.
 */
public class EventsAdminActivity
        extends BaseAdminActivity {

    /** Key to store optional task id when activity is started. */
    public static final String REQ_BKEY_TASK_ID = "EventsAdminActivity.TaskId";

    /** Task ID, if provided in intent. */
    private long mTaskId;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {

        mTaskId = getIntent().getLongExtra(REQ_BKEY_TASK_ID, 0);
        // Once we have the task id, call the parent
        super.onCreate(savedInstanceState);

        setTitle(R.string.gr_tq_title_task_errors);

        //When any event is added/changed/deleted, we'll update the Cursor.
        QueueManager.getQueueManager().registerEventListener(mOnChangeListener);

        if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_background_task_events, null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.gr_tq_btn_cleanup_old_events)
            .setIcon(R.drawable.ic_delete)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_RESET: {
                new MaterialAlertDialogBuilder(this)
                        .setIcon(R.drawable.ic_warning)
                        .setMessage(R.string.gr_tq_btn_cleanup_old_events)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            QueueManager.getQueueManager().cleanupOldEvents();
                            refreshData();
                        })
                        .create()
                        .show();
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Get a CursorAdapter returning all events we are interested in,
     * either specific to our task or all events.
     *
     * @param db Database Access
     *
     * @return CursorAdapter to use
     */
    @NonNull
    @Override
    protected EventCursorAdapter getListAdapter(@NonNull final DAO db) {
        final Cursor cursor;
        if (mTaskId == 0) {
            cursor = QueueManager.getQueueManager().getEvents();
        } else {
            cursor = QueueManager.getQueueManager().getEvents(mTaskId);
        }

        return new EventCursorAdapter(this, cursor, db);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        QueueManager.getQueueManager().unregisterEventListener(mOnChangeListener);
        super.onDestroy();
    }
}
