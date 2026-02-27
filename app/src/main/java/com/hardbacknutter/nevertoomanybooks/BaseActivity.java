/*
 * @Copyright 2018-2026 HardBackNutter
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
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.AppBarLayout;

/**
 * Base class for all Activity's (except the startup and ACRA activity).
 * <p>
 * Handles EdgeToEdge basics and localised-context for the ui-language setting.
 * Provides methods to make the system/menu bar scrolling or fixed.
 */
public abstract class BaseActivity
        extends AppCompatActivity {

    /**
     * Preference key: Whether to use scrolling or fixed  system/menu bars.
     * <p>
     * Type: stringified int
     * <p>
     * {@code 0}: scroll
     * {@code 1}: fixed
     */
    public static final String PK_UI_TOP_MENU = "ui.screen.systembars.fixed";

    /**
     * Check if the system/menu bar should be scrolling or fixed.
     *
     * @return {@code true} for fixed, {@code false} for scrolling
     *
     * @see #applyScrollFlags(Toolbar)
     */
    static boolean useFixedHeaderAndFooter() {
        // 0 -> scroll
        // 1 -> fixed
        return 0 != ServiceLocator.getInstance().getSharedPreferences()
                                  .getIntFromString(PK_UI_TOP_MENU, 0);
    }

    /**
     * Apply the scroll flags to the toolbar according to use preferences.
     *
     * @param toolbar to handle
     *
     * @see #useFixedHeaderAndFooter()
     */
    static void applyScrollFlags(@NonNull final Toolbar toolbar) {
        final AppBarLayout.LayoutParams lp = (AppBarLayout.LayoutParams)
                toolbar.getLayoutParams();
        if (useFixedHeaderAndFooter()) {
            lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL);
        } else {
            lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                              | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                              | AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
            );
        }
        toolbar.setLayoutParams(lp);
    }

    @Override
    protected void attachBaseContext(@NonNull final Context base) {
        final Context localizedContext = ServiceLocator.getInstance().getAppLocale().apply(base);
        super.attachBaseContext(localizedContext);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        // EdgeToEdge on Android pre-15
        // There are some serious insets listener issues on API 28/29,
        // at least in the emulator, I don't have a physical device on those versions.
        // ViewPager2 also documents a serious bug when using API < 30.
        // Therefore, we're only supporting edge-to-edge starting from API-30
        // being drawn under the bottom 3-btn-nav-bar, i.e. the insets not being passed in.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            EdgeToEdge.enable(this);
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(@NonNull final View view) {
        super.setContentView(view);
        handleEdgeToEdge();
    }

    @Override
    public void setContentView(@LayoutRes final int layoutResID) {
        super.setContentView(layoutResID);
        handleEdgeToEdge();
    }

    @Override
    public void setContentView(@NonNull final View view,
                               @Nullable final ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        handleEdgeToEdge();
    }

    private void handleEdgeToEdge() {
        // EdgeToEdge. See note in onCreate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }
}
