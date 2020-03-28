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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleFragment;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;

/**
 * Hosting activity for Preference editing.
 */
public class SettingsActivity
        extends BaseActivity
        implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
                   PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
                   SharedPreferences.OnSharedPreferenceChangeListener {

    /** Log tag. */
    private static final String TAG = "SettingsActivity";

    private ResultDataModel mModel;

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_main_nav);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(ResultDataModel.class);

        String tag = getIntent().getStringExtra(UniqueId.BKEY_FRAGMENT_TAG);
        if (tag == null) {
            tag = GlobalPreferenceFragment.TAG;
        }

        // add! not replace!
        addFragment(R.id.main_fragment, tag);
    }

    /**
     * Create a fragment based on the given tag.
     *
     * @param containerViewId to receive the fragment
     * @param tag             for the required fragment
     *
     * @throws UnexpectedValueException if an invalid tag was passed in
     */
    private void addFragment(@SuppressWarnings("SameParameterValue")
                             @IdRes final int containerViewId,
                             @NonNull final String tag) {
        switch (tag) {
            case GlobalPreferenceFragment.TAG:
                addFragment(containerViewId, GlobalPreferenceFragment.class, tag);
                return;

            case StyleFragment.TAG:
                addFragment(containerViewId, StyleFragment.class, tag);
                return;

            default:
                throw new UnexpectedValueException(tag);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        setTitle(R.string.lbl_settings);

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
        // Instantiate the new Fragment
        final Fragment fragment = getSupportFragmentManager()
                .getFragmentFactory().instantiate(getClassLoader(), pref.getFragment());

        // combine the original extras with any new ones (the latter can override the former)
        Bundle args = getIntent().getExtras();
        if (args == null) {
            args = pref.getExtras();
        } else {
            args.putAll(pref.getExtras());
        }
        fragment.setArguments(args);

        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                                   .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                                   .addToBackStack(null)
                                   .replace(R.id.main_fragment, fragment)
                                   .commit();
        return true;
    }


    /**
     * If any of the child preference fragments have an xml configuration with nested
     * PreferenceScreen elements, then a click on those will trigger this method.
     *
     * <br><br>{@inheritDoc}
     *
     * @deprecated android docs claim nested PreferenceScreen tags are no longer supported.
     */
    @Override
    @Deprecated
    public boolean onPreferenceStartScreen(@NonNull final PreferenceFragmentCompat caller,
                                           @NonNull final PreferenceScreen pref) {

        // start a NEW copy of the same fragment
        Fragment fragment;
        try {
            fragment = caller.getClass().newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            throw new IllegalStateException();
        }

        // and set it to start with the new root key (screen)
        Bundle callerArgs = caller.getArguments();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        if (callerArgs != null) {
            args.putAll(callerArgs);
        }
        fragment.setArguments(args);
        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(pref.getKey())
                .replace(R.id.main_fragment, fragment, pref.getKey())
                .commit();

        return true;
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {

        switch (key) {
            // Trigger a recreate of this activity, if this setting has changed.
            case Prefs.pk_ui_theme:
                if (App.isThemeChanged(this, mInitialThemeId)) {
                    setIsRecreating();
                    recreate();
                }
                break;

            // Trigger a recreate of this activity, if this setting has changed.
            case Prefs.pk_ui_locale:
                if (LocaleUtils.isChanged(this, mInitialLocaleSpec)) {
                    LocaleUtils.onLocaleChanged();
                    setIsRecreating();
                    recreate();
                }
                break;

            default:
                break;
        }

        // set the result (and again and again...). Also see the fragment method.
        // TODO: make the response conditional, not all changes warrant a recreate!
        mModel.putResultData(BaseActivity.BKEY_RECREATE, true);
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        if (data != null) {
            mModel.putResultData(data);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_OK, mModel.getResultData());
        super.onBackPressed();
    }
}
