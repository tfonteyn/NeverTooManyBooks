/*
 * @Copyright 2018-2023 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.booklist.header;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfHeaderBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;

public class HeaderViewHolder
        extends RecyclerView.ViewHolder
        implements BindableViewHolder<BooklistHeader> {

    @NonNull
    private final BooksonbookshelfHeaderBinding vb;

    HeaderViewHolder(@NonNull final BooksonbookshelfHeaderBinding vb) {
        super(vb.getRoot());
        this.vb = vb;
    }

    @Override
    public void onBind(@NonNull final BooklistHeader headerContent) {
        String header;
        header = headerContent.getStyleName();
        vb.styleName.setText(header);
        vb.styleName.setVisibility(header != null ? View.VISIBLE : View.GONE);

        header = headerContent.getFilterText();
        vb.filterText.setText(header);
        vb.filterText.setVisibility(header != null ? View.VISIBLE : View.GONE);

        header = headerContent.getSearchText();
        vb.searchText.setText(header);
        vb.searchText.setVisibility(header != null ? View.VISIBLE : View.GONE);

        header = headerContent.getBookCount();
        vb.bookCount.setText(header);
        vb.bookCount.setVisibility(header != null ? View.VISIBLE : View.GONE);
    }
}
