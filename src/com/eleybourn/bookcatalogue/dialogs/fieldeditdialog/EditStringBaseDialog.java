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

        final EditText editView = root.findViewById(R.id.name);
        editView.setText(currentText);
        if (editView instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) editView).setAdapter(mAdapter);
        }

        final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(title)
                .create();

        root.findViewById(R.id.confirm).setOnClickListener(v -> {
            String newText = editView.getText().toString().trim();
            if (newText.isEmpty()) {
                UserMessage.showUserMessage(editView, R.string.warning_required_name);
                return;
            }
            dialog.dismiss();
            // if there are no differences, just bail out.
            if (newText.equals(currentText)) {
                return;
            }
            // ask child class to save
            saveChanges(currentText, newText);
            if (mOnChanged != null) {
                mOnChanged.run();
            }
        });

        root.findViewById(R.id.cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    protected abstract void saveChanges(@NonNull final String from,
                                        @NonNull final String to);
}
