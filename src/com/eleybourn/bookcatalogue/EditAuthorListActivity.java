/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.utils.Utils;


/**
 * Activity to edit a list of authors provided in an ArrayList<Author> and
 * return an updated list.
 *
 * @author Philip Warner
 */
public class EditAuthorListActivity extends EditObjectListActivity<Author> {

    /**
     * Constructor; pass the superclass the main and row based layouts to use.
     */
    public EditAuthorListActivity() {
        super(UniqueId.BKEY_AUTHOR_ARRAY, R.layout.activity_edit_list_author, R.layout.row_edit_author_list);
    }

    @Override
    protected void onSetupView(@NonNull final View target, @NonNull final Author object) {
        TextView at = target.findViewById(R.id.row_author);
        if (at != null) {
            at.setText(object.getDisplayName());
        }
        at = target.findViewById(R.id.row_author_sort);
        if (at != null) {
            at.setText(object.getSortName());
        }
    }

    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setTitle(mBookTitle);

        try {
            // Setup autocomplete for author name
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line,
                    mDb.getAuthors());
            ((AutoCompleteTextView) this.findViewById(R.id.author)).setAdapter(adapter);

            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    /**
     * Do the work of the onClickListener for the 'Add' button.
     */
    protected void onAdd(final View target) {
        AutoCompleteTextView authorField = findViewById(R.id.author);
        String authorName = authorField.getText().toString().trim();
        if (!authorName.isEmpty()) {
            // Get an author and try to find in DB.
            Author author = new Author(authorField.getText().toString().trim());
            author.id = mDb.getAuthorIdByName(author.familyName, author.givenNames);
            for (Author s : mList) {
                if (s.equals(author)) {
                    Toast.makeText(this, getResources().getString(R.string.author_already_in_list), Toast.LENGTH_LONG).show();
                    return;
                }
            }
            mList.add(author);
            mAdapter.notifyDataSetChanged();
            authorField.setText("");
        } else {
            Toast.makeText(this, getResources().getString(R.string.author_is_blank), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onRowClick(@NonNull final View target, @NonNull final Author author, final int position) {
        final Dialog dialog = new StandardDialogs.BasicDialog(this);
        dialog.setContentView(R.layout.dialog_edit_author);
        dialog.setTitle(R.string.edit_author_details);

        EditText familyView = dialog.findViewById(R.id.family_name);
        familyView.setText(author.familyName);
        EditText givenView = dialog.findViewById(R.id.given_names);
        givenView.setText(author.givenNames);

        Button saveButton = dialog.findViewById(R.id.confirm);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText familyView = dialog.findViewById(R.id.family_name);
                String newFamily = familyView.getText().toString().trim();
                if (newFamily.isEmpty()) {
                    Toast.makeText(EditAuthorListActivity.this, R.string.author_is_blank, Toast.LENGTH_LONG).show();
                    return;
                }

                EditText givenView = dialog.findViewById(R.id.given_names);
                String newGiven = givenView.getText().toString().trim();
                Author newAuthor = new Author(newFamily, newGiven);
                dialog.dismiss();
                confirmEdit(author, newAuthor);
            }
        });
        Button cancelButton = dialog.findViewById(R.id.cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void confirmEdit(@NonNull final Author from, @NonNull final Author to) {

        if (to.equals(from)) {
            return;
        }

        // Get the new author ID
        from.id = mDb.getAuthorIdByName(from.familyName, from.givenNames);
        to.id = mDb.getAuthorIdByName(to.familyName, to.givenNames);

        // See if the old author is used in any other books.
        long nRefs = mDb.countAuthorBooks(from) + mDb.countAuthorAnthologies(from);
        boolean oldHasOthers = nRefs > (mRowId == 0 ? 0 : 1);

        // Case: author is the same, or is only used in this book
        if (to.id == from.id || !oldHasOthers) {
            // Just update with the most recent spelling and format
            from.copyFrom(to);
            Utils.pruneList(mDb, mList);
            mDb.updateOrInsertAuthorByName(from);
            mAdapter.notifyDataSetChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old author is used in more than one place.
        String format = getResources().getString(R.string.changed_author_how_apply);
        String allBooks = getResources().getString(R.string.all_books);
        String thisBook = getResources().getString(R.string.this_book);
        String message = String.format(format, from.getSortName(), to.getSortName(), allBooks);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(message)
                .setTitle(getResources().getString(R.string.scope_of_change))
                .setIcon(R.drawable.ic_info_outline)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, thisBook, new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, final int which) {
                from.copyFrom(to);
                Utils.pruneList(mDb, mList);
                mAdapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks, new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, final int which) {
                mDb.globalReplaceAuthor(from, to);
                from.copyFrom(to);
                Utils.pruneList(mDb, mList);
                mAdapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    @Override
    protected boolean onSave(@NonNull final Intent intent) {
        final AutoCompleteTextView textView = findViewById(R.id.author);
        Resources res = this.getResources();
        String str = textView.getText().toString().trim();
        if (str.isEmpty()) {
            return true;
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(res.getText(R.string.unsaved_edits))
                .setTitle(res.getText(R.string.unsaved_edits_title))
                .setIcon(R.drawable.ic_info_outline)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, res.getText(R.string.yes), new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, final int which) {
                textView.setText("");
                findViewById(R.id.confirm).performClick();
            }
        });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, res.getText(R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, final int which) {
                //do nothing
            }
        });

        dialog.show();
        return false;
    }
}
