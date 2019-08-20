/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Hosting activity for Preference editing.
 */
public class SettingsActivity
        extends BaseActivity
        implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
                   SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_nav;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.lbl_settings);

        String tag = getIntent().getStringExtra(UniqueId.BKEY_FRAGMENT_TAG);
        if (tag == null) {
            tag = GlobalSettingsFragment.TAG;
        }

        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(tag) == null) {
            Fragment frag = createFragment(tag);
            frag.setArguments(getIntent().getExtras());
            fm.beginTransaction()
              .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
              // add! not replace!
              .add(R.id.main_fragment, frag, tag)
              .commit();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                         .registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * create a new fragment instance from the tag.
     *
     * @param tag name of fragment to instantiate
     *
     * @return new instance
     */
    private Fragment createFragment(@NonNull final String tag) {
        if (GlobalSettingsFragment.TAG.equals(tag)) {
            return new GlobalSettingsFragment();
        } else if (StyleSettingsFragment.TAG.equals(tag)) {
            return new StyleSettingsFragment();
        } else {
            throw new IllegalArgumentException("tag=" + tag);
        }
    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
                         .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    /**
     * If any of the child preference fragments have an xml configuration with nested
     * PreferenceScreen elements, then a click on those will trigger this method.
     *
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    public boolean onPreferenceStartScreen(@NonNull final PreferenceFragmentCompat caller,
                                           @NonNull final PreferenceScreen pref) {

        // start a NEW copy of the same fragment
        //noinspection ConstantConditions
        Fragment frag = createFragment(caller.getTag());

        // and set it to start with the new root key (screen)
        Bundle callerArgs = caller.getArguments();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        if (callerArgs != null) {
            args.putAll(callerArgs);
        }
        frag.setArguments(args);
        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(pref.getKey())
                .replace(R.id.main_fragment, frag, pref.getKey())
                .commit();

        return true;
    }

    /**
     * Apply preference changes.
     */
    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugEnter(this, "BaseActivity.onSharedPreferenceChanged",
                              "key=" + key);
        }

        // Trigger a recreate of this activity, if the setting has changed.
        switch (key) {
            case Prefs.pk_ui_theme:
                if (App.isThemeChanged(this)) {
                    recreate();
                    App.setIsRecreating();
                }
                break;

            case Prefs.pk_ui_language:
                if (LocaleUtils.isChanged(this)) {
                    recreate();
                    App.setIsRecreating();
                }
                break;

            default:
                break;
        }
    }
}
