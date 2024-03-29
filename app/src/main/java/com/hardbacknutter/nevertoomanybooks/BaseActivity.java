/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;

/**
 * Base class for all Activity's (except the startup and the crop activity)
 * providing the recreation mechanism.
 */
public abstract class BaseActivity
        extends AppCompatActivity {

    /** Handles Activity recreation. */
    private RecreateViewModel recreateVm;

    /**
     * Called when we return from editing the Settings.
     * Override as needed.
     *
     * @param result from the {@link SettingsContract}.
     */
    @CallSuper
    public void onSettingsChanged(@NonNull final SettingsContract.Output result) {
        if (result.isRecreateActivity()) {
            recreateVm.setRecreationRequired();
        }
    }

    @Override
    protected void attachBaseContext(@NonNull final Context base) {
        final Context localizedContext = ServiceLocator.getInstance().getAppLocale().apply(base);
        super.attachBaseContext(localizedContext);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        recreateVm = new ViewModelProvider(this).get(RecreateViewModel.class);
        recreateVm.onCreate();

        super.onCreate(savedInstanceState);
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        if (recreateVm.isRecreationRequired()) {
            recreate();
        }
    }

    protected boolean isRecreating() {
        return recreateVm.isRecreating();
    }
}
