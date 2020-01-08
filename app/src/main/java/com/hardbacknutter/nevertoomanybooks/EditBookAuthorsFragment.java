/*
 * @Copyright 2020 HardBackNutter
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

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Edit the list of Authors of a Book.
 * <p>
 * You can edit the type in the edit-author dialog, but you cannot yet set it when adding
 * an author to the list.
 */
public class EditBookAuthorsFragment
        extends EditBookBaseFragment {

    /** Log tag. */
    public static final String TAG = "EditBookAuthorsFragment";

    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mBookModel.setDirty(true);
                }
            };

    /** Author name field. */
    private AutoCompleteTextView mAuthorNameView;
    /** The View for the list. */
    private RecyclerView mListView;
    /** the rows. */
    private ArrayList<Author> mList;
    /** The adapter for the list itself. */
    private RecyclerViewAdapterBase mListAdapter;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_list_author, container, false);
        mListView = view.findViewById(android.R.id.list);
        mAuthorNameView = view.findViewById(R.id.author);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //URGENT: this is temporary.. until it's decided what UI to go for.
        //noinspection ConstantConditions
        getActivity().findViewById(R.id.tab_panel).setVisibility(View.GONE);

        List<String> names = mBookModel.getDb().getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED);
        //noinspection ConstantConditions
        DiacriticArrayAdapter<String> nameAdapter = new DiacriticArrayAdapter<>(
                getContext(), android.R.layout.simple_dropdown_item_1line, names);
        mAuthorNameView.setAdapter(nameAdapter);

        // set up the list view. The adapter is setup in onLoadFields
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mListView.setLayoutManager(layoutManager);
        mListView.setHasFixedSize(true);

        // adding a new entry
        //noinspection ConstantConditions
        getView().findViewById(R.id.btn_add).setOnClickListener(v -> onAdd());
    }

    @Override
    protected void onLoadFields(@NonNull final Book book) {
        super.onLoadFields(book);

        mList = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);

        //noinspection ConstantConditions
        mListAdapter = new AuthorListAdapter(getContext(), mList,
                                             vh -> mItemTouchHelper.startDrag(vh));
        mListView.setAdapter(mListAdapter);
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);
    }

    @Override
    public void onSaveFields(@NonNull final Book book) {
        super.onSaveFields(book);

        // The list is not a 'real' field. Hence the need to store it manually here.
        book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mList);
    }

    private void onAdd() {
        String name = mAuthorNameView.getText().toString().trim();
        if (name.isEmpty()) {
            Snackbar.make(mAuthorNameView, R.string.warning_missing_name,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        Author newAuthor = Author.fromString(name);

        // see if it already exists
        //noinspection ConstantConditions
        newAuthor.fixId(getContext(), mBookModel.getDb(), Locale.getDefault());
        // and check it's not already in the list.
        if (mList.contains(newAuthor)) {
            Snackbar.make(mAuthorNameView, R.string.warning_author_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            return;
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newAuthor);
        mListAdapter.notifyDataSetChanged();

        // and clear the form for next entry.
        mAuthorNameView.setText("");
    }

    /**
     * Process the modified (if any) data.
     *
     * @param author  the user was editing (with the original data)
     * @param tmpData the modifications the user made in a placeholder object.
     *                Non-modified data was copied here as well.
     *                The id==0.
     */
    private void processChanges(@NonNull final Author author,
                                @NonNull final Author tmpData) {

        // name not changed ?
        if (author.getFamilyName().equals(tmpData.getFamilyName())
            && author.getGivenNames().equals(tmpData.getGivenNames())) {

            // copy the completion state, we don't have to warn/ask the user about it.
            author.setComplete(tmpData.isComplete());

            // Type is not part of the Author table, but of the book_author table.
            if (author.getType() != tmpData.getType()) {
                // so if the type is different, just update it
                author.setType(tmpData.getType());
                //noinspection ConstantConditions
                ItemWithFixableId.pruneList(mList, getContext(), mBookModel.getDb(),
                                            Locale.getDefault(), false);
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }

        // The name of the Author was modified.
        // Check if it's used by any other books.
        //noinspection ConstantConditions
        if (mBookModel.isSingleUsage(getContext(), author)) {
            // If it's not, we can simply modify the original and we're done here.
            // There is no need to consult the user.
            // Copy the new data into the original Author object that the user was changing.
            author.copyFrom(tmpData, true);
            //noinspection ConstantConditions
            ItemWithFixableId.pruneList(mList, getContext(), mBookModel.getDb(),
                                        Locale.getDefault(), false);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, we know the name of the Author was modified
        // and the it's used in more than one place.
        // We need to ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.bookshelf_all_books);
        String message = getString(R.string.confirm_apply_author_changed,
                                   author.getSorting(getContext()),
                                   tmpData.getSorting(getContext()),
                                   allBooks);
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.title_scope_of_change)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(allBooks, (d, which) -> {
                    // copy all new data
                    author.copyFrom(tmpData, true);
                    // This change is done in the database right NOW!
                    int rowsAffected = mBookModel.getDb().updateAuthor(getContext(), author);
                    if (rowsAffected == 1) {
                        ItemWithFixableId.pruneList(mList, getContext(), mBookModel.getDb(),
                                                    Locale.getDefault(), false);
                        mBookModel.getBook().refreshAuthorList(getContext(), mBookModel.getDb());
                        mListAdapter.notifyDataSetChanged();

                    } else {
                        Logger.warnWithStackTrace(getContext(), TAG, "Could not update",
                                                  "author=" + author,
                                                  "tmpAuthor=" + tmpData);
                        new AlertDialog.Builder(getContext())
                                .setIconAttribute(android.R.attr.alertDialogIcon)
                                .setMessage(R.string.error_unexpected_error)
                                .show();
                    }
                })
                .setPositiveButton(R.string.btn_this_book, (d, which) -> {
                    // treat the new data as a new Author; save it so we have a valid id.
                    // Note that if the user abandons the entire book edit,
                    // we will orphan this new author. That's ok, it will get
                    // garbage collected from the database sooner or later.
                    mBookModel.getDb().updateOrInsertAuthor(getContext(), tmpData);

                    // unlink the old one (and unmodified), and link with the new one
                    // book/author positions will be fixed up when saving.
                    // Note that the old one *might* be orphaned at this time.
                    // Same remark as above.
                    mList.remove(author);
                    mList.add(tmpData);
                    ItemWithFixableId.pruneList(mList, getContext(), mBookModel.getDb(),
                                                Locale.getDefault(), false);

                    //URGENT: right now, the Book gets the updated author(s), but the TocEntries
                    // remain using the old Author(s)
                    // .
                    // A TocEntry is unique based on author and title_od.
                    // Updating the in-memory TOC list and/or the TocEntries stored in the database
                    // with the new author:
                    // .
                    // The problem is two-fold:
                    // If we simply create a new TocEntry?
                    // - old one not used anywhere else ? ok, just delete it
                    // - old one present in other books ? replace ? leave as-is ?
                    // but it's the SAME story (text), now existing with two different authors.
                    // - update the TocEntry as-is... i.e. in the database?
                    // .. more headaches....
                    // .
                    // SOLUTION: just ASK the user with a "mod toc" or "no"

                    mListAdapter.notifyDataSetChanged();
                })
                .create()
                .show();
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

    /**
     * Edit an Author from the list.
     * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
     * <p>
     * Must be a public static class to be properly recreated from instance state.
     */
    public static class EditBookAuthorDialogFragment
            extends DialogFragment {

        /** Log tag. */
        static final String TAG = "EditBookAuthorDialogFrag";
        /** Key: type. */
        private final SparseArray<CompoundButton> mTypeButtons = new SparseArray<>();

        private AutoCompleteTextView mFamilyNameView;
        private AutoCompleteTextView mGivenNamesView;
        private Checkable mIsCompleteView;
        /** Enable or disable the type buttons. */
        private CompoundButton mUseTypeBtn;
        /** The Author we're editing. */
        private Author mAuthor;
        /** Current edit. */
        private String mFamilyName;
        /** Current edit. */
        private String mGivenNames;
        /** Current edit. */
        private boolean mIsComplete;
        /** Current edit. */
        @Author.Type
        private int mType;

        /** Database Access. */
        private DAO mDb;

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mDb = new DAO(TAG);

            Bundle args = requireArguments();
            mAuthor = args.getParcelable(DBDefinitions.KEY_FK_AUTHOR);
            Objects.requireNonNull(mAuthor, "Author must be passed in args");

            if (savedInstanceState == null) {
                mFamilyName = mAuthor.getFamilyName();
                mGivenNames = mAuthor.getGivenNames();
                mIsComplete = mAuthor.isComplete();
                mType = mAuthor.getType();
            } else {
                mFamilyName = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
                mGivenNames = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
                mIsComplete = savedInstanceState.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE,
                                                            false);
                mType = savedInstanceState.getInt(DBDefinitions.KEY_AUTHOR_TYPE);
            }
        }

        @Override
        public void onDestroy() {
            if (mDb != null) {
                mDb.close();
            }
            super.onDestroy();
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

            Objects.requireNonNull(getTargetFragment(), "no target fragment set");

            // Reminder: *always* use the activity inflater here.
            //noinspection ConstantConditions
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            View root = layoutInflater.inflate(R.layout.dialog_edit_book_author, null);


            @SuppressWarnings("ConstantConditions")
            DiacriticArrayAdapter<String> mFamilyNameAdapter = new DiacriticArrayAdapter<>(
                    getContext(), android.R.layout.simple_dropdown_item_1line,
                    mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FAMILY_NAME));
            DiacriticArrayAdapter<String> mGivenNameAdapter = new DiacriticArrayAdapter<>(
                    getContext(), android.R.layout.simple_dropdown_item_1line,
                    mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES));

            // the dialog fields != screen fields.
            mFamilyNameView = root.findViewById(R.id.family_name);
            mFamilyNameView.setText(mFamilyName);
            mFamilyNameView.setAdapter(mFamilyNameAdapter);

            mGivenNamesView = root.findViewById(R.id.given_names);
            mGivenNamesView.setText(mGivenNames);
            mGivenNamesView.setAdapter(mGivenNameAdapter);

            mIsCompleteView = root.findViewById(R.id.cbx_is_complete);
            if (mIsCompleteView != null) {
                mIsCompleteView.setChecked(mIsComplete);
            }

            mUseTypeBtn = root.findViewById(R.id.use_author_type_button);
            if (mUseTypeBtn != null) {
                boolean useAuthorType = App.isUsed(DBDefinitions.KEY_AUTHOR_TYPE);
                Group authorTypeGroup = root.findViewById(R.id.author_type_group);
                authorTypeGroup.setVisibility(useAuthorType ? View.VISIBLE : View.GONE);
                if (useAuthorType) {

                    mUseTypeBtn.setOnCheckedChangeListener(
                            (v, isChecked) -> setTypeEnabled(isChecked));

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
                    mTypeButtons.put(Author.TYPE_INKING,
                                     root.findViewById(R.id.cbx_author_type_inking));
                    mTypeButtons.put(Author.TYPE_COLORIST,
                                     root.findViewById(R.id.cbx_author_type_colorist));
                    mTypeButtons.put(Author.TYPE_COVER_ARTIST,
                                     root.findViewById(R.id.cbx_author_type_cover_artist));
                    mTypeButtons.put(Author.TYPE_COVER_INKING,
                                     root.findViewById(R.id.cbx_author_type_cover_inking));
                    mTypeButtons.put(Author.TYPE_COVER_COLORIST,
                                     root.findViewById(R.id.cbx_author_type_cover_colorist));

                    if (mType != Author.TYPE_UNKNOWN) {
                        setTypeEnabled(true);
                        for (int i = 0; i < mTypeButtons.size(); i++) {
                            mTypeButtons.valueAt(i)
                                        .setChecked((mType & mTypeButtons.keyAt(i)) != 0);
                        }
                    } else {
                        setTypeEnabled(false);
                    }
                }
            }

            return new AlertDialog.Builder(getContext())
                    .setIcon(R.drawable.ic_edit)
                    .setView(root)
                    .setTitle(R.string.title_edit_author)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismiss())
                    .setPositiveButton(R.string.btn_confirm_save, (dialog, which) -> {
                        // don't check on anything else here,
                        // we're doing more extensive checks later on.
                        mFamilyName = mFamilyNameView.getText().toString().trim();
                        if (mFamilyName.isEmpty()) {
                            Snackbar.make(mFamilyNameView, R.string.warning_missing_name,
                                          Snackbar.LENGTH_LONG).show();
                            return;
                        }

                        mGivenNames = mGivenNamesView.getText().toString().trim();

                        // Create a new Author as a holder for all changes.
                        Author tmpAuthor = new Author(mFamilyName, mGivenNames);

                        // allow for future layout(s) not displaying the isComplete checkbox
                        if (mIsCompleteView != null) {
                            mIsComplete = mIsCompleteView.isChecked();
                            tmpAuthor.setComplete(mIsComplete);
                        } else {
                            tmpAuthor.setComplete(mAuthor.isComplete());
                        }

                        // allow for future layout(s) not displaying the type fields
                        if (mUseTypeBtn != null) {
                            mType = getTypeFromViews();
                            if (mUseTypeBtn.isChecked()) {
                                tmpAuthor.setType(mType);
                            } else {
                                tmpAuthor.setType(Author.TYPE_UNKNOWN);
                            }
                        } else {
                            tmpAuthor.setType(mAuthor.getType());
                        }

                        ((EditBookAuthorsFragment) getTargetFragment())
                                .processChanges(mAuthor, tmpAuthor);
                    })
                    .create();
        }

        @Override
        public void onStart() {
            super.onStart();

            if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
                // force the dialog to be big enough
                Dialog dialog = getDialog();
                if (dialog != null) {
                    int width = ViewGroup.LayoutParams.MATCH_PARENT;
                    int height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    //noinspection ConstantConditions
                    dialog.getWindow().setLayout(width, height);
                }
            }
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
            if (mIsCompleteView != null) {
                mIsComplete = mIsCompleteView.isChecked();
            }
            if (mUseTypeBtn != null) {
                mType = getTypeFromViews();
            }
            super.onPause();
        }

        /**
         * Enable or disable the type related fields.
         *
         * @param enable Flag
         */
        private void setTypeEnabled(final boolean enable) {
            // don't bother changing the 'checked' status, we'll ignore them anyhow.
            // and this is more user friendly if they flip the switch more than once.
            mUseTypeBtn.setChecked(enable);
            for (int i = 0; i < mTypeButtons.size(); i++) {
                CompoundButton typeBtn = mTypeButtons.valueAt(i);
                typeBtn.setEnabled(enable);
            }
        }

        /**
         * Read all the type buttons, and construct the bitmask.
         *
         * @return author type bitmask.
         */
        private int getTypeFromViews() {
            int authorType = Author.TYPE_UNKNOWN;
            for (int i = 0; i < mTypeButtons.size(); i++) {
                if (mTypeButtons.valueAt(i).isChecked()) {
                    authorType |= mTypeButtons.keyAt(i);
                }
            }
            return authorType;
        }
    }

    protected class AuthorListAdapter
            extends RecyclerViewAdapterBase<Author, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of Authors
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        AuthorListAdapter(@NonNull final Context context,
                          @NonNull final List<Author> items,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = getLayoutInflater().inflate(R.layout.row_edit_author_list, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Context context = getContext();

            final Author author = getItem(position);
            final String authorLabel = author.getLabel(context);
            holder.authorView.setText(authorLabel);

            if (!authorLabel.equals(author.getSorting(context))) {
                holder.authorSortView.setVisibility(View.VISIBLE);
                holder.authorSortView.setText(author.getSorting(context));
            } else {
                holder.authorSortView.setVisibility(View.GONE);
            }

            if (App.isUsed(DBDefinitions.KEY_AUTHOR_TYPE)
                && author.getType() != Author.TYPE_UNKNOWN) {
                holder.authorTypeView.setText(author.getTypeLabels(context));
                holder.authorTypeView.setVisibility(View.VISIBLE);
            } else {
                holder.authorTypeView.setVisibility(View.GONE);
            }

            // click -> edit
            holder.rowDetailsView.setOnClickListener(v -> {
                EditBookAuthorDialogFragment frag = new EditBookAuthorDialogFragment();
                Bundle args = new Bundle(1);
                args.putParcelable(DBDefinitions.KEY_FK_AUTHOR, author);
                frag.setArguments(args);
                frag.setTargetFragment(EditBookAuthorsFragment.this, 0);
                frag.show(getParentFragmentManager(), EditBookAuthorDialogFragment.TAG);
            });
        }
    }
}
