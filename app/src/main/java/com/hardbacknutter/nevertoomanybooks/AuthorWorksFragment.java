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

import android.content.Context;
import android.content.Intent;
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
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.AuthorWorksModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;
import com.hardbacknutter.nevertoomanybooks.widgets.FastScrollerOverlay;
import com.hardbacknutter.nevertoomanybooks.widgets.cfs.CFSRecyclerView;

/**
 * Display all TocEntry's for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 */
public class AuthorWorksFragment
        extends Fragment {

    public static final String TAG = "AuthorWorksFragment";

    /** Optional. Show the TOC. Defaults to {@code true}. */
    public static final String BKEY_WITH_TOC = TAG + ":tocs";
    /** Optional. Show the books. Defaults to {@code true}. */
    public static final String BKEY_WITH_BOOKS = TAG + ":books";

    /** The ViewModel. */
    private AuthorWorksModel mModel;
    /** The Adapter. */
    private TocAdapter mAdapter;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Mandatory
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_author_works, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mModel = new ViewModelProvider(getActivity()).get(AuthorWorksModel.class);
        mModel.init(requireArguments());

        Context context = getContext();

        //noinspection ConstantConditions
        getActivity().setTitle(mModel.getScreenTitle(context));

        //noinspection ConstantConditions
        RecyclerView listView = getView().findViewById(R.id.authorWorks);
        listView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
        listView.setLayoutManager(linearLayoutManager);
        listView.addItemDecoration(
                new DividerItemDecoration(context, linearLayoutManager.getOrientation()));

        if (!(listView instanceof CFSRecyclerView)) {
            listView.addItemDecoration(
                    new FastScrollerOverlay(context, R.drawable.fast_scroll_overlay));
        }

        mAdapter = new TocAdapter(context, mModel.getDb());
        listView.setAdapter(mAdapter);

        if (savedInstanceState == null) {
            TipManager.display(context, R.string.tip_authors_works, null);
        }
    }

    @Override
    @CallSuper
    public void onResume() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Log.d(TAG, "ENTER|onResume");
        }
        super.onResume();
        if (getActivity() instanceof BaseActivity) {
            BaseActivity activity = (BaseActivity) getActivity();
            if (activity.isGoingToRecreate()) {
                return;
            }
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Log.d(TAG, "EXIT|onResume");
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.o_author_works, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        switch (item.getItemId()) {
            case R.id.MENU_AUTHOR_WORKS_ALL:
                item.setChecked(true);
                mModel.loadTocEntries(true, true);
                mAdapter.notifyDataSetChanged();
                return true;

            case R.id.MENU_AUTHOR_WORKS_TOC:
                item.setChecked(true);
                mModel.loadTocEntries(true, false);
                mAdapter.notifyDataSetChanged();
                return true;

            case R.id.MENU_AUTHOR_WORKS_BOOKS:
                item.setChecked(true);
                mModel.loadTocEntries(false, true);
                mAdapter.notifyDataSetChanged();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onCreateContextMenu(final int position) {
        Resources r = getResources();

        TocEntry item = mModel.getTocEntries().get(position);

        //noinspection ConstantConditions
        Menu menu = MenuPicker.createMenu(getContext());
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
        TocEntry item = mModel.getTocEntries().get(position);

        //noinspection SwitchStatementWithTooFewBranches
        switch (menuItem.getItemId()) {
            case R.id.MENU_DELETE:
                switch (item.getType()) {
                    case Book:
                        //noinspection ConstantConditions
                        StandardDialogs.deleteBookAlert(getContext(), item.getTitle(),
                                                        item.getAuthors(), () -> {
                                    mModel.delTocEntry(getContext(), item);
                                    mAdapter.notifyItemRemoved(position);
                                });
                        return true;

                    case Toc:
                        //noinspection ConstantConditions
                        StandardDialogs.deleteTocEntryAlert(getContext(), item, () -> {
                            mModel.delTocEntry(getContext(), item);
                            mAdapter.notifyItemRemoved(position);
                        });
                        return true;
                }
                break;

            default:
                throw new UnexpectedValueException(menuItem.getItemId());
        }

        return false;
    }

    /**
     * User tapped on an entry; get the book(s) for that entry and display.
     *
     * @param item the TocEntry or Book
     */
    private void gotoBook(@NonNull final TocEntry item) {
        Intent intent;
        switch (item.getType()) {
            case Toc:
                final ArrayList<Long> bookIds = mModel.getBookIds(item);
                // story in one book, goto that book.
                if (bookIds.size() == 1) {
                    intent = new Intent(getContext(), BookDetailsActivity.class)
                            .putExtra(DBDefinitions.KEY_PK_ID, bookIds.get(0));
                    startActivity(intent);

                } else {
                    // multiple books, go to the list, filtering on the books.
                    intent = new Intent(getContext(), BooksOnBookshelf.class)
                            // clear the back-stack.
                            // We want to keep BooksOnBookshelf on top
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            // bring up list, filtered on the book ID's
                            .putExtra(UniqueId.BKEY_ID_LIST, bookIds)
                            // if we don't expand, then you often end up
                            // with the author as a single line, and no books shown
                            // which is quite confusing to the user.
                            .putExtra(BooksOnBookshelfModel.BKEY_LIST_STATE,
                                      BooklistBuilder.PREF_LIST_REBUILD_ALWAYS_EXPANDED);
                    startActivity(intent);
                }
                break;

            case Book:
                intent = new Intent(getContext(), BookDetailsActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID, item.getId());
                startActivity(intent);
                break;
        }

        //noinspection ConstantConditions
        getActivity().finish();
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView titleView;
        @Nullable
        final TextView authorView;
        @Nullable
        final TextView firstPublicationView;
        @Nullable
        final CompoundButton multipleBooksView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.title);
            // optional
            authorView = itemView.findViewById(R.id.author);
            // optional
            firstPublicationView = itemView.findViewById(R.id.year);
            // optional icon to indicate a story appears in more than one book
            multipleBooksView = itemView.findViewById(R.id.cbx_multiple_books);
        }
    }

    public class TocAdapter
            extends RecyclerView.Adapter<Holder>
            implements FastScrollerOverlay.SectionIndexerV2 {

        /** Caching the inflater. */
        private final LayoutInflater mInflater;
        private final DAO mDb;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param db      Database Access
         */
        TocAdapter(@NonNull final Context context,
                   @NonNull final DAO db) {
            mInflater = LayoutInflater.from(context);
            mDb = db;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            TocEntry.Type type = TocEntry.Type.get((char) viewType);
            View itemView;
            switch (type) {
                case Toc:
                    itemView = mInflater.inflate(R.layout.row_toc_entry, parent, false);
                    break;
                case Book:
                    itemView = mInflater.inflate(R.layout.row_toc_entry_book, parent, false);
                    break;
                default:
                    throw new UnexpectedValueException(viewType);
            }

            return new Holder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            TocEntry tocEntry = mModel.getTocEntries().get(position);

            String title = tocEntry.getTitle();
            //noinspection ConstantConditions
            if (Prefs.reorderTitleForDisplaying(getContext())) {
                Locale locale = tocEntry.getLocale(getContext(), mDb, Locale.getDefault());
                title = LocaleUtils.reorderTitle(getContext(), title, locale);
            }
            holder.titleView.setText(title);

            // optional
            if (holder.authorView != null) {
                holder.authorView.setText(tocEntry.getAuthor().getLabel(getContext()));
            }
            // optional
            if (holder.firstPublicationView != null) {
                String date = tocEntry.getFirstPublication();
                if (date.isEmpty()) {
                    holder.firstPublicationView.setVisibility(View.GONE);
                } else {
                    String fp = holder.firstPublicationView
                            .getContext().getString(R.string.brackets, date);
                    holder.firstPublicationView.setText(fp);
                    holder.firstPublicationView.setVisibility(View.VISIBLE);
                }
            }
            // optional
            if (holder.multipleBooksView != null) {
                boolean isSet = tocEntry.getBookCount() > 1;
                holder.multipleBooksView.setChecked(isSet);
                // Using INVISIBLE, to get the proper margin just like other rows.
                holder.multipleBooksView.setVisibility(isSet ? View.VISIBLE : View.INVISIBLE);
            }

            // click -> get the book(s) for that entry and display.
            holder.itemView.setOnClickListener(v -> gotoBook(tocEntry));

            holder.itemView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getAdapterPosition());
                return true;
            });
        }

        @Override
        public int getItemViewType(final int position) {
            return mModel.getTocEntries().get(position).getType().getChar();
        }

        @Override
        public int getItemCount() {
            return mModel.getTocEntries().size();
        }

        @Nullable
        @Override
        public String[] getSectionText(@NonNull final Context context,
                                       final int position) {
            // make sure it's still in range.
            int index = MathUtils.clamp(position, 0, mModel.getTocEntries().size() - 1);
            return new String[]{mModel.getTocEntries().get(index)
                                      .getTitle()
                                      .substring(0, 1)
                                        .toUpperCase(Locale.getDefault())};
        }
    }
}
