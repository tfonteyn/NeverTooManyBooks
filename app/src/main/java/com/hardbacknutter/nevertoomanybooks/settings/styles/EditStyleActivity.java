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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Hosting activity for Style editing.
 */
public class EditStyleActivity
        extends BaseActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addFirstFragment(R.id.main_fragment, StyleFragment.class, StyleFragment.TAG);
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull final PreferenceFragmentCompat caller,
                                             @NonNull final Preference pref) {

        final FragmentManager fm = getSupportFragmentManager();

        /* Instantiate the new Fragment
         *
         * Proguard rule needed:
         * -keep public class * extends androidx.preference.PreferenceFragmentCompat
         */
        final Fragment fragment =
                fm.getFragmentFactory().instantiate(getClassLoader(), pref.getFragment());

        // combine the original extras with any new ones (the latter can override the former)
        Bundle args = getIntent().getExtras();
        if (args == null) {
            args = pref.getExtras();
        } else {
            args.putAll(pref.getExtras());
        }
        fragment.setArguments(args);

        fm.beginTransaction()
          .addToBackStack(fragment.getTag())
          // FIXME: https://issuetracker.google.com/issues/169874632
          // .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
          .replace(R.id.main_fragment, fragment)
          .commit();
        return true;
    }
}
