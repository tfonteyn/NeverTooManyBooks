/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PreferredStylesContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditStylesBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Edit the list of styles.
 * <ul>
 *      <li>Enable/disable their presence in the styles menu</li>
 *      <li>Individual context menus allow cloning/editing/deleting of styles</li>
 * </ul>
 * All changes are saved immediately.
 */
public class PreferredStylesFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "PreferredStylesFragment";

    /** View Binding. */
    private FragmentEditStylesBinding mVb;

    /** The adapter for the list. */
    private ListStylesAdapter mListAdapter;

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;
    /** The Activity ViewModel. */
    private PreferredStylesViewModel mVm;

    /** React to changes in the adapter. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {

                /** called if the user flipped the 'isPreferred' status. */
                @Override
                public void onItemRangeChanged(final int positionStart,
                                               final int itemCount) {
                    final ListStyle style = mListAdapter.getItem(positionStart);
                    // only the style was changed, update the database now
                    mVm.updateStyle(style);
                    // We'll update the list order in onPause.
                    mVm.setDirty(true);
                }

                @Override
                public void onItemRangeRemoved(final int positionStart,
                                               final int itemCount) {
                    // Deleting the style is already done.
                    // We'll update the list order in onPause.
                    mVm.setDirty(true);
                }

                @Override
                public void onItemRangeMoved(final int fromPosition,
                                             final int toPosition,
                                             final int itemCount) {
                    // warning: this will get called each time a row is moved 1 position
                    // so moving a row 5 rows up... this gets called FIVE times.

                    // We'll update the list order in onPause.
                    mVm.setDirty(true);
                }

                /** Fallback for all other types of notification (if any). */
                @Override
                public void onChanged() {
                    // We'll update the list order in onPause.
                    mVm.setDirty(true);
                }
            };

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    PreferredStylesContract.setResultAndFinish(getActivity(),
                                                               mListAdapter.getSelectedStyle(),
                                                               mVm.isDirty());
                }
            };

    private final ActivityResultLauncher<EditStyleContract.Input> mEditStyleContract =
            registerForActivityResult(new EditStyleContract(), data -> {
                if (data != null) {
                    @Nullable
                    final ListStyle style;
                    if (data.uuid != null && !data.uuid.isEmpty()) {
                        //noinspection ConstantConditions
                        style = mVm.getStyle(getContext(), data.uuid);
                    } else {
                        style = null;
                    }

                    if (data.modified) {
                        if (style != null) {
                            // calculate the (new) position in the list
                            final int position = mVm.onStyleEdited(style, data.templateUuid);
                            mListAdapter.setSelectedPosition(position);
                        }

                        // always update all rows as the order might have changed
                        mListAdapter.notifyDataSetChanged();

                    } else {
                        // The style was not modified. If this was a cloned (new) style,
                        // discard it by deleting the SharedPreferences file
                        if (style != null && style.getId() == 0) {
                            getContext().deleteSharedPreferences(style.getUuid());
                        }
                    }
                }
            });

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditStylesBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        setTitle(R.string.lbl_styles_long);

        mVm = new ViewModelProvider(this).get(PreferredStylesViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), requireArguments());

        mVb.stylesList.addItemDecoration(
                new DividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        mVb.stylesList.setHasFixedSize(true);

        // setup the adapter
        mListAdapter = new ListStylesAdapter(getContext(), mVm.getList(),
                                             mVm.getInitialStyleUuid(),
                                             vh -> mItemTouchHelper.startDrag(vh));
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mVb.stylesList.setAdapter(mListAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.stylesList);

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.getInstance()
                      .display(getContext(), R.string.tip_booklist_styles_editor, null);
        }
    }

    @Override
    public void onPause() {
        if (mVm.isDirty()) {
            mVm.updateMenuOrder();
        }
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_PURGE_BLNS, 0, R.string.lbl_purge_blns)
            .setIcon(R.drawable.ic_baseline_delete_24);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        // only enable if a style is selected
        menu.findItem(R.id.MENU_PURGE_BLNS)
            .setEnabled(mListAdapter.getSelectedStyle() != null);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_PURGE_BLNS) {
            final ListStyle selected = mListAdapter.getSelectedStyle();
            if (selected != null) {
                //noinspection ConstantConditions
                StandardDialogs.purgeBLNS(getContext(), R.string.lbl_style,
                                          selected.getLabel(getContext()), () ->
                                                  mVm.purgeBLNS(selected.getId()));
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void onCreateContextMenu(@NonNull final View anchor,
                                     final int position) {
        final Resources res = getResources();
        final ListStyle style = mVm.getList().get(position);
        //noinspection ConstantConditions
        final Menu menu = ExtPopupMenu.createMenu(getContext());
        if (style instanceof UserStyle) {
            menu.add(Menu.NONE, R.id.MENU_EDIT,
                     res.getInteger(R.integer.MENU_ORDER_EDIT),
                     R.string.action_edit_ellipsis)
                .setIcon(R.drawable.ic_baseline_edit_24);
            menu.add(Menu.NONE, R.id.MENU_DELETE,
                     res.getInteger(R.integer.MENU_ORDER_DELETE),
                     R.string.action_delete)
                .setIcon(R.drawable.ic_baseline_delete_24);
        }

        menu.add(Menu.NONE, R.id.MENU_DUPLICATE,
                 res.getInteger(R.integer.MENU_ORDER_DUPLICATE),
                 R.string.action_duplicate)
            .setIcon(R.drawable.ic_baseline_content_copy_24);

        new ExtPopupMenu(getContext(), menu, this::onContextItemSelected)
                .showAsDropDown(anchor, position);
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          final int position) {
        final int itemId = menuItem.getItemId();

        final ListStyle style = mVm.getList().get(position);

        if (itemId == R.id.MENU_EDIT) {
            // dev sanity check
            if (BuildConfig.DEBUG /* always */) {
                if (!(style instanceof UserStyle)) {
                    throw new IllegalStateException("Not a UserStyle");
                }
            }
            mEditStyleContract.launch(new EditStyleContract.Input(
                    StyleViewModel.BKEY_ACTION_EDIT, style.getUuid(), style.isPreferred()));
            return true;

        } else if (itemId == R.id.MENU_DELETE) {
            //noinspection ConstantConditions
            mVm.deleteStyle(getContext(), style);
            mListAdapter.notifyItemRemoved(position);
            return true;

        } else if (itemId == R.id.MENU_DUPLICATE) {
            mEditStyleContract.launch(new EditStyleContract.Input(
                    StyleViewModel.BKEY_ACTION_CLONE, style.getUuid(), style.isPreferred()));
            return true;
        }
        return false;
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        @NonNull
        final TextView nameView;
        @NonNull
        final TextView groupsView;
        @NonNull
        final TextView typeView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            nameView = itemView.findViewById(R.id.name);
            groupsView = itemView.findViewById(R.id.groups);
            typeView = itemView.findViewById(R.id.type);
        }
    }

    private class ListStylesAdapter
            extends RecyclerViewAdapterBase<ListStyle, Holder> {

        /** The UUID of the item which should be / is selected at creation time. */
        @NonNull
        private final String mInitialSelectedItemUuid;

        /** Currently selected row. */
        private int mSelectedPosition = RecyclerView.NO_POSITION;

        /**
         * Constructor.
         *
         * @param context                 Current context
         * @param items                   List of styles
         * @param initialSelectedItemUuid initially selected style UUID
         * @param dragStartListener       Listener to handle the user moving rows up and down
         */
        ListStylesAdapter(@NonNull final Context context,
                          @NonNull final List<ListStyle> items,
                          @NonNull final String initialSelectedItemUuid,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
            mInitialSelectedItemUuid = initialSelectedItemUuid;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_preferred_styles, parent, false);
            final Holder holder = new Holder(view);

            // click -> set the row as 'selected'.
            // Do NOT modify the 'preferred' state of the row.
            holder.rowDetailsView.setOnClickListener(v -> {
                // first update the previous, now unselected, row.
                notifyItemChanged(mSelectedPosition);
                // get and update the newly selected row.
                mSelectedPosition = holder.getBindingAdapterPosition();
                notifyItemChanged(mSelectedPosition);
            });

            holder.rowDetailsView.setOnLongClickListener(v -> {
                onCreateContextMenu(v, holder.getBindingAdapterPosition());
                return true;
            });

            // click the checkbox -> set as 'preferred'
            //noinspection ConstantConditions
            holder.mCheckableButton.setOnClickListener(v -> onItemCheckChanged(holder));

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final ListStyle style = getItem(position);

            holder.nameView.setText(style.getLabel(getContext()));

            holder.groupsView.setText(style.getGroups().getSummaryText(getContext()));
            if (style instanceof UserStyle) {
                holder.typeView.setText(R.string.style_is_user_defined);
            } else if (style instanceof BuiltinStyle) {
                holder.typeView.setText(R.string.style_is_builtin);
            } else {
                throw new IllegalStateException("Unhandled style: " + style);
            }

            // set the 'preferred style' checkable
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(style.isPreferred());

            // select the original row if there was nothing selected (yet).
            if (mSelectedPosition == RecyclerView.NO_POSITION
                && mInitialSelectedItemUuid.equals(style.getUuid())) {
                mSelectedPosition = position;
            }

            // finally update the new current row
            holder.itemView.setSelected(mSelectedPosition == position);
        }

        /**
         * The user clicked the checkable button of the row.
         * <ol>User checked the row:
         * <li>set the row/style 'preferred'</li>
         * <li>set the row 'selected'</li>
         * </ol>
         * <ol>User unchecked the row:
         * <li>set the row to 'not preferred'</li>
         * <li>look up and down in the list to find a 'preferred' row, and set it 'selected'</li>
         * </ol>
         *
         * @param holder for the checked row.
         */
        private void onItemCheckChanged(@NonNull final Holder holder) {
            // current row/style
            final int position = holder.getBindingAdapterPosition();
            final ListStyle style = getItem(position);

            // handle the 'preferred' state of the current row/style
            final boolean checked = !style.isPreferred();
            style.setPreferred(checked);

            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(checked);

            // handle the 'selected' state of the current row/style.
            if (!checked && mSelectedPosition == position) {
                //look up and down in the list to find a 'preferred' row, and set it 'selected'
                if (!findPreferred(-1) && !findPreferred(+1)) {
                    // if no such row found, force the current row regardless
                    mSelectedPosition = position;
                }
                // update the newly selected row. This might be another, or the current row.
                notifyItemRangeChanged(mSelectedPosition, 1);
            }

            // update the current row unless we've already done that above.
            if (mSelectedPosition != position) {
                // update the current row.
                notifyItemRangeChanged(position, 1);
            }
        }

        /**
         * Look up and down in the list to find a 'preferred' row.
         * If found, set it as the selected position.
         *
         * @param direction must be either '-1' or '+1'
         *
         * @return {@code true} if a suitable position was found (and set)
         */
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private boolean findPreferred(final int direction) {
            int newPosition = mSelectedPosition;
            while (true) {
                // move one up or down.
                newPosition = newPosition + direction;

                // breached the upper or lower limit ?
                if (newPosition < 0 || newPosition >= this.getItemCount()) {
                    return false;
                }

                if (getItem(newPosition).isPreferred()) {
                    mSelectedPosition = newPosition;
                    return true;
                }
            }
        }

        /**
         * Update the selection.
         *
         * @param position the newly selected row
         */
        void setSelectedPosition(final int position) {
            mSelectedPosition = position;
        }

        /**
         * Get the currently selected style.
         *
         * @return style, or {@code null} if none selected (which should never happen... flw)
         */
        @Nullable
        ListStyle getSelectedStyle() {
            if (mSelectedPosition != RecyclerView.NO_POSITION) {
                return getItem(mSelectedPosition);
            }
            return null;
        }

        @Override
        public boolean onItemMove(final int fromPosition,
                                  final int toPosition) {

            if (fromPosition == mSelectedPosition) {
                // moving the selected row.
                mSelectedPosition = toPosition;
            } else if (toPosition == mSelectedPosition) {
                if (fromPosition > mSelectedPosition) {
                    // push down
                    mSelectedPosition++;
                } else {
                    // push up
                    mSelectedPosition--;
                }
            }
            return super.onItemMove(fromPosition, toPosition);
        }
    }
}
