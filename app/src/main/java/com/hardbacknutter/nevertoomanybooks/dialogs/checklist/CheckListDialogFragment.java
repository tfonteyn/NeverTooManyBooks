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
package com.hardbacknutter.nevertoomanybooks.dialogs.checklist;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * DialogFragment to edit a list of checkbox options.
 * <p>
 * This is really overkill.. maybe time to switch back to a simple Dialog.
 * https://developer.android.com/guide/topics/ui/dialogs
 *
 * @param <T> type to use for {@link CheckListItem}
 */
public class CheckListDialogFragment<T>
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "CheckListDialogFragment";

    /** Argument. */
    private static final String BKEY_CHECK_LIST = TAG + ":list";

    /** The list of items to display. Object + checkbox. */
    private ArrayList<CheckListItem<T>> mList;
    private WeakReference<CheckListResultsListener<T>> mListener;

    /** identifier of the field this dialog is bound to. */
    @IdRes
    private
    int mDestinationFieldId;

    /**
     * Constructor.
     *
     * @param fieldId       the field whose content we want to edit
     * @param dialogTitleId resource id for the dialog title
     * @param listGetter    callback interface for getting the list to use.
     * @param <T>           type of the {@link CheckListItem}
     *
     * @return the new instance
     */
    public static <T> CheckListDialogFragment<T> newInstance(
            @IdRes final int fieldId,
            @StringRes final int dialogTitleId,
            @NonNull final CheckListEditorListGetter<T> listGetter) {

        CheckListDialogFragment<T> frag = new CheckListDialogFragment<>();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putInt(UniqueId.BKEY_FIELD_ID, fieldId);
        args.putParcelableArrayList(BKEY_CHECK_LIST, listGetter.getList());
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDestinationFieldId = requireArguments().getInt(UniqueId.BKEY_FIELD_ID);

        Bundle currentArgs = savedInstanceState != null ? savedInstanceState : requireArguments();
        mList = Objects.requireNonNull(currentArgs.getParcelableArrayList(BKEY_CHECK_LIST));
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        @SuppressLint("InflateParams")
        View root = layoutInflater.inflate(R.layout.dialog_edit_checklist, null);

        // Takes the list of items and create a list of checkboxes in the display.
        ViewGroup body = root.findViewById(R.id.content);
        for (CheckListItem item : mList) {
            CompoundButton buttonView = new CheckBox(getContext());
            buttonView.setChecked(item.isChecked());
            //noinspection ConstantConditions
            buttonView.setText(item.getLabel(getContext()));
            buttonView.setOnCheckedChangeListener((v, isChecked) -> item.setChecked(isChecked));
            body.addView(buttonView);
        }

        @SuppressWarnings("ConstantConditions")
        AlertDialog dialog =
                new AlertDialog.Builder(getContext())
                        .setView(root)
                        .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                        .setPositiveButton(android.R.string.ok, (d, which) -> {
                            if (mListener.get() != null) {
                                mListener.get().onCheckListEditorSave(mDestinationFieldId,
                                                                      extractList(mList));
                            } else {
                                if (BuildConfig.DEBUG
                                    && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                                    Logger.debug(this, "onCheckListEditorSave",
                                                 Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
                                }
                            }
                        })
                        .create();

        Bundle args = getArguments();
        if (args != null) {
            @StringRes
            int titleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.edit);
            if (titleId != 0) {
                dialog.setTitle(titleId);
            }
        }
        return dialog;
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(BKEY_CHECK_LIST, mList);
    }

    /**
     * Access the list of {@link CheckListItem} and extract the actual items.
     *
     * @param list to dissect
     *
     * @return the extracted list
     */
    @NonNull
    private ArrayList<T> extractList(@NonNull final List<CheckListItem<T>> list) {
        ArrayList<T> result = new ArrayList<>(list.size());
        for (CheckListItem<T> entry : list) {
            if (entry.isChecked()) {
                result.add(entry.getItem());
            }
        }
        return result;
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final CheckListResultsListener<T> listener) {
        mListener = new WeakReference<>(listener);
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @param <T> - type of item in the checklist
     */
    public interface CheckListResultsListener<T> {

        /**
         * reports the results after this dialog was confirmed.
         *
         * @param destinationFieldId the field this dialog is bound to
         * @param list               the list of CHECKED options
         *                           (non-checked options have been removed)
         */
        void onCheckListEditorSave(int destinationFieldId,
                                   @NonNull List<T> list);
    }

    /**
     * Loads the {@link CheckListDialogFragment} with the *current* list,
     * e.g. not the state of the list at init time.
     */
    public interface CheckListEditorListGetter<T> {

        @NonNull
        ArrayList<CheckListItem<T>> getList();
    }
}
