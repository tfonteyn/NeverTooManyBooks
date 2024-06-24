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

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.SoftwareKeyboardControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.utils.Delay;

public abstract class BaseFragment
        extends Fragment {

    @Nullable
    private View progressFrame;
    @Nullable
    private Toolbar toolbar;
    @Nullable
    private FloatingActionButton fab;

    /**
     * Hide the keyboard.
     *
     * @param v a View from which we can get the window token.
     */
    protected void hideKeyboard(@NonNull final View v) {
        new SoftwareKeyboardControllerCompat(v).hide();
    }

    @NonNull
    protected View getProgressFrame() {
        if (progressFrame == null) {
            //noinspection DataFlowIssue
            progressFrame = Objects.requireNonNull(getActivity().findViewById(R.id.progress_frame),
                                                   "R.id.progress_frame");
        }
        return progressFrame;
    }

    @NonNull
    protected Toolbar getToolbar() {
        if (toolbar == null) {
            //noinspection DataFlowIssue
            toolbar = Objects.requireNonNull(getActivity().findViewById(R.id.toolbar),
                                             "R.id.toolbar");
        }
        return toolbar;
    }

    @NonNull
    protected FloatingActionButton getFab() {
        if (fab == null) {
            //noinspection DataFlowIssue
            fab = Objects.requireNonNull(getActivity().findViewById(R.id.fab),
                                         "R.id.fab");
        }
        return fab;
    }

    /**
     * Show a Snackbar message and after a delay, finish the Activity.
     *
     * @param message to show
     */
    protected void showMessageAndFinishActivity(@NonNull final CharSequence message) {
        final View view = getView();
        // Can be null in race conditions.
        if (view != null) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
            view.postDelayed(() -> {
                // Can be null in race conditions.
                // i.e. the user cancelled which got us here, and then very quickly taps 'back'
                // before we get here.
                final FragmentActivity activity = getActivity();
                if (activity != null) {
                    activity.finish();
                }
            }, Delay.LONG_MS);
        }
    }
}
