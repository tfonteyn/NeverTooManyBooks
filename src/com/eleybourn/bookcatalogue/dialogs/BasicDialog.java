package com.eleybourn.bookcatalogue.dialogs;

import android.content.Context;
import android.support.v7.app.AppCompatDialog;
import android.view.View;
import android.widget.Button;

import com.eleybourn.bookcatalogue.BookCatalogueApp;

/**
 * This class exists only for:
 * - we use AppCompatDialog in ONE place (here) .. so any future removal is easy
 * - optional (default:true) ActionBar
 * - optional Close Button, if your layout has a Button with id=android.R.id.closeButton
 */
public class BasicDialog extends AppCompatDialog {

    /**
     * a Dialog WITH an ActionBar
     *
     * @param context   the context
     */
    public BasicDialog(Context context) {
        this(context, true);
    }

    /**
     * Dialog with optional ActionBar
     *
     * @param context               the context
     * @param enableActionBar       flag
     */
    public BasicDialog(Context context, boolean enableActionBar) {
        super(context, enableActionBar ? BookCatalogueApp.getDialogThemeResId() : 0);

        Button closeButton = findViewById(android.R.id.closeButton);
        if (closeButton != null) {
            closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BasicDialog.this.dismiss();
                }
            });
        }
    }
}
