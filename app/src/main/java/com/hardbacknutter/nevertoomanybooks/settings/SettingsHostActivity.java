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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleFragment;

/**
 * Hosting activity for Preference fragments.
 */
public class SettingsHostActivity
        extends BaseActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
                   SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "SettingsHostActivity";
    private static final String BKEY_FRAGMENT_TAG = TAG + ":fragment";

    private static final Map<String, Class<? extends BasePreferenceFragment>> sMap =
            new HashMap<>();

    static {
        sMap.put(SettingsFragment.TAG, SettingsFragment.class);
        sMap.put(CalibrePreferencesFragment.TAG, CalibrePreferencesFragment.class);
        sMap.put(StyleFragment.TAG, StyleFragment.class);
    }

    public static Intent createIntent(@NonNull final Context context,
                                      @NonNull final String fragmentTag) {
        return new Intent(context, SettingsHostActivity.class)
                .putExtra(BKEY_FRAGMENT_TAG, fragmentTag);
    }

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String tag = Objects.requireNonNull(
                getIntent().getStringExtra(BKEY_FRAGMENT_TAG), "tag");

        final Class<? extends BasePreferenceFragment> aClass = sMap.get(tag);
        if (aClass != null) {
            addFirstFragment(R.id.main_fragment, aClass, tag);
        } else {
            throw new IllegalArgumentException(tag);
        }
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {
        switch (key) {
            // Trigger a recreate of this activity, if one of these settings have changed.
            case Prefs.pk_ui_theme:
            case Prefs.pk_ui_locale:
                recreateIfNeeded();
                break;

            default:
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                         .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
                         .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull final PreferenceFragmentCompat caller,
                                             @NonNull final Preference pref) {

        final FragmentManager fm = getSupportFragmentManager();

        /* Instantiate the new Fragment
         *
         * Proguard rule needed:
         * -keep public class * extends androidx.preference.PreferenceFragmentCompat
         * or use @Keep annotation on individual fragments
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
          .setReorderingAllowed(true)
          .addToBackStack(fragment.getTag())
          .replace(R.id.main_fragment, fragment)
          .commit();
        return true;
    }
}
