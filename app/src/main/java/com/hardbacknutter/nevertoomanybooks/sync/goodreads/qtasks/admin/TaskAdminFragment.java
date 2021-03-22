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

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TQItem;

public class TaskAdminFragment
        extends BaseAdminFragment {

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setSubtitle(R.string.gr_tq_menu_background_tasks);

        //When any task is added/changed/deleted, we'll update the Cursor.
        QueueManager.getInstance().registerTaskListener(mOnChangeListener);

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.getInstance().display(getContext(), R.string.tip_background_tasks, null);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.gr_tq_btn_cleanup_old_tasks)
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
                    .setMessage(R.string.gr_tq_btn_cleanup_old_tasks)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        QueueManager.getInstance().deleteTasksOlderThan(7);
                        refreshData();
                    })
                    .create()
                    .show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Get a CursorAdapter returning all tasks.
     *
     * @return CursorAdapter to use
     */
    @NonNull
    @Override
    protected TaskCursorAdapter getListAdapter(@NonNull final BookDao bookDao) {
        //noinspection ConstantConditions
        return new TaskCursorAdapter(getContext(), QueueManager.getInstance().getTasks());
    }

    @Override
    protected void addContextMenuItems(@NonNull final List<ContextDialogItem> menuItems,
                                       @NonNull final TQItem item) {
        // do this here instead of on the task itself, so we have clean access to the fragment
        menuItems.add(new ContextDialogItem(getString(R.string.gr_tq_show_events_ellipsis), () -> {
            final Bundle args = new Bundle();
            args.putLong(EventAdminFragment.REQ_BKEY_TASK_ID, item.getId());

            final Fragment fragment = new EventAdminFragment();
            fragment.setArguments(args);
            final FragmentManager fm = getParentFragmentManager();
            fm.beginTransaction()
              .setReorderingAllowed(true)
              .addToBackStack(EventAdminFragment.TAG)
              .replace(R.id.main_fragment, fragment, EventAdminFragment.TAG)
              .commit();
        }));
    }

    @Override
    @CallSuper
    public void onDestroy() {
        QueueManager.getInstance().unregisterTaskListener(mOnChangeListener);
        super.onDestroy();
    }
}
