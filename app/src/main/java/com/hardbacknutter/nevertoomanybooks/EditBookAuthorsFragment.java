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
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookAuthorsBinding;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.AuthorFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
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
                    mBookViewModel.setDirty(true);
                }
            };

    /** View Binding. */
    private FragmentEditBookAuthorsBinding mVb;
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
        mVb = FragmentEditBookAuthorsBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        if (!EditBookActivity.showAuthSeriesOnTabs(getContext())) {
            //noinspection ConstantConditions
            getActivity().findViewById(R.id.tab_panel).setVisibility(View.GONE);
        }

        final DiacriticArrayAdapter<String> nameAdapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item,
                mFragmentVM.getAuthorNames());
        mVb.author.setAdapter(nameAdapter);

        // set up the list view. The adapter is setup in onPopulateViews
        final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mVb.authorList.setLayoutManager(layoutManager);
        mVb.authorList.setHasFixedSize(true);

        // adding a new entry
        mVb.btnAdd.setOnClickListener(v -> onAdd());
    }

    @Override
    void onPopulateViews(@NonNull final Book book) {
        super.onPopulateViews(book);

        mList = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);

        //noinspection ConstantConditions
        mListAdapter = new AuthorListAdapter(getContext(), mList,
                                             vh -> mItemTouchHelper.startDrag(vh));
        mVb.authorList.setAdapter(mListAdapter);
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.authorList);
    }

    @Override
    public void onSaveFields(@NonNull final Book book) {
        super.onSaveFields(book);

        // The list is not a 'real' field. Hence the need to store it manually here.
        book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mList);
    }

    @Override
    public boolean hasUnfinishedEdits() {
        return !mVb.author.getText().toString().isEmpty();
    }

    private void onAdd() {
        final String name = mVb.author.getText().toString().trim();
        if (name.isEmpty()) {
            Snackbar.make(mVb.author, R.string.warning_missing_name,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        final Author newAuthor = Author.fromString(name);

        // see if it already exists
        //noinspection ConstantConditions
        newAuthor.fixId(getContext(), mFragmentVM.getDb(), LocaleUtils.getUserLocale(getContext()));
        // and check it's not already in the list.
        if (mList.contains(newAuthor)) {
            Snackbar.make(mVb.author, R.string.warning_author_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            return;
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newAuthor);
        mListAdapter.notifyDataSetChanged();

        // and clear the form for next entry.
        mVb.author.setText("");
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
                ItemWithFixableId.pruneList(mList, getContext(), mFragmentVM.getDb(),
                                            LocaleUtils.getUserLocale(getContext()),
                                            false);
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }

        // The name of the Author was modified.
        // Check if it's used by any other books.
        //noinspection ConstantConditions
        if (mBookViewModel.isSingleUsage(getContext(), author)) {
            // If it's not, we can simply modify the original and we're done here.
            // There is no need to consult the user.
            // Copy the new data into the original Author object that the user was changing.
            author.copyFrom(tmpData, true);
            //noinspection ConstantConditions
            ItemWithFixableId.pruneList(mList, getContext(), mFragmentVM.getDb(),
                                        LocaleUtils.getUserLocale(getContext()),
                                        false);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, we know the name of the Author was modified
        // and the it's used in more than one place.
        // We need to ask the user if they want to make the changes globally.
        final String allBooks = getString(R.string.bookshelf_all_books);
        final String message = getString(R.string.confirm_apply_author_changed,
                                         author.getLabel(getContext()),
                                         tmpData.getLabel(getContext()),
                                         allBooks);
        new MaterialAlertDialogBuilder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.title_scope_of_change)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(allBooks, (d, which) -> {
                    // copy all new data
                    author.copyFrom(tmpData, true);
                    // This change is done in the database right NOW!
                    if (mFragmentVM.getDb().updateAuthor(getContext(), author)) {
                        ItemWithFixableId.pruneList(mList, getContext(), mFragmentVM.getDb(),
                                                    LocaleUtils.getUserLocale(getContext()),
                                                    false);
                        mBookViewModel.refreshAuthorList(getContext());
                        mListAdapter.notifyDataSetChanged();

                    } else {
                        Logger.warnWithStackTrace(getContext(), TAG, "Could not update",
                                                  "author=" + author,
                                                  "tmpAuthor=" + tmpData);
                        new MaterialAlertDialogBuilder(getContext())
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
                    mFragmentVM.getDb().updateOrInsertAuthor(getContext(), tmpData);

                    // unlink the old one (and unmodified), and link with the new one
                    // book/author positions will be fixed up when saving.
                    // Note that the old one *might* be orphaned at this time.
                    // Same remark as above.
                    mList.remove(author);
                    mList.add(tmpData);
                    ItemWithFixableId.pruneList(mList, getContext(), mFragmentVM.getDb(),
                                                LocaleUtils.getUserLocale(getContext()),
                                                false);

                    //URGENT: updated author(s): Book gets them, but TocEntries remain using old set
                    //
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
                    // SOLUTION one of:
                    // - just ASK the user with a "mod toc" or "no"
                    // - don't bother, assume this won't be needed very often and
                    //   have the user will do it manually

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

        Holder(@NonNull final View itemView) {
            super(itemView);

            authorView = itemView.findViewById(R.id.row_author);
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
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        static final String TAG = "EditBookAuthorDialogFrag";
        /** Key: type. */
        private final SparseArray<CompoundButton> mTypeButtons = new SparseArray<>();

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
        /** View Binding. */
        private DialogEditBookAuthorBinding mVb;

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mDb = new DAO(TAG);

            final Bundle args = requireArguments();
            mAuthor = args.getParcelable(DBDefinitions.KEY_FK_AUTHOR);
            Objects.requireNonNull(mAuthor, ErrorMsg.ARGS_MISSING_AUTHOR);

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
                mType = savedInstanceState.getInt(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK);
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

            Objects.requireNonNull(getTargetFragment(), ErrorMsg.NO_TARGET_FRAGMENT_SET);

            // Reminder: *always* use the activity inflater here.
            //noinspection ConstantConditions
            final LayoutInflater inflater = getActivity().getLayoutInflater();
            mVb = DialogEditBookAuthorBinding.inflate(inflater);

            //noinspection ConstantConditions
            final DiacriticArrayAdapter<String> mFamilyNameAdapter = new DiacriticArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item,
                    mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FAMILY_NAME));
            final DiacriticArrayAdapter<String> mGivenNameAdapter = new DiacriticArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item,
                    mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES));

            // the dialog fields != screen fields.
            mVb.familyName.setText(mFamilyName);
            mVb.familyName.setAdapter(mFamilyNameAdapter);

            mVb.givenNames.setText(mGivenNames);
            mVb.givenNames.setAdapter(mGivenNameAdapter);

            mVb.cbxIsComplete.setChecked(mIsComplete);

            final boolean useAuthorType = App.isUsed(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK);
            mVb.authorTypeGroup.setVisibility(useAuthorType ? View.VISIBLE : View.GONE);
            if (useAuthorType) {

                mVb.useAuthorTypeButton.setOnCheckedChangeListener(
                        (v, isChecked) -> setTypeEnabled(isChecked));

                mTypeButtons.put(Author.TYPE_WRITER, mVb.cbxAuthorTypeWriter);
                mTypeButtons.put(Author.TYPE_CONTRIBUTOR, mVb.cbxAuthorTypeContributor);
                mTypeButtons.put(Author.TYPE_INTRODUCTION, mVb.cbxAuthorTypeIntro);
                mTypeButtons.put(Author.TYPE_TRANSLATOR, mVb.cbxAuthorTypeTranslator);
                mTypeButtons.put(Author.TYPE_EDITOR, mVb.cbxAuthorTypeEditor);

                mTypeButtons.put(Author.TYPE_ARTIST, mVb.cbxAuthorTypeArtist);
                mTypeButtons.put(Author.TYPE_INKING, mVb.cbxAuthorTypeInking);
                mTypeButtons.put(Author.TYPE_COLORIST, mVb.cbxAuthorTypeColorist);

                mTypeButtons.put(Author.TYPE_COVER_ARTIST, mVb.cbxAuthorTypeCoverArtist);
                mTypeButtons.put(Author.TYPE_COVER_INKING, mVb.cbxAuthorTypeCoverInking);
                mTypeButtons.put(Author.TYPE_COVER_COLORIST, mVb.cbxAuthorTypeCoverColorist);

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

            return new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_edit)
                    .setView(mVb.getRoot())
                    .setTitle(R.string.title_edit_author)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismiss())
                    .setPositiveButton(R.string.btn_confirm_save, (dialog, which) -> {
                        // don't check on anything else here,
                        // we're doing more extensive checks later on.
                        mFamilyName = mVb.familyName.getText().toString().trim();
                        if (mFamilyName.isEmpty()) {
                            Snackbar.make(mVb.familyName, R.string.warning_missing_name,
                                          Snackbar.LENGTH_LONG).show();
                            return;
                        }

                        mGivenNames = mVb.givenNames.getText().toString().trim();

                        // Create a new Author as a holder for all changes.
                        final Author tmpAuthor = new Author(mFamilyName, mGivenNames);

                        mIsComplete = mVb.cbxIsComplete.isChecked();
                        tmpAuthor.setComplete(mIsComplete);

                        mType = getTypeFromViews();
                        if (mVb.useAuthorTypeButton.isChecked()) {
                            tmpAuthor.setType(mType);
                        } else {
                            tmpAuthor.setType(Author.TYPE_UNKNOWN);
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
                final Dialog dialog = getDialog();
                if (dialog != null) {
                    final int width = ViewGroup.LayoutParams.MATCH_PARENT;
                    final int height = ViewGroup.LayoutParams.WRAP_CONTENT;
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
            outState.putInt(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK, mType);
        }

        @Override
        public void onPause() {
            mFamilyName = mVb.familyName.getText().toString().trim();
            mGivenNames = mVb.givenNames.getText().toString().trim();
            mIsComplete = mVb.cbxIsComplete.isChecked();
            mType = getTypeFromViews();
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
            mVb.useAuthorTypeButton.setChecked(enable);
            for (int i = 0; i < mTypeButtons.size(); i++) {
                final CompoundButton typeBtn = mTypeButtons.valueAt(i);
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

        @NonNull
        private final FieldFormatter<Author> mFormatter;

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

            mFormatter = new AuthorFormatter(Author.Details.Full, false);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_author_list, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Author author = getItem(position);
            mFormatter.apply(author, holder.authorView);

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
