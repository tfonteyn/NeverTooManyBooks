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
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditTocEntryDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.Edition;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbGetBookTask;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbGetEditionsTask;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbManager;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.FocusFixer;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.viewmodels.UpdateFieldsModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.TaskModel;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * This class is called by {@link EditBookFragment} and displays the Content Tab.
 * <p>
 * Doesn't use {@link UpdateFieldsModel}
 * <p>
 * The ISFDB direct interaction should however be seen as temporary as this class should not
 * have to know about any specific search web site.
 * <p>
 * This is still not obsolete as the standard search engines can only return a single book,
 * and hence a single TOC. The interaction here with ISFDB allows the user to reject the first
 * (book)TOC found, and get the next one (etc...).
 */
public class EditBookTocFragment
        extends EditBookBaseFragment {

    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mBookModel.setDirty(true);
                }
            };
    /** The book. */
    @Nullable
    private String mIsbn;
    /** primary author of the book. */
    private Author mBookAuthor;
    /** the rows. */
    private ArrayList<TocEntry> mList;
    /** The adapter for the list. */
    private TocListEditAdapter mListAdapter;
    /** The View for the list. */
    private RecyclerView mListView;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;
    private CompoundButton mIsAnthologyCbx;

    private TaskModel mIsfdbTaskModel;

    /**
     * ISFDB editions of a book(isbn).
     * We'll try them one by one if the user asks for a re-try.
     */
    @Nullable
    private ArrayList<Edition> mIsfdbEditions;
    private final TaskListener<Bundle> mIsfdbBookResultsListener = new TaskListener<Bundle>() {

        @Override
        public void onFinished(@NonNull final FinishMessage<Bundle> message) {
            Bundle bookData = message.result;
            if (bookData == null) {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.warning_book_not_found, Snackbar.LENGTH_LONG)
                        .show();
                return;
            }

            Book book = mBookModel.getBook();

            // update the book with Series information that was gathered from the TOC
            List<Series> series = bookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
            if (series != null && !series.isEmpty()) {
                ArrayList<Series> inBook = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
                // add, weeding out duplicates
                for (Series s : series) {
                    if (!inBook.contains(s)) {
                        inBook.add(s);
                    }
                }
                book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, inBook);
            }

            // update the book with the first publication date that was gathered from the TOC
            final String bookFirstPublication =
                    bookData.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION);
            if (bookFirstPublication != null) {
                if (book.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION).isEmpty()) {
                    book.putString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION, bookFirstPublication);
                }
            }

            // finally the TOC itself;  only put on display for the user to approve
            boolean hasOtherEditions = (mIsfdbEditions != null) && (mIsfdbEditions.size() > 1);
            ConfirmTocDialogFragment.newInstance(bookData, hasOtherEditions)
                                    .show(getChildFragmentManager(), ConfirmTocDialogFragment.TAG);
        }
    };

    /**
     * we got one or more editions from ISFDB.
     * Stores the url's locally as the user might want to try the next in line
     */
    private final TaskListener<ArrayList<Edition>> mIsfdbEditionResultsListener =
            new TaskListener<ArrayList<Edition>>() {
                public void onFinished(@NonNull final FinishMessage<ArrayList<Edition>> message) {
                    mIsfdbEditions = message.result != null ? message.result : new ArrayList<>();
                    searchIsfdb();
                }
            };

    private final ConfirmTocDialogFragment.ConfirmTocResults mConfirmTocResultsListener =
            new ConfirmTocDialogFragment.ConfirmTocResults() {
                /**
                 * The user approved, so add the TOC to the list and refresh the screen
                 * (still not saved to database).
                 */
                public void commitIsfdbData(final long tocBitMask,
                                            @NonNull final List<TocEntry> tocEntries) {
                    if (tocBitMask != 0) {
                        Book book = mBookModel.getBook();
                        book.putLong(DBDefinitions.KEY_TOC_BITMASK, tocBitMask);
                        getFields().getField(R.id.cbx_is_anthology).setValueFrom(book);
                        getFields().getField(R.id.cbx_multiple_authors).setValueFrom(book);
                    }

                    mList.addAll(tocEntries);
                    mListAdapter.notifyDataSetChanged();
                }

                /**
                 * Start a task to get the next edition of this book (that we know of).
                 */
                public void getNextEdition() {
                    // remove the top one, and try again
                    mIsfdbEditions.remove(0);
                    searchIsfdb();
                }
            };

    /** checkbox to hide/show the author edit field. */
    private CompoundButton mMultiAuthorsView;
    /** Hold the item position in the ist while we're editing an item. */
    @Nullable
    private Integer mEditPosition;
    private final EditTocEntryDialogFragment.EditTocEntryResults mEditTocEntryResultsListener =
            new EditTocEntryDialogFragment.EditTocEntryResults() {
                /**
                 * Add the author/title from the edit fields as a new row in the TOC list.
                 */
                public void addOrUpdateEntry(@NonNull final TocEntry tocEntry) {

                    if (mEditPosition == null) {
                        // add the new entry
                        mList.add(tocEntry);
                    } else {
                        // find and update
                        TocEntry original = mList.get(mEditPosition);
                        original.copyFrom(tocEntry);
                    }
                    mListAdapter.notifyDataSetChanged();
                }
            };

    private void searchIsfdb() {
        if (mIsfdbEditions != null && !mIsfdbEditions.isEmpty()) {
            IsfdbGetBookTask task = new IsfdbGetBookTask(mIsfdbEditions,
                                                         isAddSeriesFromToc(),
                                                         mIsfdbBookResultsListener);
            mIsfdbTaskModel.setTask(task);
            task.execute();
        } else {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_no_editions, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_book_toc, container, false);
        mListView = view.findViewById(R.id.tocList);
        mIsAnthologyCbx = view.findViewById(R.id.cbx_is_anthology);
        mMultiAuthorsView = view.findViewById(R.id.cbx_multiple_authors);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        Resources r = getResources();
        menu.add(Menu.NONE, R.id.MENU_POPULATE_TOC_FROM_ISFDB,
                 r.getInteger(R.integer.MENU_ORDER_VIEW_BOOK_AT_ISFDB),
                 R.string.menu_populate_toc)
            .setIcon(R.drawable.ic_autorenew);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    protected void initFields() {
        super.initFields();
        Fields fields = getFields();

        // Anthology is provided as a bitmask, see {@link Book#initAccessorsAndValidators()}
        fields.addBoolean(R.id.cbx_is_anthology, mIsAnthologyCbx, Book.HAS_MULTIPLE_WORKS);

        fields.addBoolean(R.id.cbx_multiple_authors, mMultiAuthorsView, Book.HAS_MULTIPLE_AUTHORS);

        // adding a new TOC entry
        //noinspection ConstantConditions
        getView().findViewById(R.id.btn_add).setOnClickListener(v -> newEntry());

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mListView.setLayoutManager(linearLayoutManager);
        //noinspection ConstantConditions
        mListView.addItemDecoration(
                new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));
        mListView.setHasFixedSize(true);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mIsfdbTaskModel = new ViewModelProvider(this).get(TaskModel.class);
        TaskBase task = mIsfdbTaskModel.getTask();
        if (task != null) {
            if (task instanceof IsfdbGetBookTask) {
                //noinspection unchecked
                mIsfdbTaskModel.getTask().setListener(mIsfdbBookResultsListener);
            } else if (task instanceof IsfdbGetEditionsTask) {
                //noinspection unchecked
                mIsfdbTaskModel.getTask().setListener(mIsfdbEditionResultsListener);
            }
        }
        Book book = mBookModel.getBook();

        // Author to use if mMultiAuthorsView is set to false
        List<Author> authorList = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (!authorList.isEmpty()) {
            mBookAuthor = authorList.get(0);
        } else {
            // not ideal, but oh well.
            String unknownString = getString(R.string.unknown);
            mBookAuthor = new Author(unknownString, unknownString);
        }

        // used to call Search sites to populate the TOC
        mIsbn = book.getString(DBDefinitions.KEY_ISBN);

        // do other stuff here that might affect the view.

        // Fix the focus order for the views
        //noinspection ConstantConditions
        FocusFixer.fix(getView());
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_POPULATE_TOC_FROM_ISFDB:
                long isfdbId = mBookModel.getBook().getLong(DBDefinitions.KEY_EID_ISFDB);
                if (isfdbId != 0) {
                    //noinspection ConstantConditions
                    Snackbar.make(getView(), R.string.progress_msg_connecting,
                                  Snackbar.LENGTH_LONG).show();
                    IsfdbGetBookTask task =
                            new IsfdbGetBookTask(isfdbId, isAddSeriesFromToc(),
                                                 mIsfdbBookResultsListener);
                    mIsfdbTaskModel.setTask(task);
                    task.execute();
                    return true;
                }

                ISBN isbn = ISBN.createISBN(mIsbn);
                if (isbn != null && isbn.isValid()) {
                    //noinspection ConstantConditions
                    Snackbar.make(getView(), R.string.progress_msg_connecting,
                                  Snackbar.LENGTH_LONG).show();
                    //noinspection ConstantConditions
                    IsfdbGetEditionsTask task =
                            new IsfdbGetEditionsTask(isbn.asText(),
                                                     mIsfdbEditionResultsListener);
                    mIsfdbTaskModel.setTask(task);
                    task.execute();
                    return true;
                }

                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.warning_requires_isbn,
                              Snackbar.LENGTH_LONG).show();
                return false;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (ConfirmTocDialogFragment.TAG.equals(childFragment.getTag())) {
            ((ConfirmTocDialogFragment) childFragment).setListener(mConfirmTocResultsListener);

        } else if (EditTocEntryDialogFragment.TAG.equals(childFragment.getTag())) {
            ((EditTocEntryDialogFragment) childFragment).setListener(mEditTocEntryResultsListener);
        }
    }

    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        Book book = mBookModel.getBook();

        // Populate the list view with the book content table.
        mList = book.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        //noinspection ConstantConditions
        mListAdapter = new TocListEditAdapter(getContext(), mList,
                                              vh -> mItemTouchHelper.startDrag(vh));
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mListView.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);

        // hide unwanted fields
        showOrHideFields(false, false);
    }

    /**
     * The toc list is not a 'real' field. Hence the need to store it manually here.
     */
    @Override
    protected void onSaveFieldsToBook() {
        super.onSaveFieldsToBook();
        mBookModel.getBook().putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, mList);
    }

    private void onCreateContextMenu(final int position) {
        Resources r = getResources();

        TocEntry item = mList.get(position);
        @SuppressWarnings("ConstantConditions")
        Menu menu = MenuPicker.createMenu(getContext());
        menu.add(Menu.NONE, R.id.MENU_EDIT,
                 r.getInteger(R.integer.MENU_ORDER_EDIT),
                 R.string.menu_edit)
            .setIcon(R.drawable.ic_edit);
        menu.add(Menu.NONE, R.id.MENU_DELETE,
                 r.getInteger(R.integer.MENU_ORDER_DELETE),
                 R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        String title = item.getTitle();
        new MenuPicker<>(getContext(), title, menu, position, this::onContextItemSelected)
                .show();
    }

    /**
     * Using {@link ValuePicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          @NonNull final Integer position) {
        TocEntry tocEntry = mList.get(position);

        switch (menuItem.getItemId()) {
            case R.id.MENU_EDIT:
                editEntry(tocEntry, position);
                return true;

            case R.id.MENU_DELETE:
                //noinspection ConstantConditions
                StandardDialogs.deleteTocEntryAlert(getContext(), tocEntry, () -> {
                    if (mBookModel.getDb().deleteTocEntry(tocEntry.getId()) == 1) {
                        mList.remove(tocEntry);
                        mListAdapter.notifyItemRemoved(position);
                    }
                });
                return true;

            default:
                return false;
        }
    }


    /**
     * Create a new entry.
     */
    private void newEntry() {
        editEntry(new TocEntry(mBookAuthor, "", ""), null);
    }

    /**
     * Start the fragment dialog to edit a Bookshelf.
     *
     * @param tocEntry to edit
     * @param position the item position which will be used to update the data after editing.
     */
    private void editEntry(@NonNull final TocEntry tocEntry,
                           @Nullable final Integer position) {
        mEditPosition = position;

        EditTocEntryDialogFragment.newInstance(tocEntry, mMultiAuthorsView.isChecked())
                                  .show(getChildFragmentManager(), EditTocEntryDialogFragment.TAG);

    }

    private boolean isAddSeriesFromToc() {
        //noinspection ConstantConditions
        return PreferenceManager.getDefaultSharedPreferences(getContext())
                                .getBoolean(IsfdbManager.PREFS_SERIES_FROM_TOC, false);
    }

    /**
     * Dialog that shows the downloaded TOC titles for approval by the user.
     * <p>
     * Show with the {@link Fragment#getChildFragmentManager()}
     * <p>
     * Uses {@link Fragment#getParentFragment()} for sending results back.
     */
    public static class ConfirmTocDialogFragment
            extends DialogFragment {

        /** Log tag. */
        private static final String TAG = "ConfirmTocDialogFrag";

        private static final String BKEY_HAS_OTHER_EDITIONS = TAG + ":hasOtherEditions";

        private boolean mHasOtherEditions;
        private long mTocBitMask;
        private ArrayList<TocEntry> mTocEntries;

        private WeakReference<ConfirmTocResults> mListener;

        /**
         * Constructor.
         *
         * @param hasOtherEditions flag
         *
         * @return the instance
         */
        static ConfirmTocDialogFragment newInstance(@NonNull final Bundle bookData,
                                                    final boolean hasOtherEditions) {
            ConfirmTocDialogFragment frag = new ConfirmTocDialogFragment();
            bookData.putBoolean(BKEY_HAS_OTHER_EDITIONS, hasOtherEditions);
            frag.setArguments(bookData);
            return frag;
        }

        /**
         * Call this from {@link #onAttachFragment} in the parent.
         *
         * @param listener the object to send the result to.
         */
        void setListener(@NonNull final ConfirmTocResults listener) {
            mListener = new WeakReference<>(listener);
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle args = requireArguments();
            mTocEntries = args.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
            Objects.requireNonNull(mTocEntries, "TocEntries must be passed in args");

            mTocBitMask = args.getLong(DBDefinitions.KEY_TOC_BITMASK);
            mHasOtherEditions = args.getBoolean(BKEY_HAS_OTHER_EDITIONS, false);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            // Reminder: *always* use the activity inflater here.
            //noinspection ConstantConditions
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();

            boolean hasToc = mTocEntries != null && !mTocEntries.isEmpty();

            // custom payout, as we want the text to be smaller.
            @SuppressLint("InflateParams")
            View root = layoutInflater.inflate(R.layout.dialog_toc_confirm, null);

            TextView textView = root.findViewById(R.id.content);
            if (hasToc) {
                StringBuilder message =
                        new StringBuilder(getString(R.string.warning_toc_confirm))
                                .append("\n\n")
                                .append(Csv.join(", ", mTocEntries, TocEntry::getTitle));
                textView.setText(message);

            } else {
                textView.setText(getString(R.string.error_auto_toc_population_failed));
            }

            @SuppressWarnings("ConstantConditions")
            AlertDialog dialog =
                    new AlertDialog.Builder(getContext())
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .setView(root)
                            .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                            .create();

            if (hasToc) {
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                                 this::onCommitToc);
            }

            // if we found multiple editions, allow a re-try with the next edition
            if (mHasOtherEditions) {
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.retry),
                                 this::onGetNext);
            }

            return dialog;
        }

        private void onCommitToc(@SuppressWarnings("unused") @NonNull final DialogInterface d,
                                 @SuppressWarnings("unused") final int which) {
            if (mListener.get() != null) {
                mListener.get().commitIsfdbData(mTocBitMask, mTocEntries);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onCommitToc|" + Logger.WEAK_REFERENCE_DEAD);
                }
            }
        }

        private void onGetNext(@SuppressWarnings("unused") @NonNull final DialogInterface d,
                               @SuppressWarnings("unused") final int which) {
            if (mListener.get() != null) {
                mListener.get().getNextEdition();
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onGetNext|" + Logger.WEAK_REFERENCE_DEAD);
                }
            }
        }

        interface ConfirmTocResults {

            void commitIsfdbData(long tocBitMask,
                                 @NonNull List<TocEntry> tocEntries);

            void getNextEdition();
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final TextView titleView;
        @NonNull
        final TextView authorView;
        @NonNull
        final TextView firstPublicationView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            titleView = rowDetailsView.findViewById(R.id.title);
            authorView = rowDetailsView.findViewById(R.id.author);
            firstPublicationView = rowDetailsView.findViewById(R.id.year);
        }
    }

    private class TocListEditAdapter
            extends RecyclerViewAdapterBase<TocEntry, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of TocEntry's
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        TocListEditAdapter(@NonNull final Context context,
                           @NonNull final List<TocEntry> items,
                           @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_toc_entry, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final TocEntry item = getItem(position);

            holder.titleView.setText(item.getTitle());
            holder.authorView.setText(item.getAuthor().getLabel(getContext()));

            String year = item.getFirstPublication();
            if (year.isEmpty()) {
                holder.firstPublicationView.setVisibility(View.GONE);
            } else {
                holder.firstPublicationView.setVisibility(View.VISIBLE);
                holder.firstPublicationView.setText(getString(R.string.brackets, year));
            }

            // click -> edit
            holder.rowDetailsView.setOnClickListener(
                    v -> editEntry(item, holder.getAdapterPosition()));

            holder.rowDetailsView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getAdapterPosition());
                return true;
            });
        }
    }
}
