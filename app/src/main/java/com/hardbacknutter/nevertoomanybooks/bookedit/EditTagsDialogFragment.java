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
import android.view.LayoutInflater;
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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTagsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditTagListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexToolbar;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.EditStringLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.OnRowClickListener;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.SimpleAdapterDataObserver;

public class EditTagsDialogFragment
        extends DialogFragment
        implements FlexToolbar {

    /** Fragment/Log tag. */
    private static final String TAG = "EditTagsDialogFragment";
    private static final String RK_EDIT_TAG = TAG + ":rk:tag";

    /** Book View model. Activity scope. */
    private EditBookViewModel vm;
    /** View Binding. */
    private DialogEditBookTagsBinding vb;
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
        new EditTagsDialogFragment()
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

        initToolbar(this, DialogType.Fullscreen, vb.toolbar);
        vb.toolbar.setSubtitle(vm.getBook().getTitle());

        //noinspection DataFlowIssue
        vb.fab.setOnClickListener(v -> editStringLauncher.launch(getActivity(),
                                                                 getString(R.string.action_add),
                                                                 null,
                                                                 "",
                                                                 null));

        editStringLauncher = new EditStringLauncher(RK_EDIT_TAG);
        editStringLauncher.setResultListener((previousValue, currentValue, extras)
                                                     -> bookTagsAdapter.add(new Tag(currentValue)));
        editStringLauncher.registerForFragmentResult(getChildFragmentManager(), this);

        bookTags = vm.getBook().getTags();
        final List<Tag> availableTags = vm.getAllTags();
        availableTags.removeAll(bookTags);

        final Context context = getContext();
        //noinspection DataFlowIssue
        final TagAdapter availableTagsAdapter = new TagAdapter(context, availableTags);
        bookTagsAdapter = new TagAdapter(context, bookTags);
        availableTagsAdapter.setDestination(bookTagsAdapter);
        bookTagsAdapter.setDestination(availableTagsAdapter);

        bookTagsAdapter.registerAdapterDataObserver(bookAdapterObserver);

        vb.tagAvailable.setAdapter(availableTagsAdapter);
        vb.tagsInBook.setAdapter(bookTagsAdapter);
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
    public boolean onToolbarButtonClick(@Nullable final View button) {
        return false;
    }

    private boolean saveChanges() {
        // The list itself is already saved by the adapterDataObserver
        return true;
    }

    private static class Holder
            extends RecyclerView.ViewHolder
            implements BindableViewHolder<Tag> {

        @NonNull
        private final RowEditTagListBinding vb;

        Holder(@NonNull final RowEditTagListBinding vb,
               @NonNull final OnRowClickListener onRowClickListener) {
            super(vb.getRoot());
            this.vb = vb;

            this.vb.name.setOnClickListener(v -> onRowClickListener
                    .onClick(v, getBindingAdapterPosition()));
        }

        @Override
        public void onBind(@NonNull final Tag tag) {
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
                final Tag tag = tags.get(position);
                destination.add(tag);
                tags.remove(tag);
                notifyItemRemoved(position);
            };
        }

        void add(@NonNull final Tag tag) {
            // Sanity check: reject duplicates
            if (tags.stream()
                    .map(Tag::getName)
                    .noneMatch(name -> name.equals(tag.getName()))) {
                // find insertion point without resorting,
                // using a brute-force sequential search...
                int i = 0;
                while (i < tags.size() && tags.get(i).compareTo(tag) < 0) {
                    i++;
                }
                tags.add(i, tag);
                notifyItemInserted(i);
            }
        }

        /**
         * Set the "other side" to move tags to.
         *
         * @param destination other tag list
         */
        public void setDestination(@NonNull final TagAdapter destination) {
            this.destination = destination;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowEditTagListBinding vb = RowEditTagListBinding.inflate(inflater, parent,
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
