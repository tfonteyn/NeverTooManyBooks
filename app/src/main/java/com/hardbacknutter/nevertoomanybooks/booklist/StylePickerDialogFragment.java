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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleBaseFragment;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleFragment;
import com.hardbacknutter.nevertoomanybooks.widgets.RadioGroupRecyclerAdapter;

public class StylePickerDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "StylePickerDialogFrag";

    private static final String BKEY_SHOW_ALL_STYLES = TAG + ":showAllStyles";
    /** The styles get transformed into Pair records which are passed to the adapter. */
    private final List<Pair<String, String>> mAdapterItemList = new ArrayList<>();
    /** Show all styles, or only the preferred styles. */
    private boolean mShowAllStyles;
    /** The map with all styles as loaded from the database. */
    private Map<String, BooklistStyle> mBooklistStyles;
    private RadioGroupRecyclerAdapter<String, String> mAdapter;
    /** Currently selected style. */
    @Nullable
    private String mCurrentStyleUuid;

    /** Where to send the result. */
    @Nullable
    private WeakReference<StyleChangedListener> mListener;

    /**
     * Constructor.
     *
     * @param currentStyle the currently active style
     * @param all          if {@code true} show all styles, otherwise only the preferred ones.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final BooklistStyle currentStyle,
                                             final boolean all) {
        final DialogFragment frag = new StylePickerDialogFragment();
        final Bundle args = new Bundle(2);
        args.putString(BooklistStyle.BKEY_STYLE_UUID, currentStyle.getUuid());
        args.putBoolean(BKEY_SHOW_ALL_STYLES, all);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final StyleChangedListener listener) {
        mListener = new WeakReference<>(listener);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mCurrentStyleUuid = args.getString(BooklistStyle.BKEY_STYLE_UUID);
        Objects.requireNonNull(mCurrentStyleUuid, ErrorMsg.NULL_STYLE);
        mShowAllStyles = args.getBoolean(BKEY_SHOW_ALL_STYLES, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final View root = getLayoutInflater().inflate(R.layout.dialog_styles_menu, null);

        final RecyclerView listView = root.findViewById(R.id.styles);
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        listView.setLayoutManager(linearLayoutManager);

        loadStyles();

        //noinspection ConstantConditions
        mAdapter = new RadioGroupRecyclerAdapter<>(getContext(),
                                                   mAdapterItemList, mCurrentStyleUuid,
                                                   uuid -> mCurrentStyleUuid = uuid);
        listView.setAdapter(mAdapter);

        return new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.lbl_select_style)
                .setView(root)
                // We set the OnClickListener in onResume.
                // This allows reloading the list without having the dialog close
                // on us after the user clicks a button.
                .setNeutralButton(getMoreOrLessBtnTxtId(), null)
                .setNegativeButton(R.string.action_edit, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    @Override
    public void onResume() {
        super.onResume();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(Dialog.BUTTON_NEUTRAL)
                  .setOnClickListener(v -> {
                      mShowAllStyles = !mShowAllStyles;
                      ((AlertDialog) getDialog()).getButton(Dialog.BUTTON_NEUTRAL)
                                                 .setText(getMoreOrLessBtnTxtId());
                      loadStyles();
                      mAdapter.notifyDataSetChanged();
                  });
            dialog.getButton(Dialog.BUTTON_NEGATIVE)
                  .setOnClickListener(v -> onEditStyle());
            dialog.getButton(Dialog.BUTTON_POSITIVE)
                  .setOnClickListener(v -> onStyleSelected());
        }
    }

    /**
     * Send the selected style id back. Silently returns if there was nothing selected.
     */
    private void onStyleSelected() {
        mCurrentStyleUuid = mAdapter.getSelection();
        if (mCurrentStyleUuid == null) {
            return;
        }
        dismiss();

        if (mListener != null && mListener.get() != null) {
            mListener.get().onStyleChanged(mCurrentStyleUuid);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "onStyleSelected|"
                           + (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }
    }

    /**
     * Edit the selected style. Silently returns if there was nothing selected.
     */
    private void onEditStyle() {
        mCurrentStyleUuid = mAdapter.getSelection();
        if (mCurrentStyleUuid == null) {
            return;
        }
        dismiss();

        BooklistStyle style = mBooklistStyles.get(mCurrentStyleUuid);
        //noinspection ConstantConditions
        final long templateId = style.getId();
        if (!style.isUserDefined()) {
            // clone a builtin style first
            //noinspection ConstantConditions
            style = style.clone(getContext());
        }

        // use the activity so we get the results there.
        final Activity activity = getActivity();
        final Intent intent = new Intent(activity, SettingsActivity.class)
                .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, StyleFragment.TAG)
                .putExtra(BooklistStyle.BKEY_STYLE, style)
                .putExtra(StyleBaseFragment.BKEY_TEMPLATE_ID, templateId);
        //noinspection ConstantConditions
        activity.startActivityForResult(intent, RequestCode.EDIT_STYLE);
    }

    @StringRes
    private int getMoreOrLessBtnTxtId() {
        return mShowAllStyles ? R.string.btn_less_ellipsis
                              : R.string.btn_more_ellipsis;
    }

    /**
     * Fetch the styles.
     */
    private void loadStyles() {
        final Context context = getContext();

        try (DAO db = new DAO(TAG)) {
            //noinspection ConstantConditions
            mBooklistStyles = BooklistStyle.getStyles(context, db, mShowAllStyles);
        }

        mAdapterItemList.clear();
        for (BooklistStyle style : mBooklistStyles.values()) {
            mAdapterItemList.add(new Pair<>(style.getUuid(), style.getLabel(context)));
        }
    }

    public interface StyleChangedListener {

        void onStyleChanged(@NonNull String uuid);
    }
}
