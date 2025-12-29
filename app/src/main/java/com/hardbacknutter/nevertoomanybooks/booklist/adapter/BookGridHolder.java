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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.covers.ImageViewSize;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfGridBookBinding;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.OnRowClickListener;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;

/**
 * ViewHolder for a {@link BooklistGroup#BOOK} row.
 * <p>
 * This holder will disregard the cover visibility setting
 * and simply show either the frontcover, or a title-placeholder (and optional author).
 * <p>
 * Detail and context menu buttons are always shown regardless of user ExtMenuButton preference.
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

    private final boolean useAuthor;

    /** Only active when running in debug mode; displays the "position/rowId" for a book. */
    @Nullable
    private BookDebugRowIdView dbgRowIdView;

    /**
     * Constructor.
     *
     * @param itemView      the view specific for this holder
     * @param style         to use
     * @param imageViewSize to use
     * @param coverHelper   to use
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    BookGridHolder(@NonNull final View itemView,
                   @NonNull final Style style,
                   @NonNull final ImageViewSize imageViewSize,
                   @NonNull final CoverHelper coverHelper) {
        super(itemView);
        vb = BooksonbookshelfGridBookBinding.bind(itemView);

        this.style = style;
        useAuthor = style.isShowField(FieldVisibility.Screen.List, DBKey.FK_AUTHOR);

        // Enforce the width/height of the image itself.
        final ViewGroup.LayoutParams lp = vb.coverImage0.getLayoutParams();
        lp.width = imageViewSize.width;
        lp.height = imageViewSize.height;

        this.coverHelper = coverHelper;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
            dbgRowIdView = new BookDebugRowIdView(vb.content);
        }
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
                // Do not go overkill here by adding a full ImageHandler.
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
    public void setOnRowLongClickListener(@Nullable final ExtMenuButton contextMenuMode,
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
                    // Force-hide the context menu button.
                    super.setOnRowLongClickListener(ExtMenuButton.None, listener);
                    break;
                }
                case Ignore:
                    // Force-show the context menu button,
                    // as long-clicking the background is not easy/possible in grid=mode.
                    super.setOnRowLongClickListener(ExtMenuButton.Always, listener);
                    break;
            }
        } else {
            vb.coverImage0.setOnLongClickListener(null);
            super.setOnRowLongClickListener(contextMenuMode, null);
        }
    }

    @Override
    public void onFastScroll(final boolean isDragging) {
        vb.content.setVisibility(isDragging ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public void onBind(@NonNull final DataHolder rowData) {
        vb.content.setVisibility(View.VISIBLE);

        final boolean hasImage = coverHelper.setImageView(vb.coverImage0,
                                                          rowData.getString(DBKey.BOOK_UUID));
        if (hasImage) {
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
                vb.author.setText(rowData.getString(DBKey.AUTHOR.FORMATTED_FULL_NAME));
                vb.author.setVisibility(View.VISIBLE);
            } else {
                vb.author.setText(null);
                vb.author.setVisibility(View.GONE);
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
            if (dbgRowIdView != null) {
                dbgRowIdView.onBind(getBindingAdapterPosition(),
                                    rowData.getLong(DBKey.BL_NODE.ROW_ID));
            }
        }
    }
}
