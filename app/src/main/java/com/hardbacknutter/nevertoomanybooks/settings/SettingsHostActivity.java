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

import androidx.activity.result.contract.ActivityResultContract;
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
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleFragment;

/**
 * Hosting activity for Preference fragments.
 */
public class SettingsHostActivity
        extends BaseActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
                   SharedPreferences.OnSharedPreferenceChangeListener {

    private static final Map<String, Class<? extends BasePreferenceFragment>> sMap =
            new HashMap<>();

    static {
        sMap.put(SettingsFragment.TAG, SettingsFragment.class);
        sMap.put(StyleFragment.TAG, StyleFragment.class);
        sMap.put(CalibrePreferencesFragment.TAG, CalibrePreferencesFragment.class);
    }

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String tag = Objects.requireNonNull(
                getIntent().getStringExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG), "tag");

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
          .addToBackStack(fragment.getTag())
          .replace(R.id.main_fragment, fragment)
          .commit();
        return true;
    }

    public static class ResultContract
            extends ActivityResultContract<Bundle, Bundle> {

        @NonNull
        private final String mFragmentTag;

        public ResultContract(@NonNull final String fragmentTag) {
            mFragmentTag = fragmentTag;
        }

        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @Nullable final Bundle args) {
            final Intent intent = new Intent(context, SettingsHostActivity.class)
                    .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG, mFragmentTag);
            if (args != null && !args.isEmpty()) {
                intent.putExtras(args);
            }
            return intent;
        }

        @Override
        @Nullable
        public Bundle parseResult(final int resultCode,
                                  @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Logger.d(mFragmentTag, "parseResult",
                         "|resultCode=" + resultCode + "|intent=" + intent);
            }

            if (intent == null || resultCode != RESULT_OK) {
                return null;
            }
            return intent.getExtras();
        }
    }
}
