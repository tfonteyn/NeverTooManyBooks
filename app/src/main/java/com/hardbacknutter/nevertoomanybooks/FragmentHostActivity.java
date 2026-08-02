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
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.insets.Side;

/**
 * Hosting activity for generic fragments.
 */
public class FragmentHostActivity
        extends BaseActivity {

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle rawArgs = Objects.requireNonNull(getIntent().getExtras());
        final FragmentHostActivityLauncher.Input args =
                FragmentHostActivityLauncher.Input.fromBundle(rawArgs);

        @LayoutRes
        final int activityResId = args.getActivityLayoutId();
        setContentView(activityResId);

        final CoordinatorLayout coordinatorLayout = findViewById(R.id.coordinator_container);
        final MaterialToolbar toolbar = findViewById(R.id.toolbar);
        final FloatingActionButton fab = findViewById(R.id.fab);

        InsetsListenerBuilder.apply(null, coordinatorLayout, toolbar, fab);

        @Nullable
        final View contentFrame = findViewById(R.id.content_frame);
        if (contentFrame != null && useFixedHeaderAndFooter()) {
            new InsetsListenerBuilder(contentFrame)
                    .systemBars()
                    .margins(Side.Bottom)
                    .apply();
        }

        initToolbar(toolbar);

        final String fragmentClassName = args.getFragmentClassName();
        final Class<? extends Fragment> fragmentClass;
        try {
            //noinspection unchecked
            fragmentClass = (Class<? extends Fragment>) getClassLoader()
                    .loadClass(fragmentClassName);
        } catch (@NonNull final ClassNotFoundException e) {
            throw new IllegalArgumentException(fragmentClassName);
        }

        addFirstFragment(R.id.content_frame, fragmentClass, fragmentClassName);
    }

    private void initToolbar(@Nullable final Toolbar toolbar) {
        if (toolbar != null) {
            applyScrollFlags(toolbar);

            if (isTaskRoot()) {
                toolbar.setNavigationIcon(R.drawable.menu_24px);
            } else {
                toolbar.setNavigationIcon(R.drawable.arrow_back_24px);
            }

            toolbar.setNavigationOnClickListener(v -> {
                if (!isTaskRoot()) {
                    // Simulate the user pressing the 'back' key.
                    getOnBackPressedDispatcher().onBackPressed();
                }
            });
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
}
