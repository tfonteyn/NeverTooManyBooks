/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.admin;

import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.QueueManager;

public class EventAdminFragment
        extends BaseAdminFragment {

    static final String TAG = "EventAdminFragment";

    /** Key to store optional task id when activity is started. */
    static final String REQ_BKEY_TASK_ID = TAG + ":taskId";

    /** Task ID, if provided in the arguments. */
    private long mTaskId;

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {

        final Bundle args = getArguments();
        if (args != null) {
            mTaskId = args.getLong(REQ_BKEY_TASK_ID, 0);
        }

        // Once we have the task id, call the parent
        super.onViewCreated(view, savedInstanceState);
        setSubtitle(R.string.gr_tq_title_task_errors);

        //When any event is added/changed/deleted, we'll update the Cursor.
        QueueManager.getInstance().registerEventListener(mOnChangeListener);

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.getInstance()
                      .display(getContext(), R.string.tip_background_task_events, null);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.gr_tq_btn_cleanup_old_events)
            .setIcon(R.drawable.ic_baseline_delete_24)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_RESET) {
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setMessage(R.string.gr_tq_btn_cleanup_old_events)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        QueueManager.getInstance().deleteEventsOlderThan(7);
                        refreshData();
                    })
                    .create()
                    .show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Get a CursorAdapter returning all events we are interested in,
     * either specific to our task or all events.
     *
     * @param bookDao Database Access
     *
     * @return CursorAdapter to use
     */
    @NonNull
    @Override
    protected EventCursorAdapter getListAdapter(@NonNull final BookDao bookDao) {
        final Cursor cursor;
        if (mTaskId == 0) {
            cursor = QueueManager.getInstance().getEvents();
        } else {
            cursor = QueueManager.getInstance().getEvents(mTaskId);
        }

        //noinspection ConstantConditions
        return new EventCursorAdapter(getContext(), cursor, bookDao);
    }

    @Override
    @CallSuper
    public void onDestroy() {
        QueueManager.getInstance().unregisterEventListener(mOnChangeListener);
        super.onDestroy();
    }
}
