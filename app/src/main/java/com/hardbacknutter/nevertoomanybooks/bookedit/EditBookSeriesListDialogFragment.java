/*
 * @Copyright 2018-2022 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.ShowContextMenu;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookSeriesListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Edit the list of Series of a Book.
 */
public class EditBookSeriesListDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditBookSeriesListDlg";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_SERIES =
            TAG + ":rk:" + EditBookSeriesDialogFragment.TAG;

    /** The book. Must be in the Activity scope. */
    private EditBookViewModel vm;
    /** View Binding. */
    private DialogEditBookSeriesListBinding vb;
    /** the rows. */
    private List<Series> seriesList;
    /** React to list changes. */
    private final SimpleAdapterDataObserver adapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    vm.getBook().setStage(EntityStage.Stage.Dirty);
                    vm.updateSeries(seriesList);
                }
            };
    /** The adapter for the list itself. */
    private SeriesListAdapter adapter;

    private ExtPopupMenu contextMenu;

    private final EditBookSeriesDialogFragment.Launcher editLauncher =
            new EditBookSeriesDialogFragment.Launcher() {
                @Override
                public void onAdd(@NonNull final Series series) {
                    add(series);
                }

                @Override
                public void onModified(@NonNull final Series original,
                                       @NonNull final Series modified) {
                    processChanges(original, modified);
                }
            };

    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookSeriesListDialogFragment() {
        super(R.layout.dialog_edit_book_series_list);
        setForceFullscreen();
    }

    /**
     * Constructor.
     *
     * @param fm The FragmentManager this fragment will be added to.
     */
    public static void launch(@NonNull final FragmentManager fm) {
        new EditBookSeriesListDialogFragment()
                .show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        editLauncher.registerForFragmentResult(getChildFragmentManager(),
                                               EditBookSeriesDialogFragment.BKEY_REQUEST_KEY,
                                               RK_EDIT_SERIES,
                                               this);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vb = DialogEditBookSeriesListBinding.bind(view);

        //noinspection ConstantConditions
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);

        vb.toolbar.setSubtitle(vm.getBook().getTitle());

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> titleAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAllSeriesTitles());
        vb.seriesTitle.setAdapter(titleAdapter);
        autoRemoveError(vb.seriesTitle, vb.lblSeriesTitle);

        // soft-keyboards 'done' button act as a shortcut to add the series
        vb.seriesNum.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v);
                onAdd(false);
                return true;
            }
            return false;
        });

        contextMenu = MenuUtils.createEditDeleteContextMenu(getContext());
        initListView();

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(adapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.seriesList);
    }

    private void initListView() {
        final Context context = getContext();

        seriesList = vm.getBook().getSeries();

        //noinspection ConstantConditions
        adapter = new SeriesListAdapter(context, seriesList,
                                        vh -> itemTouchHelper.startDrag(vh));
        adapter.setOnRowClickListener((v, position) -> editEntry(position));
        adapter.setOnRowShowMenuListener(
                ShowContextMenu.getPreferredMode(context),
                (v, position) -> contextMenu
                        .showAsDropDown(v, menuItem -> onMenuItemSelected(menuItem, position)));


        adapter.registerAdapterDataObserver(adapterDataObserver);
        vb.seriesList.setAdapter(adapter);

        vb.seriesList.setHasFixedSize(true);
    }

    private void editEntry(final int position) {
        final Book book = vm.getBook();
        editLauncher.launch(book.getTitle(),
                            book.getString(DBKey.LANGUAGE),
                            EditAction.Edit,
                            seriesList.get(position));
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(@NonNull final MenuItem menuItem,
                                       final int position) {
        final int itemId = menuItem.getItemId();

        if (itemId == R.id.MENU_EDIT) {
            editEntry(position);
            return true;

        } else if (itemId == R.id.MENU_DELETE) {
            // simply remove and refresh
            seriesList.remove(position);
            adapter.notifyItemRemoved(position);
            return true;
        }
        return false;
    }


    @Override
    public void onDestroyView() {
        adapter.unregisterAdapterDataObserver(adapterDataObserver);
        super.onDestroyView();
    }

    @Override
    protected void onToolbarNavigationClick(@NonNull final View v) {
        if (saveChanges()) {
            dismiss();
        }
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem menuItem,
                                             @Nullable final Button button) {
        if (menuItem.getItemId() == R.id.MENU_ACTION_CONFIRM && button != null) {
            // R.id.btn_add
            // R.id.btn_add_details
            onAdd(button.getId() == R.id.btn_add_details);
            return true;
        }
        return false;
    }

    /**
     * Create a new entry.
     *
     * @param withDetails {@code true} to use the detailed dialog to add a Series
     *                    {@code false} to just add the Series name as-is
     */
    private void onAdd(final boolean withDetails) {
        // clear any previous error
        vb.lblSeriesTitle.setError(null);

        final String title = vb.seriesTitle.getText().toString().trim();
        if (title.isEmpty()) {
            vb.lblSeriesTitle.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        final Series series = new Series(title);
        //noinspection ConstantConditions
        series.setNumber(vb.seriesNum.getText().toString().trim());
        if (withDetails) {
            final Book book = vm.getBook();
            editLauncher.launch(book.getTitle(),
                                book.getString(DBKey.LANGUAGE),
                                EditAction.Add,
                                series);
        } else {
            add(series);
        }
    }

    /**
     * Add the given Series to the list, providing it's not already there.
     *
     * @param series to add
     */
    private void add(@NonNull final Series series) {
        // see if it already exists
        //noinspection ConstantConditions
        vm.fixId(getContext(), series);
        // and check it's not already in the list.
        if (seriesList.contains(series)) {
            vb.lblSeriesTitle.setError(getString(R.string.warning_already_in_list));
        } else {
            // add and scroll to the new item
            seriesList.add(series);
            adapter.notifyItemInserted(seriesList.size() - 1);
            vb.seriesList.scrollToPosition(adapter.getItemCount() - 1);

            // clear the form for next entry
            vb.seriesTitle.setText("");
            vb.seriesNum.setText("");
            vb.seriesTitle.requestFocus();
        }
    }

    private boolean saveChanges() {
        if (!vb.seriesTitle.getText().toString().isEmpty()) {
            // Discarding applies to the edit field(s) only.
            //noinspection ConstantConditions
            StandardDialogs.unsavedEdits(getContext(), null, () -> {
                vb.seriesTitle.setText("");
                if (saveChanges()) {
                    dismiss();
                }
            });
            return false;
        }

        // The list itself is already saved by the adapterDataObserver
        return true;
    }

    /**
     * Process the modified (if any) data.
     *
     * @param original the original data the user was editing
     * @param modified the modifications the user made in a placeholder object.
     *                 Non-modified data was copied here as well.
     */
    @SuppressLint("NotifyDataSetChanged")
    private void processChanges(@NonNull final Series original,
                                @NonNull final Series modified) {

        final Context context = getContext();

        // The name was not changed OR
        // the name was modified but not used by any other books.
        //noinspection ConstantConditions
        if (original.getTitle().equals(modified.getTitle())
            || vm.isSingleUsage(context, original)) {

            original.copyFrom(modified, true);
            adapter.notifyDataSetChanged();

        } else {
            // Object was modified and it's used in more than one place.
            // We need to ask the user if they want to make the changes globally.
            StandardDialogs.confirmScopeForChange(
                    context, context.getString(R.string.lbl_series),
                    //TODO: if the names are the same, we should probably state
                    // that some other attribute was changed
                    original.getLabel(context), modified.getLabel(context),
                    () -> changeForAllBooks(original, modified),
                    () -> changeForThisBook(original, modified));
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void changeForAllBooks(@NonNull final Series original,
                                   @NonNull final Series modified) {
        // This change is done in the database right NOW!
        //noinspection ConstantConditions
        if (vm.changeForAllBooks(getContext(), original, modified)) {
            adapter.notifyDataSetChanged();
        } else {
            StandardDialogs.showError(getContext(), R.string.error_storage_not_writable);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void changeForThisBook(@NonNull final Series original,
                                   @NonNull final Series modified) {
        // treat the new data as a new Series; save it so we have a valid id.
        // Note that if the user abandons the entire book edit,
        // we will orphan this new Series. That's ok, it will get
        // garbage collected from the database sooner or later.
        //noinspection ConstantConditions
        if (vm.changeForThisBook(getContext(), original, modified)) {
            adapter.notifyDataSetChanged();
        } else {
            StandardDialogs.showError(getContext(), R.string.error_storage_not_writable);
        }
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        @NonNull
        final TextView seriesView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            seriesView = itemView.findViewById(R.id.row_series);
        }
    }

    private static class SeriesListAdapter
            extends RecyclerViewAdapterBase<Series, Holder> {


        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of Series
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        SeriesListAdapter(@NonNull final Context context,
                          @NonNull final List<Series> items,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_series_list, parent, false);
            final Holder holder = new Holder(view);
            holder.setOnRowClickListener(rowClickListener);
            holder.setOnRowShowContextMenuListener(contextMenuMode, rowShowMenuListener);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Series series = getItem(position);
            holder.seriesView.setText(series.getLabel(getContext()));
        }
    }
}
