/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.baseactivity.EditObjectListActivity;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Activity to edit a list of authors provided in an {@code ArrayList<Author>}
 * and return an updated list.
 * <p>
 * Calling point is a Book; see {@link EditBookAuthorDialogFragment} for list
 * <p>
 * TODO: setting the TYPE of Author is still WIP.
 * You can edit the type in the edit-author dialog, but you cannot set it when adding
 * an author to the list.
 */
public class EditBookAuthorsActivity
        extends EditObjectListActivity<Author> {

    /**
     * Constructor.
     */
    public EditBookAuthorsActivity() {
        super(UniqueId.BKEY_AUTHOR_ARRAY);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_list_author;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_edit_book_authors);

        mAutoCompleteAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                                   mModel.getDb()
                                         .getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED));

        mAutoCompleteTextView = findViewById(R.id.author);
        mAutoCompleteTextView.setAdapter(mAutoCompleteAdapter);
    }

    @Override
    protected RecyclerViewAdapterBase
    createListAdapter(@NonNull final ArrayList<Author> list,
                      @NonNull final StartDragListener dragStartListener) {
        return new AuthorListAdapter(this, list, dragStartListener);
    }

    @Override
    protected void onAdd(@NonNull final View target) {
        String name = mAutoCompleteTextView.getText().toString().trim();
        if (name.isEmpty()) {
            UserMessage.show(mAutoCompleteTextView, R.string.warning_missing_name);
            return;
        }

        Author newAuthor = Author.fromString(name);
        // see if it already exists
        newAuthor.fixId(this, mModel.getDb(), Locale.getDefault());
        // and check it's not already in the list.
        if (mList.contains(newAuthor)) {
            UserMessage.show(mAutoCompleteTextView, R.string.warning_author_already_in_list);
            return;
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newAuthor);
        mListAdapter.notifyDataSetChanged();

        // and clear the form for next entry.
        mAutoCompleteTextView.setText("");
    }

    @Override
    protected void processChanges(@NonNull final Author author,
                                  @NonNull final Author newAuthorData) {

        // anything other than the type changed ?
        if (author.getFamilyName().equals(newAuthorData.getFamilyName())
            && author.getGivenNames().equals(newAuthorData.getGivenNames())
            && author.isComplete() == newAuthorData.isComplete()) {

            // Type is not part of the Author table, but of the book_author table.
            if (author.getType() != newAuthorData.getType()) {
                author.setType(newAuthorData.getType());
                ItemWithFixableId
                        .pruneList(mList, this, mModel.getDb(), Locale.getDefault(), false);
                mListAdapter.notifyDataSetChanged();
            }
            // nothing or only the type was different, so we're done here.
            return;
        }

        // See if the old one is used by any other books.
        long nrOfReferences = mModel.getDb().countBooksByAuthor(this, author)
                              + mModel.getDb().countTocEntryByAuthor(this, author);

        // if it's not, we simply re-use the old object.
        if (mModel.isSingleUsage(nrOfReferences)) {
            /*
             * Use the original Author object, but update its fields
             *
             * see below and {@link DAO#insertBookDependents} where an *insert* will be done
             * The 'old' Author will be orphaned. TODO: simplify / don't orphan?
             */
            updateItem(author, newAuthorData, Locale.getDefault());
            return;
        }

        // At this point, we know the names are genuinely different and the old Author
        // is used in more than one place. Ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.bookshelf_all_books);
        String message = getString(R.string.confirm_apply_author_changed,
                                   author.getSorting(this),
                                   newAuthorData.getSorting(this),
                                   allBooks);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.title_scope_of_change)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .create();

        /*
         * Choosing 'all books':
         * globalReplace:
         * - Find/update or insert the new Author.
         * - update the TOC of all books so they use the new author id.
         * - update TBL_BOOK_AUTHORS for all books to use the new author id
         * - re-order the 'position' if needed.
         * Result:
         * - all books previously using the olf author, now point to the new author.
         * - the old author will still exist, but won't be in use.
         *
         * WARNING: if the given-name is/was empty, the replace might have failed.
         * Solution: make a change to the family name, do replace, change family name back,
         * do replace.
         *
         * Copy the data fields (name,..) from the holder to the 'old' author.
         * and remove any duplicates.
         *
         * When the actual book is saved, {@link DAO#updateBook} will call
         * {@link DAO#insertBookDependents} which when updating TBL_BOOK_AUTHORS
         * will first try and find the author (with the old id) based on name.
         * => it will find the NEW author, and update the id in memory (old becomes new)
         * Result:
         * - this book uses the new author (by recycling the old object with all new id/data)
         *
         * TODO: speculate if this can be simplified.
         */
        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, allBooks, (d, which) -> {
            mModel.setGlobalReplacementsMade(
                    mModel.getDb().globalReplace(this, author, newAuthorData));
            updateItem(author, newAuthorData, Locale.getDefault());
        });

        /*
         * choosing 'this book':
         * Copy the data fields (name,..) from the holder to the 'old' author.
         * and remove any duplicates.
         *
         * When the actual book is saved, {@link DAO#updateBook} will call
         * {@link DAO#insertBookDependents} which when updating TBL_BOOK_AUTHORS
         * will first try and find the author based on name.
         * If its names differ -> new Author -> inserts the new author.
         * Result: *this* book now uses the modified/new author,
         * while all others keep using the original one.
         *
         * TODO: speculate if it would not be easier to:
         * - fixup(newAuthor) and  if id == 0 insert newAuthor
         * - remove old author from book
         * - add new author to book
         */
        dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                         getString(R.string.btn_this_book), (d, which) ->
                                 updateItem(author, newAuthorData, Locale.getDefault()));

        dialog.show();
    }

    @Override
    protected void updateItem(@NonNull final Author author,
                              @NonNull final Author newAuthorData,
                              @NonNull final Locale fallbackLocale) {
        // make the book related field changes
        author.copyFrom(newAuthorData, true);
        ItemWithFixableId.pruneList(mList, this, mModel.getDb(), fallbackLocale, false);
        mListAdapter.notifyDataSetChanged();
    }

    /**
     * Edit an Author from the list.
     * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
     * <p>
     * Must be a public static class to be properly recreated from instance state.
     */
    public static class EditBookAuthorDialogFragment
            extends DialogFragment {

        /** Fragment manager tag. */
        static final String TAG = "EditBookAuthorDialogFragment";

        /** Database Access. */
        protected DAO mDb;
        WeakReference<BookChangedListener> mBookChangedListener;
        private EditBookAuthorsActivity mHostActivity;
        private AutoCompleteTextView mFamilyNameView;
        private AutoCompleteTextView mGivenNamesView;
        private Checkable mIsCompleteView;

        /** Enable or disable the type buttons. */
        private CompoundButton mUseTypeBtn;
        /** Key: type. */
        @SuppressLint("UseSparseArrays")
        private final Map<Integer, CompoundButton> mTypeButtons = new HashMap<>();

        /** The Author we're editing. */
        private Author mAuthor;
        /** Current edit. */
        private String mFamilyName;
        /** Current edit. */
        private String mGivenNames;
        /** Current edit. */
        private boolean mIsComplete;
        /** Current edit. */
        private int mType;

        private boolean mAuthorTypeIsUsed;

        /**
         * Constructor.
         *
         * @param author to edit.
         *
         * @return the instance
         */
        static EditBookAuthorDialogFragment newInstance(@NonNull final Author author) {
            EditBookAuthorDialogFragment frag = new EditBookAuthorDialogFragment();
            Bundle args = new Bundle();
            args.putParcelable(DBDefinitions.KEY_FK_AUTHOR, author);
            frag.setArguments(args);
            return frag;
        }

        protected int getLayoutId() {
            return R.layout.dialog_edit_book_author;
        }

        @Override
        public void onAttach(@NonNull final Context context) {
            super.onAttach(context);
            mHostActivity = (EditBookAuthorsActivity) context;
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mDb = new DAO();

            mAuthorTypeIsUsed = App.isUsed(DBDefinitions.KEY_AUTHOR_TYPE);

            Bundle args = requireArguments();
            mAuthor = Objects.requireNonNull(args.getParcelable(DBDefinitions.KEY_FK_AUTHOR),
                                             "Author must be passed in args");
            if (savedInstanceState == null) {
                mFamilyName = mAuthor.getFamilyName();
                mGivenNames = mAuthor.getGivenNames();
                mIsComplete = mAuthor.isComplete();
                mType = mAuthor.getType();
            } else {
                mFamilyName = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
                mGivenNames = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
                mIsComplete = savedInstanceState.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
                mType = savedInstanceState.getInt(DBDefinitions.KEY_AUTHOR_TYPE);
            }
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            // Reminder: *always* use the activity inflater here.
            //noinspection ConstantConditions
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            View root = layoutInflater.inflate(getLayoutId(), null);

            Context context = getContext();

            @SuppressWarnings("ConstantConditions")
            ArrayAdapter<String> mFamilyNameAdapter =
                    new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                                       mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FAMILY_NAME));
            ArrayAdapter<String> mGivenNameAdapter =
                    new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                                       mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES));

            // the dialog fields != screen fields.
            mFamilyNameView = root.findViewById(R.id.family_name);
            mFamilyNameView.setText(mFamilyName);
            mFamilyNameView.setAdapter(mFamilyNameAdapter);

            mGivenNamesView = root.findViewById(R.id.given_names);
            mGivenNamesView.setText(mGivenNames);
            mGivenNamesView.setAdapter(mGivenNameAdapter);

            mIsCompleteView = root.findViewById(R.id.cbx_is_complete);
            mIsCompleteView.setChecked(mIsComplete);

            mTypeButtons.put(Author.TYPE_WRITER,
                             root.findViewById(R.id.cbx_author_type_writer));
            mTypeButtons.put(Author.TYPE_CONTRIBUTOR,
                             root.findViewById(R.id.cbx_author_type_contributor));
            mTypeButtons.put(Author.TYPE_INTRODUCTION,
                             root.findViewById(R.id.cbx_author_type_intro));
            mTypeButtons.put(Author.TYPE_TRANSLATOR,
                             root.findViewById(R.id.cbx_author_type_translator));
            mTypeButtons.put(Author.TYPE_EDITOR,
                             root.findViewById(R.id.cbx_author_type_editor));
            mTypeButtons.put(Author.TYPE_ARTIST,
                             root.findViewById(R.id.cbx_author_type_artist));
            mTypeButtons.put(Author.TYPE_COLORIST,
                             root.findViewById(R.id.cbx_author_type_colorist));
            mTypeButtons.put(Author.TYPE_COVER_ARTIST,
                             root.findViewById(R.id.cbx_author_type_cover_artist));
            mTypeButtons.put(Author.TYPE_COVER_COLORIST,
                             root.findViewById(R.id.cbx_author_type_cover_colorist));

            mUseTypeBtn = root.findViewById(R.id.use_author_type_button);
            mUseTypeBtn.setOnCheckedChangeListener((v, isChecked) -> setTypeEnabled(isChecked));

            Group authorTypeGroup = root.findViewById(R.id.author_type_group);
            authorTypeGroup.setVisibility(mAuthorTypeIsUsed ? View.VISIBLE : View.GONE);

            if (mType != Author.TYPE_UNKNOWN) {
                setTypeEnabled(true);
                for (Map.Entry<Integer, CompoundButton> entry : mTypeButtons.entrySet()) {
                    entry.getValue().setChecked((mType & entry.getKey()) != 0);
                }
            } else {
                setTypeEnabled(false);
            }

            return new AlertDialog.Builder(context)
                    .setIcon(R.drawable.ic_edit)
                    .setView(root)
                    .setTitle(R.string.title_edit_author)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setPositiveButton(R.string.btn_confirm_save, (d, which) -> {
                        mFamilyName = mFamilyNameView.getText().toString().trim();
                        if (mFamilyName.isEmpty()) {
                            UserMessage.show(mFamilyNameView, R.string.warning_missing_name);
                            return;
                        }

                        mGivenNames = mGivenNamesView.getText().toString().trim();
                        mIsComplete = mIsCompleteView.isChecked();
                        mType = getTypeFromViews();

                        // Create a new Author as a holder for the changes.
                        Author newAuthorData = new Author(mFamilyName, mGivenNames, mIsComplete);
                        if (mUseTypeBtn.isChecked()) {
                            newAuthorData.setType(mType);
                        }
                        mHostActivity.processChanges(mAuthor, newAuthorData);
                    })
                    .create();
        }

        /**
         * Call this from {@link #onAttachFragment} in the parent.
         *
         * @param listener the object to send the result to.
         */
        public void setListener(@NonNull final BookChangedListener listener) {
            mBookChangedListener = new WeakReference<>(listener);
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME, mFamilyName);
            outState.putString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES, mGivenNames);
            outState.putBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE, mIsComplete);
            outState.putInt(DBDefinitions.KEY_AUTHOR_TYPE, mType);
        }

        @Override
        public void onPause() {
            mFamilyName = mFamilyNameView.getText().toString().trim();
            mGivenNames = mGivenNamesView.getText().toString().trim();
            mIsComplete = mIsCompleteView.isChecked();
            mType = getTypeFromViews();
            super.onPause();
        }

        @Override
        public void onDestroy() {
            if (mDb != null) {
                mDb.close();
            }
            super.onDestroy();
        }

        private void setTypeEnabled(final boolean enable) {
            // don't bother changing the 'checked' status, we'll ignore them anyhow.
            // and this is more user friendly if they flip the switch more than once.
            mUseTypeBtn.setChecked(enable);
            for (CompoundButton typeBtn : mTypeButtons.values()) {
                typeBtn.setEnabled(enable);
            }
        }

        private int getTypeFromViews() {
            int authorType = Author.TYPE_UNKNOWN;
            for (Integer type : mTypeButtons.keySet()) {
                //noinspection ConstantConditions
                if (mTypeButtons.get(type).isChecked()) {
                    authorType |= type;
                }
            }
            return authorType;
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final TextView authorView;
        @NonNull
        final TextView authorSortView;
        @NonNull
        final TextView authorTypeView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            authorView = itemView.findViewById(R.id.row_author);
            authorSortView = itemView.findViewById(R.id.row_author_sort);
            authorTypeView = itemView.findViewById(R.id.row_author_type);

            if (!App.isUsed(DBDefinitions.KEY_AUTHOR_TYPE)) {
                authorTypeView.setVisibility(View.GONE);
            }
        }
    }


    protected class AuthorListAdapter
            extends RecyclerViewAdapterBase<Author, Holder> {

        private final boolean mAuthorTypeIsUsed;

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of Authors
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        AuthorListAdapter(@NonNull final Context context,
                          @NonNull final ArrayList<Author> items,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);

            mAuthorTypeIsUsed = App.isUsed(DBDefinitions.KEY_AUTHOR_TYPE);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_author_list, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Context context = getContext();

            final Author author = getItem(position);
            String authorLabel = author.getLabel(context);

            holder.authorView.setText(authorLabel);

            if (!authorLabel.equals(author.getSorting(context))) {
                holder.authorSortView.setVisibility(View.VISIBLE);
                holder.authorSortView.setText(author.getSorting(context));
            } else {
                holder.authorSortView.setVisibility(View.GONE);
            }

            if (mAuthorTypeIsUsed && author.getType() != Author.TYPE_UNKNOWN) {
                holder.authorTypeView.setText(author.getTypeLabels(context));
                holder.authorTypeView.setVisibility(View.VISIBLE);
            } else {
                holder.authorTypeView.setVisibility(View.GONE);
            }

            // click -> edit
            holder.rowDetailsView.setOnClickListener(v -> {
                FragmentManager fm = getSupportFragmentManager();
                if (fm.findFragmentByTag(EditBookAuthorDialogFragment.TAG) == null) {
                    EditBookAuthorDialogFragment.newInstance(author)
                                                .show(fm, EditBookAuthorDialogFragment.TAG);
                }
            });
        }
    }
}
