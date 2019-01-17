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
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

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
 * <p>
 * Calling point is a Book; see {@link EditAuthorDialog} for list
 *
 * @author Philip Warner
 */
public class EditAuthorListActivity
        extends EditObjectListActivity<Author> {

    /**
     * Constructor; pass the superclass the main and row based layouts to use.
     */
    public EditAuthorListActivity() {
        super(R.layout.activity_edit_list_author, R.layout.row_edit_author_list,
              UniqueId.BKEY_AUTHOR_ARRAY);
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(mBookTitle);

        // Setup AutoCompleteTextView for author name
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this,
                                   android.R.layout.simple_dropdown_item_1line,
                                   mDb.getAuthorsFormattedName());
        ((AutoCompleteTextView) this.findViewById(R.id.author)).setAdapter(adapter);

        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        Tracker.exitOnCreate(this);
    }

    /**
     * Do the work of the onClickListener for the 'Add' button.
     */
    protected void onAdd(@NonNull final View target) {
        AutoCompleteTextView authorField = findViewById(R.id.author);
        String authorName = authorField.getText().toString().trim();
        if (!authorName.isEmpty()) {
            // Get an author and try to find in DB.
            Author author = new Author(authorField.getText().toString().trim());
            author.id = mDb.getAuthorIdByName(author.getFamilyName(), author.getGivenNames());
            for (Author s : mList) {
                if (s.equals(author)) {
                    // Snackbar.make(target, R.string.author_already_in_list,
                    // Snackbar.LENGTH_LONG).show();
                    StandardDialogs.showUserMessage(this,
                                                    R.string.warning_author_already_in_list);
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

    /** TODO: almost duplicate code in {@link EditAuthorDialog}. */
    private void edit(@NonNull final Author author) {
        // Build the base dialog
        final View root = EditAuthorListActivity.this.getLayoutInflater().inflate(
                R.layout.dialog_edit_author, null);

        final EditText familyView = root.findViewById(R.id.family_name);
        //noinspection ConstantConditions
        familyView.setText(author.getFamilyName());
        final EditText givenView = root.findViewById(R.id.given_names);
        //noinspection ConstantConditions
        givenView.setText(author.getGivenNames());

        final AlertDialog dialog = new AlertDialog.Builder(EditAuthorListActivity.this)
                .setView(root)
                .setTitle(R.string.title_edit_author)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newFamily = familyView.getText().toString().trim();
                if (newFamily.isEmpty()) {
                    StandardDialogs.showUserMessage(EditAuthorListActivity.this,
                                                    R.string.warning_required_author);
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
            public void onClick(@NonNull final View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void confirmEdit(@NonNull final Author from,
                             @NonNull final Author to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }

        // Get their id's FIXME: this call is not needed I think
        from.id = mDb.getAuthorIdByName(from.getFamilyName(), from.getGivenNames());
        to.id = mDb.getAuthorIdByName(to.getFamilyName(), to.getGivenNames());

        // See if the old author is used in any other books; allows us to skip a global replace
        long nRefs = mDb.countAuthorBooks(from) + mDb.countAuthorAnthologies(from);
        boolean fromHasOthers = nRefs > (mRowId == 0 ? 0 : 1);

        // author is the same (but maybe different case), or is only used in this book
        if (to.id == from.id || !fromHasOthers) {
            // Just update with the most recent spelling and format
            from.copyFrom(to);
            Utils.pruneList(mDb, mList);
            if (from.id == 0) {
                from.id = mDb.getAuthorIdByName(from.getFamilyName(), from.getGivenNames());
            }
            mDb.insertOrUpdateAuthor(from);
            onListChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old author
        // is used in more than one place.
        String allBooks = getString(R.string.all_books);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(
                        getString(R.string.changed_author_how_apply, from.getSortName(),
                                  to.getSortName(),
                                  allBooks))
                .setTitle(R.string.title_scope_of_change)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.btn_this_book),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 from.copyFrom(to);
                                 Utils.pruneList(mDb, mList);
                                 onListChanged();
                                 dialog.dismiss();
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks,
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
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
     *
     * @param data A newly created Intent to store output if necessary.
     *             Comes pre-populated with data.putExtra(mBKey, mList);
     *
     * @return <tt>true</tt> if activity should exit, <tt>false</tt> to abort exit.
     */
    @Override
    protected boolean onSave(@NonNull final Intent data) {
        final AutoCompleteTextView view = findViewById(R.id.author);
        String str = view.getText().toString().trim();
        if (str.isEmpty()) {
            // no current edit, so we're good to go
            return super.onSave(data);
        }

        StandardDialogs.showConfirmUnsavedEditsDialog(
                this,
                /* run when user clicks 'exit' */
                new Runnable() {
                    @Override
                    public void run() {
                        view.setText("");
                        findViewById(R.id.confirm).performClick();
                    }
                });
        return false;
    }

    protected SimpleListAdapter<Author> createListAdapter(@LayoutRes final int rowLayoutId,
                                                          @NonNull final ArrayList<Author> list) {
        return new AuthorListAdapter(this, rowLayoutId, list);
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder {

        TextView rowAuthor;
        TextView rowAuthorSort;
    }

    protected class AuthorListAdapter
            extends SimpleListAdapter<Author> {

        AuthorListAdapter(@NonNull final Context context,
                          @LayoutRes final int rowLayoutId,
                          @NonNull final ArrayList<Author> items) {
            super(context, rowLayoutId, items);
        }

        @Override
        protected void onGetView(@NonNull final View convertView,
                                 @NonNull final Author item) {
            Holder holder = ViewTagger.getTag(convertView);
            if (holder == null) {
                // New view, so build the Holder
                holder = new Holder();
                holder.rowAuthor = convertView.findViewById(R.id.row_author);
                holder.rowAuthorSort = convertView.findViewById(R.id.row_author_sort);
                // Tag the parts that need it
                ViewTagger.setTag(convertView, holder);
            }
            // Setup the variant fields in the holder.
            if (holder.rowAuthor != null) {
                holder.rowAuthor.setText(item.getDisplayName());
            }
            if (holder.rowAuthorSort != null) {
                if (item.getDisplayName().equals(item.getSortName())) {
                    holder.rowAuthorSort.setVisibility(View.GONE);
                } else {
                    holder.rowAuthorSort.setVisibility(View.VISIBLE);
                    holder.rowAuthorSort.setText(item.getSortName());
                }
            }
        }

        /**
         * edit the Author we clicked on.
         */
        @Override
        protected void onRowClick(@NonNull final View target,
                                  @NonNull final Author item,
                                  final int position) {
            edit(item);
        }

        /**
         * delegate to the ListView host.
         */
        @Override
        protected void onListChanged() {
            EditAuthorListActivity.this.onListChanged();
        }
    }
}
