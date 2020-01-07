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
package com.hardbacknutter.nevertoomanybooks.dialogs.simplestring;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

abstract class EditStringBaseDialog {

    private static final String TAG = "EditStringBaseDialog";

    /** Database Access. */
    @NonNull
    final DAO mDb;

    @Nullable
    private final WeakReference<BookChangedListener> mBookChangedListener;

    @NonNull
    private final Context mContext;

    /** Adapter for the AutoCompleteTextView field. */
    private final DiacriticArrayAdapter<String> mAdapter;

    private EditText mEditText;
    private String mCurrentText;

    /**
     * EditText.
     *
     * @param context  Current context
     * @param db       Database Access
     * @param listener Runnable to be started after user confirming
     */
    EditStringBaseDialog(@NonNull final Context context,
                         @NonNull final DAO db,
                         @Nullable final BookChangedListener listener) {
        mContext = context;
        mDb = db;
        mBookChangedListener = new WeakReference<>(listener);
        mAdapter = null;
    }

    /**
     * AutoCompleteTextView.
     *
     * @param context  Current context
     * @param db       Database Access
     * @param list     for the AutoCompleteTextView
     * @param listener BookChangedListener
     */
    EditStringBaseDialog(@NonNull final Context context,
                         @NonNull final DAO db,
                         @NonNull final List<String> list,
                         @Nullable final BookChangedListener listener) {
        mContext = context;
        mDb = db;
        mBookChangedListener = new WeakReference<>(listener);
        mAdapter = new DiacriticArrayAdapter<>(
                context, android.R.layout.simple_dropdown_item_1line, list);
    }

    @NonNull
    public Context getContext() {
        return mContext;
    }

    /**
     * @param currentText    to edit
     * @param dialogLayoutId dialog content view layout
     * @param title          dialog title
     */
    protected void edit(@NonNull final String currentText,
                        @LayoutRes final int dialogLayoutId,
                        @StringRes final int title) {

        // Build the base dialog
        final View root = LayoutInflater.from(mContext).inflate(dialogLayoutId, null);

        mCurrentText = currentText;
        mEditText = root.findViewById(R.id.name);
        mEditText.setText(mCurrentText);
        if (mEditText instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) mEditText).setAdapter(mAdapter);
        }

        new AlertDialog.Builder(mContext)
                .setIcon(R.drawable.ic_edit)
                .setView(root)
                .setTitle(title)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.btn_confirm_save, (dialog, which) -> doSave())
                .create()
                .show();
    }

    private void doSave() {
        String newText = mEditText.getText().toString().trim();
        if (newText.isEmpty()) {
            Snackbar.make(mEditText, R.string.warning_missing_name, Snackbar.LENGTH_LONG).show();
            return;
        }
        // if there are no differences, just bail out.
        if (newText.equals(mCurrentText)) {
            return;
        }
        // ask child class to save
        saveChanges(mContext, mCurrentText, newText);
    }

    void sendBookChangedMessage(@BookChangedListener.WhatChanged final int changeFlags,
                                @SuppressWarnings("SameParameterValue")
                                @Nullable final Bundle data) {
        if (mBookChangedListener != null && mBookChangedListener.get() != null) {
            mBookChangedListener.get().onBookChanged(0, changeFlags, data);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Log.d(TAG, "onBookChanged|" + Logger.WEAK_REFERENCE_DEAD);
            }
        }
    }

    protected abstract void saveChanges(@NonNull Context context,
                                        @NonNull String from,
                                        @NonNull String to);
}
