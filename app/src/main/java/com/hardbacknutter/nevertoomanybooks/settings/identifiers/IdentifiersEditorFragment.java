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

package com.hardbacknutter.nevertoomanybooks.settings.identifiers;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditIdentifiersBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditIdentifierBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

@Keep
public class IdentifiersEditorFragment
        extends BaseFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "IdentifiersEditorFrag";

    private IdentifiersEditorViewModel vm;
    /** View Binding. */
    private FragmentEditIdentifiersBinding vb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(IdentifiersEditorViewModel.class);
        vm.init(getArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditIdentifiersBinding.inflate(getLayoutInflater(), container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Allow edge-to-edge for the root view, but apply margin insets to the list itself.
        InsetsListenerBuilder.apply(vb.list);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_identifiers);

        final Context context = getContext();

        //noinspection DataFlowIssue
        vb.list.addItemDecoration(
                new MaterialDividerItemDecoration(context, RecyclerView.VERTICAL));
        vb.list.setHasFixedSize(true);

        final IdentifierAdapter adapter = new IdentifierAdapter(context, vm.getIdentifiers());
        vb.list.setAdapter(adapter);
    }

    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowEditIdentifierBinding vb;
        @NonNull
        private final HtmlFormatter<String> htmlFormatter;

        Holder(@NonNull final RowEditIdentifierBinding vb,
               @NonNull final HtmlFormatter<String> htmlFormatter) {
            super(vb.getRoot());
            this.vb = vb;
            this.htmlFormatter = htmlFormatter;
        }

        void onBind(@NonNull final Identifier identifier) {
            vb.key.setText(identifier.getKey());
            vb.name.setText(identifier.getName());

            final String siteUrl = identifier.getSiteUrl(itemView.getContext());
            if (siteUrl != null && !siteUrl.isEmpty()) {
                htmlFormatter.apply(siteUrl, vb.siteUrl);
                vb.siteUrl.setVisibility(View.VISIBLE);
            } else {
                vb.siteUrl.setVisibility(View.GONE);
            }
        }
    }

    private static class IdentifierAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final List<Identifier> identifiers;
        private final LayoutInflater inflater;
        private final HtmlFormatter<String> htmlFormatter;

        IdentifierAdapter(@NonNull final Context context,
                          @NonNull final List<Identifier> identifiers) {
            inflater = LayoutInflater.from(context);
            this.identifiers = identifiers;

            htmlFormatter = new HtmlFormatter<String>()
                    .setEnableLinks(true);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowEditIdentifierBinding vb = RowEditIdentifierBinding
                    .inflate(inflater, parent, false);
            return new Holder(vb, htmlFormatter);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            holder.onBind(identifiers.get(position));
        }

        @Override
        public int getItemCount() {
            return identifiers.size();
        }
    }
}
