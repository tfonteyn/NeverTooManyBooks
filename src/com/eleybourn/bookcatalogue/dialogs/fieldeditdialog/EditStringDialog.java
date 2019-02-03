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

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

import java.util.List;

abstract class EditStringDialog {

    @NonNull
    private final Activity mActivity;
    @NonNull
    protected final DBA mDb;
    @Nullable
    private final Runnable mOnChanged;

    /** Adapter for the AutoCompleteTextView field. */
    private final ArrayAdapter<String> mAdapter;

    /**
     * EditText.
     *
     * @param onChanged Runnable to be started after user confirming
     */
    EditStringDialog(@NonNull final Activity activity,
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
     * @param rowLayoutId the layout id to inflate a new View for a row
     * @param list        for theAutoCompleteTextView
     * @param onChanged   Runnable to be started after user confirming
     */
    EditStringDialog(@NonNull final Activity activity,
                     @NonNull final DBA db,
                     @LayoutRes final int rowLayoutId,
                     @NonNull final List<String> list,
                     @Nullable final Runnable onChanged) {
        mActivity = activity;
        mDb = db;
        mOnChanged = onChanged;
        mAdapter = new ArrayAdapter<>(activity, rowLayoutId, list);
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
        //noinspection ConstantConditions
        editView.setText(currentText);
        if (editView instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) editView).setAdapter(mAdapter);
        }

        final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(title)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newText = editView.getText().toString().trim();
                if (newText.isEmpty()) {
                    StandardDialogs.showUserMessage(mActivity, R.string.warning_required_name);
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
            }
        });

        //noinspection ConstantConditions
        root.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    protected abstract void saveChanges(@NonNull final String from,
                                        @NonNull final String to);
}
