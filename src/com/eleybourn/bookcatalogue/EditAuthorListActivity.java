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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditAuthorDialog;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;

/**
 * Activity to edit a list of authors provided in an ArrayList<Author> and return an updated list.
 *
 * Calling point is a Book; see {@link EditAuthorDialog} for list
 *
 * @author Philip Warner
 */
public class EditAuthorListActivity extends EditObjectListActivity<Author> {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_EDIT_AUTHORS;

    /**
     * Constructor; pass the superclass the main and row based layouts to use.
     */
    public EditAuthorListActivity() {
        super(R.layout.activity_edit_list_author, R.layout.row_edit_author_list, UniqueId.BKEY_AUTHOR_ARRAY);
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(mBookTitle);

        // Setup AutoCompleteTextView for author name
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, mDb.getAuthorsFormattedName());
        ((AutoCompleteTextView) this.findViewById(R.id.author)).setAdapter(adapter);

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        Tracker.exitOnCreate(this);
    }

    /**
     * Do the work of the onClickListener for the 'Add' button.
     */
    protected void onAdd(final @NonNull View target) {
        AutoCompleteTextView authorField = findViewById(R.id.author);
        String authorName = authorField.getText().toString().trim();
        if (!authorName.isEmpty()) {
            // Get an author and try to find in DB.
            Author author = new Author(authorField.getText().toString().trim());
            author.id = mDb.getAuthorIdByName(author.familyName, author.givenNames);
            for (Author s : mList) {
                if (s.equals(author)) {
                    // Snackbar.make(target, R.string.author_already_in_list, Snackbar.LENGTH_LONG).show();
                    StandardDialogs.showUserMessage(this, R.string.warning_author_already_in_list);
                    return;
                }
            }
            mList.add(author);
            onListChanged();
            authorField.setText("");
        } else {
            //Snackbar.make(target, R.string.author_is_blank, Snackbar.LENGTH_LONG).show();
            StandardDialogs.showUserMessage(this, R.string.warning_required_author);
        }
    }

    /** TOMF: TODO: almost duplicate code in {@link EditAuthorDialog} */
    private void edit(final @NonNull Author author) {
        // Build the base dialog
        final View root = EditAuthorListActivity.this.getLayoutInflater().inflate(R.layout.dialog_edit_author, null);

        final EditText familyView = root.findViewById(R.id.family_name);
        //noinspection ConstantConditions
        familyView.setText(author.familyName);
        final EditText givenView = root.findViewById(R.id.given_names);
        //noinspection ConstantConditions
        givenView.setText(author.givenNames);

        final AlertDialog dialog = new AlertDialog.Builder(EditAuthorListActivity.this)
                .setView(root)
                .setTitle(R.string.title_edit_author)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newFamily = familyView.getText().toString().trim();
                if (newFamily.isEmpty()) {
                    StandardDialogs.showUserMessage(EditAuthorListActivity.this, R.string.warning_required_author);
                    return;
                }

                String newGiven = givenView.getText().toString().trim();
                Author newAuthor = new Author(newFamily, newGiven);
                dialog.dismiss();
                confirmEdit(author, newAuthor);
            }
        });
        //noinspection ConstantConditions
        root.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void confirmEdit(final @NonNull Author from, final @NonNull Author to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }

        // Get their id's
        from.id = mDb.getAuthorIdByName(from.familyName, from.givenNames); //TODO: this call is not needed I think
        to.id = mDb.getAuthorIdByName(to.familyName, to.givenNames);

        // See if the old author is used in any other books; allows us to skip a global replace
        long nRefs = mDb.countAuthorBooks(from) + mDb.countAuthorAnthologies(from);
        boolean fromHasOthers = nRefs > (mRowId == 0 ? 0 : 1);

        // author is the same (but maybe different case), or is only used in this book
        if (to.id == from.id || !fromHasOthers) {
            // Just update with the most recent spelling and format
            from.copyFrom(to);
            Utils.pruneList(mDb, mList);
            if (from.id == 0) {
                from.id = mDb.getAuthorIdByName(from.familyName, from.givenNames);
            }
            mDb.insertOrUpdateAuthor(from);
            onListChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old author is used in more than one place.
        String allBooks = getString(R.string.all_books);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.changed_author_how_apply, from.getSortName(), to.getSortName(), allBooks))
                .setTitle(R.string.title_scope_of_change)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.btn_this_book), new DialogInterface.OnClickListener() {
            public void onClick(final @NonNull DialogInterface dialog, final int which) {
                from.copyFrom(to);
                Utils.pruneList(mDb, mList);
                onListChanged();
                dialog.dismiss();
            }
        });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks, new DialogInterface.OnClickListener() {
            public void onClick(final @NonNull DialogInterface dialog, final int which) {
                mDb.globalReplaceAuthor(from, to);
                from.copyFrom(to);
                Utils.pruneList(mDb, mList);
                onListChanged();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    /**
     * Called when user clicks the 'Save' button.
     * @param data A newly created Intent to store output if necessary.
     *             Comes pre-populated with data.putExtra(mBKey, mList);
     *
     * @return <tt>true</tt>if activity should exit, false to abort exit.
     */
    @Override
    protected boolean onSave(final @NonNull Intent data) {
        final AutoCompleteTextView view = findViewById(R.id.author);
        String str = view.getText().toString().trim();
        if (str.isEmpty()) {
            // no current edit, so we're good to go
            return super.onSave(data);
        }

        StandardDialogs.showConfirmUnsavedEditsDialog(this,
                /* run when user clicks 'exit' */
                new Runnable() {
                    @Override
                    public void run() {
                        view.setText("");
                        findViewById(R.id.confirm).performClick();
                    }
                    /* if they click 'cancel', the dialog just closes without further actions */
                });
        return false;
    }

    protected SimpleListAdapter<Author> createListAdapter(final @LayoutRes int rowViewId, final @NonNull ArrayList<Author> list) {
        return new AuthorListAdapter(this,rowViewId,list);
    }

    protected class AuthorListAdapter extends SimpleListAdapter<Author> {

        AuthorListAdapter(final @NonNull Context context,
                          final @LayoutRes int rowViewId,
                          final @NonNull ArrayList<Author> items) {
            super(context, rowViewId, items);
        }

        @Override
        protected void onGetView(final @NonNull View convertView, final @NonNull Author author) {
            Holder holder = ViewTagger.getTag(convertView);
            if (holder == null) {
                // New view, so build the Holder
                holder = new Holder();
                holder.row_author = convertView.findViewById(R.id.row_author);
                holder.row_author_sort = convertView.findViewById(R.id.row_author_sort);
                // Tag the parts that need it
                ViewTagger.setTag(convertView, holder);
            }
            // Setup the variant fields in the holder
            if (holder.row_author != null) {
                holder.row_author.setText(author.getDisplayName());
            }
            if (holder.row_author_sort != null) {
                if (author.getDisplayName().equals(author.getSortName())) {
                    holder.row_author_sort.setVisibility(View.GONE);
                } else {
                    holder.row_author_sort.setVisibility(View.VISIBLE);
                    holder.row_author_sort.setText(author.getSortName());
                }
            }
        }

        /**
         * edit the Author we clicked on
         */
        @Override
        protected void onRowClick(final @NonNull View target,
                                  final @NonNull Author author,
                                  final int position) {
            edit(author);
        }

        /**
         * delegate to the ListView host
         */
        @Override
        protected void onListChanged() {
            super.onListChanged();
            EditAuthorListActivity.this.onListChanged();
        }
    }

    /**
     * Holder pattern for each row.
     */
    private class Holder {
        TextView row_author;
        TextView row_author_sort;
    }
}
