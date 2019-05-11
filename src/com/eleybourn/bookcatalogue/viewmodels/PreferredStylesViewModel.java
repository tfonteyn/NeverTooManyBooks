package com.eleybourn.bookcatalogue.viewmodels;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DAO;

public class PreferredStylesViewModel
        extends ViewModel {

    /** Database access. */
    protected DAO mDb;

    /** The *in-memory* list of styles. */
    private ArrayList<BooklistStyle> mList;

    /** The row being edited. Set when an individual style is edited. */
    private int mEditedRow;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    public void init() {
        if (mList != null) {
            return;
        }

        mList = new ArrayList<>(BooklistStyles.getStyles(mDb, true).values());
    }

    public void setEditedRow(final int editedRow) {
        mEditedRow = editedRow;
    }

    @NonNull
    public ArrayList<BooklistStyle> getList() {
        return mList;
    }

    /**
     * Called after a style has been edited.
     */
    public void handleStyleChange(@NonNull final BooklistStyle style) {
        if (mEditedRow < 0) {
            // New Style added. Put at top and set as preferred
            mList.add(0, style);
            style.setPreferred(true);

        } else {
            // Existing Style edited.
            BooklistStyle origStyle = mList.get(mEditedRow);
            if (origStyle.getId() != style.getId()) {
                if (!origStyle.isUserDefined()) {
                    // Working on a clone of a builtin style
                    if (origStyle.isPreferred()) {
                        // Replace the original row with the new one
                        mList.set(mEditedRow, style);
                        // Make the new one preferred
                        style.setPreferred(true);
                        // And demote the original
                        origStyle.setPreferred(false);
                        mList.add(origStyle);
                    } else {
                        // Try to put it directly after original
                        mList.add(mEditedRow, style);
                    }
                } else {
                    // A clone of an user-defined. Put it directly after the user-defined
                    mList.add(mEditedRow, style);
                }
            } else {
                mList.set(mEditedRow, style);
            }
        }

        // add to the db if new.
        if (style.getId() == 0) {
            mDb.insertBooklistStyle(style);
        }
    }

    public void deleteStyle(@NonNull final BooklistStyle style) {
        style.delete(mDb);
        mList.remove(style);
    }

    public void saveMenuOrder() {
        BooklistStyles.savePreferredStyleMenuOrder(mList);
    }
}
