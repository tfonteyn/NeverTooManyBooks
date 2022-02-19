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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.NightMode;

/**
 * Base class for all Activity's (except the startup and the crop activity).
 * <p>
 * Fragments should implement:
 * <pre>
 *     {@code
 *          @Override
 *          @CallSuper
 *          public void onResume() {
 *              super.onResume();
 *              if (getActivity() instanceof BaseActivity) {
 *                  BaseActivity activity = (BaseActivity) getActivity();
 *                  if (activity.maybeRecreate()) {
 *                      return;
 *                  }
 *              }
 *
 *              // do stuff here
 *          }
 *     }
 * </pre>
 */
public abstract class BaseActivity
        extends AppCompatActivity {

    /** Used by {@link #showError} Snackbar.LENGTH_LONG is 2750 ms. */
    public static final int ERROR_DELAY_MS = 3000;

    /**
     * internal; Stage of Activity  doing/needing setIsRecreating() action.
     * See {@link #onResume()}.
     * <p>
     * Note this is a static!
     */
    @NonNull
    private static Recreating sActivityRecreateStatus = Recreating.No;

    private final ActivityResultLauncher<Long> mManageBookshelvesBaseLauncher =
            registerForActivityResult(new EditBookshelvesContract(),
                                      bookshelfId -> {
                                      });
    private final ActivityResultLauncher<String> mSettingsLauncher =
            registerForActivityResult(new SettingsContract(), recreateActivity -> {
                if (recreateActivity) {
                    sActivityRecreateStatus = Recreating.Required;
                }
            });
    /** Optional - The side/navigation panel. */
    @Nullable
    DrawerLayout mDrawerLayout;
    /** Locale at {@link #onCreate} time. */
    private String mInitialLocaleSpec;
    /** Night-mode at {@link #onCreate} time. */
    private NightMode.Mode mInitialNightModeId;
    /** Optional - The side/navigation menu. */
    @Nullable
    private NavigationView mNavigationView;

    private Toolbar mToolbar;

    /**
     * Hide the keyboard.
     *
     * @param view a View that can be used to get the context and the window token
     */
    public static void hideKeyboard(@NonNull final View view) {
        final InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @NonNull
    public Toolbar getToolbar() {
        if (mToolbar == null) {
            mToolbar = Objects.requireNonNull((Toolbar) findViewById(R.id.toolbar),
                                              "R.id.toolbar");
        }
        return mToolbar;
    }

    @Override
    protected void attachBaseContext(@NonNull final Context base) {
        final AppLocale appLocale = ServiceLocator.getInstance().getAppLocale();
        // apply the user-preferred Locale before onCreate is called.
        final Context localizedContext = appLocale.apply(base);

        super.attachBaseContext(localizedContext);

        // preserve, so we can check for changes in onResume.
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(localizedContext);
        mInitialLocaleSpec = appLocale.getPersistedLocaleSpec(global);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        // apply the user-preferred Theme before super.onCreate is called.
        // We preserve it, so we can check for changes in onResume.
        mInitialNightModeId = NightMode.getInstance().apply(this);

        super.onCreate(savedInstanceState);
    }

    @CallSuper
    void initNavDrawer() {
        mDrawerLayout = findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mNavigationView = mDrawerLayout.findViewById(R.id.nav_view);
            mNavigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
        }
    }

    /**
     * Manually add the first fragment for the given container. Not added to the BackStack.
     * <p>
     * <strong>The activity extras bundle will be set as arguments.</strong>
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     */
    void addFirstFragment(@SuppressWarnings("SameParameterValue")
                          @IdRes final int containerViewId,
                          @NonNull final Class<? extends Fragment> fragmentClass,
                          @NonNull final String fragmentTag) {

        final FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(fragmentTag) == null) {
            final Fragment fragment;
            try {
                fragment = fragmentClass.newInstance();
            } catch (final IllegalAccessException | InstantiationException e) {
                throw new IllegalStateException("Not a fragment: " + fragmentClass.getName());
            }
            fragment.setArguments(getIntent().getExtras());

            fm.beginTransaction()
              .setReorderingAllowed(true)
              .add(containerViewId, fragment, fragmentTag)
              .commit();
        }
    }

    /**
     * When resuming, recreate activity if needed.
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        recreateIfNeeded();
    }

    /**
     * Trigger a recreate() on the Activity if needed.
     *
     * @return {@code true} if a recreate was triggered.
     */
    @SuppressWarnings("UnusedReturnValue")
    protected boolean recreateIfNeeded() {
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(this);

        if (sActivityRecreateStatus == Recreating.Required
            || ServiceLocator.getInstance().getAppLocale().isChanged(global, mInitialLocaleSpec)
            || NightMode.getInstance().isChanged(global, mInitialNightModeId)) {

            sActivityRecreateStatus = Recreating.Yes;
            recreate();
            return true;

        } else {
            sActivityRecreateStatus = Recreating.No;
        }

        return false;
    }

    boolean isRecreating() {
        return sActivityRecreateStatus == Recreating.Yes;
    }

    /**
     * If the drawer is open and the user click the back-button, close the drawer
     * and ignore the back-press.
     */
    @Override
    public void onBackPressed() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);

        } else {
            // Will call any (enabled) registered OnBackPressedCallback handlers.
            // If there are none registered, the system will call finish().
            // However, if we do have an enabled/registered callback of our own,
            // it is responsible to call finish()
            super.onBackPressed();
        }
    }

    @Nullable
    protected MenuItem getNavigationMenuItem(@SuppressWarnings("SameParameterValue")
                                             @IdRes final int itemId) {
        return mNavigationView != null ? mNavigationView.getMenu().findItem(itemId) : null;
    }

    @NonNull
    View getNavigationMenuItemView(final int itemId) {
        //noinspection ConstantConditions
        final View anchor = mNavigationView.findViewById(itemId);
        // Not 100% we are using a legal way of getting the View...
        Objects.requireNonNull(anchor, "mNavigationView.findViewById(" + itemId + ")");
        return anchor;
    }

    @CallSuper
    boolean onNavigationItemSelected(@NonNull final MenuItem menuItem) {
        final int itemId = menuItem.getItemId();

        closeNavigationDrawer();

        if (itemId == R.id.MENU_MANAGE_BOOKSHELVES) {
            // child classes which have a 'current bookshelf' should
            // override and pass the current bookshelf id
            mManageBookshelvesBaseLauncher.launch(0L);
            return true;

        } else if (itemId == R.id.MENU_SETTINGS) {
            mSettingsLauncher.launch(null);
            return true;

        } else if (itemId == R.id.MENU_ABOUT) {
            final Intent intent = FragmentHostActivity.createIntent(this, AboutFragment.class);
            startActivity(intent);
            return true;
        }
        return false;
    }

    void closeNavigationDrawer() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    /**
     * Show an error text on the given view.
     * It will automatically be removed after {@link #ERROR_DELAY_MS}.
     *
     * @param view  on which to set the error
     * @param error text to set
     */
    protected void showError(@NonNull final TextInputLayout view,
                             @NonNull final CharSequence error) {
        view.setError(error);
        view.postDelayed(() -> view.setError(null), ERROR_DELAY_MS);
    }

    private enum Recreating {
        /** Situation normal. */
        No,
        /** Activity is in need of recreating. */
        Required,
        /** A {@link #recreate()} action has been triggered. */
        Yes
    }

}
