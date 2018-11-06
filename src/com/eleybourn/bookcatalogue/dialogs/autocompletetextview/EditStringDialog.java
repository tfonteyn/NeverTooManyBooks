package com.eleybourn.bookcatalogue.dialogs.autocompletetextview;

import android.app.Activity;
import android.app.Dialog;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

import java.util.List;

abstract class EditStringDialog {
    @NonNull
    protected final CatalogueDBAdapter mDb;
    @NonNull
    protected final Runnable mOnChanged;
    @NonNull
    private final Activity mActivity;
    private ArrayAdapter<String> mAdapter;

    /**
     * EditText
     */
    EditStringDialog(final @NonNull Activity activity, final @NonNull CatalogueDBAdapter db, final @NonNull Runnable onChanged) {
        this.mActivity = activity;
        mOnChanged = onChanged;
        mDb = db;
    }

    /**
     * @param adapterResId the layout id to inflate a new View for a row
     * @param list         for theAutoCompleteTextView
     * @param onChanged    Runnable to be started after user confirming
     */
    EditStringDialog(final @NonNull Activity activity,
                     final @NonNull CatalogueDBAdapter db,
                     @SuppressWarnings("SameParameterValue") final @LayoutRes int adapterResId,
                     final @NonNull List<String> list,
                     final @NonNull Runnable onChanged) {
        this.mActivity = activity;
        mOnChanged = onChanged;
        mDb = db;
        mAdapter = new ArrayAdapter<>(activity, adapterResId, list);
    }

    /**
     *
     * @param currentText   to edit
     * @param layout        dialog content view layout
     * @param title         dialog title
     */
    protected void edit(final @NonNull String currentText, final @LayoutRes int layout, final @StringRes int title) {
        final Dialog dialog = new StandardDialogs.BasicDialog(mActivity);
        dialog.setContentView(layout);
        dialog.setTitle(title);

        final EditText editView = dialog.findViewById(R.id.name);
        //noinspection ConstantConditions
        editView.setText(currentText);
        if (editView instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) editView).setAdapter(mAdapter);
        }

        //noinspection ConstantConditions
        dialog.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newText = editView.getText().toString().trim();
                if (newText.isEmpty()) {
                    StandardDialogs.showUserMessage(mActivity, R.string.name_can_not_be_blank);
                    return;
                }
                dialog.dismiss();
                confirmEdit(currentText, newText);
            }
        });

        //noinspection ConstantConditions
        dialog.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    abstract protected void confirmEdit(final @NonNull String from, final @NonNull String to);
}
