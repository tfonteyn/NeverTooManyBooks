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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.BindableItem;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.BindableItemAdminActivity;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.ContextDialogItem;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TasksCursorAdapter;

/**
 * Activity to display the available QueueManager Task object subclasses to the user.
 */
public class TasksAdminActivity
        extends BindableItemAdminActivity {

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.gr_tq_menu_background_tasks);

        //When any task is added/changed/deleted, update the list. Lazy, yes.
        QueueManager.getQueueManager().registerTaskListener(mOnChangeListener);

        if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_background_tasks, null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.gr_tq_btn_cleanup_old_tasks)
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
                        .setMessage(R.string.gr_tq_btn_cleanup_old_tasks)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            QueueManager.getQueueManager().cleanupOldTasks();
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
     * Get a CursorAdapter returning the tasks we are interested in (here: all tasks).
     *
     * @return CursorAdapter to use
     */
    @NonNull
    @Override
    protected TasksCursorAdapter getListAdapter(@NonNull final DAO db) {
        return new TasksCursorAdapter(this, QueueManager.getQueueManager().getTasks(), db);
    }

    @Override
    public void addContextMenuItems(@NonNull final List<ContextDialogItem> menuItems,
                                    @NonNull final BindableItem item) {
        menuItems.add(new ContextDialogItem(
                getString(R.string.gr_tq_show_events_ellipsis), () -> {
            Intent intent = new Intent(this, EventsAdminActivity.class)
                    .putExtra(EventsAdminActivity.REQ_BKEY_TASK_ID, item.getId());
            startActivity(intent);
        }));
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        QueueManager.getQueueManager().unregisterTaskListener(mOnChangeListener);
        super.onDestroy();
    }
}
