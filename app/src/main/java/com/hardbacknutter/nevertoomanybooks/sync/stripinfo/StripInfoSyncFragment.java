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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncStripinfoBinding;
import com.hardbacknutter.nevertoomanybooks.settings.sites.StripInfoBePreferencesFragment;
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

    public static final String TAG = "StripInfoSyncFragment";

    /** View Binding. */
    private FragmentSyncStripinfoBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentSyncStripinfoBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.action_synchronize);

        mVb.btnImport.setOnClickListener(v -> {
            if (StripInfoAuth.isUsernameSet()) {
                final Bundle args = new Bundle();
                args.putParcelable(SyncServer.BKEY_SITE, SyncServer.StripInfo);

                final Fragment fragment = new SyncReaderFragment();
                fragment.setArguments(args);
                final FragmentManager fm = getParentFragmentManager();
                fm.beginTransaction()
                  .setReorderingAllowed(true)
                  .addToBackStack(SyncReaderFragment.TAG)
                  .replace(R.id.main_fragment, fragment, SyncReaderFragment.TAG)
                  .commit();
            } else {
                openSettings();
            }
        });
        mVb.btnExport.setOnClickListener(v -> {
            if (StripInfoAuth.isUsernameSet()) {
                final Bundle args = new Bundle();
                args.putParcelable(SyncServer.BKEY_SITE, SyncServer.StripInfo);

                final Fragment fragment = new SyncWriterFragment();
                fragment.setArguments(args);
                final FragmentManager fm = getParentFragmentManager();
                fm.beginTransaction()
                  .setReorderingAllowed(true)
                  .addToBackStack(SyncWriterFragment.TAG)
                  .replace(R.id.main_fragment, fragment, SyncWriterFragment.TAG)
                  .commit();
            } else {
                openSettings();
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(R.id.MENU_GROUP_STRIPINFO, R.id.MENU_STRIP_INFO_SETTING, 0,
                 R.string.lbl_settings)
            .setIcon(R.drawable.ic_baseline_settings_24);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_STRIP_INFO_SETTING) {
            openSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openSettings() {
        final Fragment fragment = new StripInfoBePreferencesFragment();
        final FragmentManager fm = getParentFragmentManager();
        fm.beginTransaction()
          .setReorderingAllowed(true)
          .addToBackStack(StripInfoBePreferencesFragment.TAG)
          .replace(R.id.main_fragment, fragment, StripInfoBePreferencesFragment.TAG)
          .commit();
    }
}
