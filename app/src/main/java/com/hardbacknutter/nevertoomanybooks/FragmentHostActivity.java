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
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.navigation.NavigationView;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.IntentFactory;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.nevertoomanybooks.widgets.NavDrawer;

/**
 * Hosting activity for generic fragments.
 */
public class FragmentHostActivity
        extends BaseActivity {

    private static final String TAG = "FragmentHostActivity";

    /**
     * Allows passing in {@link AppBarLayout.LayoutParams}#SCROLL_FLAG_*
     * which will be set on the Activity toolbar.
     *
     * @see #initToolbar()
     */
    public static final String BKEY_TOOLBAR_SCROLL_FLAGS = TAG + ":tbf";

    private static final String BKEY_ACTIVITY = TAG + ":a";
    private static final String BKEY_FRAGMENT_CLASS = TAG + ":f";

    @Nullable
    private ActivityResultLauncher<String> editSettingsLauncher;
    @Nullable
    private ActivityResultLauncher<Long> manageBookshelvesLauncher;

    /** Optional - The side/navigation menu. */
    @Nullable
    private NavDrawer navDrawer;

    /** See comments in the BoB activity on its backPressedCallback. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (navDrawer != null && navDrawer.close()) {
                        return;
                    }
                    // Prevent looping
                    this.setEnabled(false);
                    // Simulate the user pressing the 'back' key,
                    // which will take us back to the previous screen.
                    getOnBackPressedDispatcher().onBackPressed();
                }
            };

    @NonNull
    public static Intent createIntent(@NonNull final Context context,
                                      @NonNull final Class<? extends Fragment> fragmentClass) {
        return createIntent(context, R.layout.activity_main, fragmentClass);
    }

    @NonNull
    public static Intent createIntent(@NonNull final Context context,
                                      @LayoutRes final int activityLayoutId,
                                      @NonNull final Class<? extends Fragment> fragmentClass) {
        return new Intent(context, FragmentHostActivity.class)
                .putExtra(BKEY_ACTIVITY, activityLayoutId)
                .putExtra(BKEY_FRAGMENT_CLASS, fragmentClass.getName());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        @LayoutRes
        final int activityResId = getIntent().getIntExtra(BKEY_ACTIVITY, 0);
        setContentView(activityResId);

        // Optional!
        navDrawer = NavDrawer.create(this, this::onNavigationItemSelected);
        if (navDrawer != null) {
            initLaunchers();
        }

        initToolbar();

        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

        final String classname = Objects.requireNonNull(
                getIntent().getStringExtra(BKEY_FRAGMENT_CLASS), "fragment class");

        final Class<? extends Fragment> fragmentClass;
        try {
            //noinspection unchecked
            fragmentClass = (Class<? extends Fragment>) getClassLoader().loadClass(classname);
        } catch (@NonNull final ClassNotFoundException e) {
            throw new IllegalArgumentException(classname);
        }

        addFirstFragment(R.id.main_fragment, fragmentClass, classname);
    }

    private void initLaunchers() {
        manageBookshelvesLauncher = registerForActivityResult(
                new EditBookshelvesContract(), bookshelfId -> {
                });

        editSettingsLauncher = registerForActivityResult(
                new SettingsContract(), o -> o.ifPresent(this::onSettingsChanged));
    }

    private void initToolbar() {
        final Toolbar toolbar = findViewById(R.id.toolbar);
        Objects.requireNonNull(toolbar, "R.id.toolbar");
        if (isTaskRoot()) {
            toolbar.setNavigationIcon(R.drawable.ic_baseline_menu_24);
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24);
        }

        final int flags = getIntent().getIntExtra(BKEY_TOOLBAR_SCROLL_FLAGS, -1);
        if (flags >= 0) {
            final AppBarLayout.LayoutParams lp = (AppBarLayout.LayoutParams)
                    toolbar.getLayoutParams();
            lp.setScrollFlags(flags);
        }

        toolbar.setNavigationOnClickListener(v -> {
            if (isTaskRoot()) {
                if (navDrawer != null) {
                    navDrawer.open();
                }
            } else {
                // Simulate the user pressing the 'back' key.
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    /**
     * Manually add the first fragment for the given container. Not added to the BackStack.
     * <p>
     * <strong>The activity extras bundle will be set as arguments.</strong>
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     *
     * @throws IllegalStateException if the Fragment cannot be instantiated
     */
    private void addFirstFragment(@SuppressWarnings("SameParameterValue")
                                  @IdRes final int containerViewId,
                                  @NonNull final Class<? extends Fragment> fragmentClass,
                                  @NonNull final String fragmentTag) {

        final FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(fragmentTag) == null) {
            final Fragment fragment;
            try {
                fragment = fragmentClass.getConstructor().newInstance();
            } catch (@NonNull final IllegalAccessException | InstantiationException e) {
                throw new IllegalStateException("Not a fragment: " + fragmentClass.getName());
            } catch (final NoSuchMethodException | InvocationTargetException e) {
                throw new IllegalStateException("Other failure: " + fragmentClass.getName());
            }
            fragment.setArguments(getIntent().getExtras());

            fm.beginTransaction()
              .setReorderingAllowed(true)
              .add(containerViewId, fragment, fragmentTag)
              .commit();
        }
    }

    /**
     * Handle the {@link NavigationView} menu.
     *
     * @param menuItem The menu item that was invoked.
     *
     * @return {@code true} if the menuItem was handled.
     */
    private boolean onNavigationItemSelected(@NonNull final MenuItem menuItem) {

        final int menuItemId = menuItem.getItemId();

        //noinspection DataFlowIssue
        navDrawer.close();

        if (menuItemId == R.id.MENU_MANAGE_BOOKSHELVES) {
            // child classes which have a 'current bookshelf' should
            // override and pass the current bookshelf id instead of 0L
            //noinspection DataFlowIssue
            manageBookshelvesLauncher.launch(0L);
            return true;

        } else if (menuItemId == R.id.MENU_SETTINGS) {
            //noinspection DataFlowIssue
            editSettingsLauncher.launch(null);
            return true;

        } else if (menuItemId == R.id.MENU_HELP) {
            startActivity(IntentFactory.createGithubHelpIntent(this));
            return true;

        } else if (menuItemId == R.id.MENU_ABOUT) {
            startActivity(IntentFactory.createAboutIntent(this));
            return true;
        }

        return false;
    }
}
