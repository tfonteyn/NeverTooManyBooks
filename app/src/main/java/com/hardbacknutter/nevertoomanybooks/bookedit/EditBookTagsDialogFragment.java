/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTagsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookTagListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexToolbar;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.editstring.EditStringLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.OnRowClickListener;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.SimpleAdapterDataObserver;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.insets.Side;

/**
 * Edit the list of Tags of a Book.
 * <p>
 * This is a plain fullscreen DialogFragment.
 * Displayed on top of edit-book fragment(s) which run inside a ViewPager.
 */
public class EditBookTagsDialogFragment
        extends DialogFragment
        implements FlexToolbar {

    /** Fragment/Log tag. */
    private static final String TAG = "EditBookTagsDialogFragment";
    private static final String RK_EDIT_TAG = TAG + ":rk:tag";

    /** Book View model. Activity scope. */
    private EditBookViewModel vm;
    /** View Binding. */
    private DialogEditBookTagsBinding vb;
    private TagAdapter availableTagsAdapter;
    private TagAdapter bookTagsAdapter;
    private List<Tag> bookTags;
    /** React to list changes. */
    private final SimpleAdapterDataObserver bookAdapterObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    vm.getBook().setStage(EntityStage.Stage.Dirty);
                    vm.updateTags(bookTags);
                }
            };
    private EditStringLauncher editStringLauncher;

    /**
     * Constructor.
     *
     * @param fm The FragmentManager this fragment will be added to.
     */
    public static void launch(@NonNull final FragmentManager fm) {
        new EditBookTagsDialogFragment()
                .show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = DialogEditBookTagsBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // See notes in {@link FlexClassicDialogFragment#onCreateDialog}
        return new Dialog(requireContext(), R.style.Theme_App);
    }

    @CallSuper
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        InsetsListenerBuilder.create(vb.tagAvailable)
                             .padding(Side.Start, Side.Bottom)
                             .systemBars()
                             .displayCutout()
                             .ime()
                             .apply();
        InsetsListenerBuilder.create(vb.tagsInBook)
                             .padding(Side.End, Side.Bottom)
                             .systemBars()
                             .displayCutout()
                             .ime()
                             .apply();

        initToolbar(this, DialogType.Fullscreen, vb.toolbar);
        vb.toolbar.setSubtitle(vm.getBook().getTitle());

        //noinspection DataFlowIssue
        vb.fab.setOnClickListener(v -> editStringLauncher
                .launch(getActivity(),
                        getString(R.string.action_add),
                        null,
                        InputType.TYPE_CLASS_TEXT,
                        "",
                        null));

        editStringLauncher = new EditStringLauncher(RK_EDIT_TAG);
        editStringLauncher.setResultListener((previousValue, currentValue, extras)
                                                     -> addNewTag(currentValue));
        editStringLauncher.registerForFragmentResult(getChildFragmentManager(), this);

        bookTags = vm.getBook().getTags();
        final List<Tag> availableTags = vm.getAllTags();
        availableTags.removeAll(bookTags);

        final Context context = getContext();
        //noinspection DataFlowIssue
        availableTagsAdapter = new TagAdapter(context, availableTags);
        bookTagsAdapter = new TagAdapter(context, bookTags);
        availableTagsAdapter.setDestination(bookTagsAdapter);
        bookTagsAdapter.setDestination(availableTagsAdapter);

        bookTagsAdapter.registerAdapterDataObserver(bookAdapterObserver);

        vb.tagAvailable.setAdapter(availableTagsAdapter);
        vb.tagsInBook.setAdapter(bookTagsAdapter);
    }

    private void addNewTag(final String value) {
        final Tag tag = new Tag(value);

        if (bookTagsAdapter.has(tag).isPresent()) {
            // already present in the book, we're done
            return;
        }

        final Optional<Tag> has = availableTagsAdapter.has(tag);
        if (has.isPresent()) {
            // already available, move it to the book
            availableTagsAdapter.move(has.get());
            return;
        }

        // it's new... add it
        bookTagsAdapter.add(tag);
    }

    @Override
    public void onDestroyView() {
        bookTagsAdapter.unregisterAdapterDataObserver(bookAdapterObserver);
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
        if (menuItem == null) {
            return false;
        }
        final int menuItemId = menuItem.getItemId();
        if (menuItemId == R.id.MENU_TAG_ADD_ALL) {
            availableTagsAdapter.moveAll();
            return true;

        } else if (menuItemId == R.id.MENU_TAG_REMOVE_ALL) {
            bookTagsAdapter.moveAll();
            return true;
        }
        return false;
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        return false;
    }

    private boolean saveChanges() {
        // The list itself is already saved by the adapterDataObserver
        return true;
    }

    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowEditBookTagListBinding vb;

        Holder(@NonNull final RowEditBookTagListBinding vb,
               @NonNull final OnRowClickListener onRowClickListener) {
            super(vb.getRoot());
            this.vb = vb;

            this.vb.name.setOnClickListener(v -> onRowClickListener
                    .onClick(v, getBindingAdapterPosition()));
        }

        void onBind(@NonNull final Tag tag) {
            vb.name.setText(tag.getName());
        }
    }

    private static class TagAdapter
            extends RecyclerView.Adapter<Holder> {
        @NonNull
        private final LayoutInflater inflater;
        private final OnRowClickListener onRowClickListener;
        @NonNull
        private final List<Tag> tags;

        private TagAdapter destination;

        TagAdapter(@NonNull final Context context,
                   @NonNull final List<Tag> tagList) {
            this.tags = tagList;
            inflater = LayoutInflater.from(context);

            onRowClickListener = (v, position) -> {
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                final Tag tag = tags.get(position);
                destination.add(tag);
                tags.remove(tag);
                notifyItemRemoved(position);
            };
        }

        @NonNull
        Optional<Tag> has(@NonNull final Tag t) {
            return tags.stream()
                       .filter(tag -> tag.getName().equals(t.getName()))
                       .findFirst();
        }

        void add(@NonNull final Tag tag) {
            // Sanity check: ignore duplicates
            if (has(tag).isPresent()) {
                return;
            }

            // find insertion point using a brute-force sequential search...
            int i = 0;
            while (i < tags.size() && tags.get(i).compareTo(tag) < 0) {
                i++;
            }
            tags.add(i, tag);
            notifyItemInserted(i);
        }

        /**
         * Move the given tag from this adapter(list) to the destination adapter.
         *
         * @param tag to move
         */
        void move(@NonNull final Tag tag) {
            for (int position = 0; position < tags.size(); position++) {
                final Tag tmp = tags.get(position);
                if (tmp.compareTo(tag) == 0) {
                    destination.add(tmp);
                    tags.remove(tmp);
                    notifyItemRemoved(position);
                    return;
                }
            }
        }

        /**
         * Move all tags from this adapter(list) to the destination adapter.
         */
        void moveAll() {
            for (int position = tags.size() - 1; position >= 0; position--) {
                final Tag tmp = tags.get(position);
                destination.add(tmp);
                tags.remove(tmp);
                notifyItemRemoved(position);
            }
        }

        /**
         * Set the "other side" to move tags to.
         *
         * @param destination other tag list
         */
        void setDestination(@NonNull final TagAdapter destination) {
            this.destination = destination;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowEditBookTagListBinding vb = RowEditBookTagListBinding.inflate(inflater, parent,
                                                                                   false);
            return new Holder(vb, onRowClickListener);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            holder.onBind(tags.get(position));
        }

        @Override
        public int getItemCount() {
            return tags.size();
        }
    }
}
