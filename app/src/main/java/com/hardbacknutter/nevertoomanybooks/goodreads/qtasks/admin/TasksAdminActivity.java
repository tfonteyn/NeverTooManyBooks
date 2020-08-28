/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin;

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
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQItem;

/**
 * Activity to display the available QueueManager Task object subclasses to the user.
 */
public class TasksAdminActivity
        extends BaseAdminActivity {

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.gr_tq_menu_background_tasks);

        //When any task is added/changed/deleted, we'll update the Cursor.
        QueueManager.getInstance().registerTaskListener(mOnChangeListener);

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
                            QueueManager.getInstance().cleanupOldTasks();
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
     * Get a CursorAdapter returning all tasks.
     *
     * @return CursorAdapter to use
     */
    @NonNull
    @Override
    protected TaskCursorAdapter getListAdapter(@NonNull final DAO db) {
        return new TaskCursorAdapter(this, QueueManager.getInstance().getTasks());
    }

    @Override
    protected void addContextMenuItems(@NonNull final List<ContextDialogItem> menuItems,
                                       @NonNull final TQItem item) {
        // do this here instead of on the task itself, so we have clean access to the activity
        // for startActivity.
        menuItems.add(new ContextDialogItem(getString(R.string.gr_tq_show_events_ellipsis), () -> {
            final Intent intent = new Intent(this, EventsAdminActivity.class)
                    .putExtra(EventsAdminActivity.REQ_BKEY_TASK_ID, item.getId());
            startActivity(intent);
        }));
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        QueueManager.getInstance().unregisterTaskListener(mOnChangeListener);
        super.onDestroy();
    }
}
