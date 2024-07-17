/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncStripinfoBinding;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoBePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderFragment;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterFragment;

/**
 * Starting point for sending and importing books with Calibre.
 * <p>
 * The user can specify the usual import/export options for new and/or updated books.
 * We do not yet support updating single books, nor a list of book ids.
 */
@Keep
public class StripInfoSyncFragment
        extends BaseFragment {

    /** View Binding. */
    private FragmentSyncStripinfoBinding vb;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentSyncStripinfoBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());
        toolbar.setTitle(R.string.action_synchronize);

        vb.btnLibMap.setOnClickListener(v -> {
            if (StripInfoAuth.isUsernameSet(v.getContext())) {
                replaceFragment(StripInfoBookshelfMappingFragment.create(),
                                StripInfoBookshelfMappingFragment.TAG);
            } else {
                openSettings();
            }
        });
        vb.btnImport.setOnClickListener(v -> {
            if (StripInfoAuth.isUsernameSet(v.getContext())) {
                replaceFragment(SyncReaderFragment.create(SyncServer.StripInfo),
                                SyncReaderFragment.TAG);
            } else {
                openSettings();
            }
        });
        vb.btnExport.setOnClickListener(v -> {
            if (StripInfoAuth.isUsernameSet(v.getContext())) {
                replaceFragment(SyncWriterFragment.create(SyncServer.StripInfo),
                                SyncWriterFragment.TAG);
            } else {
                openSettings();
            }
        });
    }

    private void openSettings() {
        replaceFragment(new StripInfoBePreferencesFragment(), StripInfoBePreferencesFragment.TAG);
    }

    private void replaceFragment(@NonNull final Fragment fragment,
                                 @NonNull final String tag) {
        final FragmentManager fm = getParentFragmentManager();
        fm.beginTransaction()
          .setReorderingAllowed(true)
          .addToBackStack(tag)
          .replace(R.id.main_fragment, fragment, tag)
          .commit();
    }

    private final class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menu.add(Menu.NONE, R.id.MENU_STRIP_INFO_SETTING, 0,
                     R.string.lbl_settings)
                .setIcon(R.drawable.settings_24px)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_STRIP_INFO_SETTING) {
                openSettings();
                return true;
            }
            return false;
        }
    }
}
