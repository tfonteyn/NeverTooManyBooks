/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.baseactivity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.ViewGroup;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.properties.PropertyList;

import java.util.ArrayList;

/**
 * Base class to display simple preference-based options to the user.
 *
 * @author Philip Warner
 */
abstract public class PreferencesBaseActivity extends BaseActivity {

    private Intent resultData = new Intent();
    private ArrayList<String> changedPrefKeys = new ArrayList<>();

    /** Setup the views in the layout */
    abstract protected void initFields(final @NonNull PropertyList globalProps);

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        PropertyList globalProps = new PropertyList();
        initFields(globalProps);

        ViewGroup styleProps = findViewById(R.id.dynamic_properties);
        globalProps.buildView(getLayoutInflater(), styleProps);

        Tracker.exitOnCreate(this);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);

        if (!changedPrefKeys.contains(key)) {
            changedPrefKeys.add(key);
        }

        resultData.putStringArrayListExtra(UniqueId.BKEY_PREFERENCE_KEYS, changedPrefKeys);
        setResult(Activity.RESULT_OK, resultData);
    }
}
