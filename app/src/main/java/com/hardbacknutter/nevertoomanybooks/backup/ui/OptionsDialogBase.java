/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.backup.ui;

import android.app.Dialog;
import android.content.res.Configuration;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public abstract class OptionsDialogBase<T extends Options>
        extends DialogFragment {

    private static final String TAG = "OptionsDialogBase";

    private Checkable cbxBooks;
    private Checkable cbxCovers;
    private Checkable cbxPrefs;
    /** optional. */
    private Checkable cbxXml;

    private WeakReference<OptionsListener<T>> mListener;

    public void setListener(@NonNull final OptionsListener<T> listener) {
        mListener = new WeakReference<>(listener);
    }

    void initCommonCbx(@NonNull final Options options,
                       @NonNull final View root) {
        cbxBooks = root.findViewById(R.id.cbx_books_csv);
        cbxBooks.setChecked((options.options & Options.BOOK_CSV) != 0);
        cbxCovers = root.findViewById(R.id.cbx_covers);
        cbxCovers.setChecked((options.options & Options.COVERS) != 0);
        cbxPrefs = root.findViewById(R.id.cbx_preferences);
        cbxPrefs.setChecked(
                (options.options & (Options.PREFERENCES | Options.BOOK_LIST_STYLES)) != 0);

        cbxXml = root.findViewById(R.id.cbx_xml_tables);
        if (cbxXml != null) {
            cbxXml.setChecked((options.options & Options.XML_TABLES) != 0);
        }
    }

    void updateAndSend(@NonNull final T options) {
        updateOptions();

        if (mListener.get() != null) {
            mListener.get().onOptionsSet(options);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Log.d(TAG, "onOptionsSet" + Logger.WEAK_REFERENCE_DEAD);
            }
        }
    }

    void updateOptions(@NonNull final Options options) {
        if (cbxBooks.isChecked()) {
            options.options |= Options.BOOK_CSV;
        } else {
            options.options &= ~Options.BOOK_CSV;
        }

        if (cbxCovers.isChecked()) {
            options.options |= Options.COVERS;
        } else {
            options.options &= ~Options.COVERS;
        }

        if (cbxPrefs.isChecked()) {
            options.options |= Options.PREFERENCES | Options.BOOK_LIST_STYLES;
        } else {
            options.options &= ~(Options.PREFERENCES | Options.BOOK_LIST_STYLES);
        }

        if (cbxXml != null) {
            if (cbxXml.isChecked()) {
                options.options |= Options.XML_TABLES;
            } else {
                options.options &= ~Options.XML_TABLES;
            }
        }
    }

    protected abstract void updateOptions();

    @Override
    public void onStart() {
        super.onStart();

        //FIXME: large screens will look bad
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // force the dialog to be big enough
            Dialog dialog = getDialog();
            if (dialog != null) {
                int width = ViewGroup.LayoutParams.MATCH_PARENT;
                int height = ViewGroup.LayoutParams.WRAP_CONTENT;
                //noinspection ConstantConditions
                dialog.getWindow().setLayout(width, height);
            }
        }
    }

    @Override
    public void onPause() {
        updateOptions();
        super.onPause();
    }

    /**
     * Listener interface to receive notifications when dialog is confirmed.
     */
    public interface OptionsListener<T extends Options> {

        void onOptionsSet(@NonNull T options);
    }
}
