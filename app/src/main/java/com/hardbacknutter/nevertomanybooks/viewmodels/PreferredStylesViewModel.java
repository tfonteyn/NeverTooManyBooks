package com.hardbacknutter.nevertomanybooks.viewmodels;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import com.hardbacknutter.nevertomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistStyles;
import com.hardbacknutter.nevertomanybooks.database.DAO;

public class PreferredStylesViewModel
        extends ViewModel {

    /** Database access. */
    private DAO mDb;

    /** The *in-memory* list of styles. */
    private ArrayList<BooklistStyle> mList;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    /**
     * Pseudo constructor.
     */
    public void init() {
        if (mDb != null) {
            return;
        }
        mDb = new DAO();
        mList = new ArrayList<>(BooklistStyles.getStyles(mDb, true).values());
    }

    @NonNull
    public ArrayList<BooklistStyle> getList() {
        return mList;
    }

    /**
     * Called after a style has been edited.
     */
    public void handleStyleChange(@NonNull final BooklistStyle style) {
        // based on the uuid, find the style in the list.
        // Don't use 'indexOf' though, as the incoming style object was parcelled along the way.
        int editedRow = -1;
        for (int i = 0; i < mList.size(); i++) {
            if (mList.get(i).equals(style)) {
                editedRow = i;
                break;
            }
        }

        if (editedRow < 0) {
            // New Style added. Put at top and set as preferred
            mList.add(0, style);
            style.setPreferred(true);

        } else {
            // Existing Style edited.
            BooklistStyle origStyle = mList.get(editedRow);
            if (!origStyle.equals(style)) {
                if (origStyle.isUserDefined()) {
                    // A clone of an user-defined. Put it directly after the user-defined
                    mList.add(editedRow, style);
                } else {
                    // Working on a clone of a builtin style
                    if (origStyle.isPreferred()) {
                        // Replace the original row with the new one
                        mList.set(editedRow, style);
                        // Make the new one preferred
                        style.setPreferred(true);
                        // And demote the original
                        origStyle.setPreferred(false);
                        mList.add(origStyle);
                    } else {
                        // Try to put it directly after original
                        mList.add(editedRow, style);
                    }
                }
            } else {
                mList.set(editedRow, style);
            }
        }

        // add to the db if the style is a new one.
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
