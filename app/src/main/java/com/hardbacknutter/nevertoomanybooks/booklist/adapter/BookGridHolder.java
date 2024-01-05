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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.booklist.ShowContextMenu;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.covers.ImageViewLoader;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfGridBookBinding;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.OnRowClickListener;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;

/**
 * ViewHolder for a {@link BooklistGroup#BOOK} row.
 * <p>
 * This holder will disregard the cover visibility setting
 * and simply show either the frontcover, or a title-placeholder (and optional author).
 * <p>
 * Detail and context menu buttons are always shown regardless of user ShowContextMenu preference.
 */
public class BookGridHolder
        extends RowViewHolder
        implements BindableViewHolder<DataHolder> {

    @NonNull
    private final BooksonbookshelfGridBookBinding vb;

    @NonNull
    private final Style style;

    @NonNull
    private final CoverHelper coverHelper;

    @Nullable
    private Boolean useAuthor;

    @SuppressLint("UseCompatLoadingForDrawables")
    BookGridHolder(@NonNull final View itemView,
                   @NonNull final Style style,
                   @Dimension final int coverLongestSide) {
        super(itemView);
        this.style = style;

        vb = BooksonbookshelfGridBookBinding.bind(itemView);

        coverHelper = new CoverHelper(coverLongestSide,
                                      ImageView.ScaleType.FIT_CENTER,
                                      ImageViewLoader.MaxSize.Constrained);

        vb.gridCell.setMaxWidth(coverLongestSide);
    }

    @Override
    public void setOnRowClickListener(@Nullable final OnRowClickListener listener) {
        super.setOnRowClickListener(listener);

        if (listener != null) {
            // If there is no image, tapping title or author has the same effect
            // as tapping the background.
            vb.title.setOnClickListener(v -> listener
                    .onClick(v, getBindingAdapterPosition()));
            vb.author.setOnClickListener(v -> listener
                    .onClick(v, getBindingAdapterPosition()));

            if (style.getCoverClickAction() == Style.CoverClickAction.OpenBookDetails) {
                // Tapping the cover image will open the book-details page
                vb.coverImage0.setOnClickListener(v -> listener
                        .onClick(v, getBindingAdapterPosition()));

                // No need for the extra button to do the same
                vb.viewBookDetails.setVisibility(View.GONE);
                vb.viewBookDetails.setOnClickListener(null);

            } else {
                // Tapping the cover image will zoom the image
                // Do not go overkill here by adding a full CoverHandler.
                vb.coverImage0.setOnClickListener(coverHelper::onZoomCover);

                // Add an explicit 'view' button
                // as tapping on the background is not obvious when using the grid.
                vb.viewBookDetails.setVisibility(View.VISIBLE);
                vb.viewBookDetails.setOnClickListener(v -> listener
                        .onClick(v, getBindingAdapterPosition()));
            }
        } else {
            vb.title.setOnClickListener(null);
            vb.author.setOnClickListener(null);

            vb.viewBookDetails.setVisibility(View.GONE);
            vb.viewBookDetails.setOnClickListener(null);
        }
    }

    @Override
    public void setOnRowLongClickListener(@Nullable final ShowContextMenu contextMenuMode,
                                          @Nullable final OnRowClickListener listener) {
        if (listener != null) {
            switch (style.getCoverLongClickAction()) {
                case PopupMenu: {
                    // Explicitly set the listener on the cover
                    // in addition to the background as handled by the super method
                    vb.coverImage0.setOnLongClickListener(v -> {
                        listener.onClick(v, getBindingAdapterPosition());
                        return true;
                    });
                    // Force-hide the the context menu button.
                    super.setOnRowLongClickListener(ShowContextMenu.NoButton, listener);
                    break;
                }
                case Ignore:
                    // Force-show the context menu button,
                    // as long-clicking the background is not easy/possible in grid=mode.
                    super.setOnRowLongClickListener(ShowContextMenu.Button, listener);
                    break;
            }
        } else {
            vb.coverImage0.setOnLongClickListener(null);
            super.setOnRowLongClickListener(contextMenuMode, null);
        }
    }

    @Override
    public void onBind(@NonNull final DataHolder rowData) {
        if (useAuthor == null) {
            useAuthor = style.isShowField(FieldVisibility.Screen.List, DBKey.FK_AUTHOR);
        }

        final boolean hasImage = coverHelper.setImageView(vb.coverImage0,
                                                          rowData.getString(DBKey.BOOK_UUID));
        if (hasImage) {
            final ViewGroup.LayoutParams lp = vb.coverImage0.getLayoutParams();
            // Use start and end-constraints
            lp.width = 0;
            vb.coverImage0.setLayoutParams(lp);
            vb.coverImage0.setVisibility(View.VISIBLE);

            vb.title.setText(null);
            vb.title.setVisibility(View.GONE);
            vb.author.setText(null);
            vb.author.setVisibility(View.GONE);

        } else {
            vb.coverImage0.setVisibility(View.GONE);

            vb.title.setText(rowData.getString(DBKey.TITLE));
            vb.title.setVisibility(View.VISIBLE);
            if (useAuthor) {
                vb.author.setText(rowData.getString(DBKey.AUTHOR_FORMATTED));
                vb.author.setVisibility(View.VISIBLE);
            } else {
                vb.author.setText(null);
                vb.author.setVisibility(View.GONE);
            }
        }
    }
}
