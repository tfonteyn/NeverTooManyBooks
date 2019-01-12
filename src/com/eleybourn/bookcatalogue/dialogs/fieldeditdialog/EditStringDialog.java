package com.eleybourn.bookcatalogue.dialogs.fieldeditdialog;

import android.app.Activity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

import java.util.List;

abstract class EditStringDialog {

    @NonNull
    protected final DBA mDb;
    @NonNull
    final Runnable mOnChanged;
    @NonNull
    private final Activity mActivity;
    private ArrayAdapter<String> mAdapter;

    /**
     * EditText.
     */
    EditStringDialog(@NonNull final Activity activity,
                     @NonNull final DBA db,
                     @NonNull final Runnable onChanged) {
        this.mActivity = activity;
        mOnChanged = onChanged;
        mDb = db;
    }

    /**
     * @param adapterResId the layout id to inflate a new View for a row
     * @param list         for theAutoCompleteTextView
     * @param onChanged    Runnable to be started after user confirming
     */
    EditStringDialog(@NonNull final Activity activity,
                     @NonNull final DBA db,
                     @SuppressWarnings("SameParameterValue") @LayoutRes final int adapterResId,
                     @NonNull final List<String> list,
                     @NonNull final Runnable onChanged) {
        this.mActivity = activity;
        mOnChanged = onChanged;
        mDb = db;
        mAdapter = new ArrayAdapter<>(activity, adapterResId, list);
    }

    /**
     * @param currentText to edit
     * @param layout      dialog content view layout
     * @param title       dialog title
     */
    protected void edit(@NonNull final String currentText,
                        @LayoutRes final int layout,
                        @StringRes final int title) {
        // Build the base dialog
        final View root = mActivity.getLayoutInflater().inflate(layout, null);

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
                confirmEdit(currentText, newText);
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

    protected abstract void confirmEdit(@NonNull String from,
                                        @NonNull String to);
}
