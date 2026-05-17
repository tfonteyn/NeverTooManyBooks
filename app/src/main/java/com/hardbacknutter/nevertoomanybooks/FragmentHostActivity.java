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

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GithubIntentFactory;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.insets.Side;

/**
 * Hosting activity for generic fragments.
 * <p>
 * 2024-04-20: Android Studio is completely [censored]ing up the code formatting in this class!
 * Each time we format the code, methods and variables jump around.
 * https://youtrack.jetbrains.com/issue/IDEA-311599/Poor-result-from-Rearrange-Code-for-Java
 * => fixed in IDEA 2026.2 EAP 1
 */
public class FragmentHostActivity
        extends BaseActivity {

    @Nullable
    private ActivityResultLauncher<String> editSettingsLauncher;
    @Nullable
    private ActivityResultLauncher<Long> manageBookshelvesLauncher;

    private boolean hasNavView;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        @LayoutRes
        final int activityResId = getIntent().getIntExtra(
                FragmentHostActivityLauncher.BKEY_ACTIVITY, 0);
        setContentView(activityResId);

        final CoordinatorLayout coordinatorLayout = findViewById(R.id.coordinator_container);
        final MaterialToolbar toolbar = findViewById(R.id.toolbar);
        final FloatingActionButton fab = findViewById(R.id.fab);

        InsetsListenerBuilder.apply(coordinatorLayout, toolbar, fab);

        @Nullable
        final View contentFrame = findViewById(R.id.content_frame);
        if (contentFrame != null) {
            if (useFixedHeaderAndFooter()) {
                InsetsListenerBuilder.create(contentFrame)
                                     .systemBars()
                                     .margins(Side.Bottom)
                                     .apply();
            }

            final Object tag = contentFrame.getTag();
            hasNavView = tag != null && "has_nav_view".equals(String.valueOf(tag));
        }

        if (hasNavView) {
            initNavView();
        }

        initToolbar(toolbar);

        final String classname = Objects.requireNonNull(
                getIntent().getStringExtra(FragmentHostActivityLauncher.BKEY_FRAGMENT_CLASS),
                "fragment class");

        final Class<? extends Fragment> fragmentClass;
        try {
            //noinspection unchecked
            fragmentClass = (Class<? extends Fragment>) getClassLoader().loadClass(classname);
        } catch (@NonNull final ClassNotFoundException e) {
            throw new IllegalArgumentException(classname);
        }

        addFirstFragment(R.id.content_frame, fragmentClass, classname);
    }

    private void initNavView() {
        manageBookshelvesLauncher = registerForActivityResult(
                new EditBookshelvesContract(), ignored -> {
                });

        editSettingsLauncher = registerForActivityResult(
                new SettingsContract(), o -> o.ifPresent(result -> {
                    if (result.isRecreateActivity()) {
                        ActivityRestarter.recreate();
                    }
                }));
    }

    private void initToolbar(@Nullable final Toolbar toolbar) {
        if (toolbar != null) {
            applyScrollFlags(toolbar);

            if (isTaskRoot()) {
                toolbar.setNavigationIcon(R.drawable.menu_24px);
            } else {
                toolbar.setNavigationIcon(R.drawable.arrow_back_24px);
            }

            toolbar.setNavigationOnClickListener(v -> onNavButton());
        }
    }

    private void onNavButton() {
        if (!isTaskRoot()) {
            // Simulate the user pressing the 'back' key.
            getOnBackPressedDispatcher().onBackPressed();
            return;
        }

        if (hasNavView) {
            final BottomSheetDialog dialog = new BottomSheetDialog(this);
            dialog.setContentView(R.layout.nav_view);
            final NavigationView navigationView = dialog.findViewById(R.id.nav_view);
            //noinspection DataFlowIssue
            navigationView.setNavigationItemSelectedListener(
                    menuItem -> onNavigationItemSelected(dialog, menuItem));
            dialog.show();
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
            } catch (@NonNull final NoSuchMethodException | InvocationTargetException e) {
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
     * @param dialog   hosting dialog
     * @param menuItem The menu item that was invoked.
     *
     * @return {@code true} if the menuItem was handled.
     */
    private boolean onNavigationItemSelected(@NonNull final BottomSheetDialog dialog,
                                             @NonNull final MenuItem menuItem) {
        dialog.dismiss();

        final int menuItemId = menuItem.getItemId();

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
            startActivity(GithubIntentFactory.help(this));
            return true;

        } else if (menuItemId == R.id.MENU_ABOUT) {
            startActivity(FragmentHostActivityLauncher.createIntent(this, AboutFragment.class));
            return true;
        }

        return false;
    }
}
