package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.app.Dialog;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

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
    EditStringDialog(@NonNull final Activity activity, @NonNull final CatalogueDBAdapter db, @NonNull final Runnable onChanged) {
        this.mActivity = activity;
        mOnChanged = onChanged;
        mDb = db;
    }

    /**
     * AutoCompleteTextView
     */
    EditStringDialog(@NonNull final Activity activity,
                     @NonNull final CatalogueDBAdapter db,
                     @NonNull final Runnable onChanged,
                     @SuppressWarnings("SameParameterValue") @LayoutRes final int adapterResId,
                     @NonNull final List<String> list) {
        this.mActivity = activity;
        mOnChanged = onChanged;
        mDb = db;
        mAdapter = new ArrayAdapter<>(activity, adapterResId, list);
    }

    protected void edit(@NonNull final String s, @LayoutRes final int layout, final int title) {
        final Dialog dialog = new StandardDialogs.BasicDialog(mActivity);
        dialog.setContentView(layout);
        dialog.setTitle(title);

        final EditText nameView = dialog.findViewById(R.id.name);
        //noinspection ConstantConditions
        nameView.setText(s);
        if (nameView instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) nameView).setAdapter(mAdapter);
        }

        //noinspection ConstantConditions
        dialog.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = nameView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(mActivity, R.string.name_can_not_be_blank);
                    return;
                }
                dialog.dismiss();
                confirmEdit(s, newName);
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

    abstract protected void confirmEdit(@NonNull final String from, @NonNull final String to);
}
