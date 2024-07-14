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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;

/**
 * Base class for all Activity's (except the startup and ACRA activity)
 * providing the recreation mechanism.
 * FIXME: reimplement recreation using the same mechanism as the
 *  {@link com.hardbacknutter.nevertoomanybooks.utils.theme.ThemeColorController}
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
        // ede2edge on Android pre-15
        EdgeToEdge.enable(this);

        recreateVm = new ViewModelProvider(this).get(RecreateViewModel.class);
        recreateVm.onCreate();

        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(@NonNull final View view) {
        super.setContentView(view);
        handleEdge2Edge();
    }

    @Override
    public void setContentView(@LayoutRes final int layoutResID) {
        super.setContentView(layoutResID);
        handleEdge2Edge();
    }

    @Override
    public void setContentView(@NonNull final View view,
                               @Nullable final ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        handleEdge2Edge();
    }

    private void handleEdge2Edge() {
        // Edge2Edge
        // Remove default three-button navigation background protection
        // https://developer.android.com/codelabs/edge-to-edge#2
        // The default NavigationBarContrastEnforced==true
        // ensures that the navigation bar has enough contrast when a fully
        // transparent background is requested. By setting this attribute to false,
        // you are effectively setting the three-button navigation background
        // to transparent. window.isNavigationBarContrastEnforced will only
        // impact three-button navigation and has no impact on gesture navigation.
        // MUST be called AFTER super.setContentView
        //
        //This has NO effect when fitsSystemWindows="true" is set,
        // as that flag will overrule the below flag.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        if (recreateVm.isRecreationRequired()) {
            recreate();
        }
    }

    boolean isRecreating() {
        return recreateVm.isRecreating();
    }
}
