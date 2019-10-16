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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;

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

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
                         .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
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
            throw new UnexpectedValueException(tag);
        }
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

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {

        switch (key) {
            // Trigger a recreate of this activity, if this setting has changed.
            case Prefs.pk_ui_theme:
                if (App.isThemeChanged(mInitialThemeId)) {
                    App.setIsRecreating();
                    recreate();
                }
                break;

            // Trigger a recreate of this activity, if this setting has changed.
            case Prefs.pk_ui_locale:
                if (LocaleUtils.isChanged(this, mInitialLocaleSpec)) {
                    LocaleUtils.onLocaleChanged();
                    App.setIsRecreating();
                    recreate();
                }
                break;

            default:
                break;
        }

        // set the result (and again and again...). Also see the fragment method.
        // TODO: make the response conditional, not all changes warrant a recreate!
        ResultDataModel resultDataModel = new ViewModelProvider(this).get(ResultDataModel.class);
        resultDataModel.putExtra(UniqueId.BKEY_RECREATE_ACTIVITY, true);
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (data != null) {
            ResultDataModel resultDataModel =
                    new ViewModelProvider(this).get(ResultDataModel.class);
            resultDataModel.putAll(data);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {

        ResultDataModel resultDataModel = new ViewModelProvider(this).get(ResultDataModel.class);
        Intent resultData = resultDataModel.getActivityResultData();
        if (resultData.getExtras() != null) {
            setResult(Activity.RESULT_OK, resultData);
        }

        super.onBackPressed();
    }
}
