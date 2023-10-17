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
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.AppBarLayout;

import java.util.Objects;

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

    /** See comments in the BoB activity on its backPressedCallback. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (closeNavigationDrawer()) {
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

        initNavDrawer();
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

    private void initToolbar() {
        final Toolbar toolbar = getToolbar();
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
            if (!isTaskRoot() || !openNavigationDrawer()) {
                // Simulate the user pressing the 'back' key.
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }
}
