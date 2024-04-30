/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookSeriesListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.ToolbarWithActionButtons;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLocation;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;

/**
 * Edit the list of Series of a Book.
 * <p>
 * DialogFragment: we need to display this on top of edit-book fragment(s)
 * which run inside a ViewPager. It's just much easier to show this as a fullscreen dialog.
 * We're going to show a Dialog/BottomSheet on top of it, so it should be fullscreen anyhow.
 */
public class EditBookSeriesListDialogFragment
        extends androidx.fragment.app.DialogFragment
        implements ToolbarWithActionButtons {

    /** Fragment/Log tag. */
    private static final String TAG = "EditBookSeriesListDlg";
    private static final String RK_MENU = TAG + ":menu";

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
    private EditParcelableLauncher<Series> editLauncher;
    private ExtMenuLauncher menuLauncher;

    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;

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

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);

        final FragmentManager fm = getChildFragmentManager();

        editLauncher = new EditParcelableLauncher<>(
                DialogLauncher.RK_EDIT_BOOK_SERIES,
                this::add, this::processChanges);
        editLauncher.registerForFragmentResult(fm, this);

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = DialogEditBookSeriesListBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final Dialog dialog = new Dialog(requireContext(), R.style.Theme_App_FullScreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @CallSuper
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initToolbarActionButtons(vb.toolbar, Menu.NONE, this);
        vb.toolbar.setSubtitle(vm.getBook().getTitle());

        //noinspection DataFlowIssue
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

        initListView();

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(adapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.seriesList);
    }

    private void initListView() {
        final Context context = getContext();

        seriesList = vm.getBook().getSeries();

        //noinspection DataFlowIssue
        adapter = new SeriesListAdapter(context, seriesList,
                                        vh -> itemTouchHelper.startDrag(vh));
        adapter.setOnRowClickListener((v, position) -> editEntry(position));
        adapter.setOnRowShowMenuListener(
                ExtMenuButton.getPreferredMode(context),
                (v, position) -> {
                    final Menu menu = MenuUtils.createEditDeleteContextMenu(v.getContext());
                    //noinspection DataFlowIssue
                    final ExtMenuLocation location = ExtMenuLocation
                            .getLocation(getActivity(), menu);
                    if (location.isPopup()) {
                        new ExtMenuPopupWindow(v.getContext())
                                .setListener(this::onMenuItemSelected)
                                .setPosition(position)
                                .setMenu(menu, true)
                                .show(v, location);
                    } else {
                        menuLauncher.launch(getActivity(), position, null, null,
                                            menu, true);
                    }
                });

        adapter.registerAdapterDataObserver(adapterDataObserver);
        vb.seriesList.setAdapter(adapter);

        vb.seriesList.setHasFixedSize(true);
    }

    private void editEntry(final int position) {
        //noinspection DataFlowIssue
        editLauncher.launch(getActivity(), EditAction.Edit, seriesList.get(position));
    }

    /**
     * Menu selection listener.
     *
     * @param position   in the list
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(final int position,
                                       @IdRes final int menuItemId) {

        if (menuItemId == R.id.MENU_EDIT) {
            editEntry(position);
            return true;

        } else if (menuItemId == R.id.MENU_DELETE) {
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
    public void onToolbarNavigationClick(@NonNull final View v) {
        if (saveChanges()) {
            dismiss();
        }
    }

    @Override
    public boolean onToolbarMenuItemClick(@Nullable final MenuItem menuItem) {
        return false;
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            // Fullscreen only;
            // R.id.btn_add
            // R.id.btn_add_details (2024-03-28: not used/available for now)
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
        //noinspection DataFlowIssue
        series.setNumber(vb.seriesNum.getText().toString().trim());
        if (withDetails) {
            //noinspection DataFlowIssue
            editLauncher.launch(getActivity(), EditAction.Add, series);
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
        //noinspection DataFlowIssue
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
            //noinspection DataFlowIssue
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
        //noinspection DataFlowIssue
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
        try {
            //noinspection DataFlowIssue
            vm.changeForAllBooks(getContext(), original, modified);
            adapter.notifyDataSetChanged();

        } catch (@NonNull final DaoWriteException e) {
            ErrorDialog.show(getContext(), TAG, e);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void changeForThisBook(@NonNull final Series original,
                                   @NonNull final Series modified) {
        // treat the new data as a new Series; save it so we have a valid id.
        // Note that if the user abandons the entire book edit,
        // we will orphan this new Series. That's ok, it will get
        // garbage collected from the database sooner or later.
        try {
            //noinspection DataFlowIssue
            vm.changeForThisBook(getContext(), original, modified);
            adapter.notifyDataSetChanged();

        } catch (@NonNull final DaoWriteException e) {
            ErrorDialog.show(getContext(), TAG, e);
        }
    }

    @Override
    @CallSuper
    public void onDismiss(@NonNull final DialogInterface dialog) {
        // Depending on how we close the dialog, the onscreen keyboard sometimes stays up.
        final View view = getView();
        if (view != null) {
            // dismiss it manually
            hideKeyboard(view);
        }
        super.onDismiss(dialog);
    }

    /**
     * Hide the keyboard.
     *
     * @param view a View from which we can get the window token.
     */
    private void hideKeyboard(@NonNull final View view) {
        final InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends CheckableDragDropViewHolder
            implements BindableViewHolder<Series> {

        @NonNull
        private final TextView seriesView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            seriesView = itemView.findViewById(R.id.row_series);
        }

        @Override
        public void onBind(@NonNull final Series series) {
            final Context context = itemView.getContext();
            seriesView.setText(series.getLabel(context));

        }
    }

    private static class SeriesListAdapter
            extends BaseDragDropRecyclerViewAdapter<Series, Holder> {

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
            holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);
            holder.onBind(getItem(position));
        }
    }
}
