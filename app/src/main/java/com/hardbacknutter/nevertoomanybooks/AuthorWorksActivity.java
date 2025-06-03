/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

/**
 * Needs MANIFEST setup to set launchMode="singleTask".
 * <pre>{@code
 *             <activity
 *             android:name=".AuthorWorksActivity"
 *             android:launchMode="singleTask"
 *             android:windowSoftInputMode="stateAlwaysHidden|adjustResize"
 *             />
 * }
 * </pre>
 */
public class AuthorWorksActivity
        extends FragmentHostActivity {

    private AuthorWorksViewModel vm;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(AuthorWorksViewModel.class);
        vm.init(this, Objects.requireNonNull(getIntent().getExtras()));
    }

    /**
     * Called when the user opens this Activity a second time.
     *
     * @param intent The new intent that was used to start the activity
     */
    @Override
    protected void onNewIntent(@NonNull final Intent intent) {
        super.onNewIntent(intent);
        vm.init(this, Objects.requireNonNull(intent.getExtras()));
    }
}
