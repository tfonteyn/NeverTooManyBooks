package com.eleybourn.bookcatalogue.dialogs.fieldeditdialog;

import android.app.Activity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.utils.UserMessage;

public abstract class EditStringBaseDialog {

    @NonNull
    protected final DBA mDb;
    @NonNull
    private final Activity mActivity;
    @Nullable
    private final Runnable mOnChanged;

    /** Adapter for the AutoCompleteTextView field. */
    private final ArrayAdapter<String> mAdapter;

    private EditText mEditText;
    private String mCurrentText;

    /**
     * EditText.
     *
     * @param onChanged Runnable to be started after user confirming
     */
    EditStringBaseDialog(@NonNull final Activity activity,
                         @NonNull final DBA db,
                         @Nullable final Runnable onChanged) {
        mActivity = activity;
        mDb = db;
        mOnChanged = onChanged;
        mAdapter = null;
    }

    /**
     * AutoCompleteTextView.
     *
     * @param list      for the AutoCompleteTextView
     * @param onChanged Runnable to be started after user confirming
     */
    EditStringBaseDialog(@NonNull final Activity activity,
                         @NonNull final DBA db,
                         @NonNull final List<String> list,
                         @Nullable final Runnable onChanged) {
        mActivity = activity;
        mDb = db;
        mOnChanged = onChanged;
        mAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, list);
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
        final View root = mActivity.getLayoutInflater().inflate(dialogLayoutId, null);

        mCurrentText = currentText;
        mEditText = root.findViewById(R.id.name);
        mEditText.setText(mCurrentText);
        if (mEditText instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) mEditText).setAdapter(mAdapter);
        }

        new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(title)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(R.string.btn_confirm_save, (d, which) -> doSave())
                .create()
                .show();
    }

    private void doSave() {
        String newText = mEditText.getText().toString().trim();
        if (newText.isEmpty()) {
            UserMessage.showUserMessage(mEditText, R.string.warning_required_name);
            return;
        }
        // if there are no differences, just bail out.
        if (newText.equals(mCurrentText)) {
            return;
        }
        // ask child class to save
        saveChanges(mCurrentText, newText);
        if (mOnChanged != null) {
            mOnChanged.run();
        }
    }

    protected abstract void saveChanges(@NonNull final String from,
                                        @NonNull final String to);
}
