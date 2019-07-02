/*
 * @copyright 2013 Evan Leybourn
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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.picker.MenuPicker;
import com.eleybourn.bookcatalogue.dialogs.picker.ValuePicker;
import com.eleybourn.bookcatalogue.dialogs.entities.EditTocEntryDialogFragment;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.searches.UpdateFieldsFromInternetTask;
import com.eleybourn.bookcatalogue.searches.isfdb.Editions;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBBook;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBManager;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewAdapterBase;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewViewHolderBase;
import com.eleybourn.bookcatalogue.widgets.SimpleAdapterDataObserver;
import com.eleybourn.bookcatalogue.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.eleybourn.bookcatalogue.widgets.ddsupport.StartDragListener;

/**
 * This class is called by {@link EditBookFragment} and displays the Content Tab.
 * <p>
 * Doesn't use {@link UpdateFieldsFromInternetTask}
 * as this would actually introduce the ManagedTask usage which we want to phase out.
 * <p>
 * The ISFDB direct interaction should however be seen as temporary as this class should not
 * have to know about any specific search web site.
 * Note: we also pass in 'this' as the task listener... no orientation changes ...
 * URGENT: needs ViewModel for the tasks!
 */
public class EditBookTocFragment
        extends EditBookBaseFragment {

    /** Fragment manager tag. */
    public static final String TAG = "EditBookTocFragment";

    /** The book. */
    @Nullable
    private String mIsbn;
    /** primary author of the book. */
    private Author mBookAuthor;

    /** checkbox to hide/show the author edit field. */
    private CompoundButton mMultipleAuthorsView;
    /** the rows. */
    private ArrayList<TocEntry> mList;
    /** The adapter for the list. */
    private TocListAdapterForEditing mListAdapter;
    /** The View for the list. */
    private RecyclerView mListView;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /**
     * ISFDB editions of a book(isbn).
     * We'll try them one by one if the user asks for a re-try.
     */
    @Nullable
    private ArrayList<Editions.Edition> mISFDBEditions;

    private boolean mIsCollectSeriesInfoFromToc;
    private final ConfirmToc.ConfirmTocResults mConfirmTocResultsListener =
            new ConfirmToc.ConfirmTocResults() {

                /**
                 * The user approved, so add the TOC to the list and refresh the screen
                 * (still not saved to database).
                 */
                public void commitISFDBData(final long tocBitMask,
                                            @NonNull final List<TocEntry> tocEntries) {
                    if (tocBitMask != 0) {
                        Book book = mBookBaseFragmentModel.getBook();

                        book.putLong(DBDefinitions.KEY_TOC_BITMASK, tocBitMask);
                        getField(R.id.multiple_authors).setValueFrom(book);
                    }

                    mList.addAll(tocEntries);
                    mListAdapter.notifyDataSetChanged();
                }

                /**
                 * Start a task to get the next edition of this book (that we know of).
                 */
                public void getNextEdition() {
                    // remove the top one, and try again
                    mISFDBEditions.remove(0);
                    new ISFDBGetBookTask(mISFDBEditions, mIsCollectSeriesInfoFromToc,
                                         EditBookTocFragment.this).execute();
                }
            };
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

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_book_toc, container, false);
        mListView = view.findViewById(android.R.id.list);
        return view;
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * All storage interaction is done via:
     * <ul>
     * <li>{@link #onLoadFieldsFromBook} from base class onResume</li>
     * <li>{@link #onSaveFieldsToBook} from base class onPause</li>
     * </ul>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Book book = mBookBaseFragmentModel.getBook();

        // Author to use if mMultipleAuthorsView is set to false
        List<Author> authorList = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (!authorList.isEmpty()) {
            mBookAuthor = authorList.get(0);

        } else {
            // not ideal, but oh well.
            String unknown = getString(R.string.unknown);
            mBookAuthor = new Author(unknown, unknown);
        }

        // use the preferences
        mIsCollectSeriesInfoFromToc = ISFDBManager.isCollectSeriesInfoFromToc();

        // used to call Search sites to populate the TOC
        mIsbn = book.getString(DBDefinitions.KEY_ISBN);

        // do other stuff here that might affect the view.

        // Fix the focus order for the views
        FocusSettings.fix(requireView());
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (ConfirmToc.TAG.equals(childFragment.getTag())) {
            ((ConfirmToc) childFragment).setListener(mConfirmTocResultsListener);

        } else if (EditTocEntryDialogFragment.TAG.equals(childFragment.getTag())) {
            ((EditTocEntryDialogFragment) childFragment).setListener(mEditTocEntryResultsListener);
        }
    }

    @Override
    protected void initFields() {
        super.initFields();
        Fields fields = getFields();

        Field field;
        // Anthology is provided as a bitmask, see {@link Book#initValidators()}
        fields.add(R.id.is_anthology, Book.HAS_MULTIPLE_WORKS)
              .getView().setOnClickListener(v -> {
            // enable controls as applicable.
            mMultipleAuthorsView.setEnabled(((Checkable) v).isChecked());
        });

        field = fields.add(R.id.multiple_authors, Book.HAS_MULTIPLE_AUTHORS);
        mMultipleAuthorsView = field.getView();

        // adding a new TOC entry
        requireView().findViewById(R.id.btn_add).setOnClickListener(v -> newEntry());

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mListView.setLayoutManager(linearLayoutManager);
        //noinspection ConstantConditions
        mListView.addItemDecoration(
                new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));
        mListView.setHasFixedSize(true);
    }

    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        Book book = mBookBaseFragmentModel.getBook();

        mMultipleAuthorsView.setEnabled(book.getBoolean(Book.HAS_MULTIPLE_WORKS));

        // Populate the list view with the book content table.
        mList = book.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        //noinspection ConstantConditions
        mListAdapter = new TocListAdapterForEditing(
                getContext(), mList, viewHolder -> mItemTouchHelper.startDrag(viewHolder));
        mListAdapter.registerAdapterDataObserver(new SimpleAdapterDataObserver() {
            @Override
            public void onChanged() {
                mBookBaseFragmentModel.setDirty(true);
            }
        });
        mListView.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);

        // Restore default visibility
        showHideFields(false);
    }

    /**
     * The toc list is not a 'real' field. Hence the need to store it manually here.
     */
    @Override
    protected void onSaveFieldsToBook() {
        super.onSaveFieldsToBook();
        // no special validation done.
        mBookBaseFragmentModel.getBook().putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY,
                                                                mList);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_POPULATE_TOC_FROM_ISFDB, 0, R.string.menu_populate_toc)
            .setIcon(R.drawable.ic_autorenew);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_POPULATE_TOC_FROM_ISFDB:
                if (ISBN.isValid(mIsbn)) {
                    UserMessage.showUserMessage(requireView(), R.string.progress_msg_connecting);
                    new ISFDBGetEditionsTask(mIsbn, this).execute();
                } else {
                    UserMessage.showUserMessage(requireView(),
                                                R.string.warning_action_requires_isbn);
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onCreateContextMenu(final int position) {

        TocEntry item = mList.get(position);
        @SuppressWarnings("ConstantConditions")
        Menu menu = MenuPicker.createMenu(getContext());
        menu.add(Menu.NONE, R.id.MENU_EDIT, 0, R.string.menu_edit)
            .setIcon(R.drawable.ic_edit);
        menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        String menuTitle = item.getTitle();
        final MenuPicker<Integer> picker = new MenuPicker<>(getContext(), menuTitle, menu, position,
                                                            this::onContextItemSelected);
        picker.show();
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
                editEntry(mList.get(position), position);
                return true;

            case R.id.MENU_DELETE:
                //noinspection ConstantConditions
                StandardDialogs.deleteTocEntryAlert(getContext(), tocEntry, () -> {
                    if (mBookBaseFragmentModel.getDb().deleteTocEntry(tocEntry.getId()) == 1) {
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
     * we got one or more editions from ISFDB.
     * Store the url's locally as the user might want to try the next in line
     */
    private void onGotISFDBEditions(@Nullable final ArrayList<Editions.Edition> editions) {
        mISFDBEditions = editions != null ? editions : new ArrayList<>();
        if (!mISFDBEditions.isEmpty()) {
            new ISFDBGetBookTask(mISFDBEditions, mIsCollectSeriesInfoFromToc, this).execute();
        } else {
            UserMessage.showUserMessage(requireView(), R.string.warning_no_editions);
        }
    }

    /**
     * we got a book.
     *
     * @param bookData our book from ISFDB.
     */
    private void onGotISFDBBook(@Nullable final Bundle bookData) {
        if (bookData == null) {
            UserMessage.showUserMessage(requireView(), R.string.warning_book_not_found);
            return;
        }

        Book book = mBookBaseFragmentModel.getBook();

        // update the book with series information that was gathered from the TOC
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
                bookData.getString(DBDefinitions.KEY_DATE_FIRST_PUBLISHED);
        if (bookFirstPublication != null) {
            if (book.getString(DBDefinitions.KEY_DATE_FIRST_PUBLISHED).isEmpty()) {
                book.putString(DBDefinitions.KEY_DATE_FIRST_PUBLISHED, bookFirstPublication);
            }
        }

        // finally the TOC itself; not saved here but only put on display for the user to approve
        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(ConfirmToc.TAG) == null) {
            boolean hasOtherEditions = (mISFDBEditions != null) && (mISFDBEditions.size() > 1);
            ConfirmToc.newInstance(bookData, hasOtherEditions).show(fm, ConfirmToc.TAG);
        }

    }

    /**
     * Create a new entry.
     */
    private void newEntry() {
        editEntry(new TocEntry(mBookAuthor, "", ""), null);
    }

    /**
     * Edit an entry.
     */
    private void editEntry(@NonNull final TocEntry tocEntry,
                           @Nullable final Integer position) {
        mEditPosition = position;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(EditTocEntryDialogFragment.TAG) == null) {
            EditTocEntryDialogFragment.newInstance(tocEntry, mMultipleAuthorsView.isChecked())
                                      .show(fm, EditTocEntryDialogFragment.TAG);
        }
    }

    /**
     * Dialog that shows the downloaded TOC titles for approval by the user.
     * <p>
     * Show with the {@link Fragment#getChildFragmentManager()}
     * <p>
     * Uses {@link Fragment#getParentFragment()} for sending results back.
     */
    public static class ConfirmToc
            extends DialogFragment {

        /** Fragment manager tag. */
        private static final String TAG = "ConfirmToc";

        private static final String BKEY_HAS_OTHER_EDITIONS = TAG + ":hasOtherEditions";

        private long mTocBitMask;
        private ArrayList<TocEntry> mTocEntries;

        private WeakReference<ConfirmTocResults> mListener;

        /**
         * Constructor.
         *
         * @return the instance
         */
        static ConfirmToc newInstance(@NonNull final Bundle bookData,
                                      final boolean hasOtherEditions) {
            ConfirmToc frag = new ConfirmToc();
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

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

            Bundle args = requireArguments();

            boolean hasOtherEditions = args.getBoolean(BKEY_HAS_OTHER_EDITIONS);
            mTocBitMask = args.getLong(DBDefinitions.KEY_TOC_BITMASK);
            mTocEntries = args.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
            boolean hasToc = mTocEntries != null && !mTocEntries.isEmpty();

            @SuppressLint("InflateParams")
            @SuppressWarnings("ConstantConditions")
            final View root = getActivity().getLayoutInflater()
                                           .inflate(R.layout.dialog_toc_confirm, null);

            TextView textView = root.findViewById(R.id.content);
            if (hasToc) {
                StringBuilder message = new StringBuilder(getString(R.string.warning_toc_confirm))
                        .append("\n\n")
                        .append(Csv.join(", ", mTocEntries, TocEntry::getTitle));
                textView.setText(message);
            } else {
                textView.setText(getString(R.string.error_auto_toc_population_failed));
            }

            @SuppressWarnings("ConstantConditions")
            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setView(root)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                    .create();

            if (hasToc) {
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                                 this::onCommitToc);
            }

            // if we found multiple editions, allow a re-try with the next edition
            if (hasOtherEditions) {
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.retry),
                                 this::onGetNext);
            }

            return dialog;
        }

        private void onCommitToc(@SuppressWarnings("unused") @NonNull final DialogInterface d,
                                 @SuppressWarnings("unused") final int which) {
            if (mListener.get() != null) {
                mListener.get().commitISFDBData(mTocBitMask, mTocEntries);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onCommitToc",
                                 "WeakReference to listener was dead");
                }
            }
        }

        private void onGetNext(@SuppressWarnings("unused") @NonNull final DialogInterface d,
                               @SuppressWarnings("unused") final int which) {
            if (mListener.get() != null) {
                mListener.get().getNextEdition();
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onGetNext",
                                 "WeakReference to listener was dead");
                }
            }
        }

        interface ConfirmTocResults {

            void commitISFDBData(long tocBitMask,
                                 @NonNull List<TocEntry> tocEntries);

            void getNextEdition();
        }
    }

    private static class ISFDBGetEditionsTask
            extends AsyncTask<Void, Void, ArrayList<Editions.Edition>> {

        @NonNull
        private final String mIsbn;
        @NonNull
        private final WeakReference<EditBookTocFragment> mTaskListener;

        /**
         * Constructor.
         *
         * @param isbn         to search for
         * @param taskListener to send results to
         */
        @UiThread
        ISFDBGetEditionsTask(@NonNull final String isbn,
                             @NonNull final EditBookTocFragment taskListener) {
            mIsbn = isbn;
            mTaskListener = new WeakReference<>(taskListener);
        }

        @Override
        @Nullable
        @WorkerThread
        protected ArrayList<Editions.Edition> doInBackground(final Void... params) {
            Thread.currentThread().setName("ISFDBGetEditionsTask " + mIsbn);
            try {
                return new Editions().fetch(mIsbn);
            } catch (@NonNull final SocketTimeoutException e) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                    Logger.warn(this, "doInBackground", e.getLocalizedMessage());
                }
                return null;
            }
        }

        @Override
        @UiThread
        protected void onPostExecute(@Nullable final ArrayList<Editions.Edition> result) {
            // always send result, even if empty
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGotISFDBEditions(result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
        }
    }

    private static class ISFDBGetBookTask
            extends AsyncTask<Void, Void, Bundle> {

        private final boolean mAddSeriesFromToc;
        @NonNull
        private final WeakReference<EditBookTocFragment> mTaskListener;

        @NonNull
        private final List<Editions.Edition> mEditions;

        /**
         * Constructor.
         *
         * @param editions     List of ISFDB native ids
         * @param taskListener where to send the results to
         */
        @UiThread
        ISFDBGetBookTask(@NonNull final List<Editions.Edition> editions,
                         final boolean addSeriesFromToc,
                         @NonNull final EditBookTocFragment taskListener) {
            mEditions = editions;
            mAddSeriesFromToc = addSeriesFromToc;
            mTaskListener = new WeakReference<>(taskListener);
        }

        @Override
        @Nullable
        @WorkerThread
        protected Bundle doInBackground(final Void... params) {
            Thread.currentThread().setName("ISFDBGetBookTask");
            try {
                //TODO: do not use Application Context for String resources
                Resources resources = App.getAppContext().getResources();

                return new ISFDBBook().fetch(mEditions, mAddSeriesFromToc, false, resources);
            } catch (@NonNull final SocketTimeoutException e) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                    Logger.warn(this, "doInBackground", e.getLocalizedMessage());
                }
                return null;
            }
        }

        @Override
        @UiThread
        protected void onPostExecute(@Nullable final Bundle result) {
            // always send result, even if empty
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGotISFDBBook(result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
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

    private class TocListAdapterForEditing
            extends RecyclerViewAdapterBase<TocEntry, Holder> {

        /**
         * Constructor.
         *
         * @param context Current context
         * @param items   the list
         */
        TocListAdapterForEditing(@NonNull final Context context,
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
            holder.authorView.setText(item.getAuthor().getLabel());

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

            // long-click -> menu
            holder.rowDetailsView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getAdapterPosition());
                return true;
            });

        }
    }
}
