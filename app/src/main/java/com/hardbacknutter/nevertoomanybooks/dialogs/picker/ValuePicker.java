/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.dialogs.picker;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Provides an AlertDialog with an optional title and message.
 * The content is a list of options, behaving like a menu.
 * Set an OnClickListener on each item in the adapter,
 * and call {@link PickListener#onPicked(Object)} from it.
 * <p>
 * So you basically get a 'deluxe' {@link PopupMenu}.
 */
public class ValuePicker {

    @Nullable
    private final TextView mMessageView;
    @NonNull
    private final RecyclerView mListView;
    @NonNull
    private final AlertDialog mDialog;

    /**
     * Constructor.
     *
     * @param context    Current context
     * @param title      (optional) Dialog title
     * @param message    (optional) Message to display at the top
     * @param showCancel set to {@code true} to show an explicit 'cancel' button.
     */
    ValuePicker(@NonNull final Context context,
                @Nullable final CharSequence title,
                @Nullable final CharSequence message,
                final boolean showCancel) {

        View root = LayoutInflater.from(context).inflate(R.layout.dialog_popupmenu, null);

        mMessageView = root.findViewById(R.id.message);

        // list of options
        mListView = root.findViewById(R.id.itemList);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
        mListView.setLayoutManager(linearLayoutManager);

        mDialog = new MaterialAlertDialogBuilder(context)
                .setView(root)
                .create();

        // optional title
        setTitle(title);
        // Optional message
        setMessage(message);

        if (showCancel) {
            mDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                              context.getText(android.R.string.cancel),
                              (dialog, which) -> dialog.cancel());
        }
    }

    public void show() {
        mDialog.show();
    }

    void dismiss() {
        mDialog.dismiss();
    }

    /**
     * @param adapter          to use
     * @param scrollToPosition position to scroll initially to. Set to 0 for no scroll.
     */
    void setAdapter(@NonNull final RecyclerView.Adapter adapter,
                    final int scrollToPosition) {
        mListView.setAdapter(adapter);
        mListView.scrollToPosition(scrollToPosition);
    }

    void setTitle(@Nullable final CharSequence title) {
        if (title != null && title.length() > 0) {
            mDialog.setTitle(title);
        }
    }

    void setMessage(@Nullable final CharSequence message) {
        if (mMessageView != null) {
            if (message != null && message.length() > 0) {
                mMessageView.setText(message);
                mMessageView.setVisibility(View.VISIBLE);
            } else {
                mMessageView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Interface to listen for item selection in a custom dialog list.
     */
    public interface PickListener<T> {

        void onPicked(@NonNull T item);
    }
}
