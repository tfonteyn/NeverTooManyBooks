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

package com.hardbacknutter.nevertoomanybooks.citations;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.provider.GenericFileProvider;
import com.hardbacknutter.util.logger.LoggerFactory;

public final class CitationFactory {

    private static final String TAG = "CitationFactory";

    private CitationFactory() {
    }

    /**
     * Create a citation formatter.
     *
     * @param style to use
     *
     * @return a new instance
     */
    @NonNull
    public static Citation create(@NonNull final Style style) {

        switch (style.getCitationType()) {
            case BibTeX:
                return new BibTeXCitation(style);
            case MLA:
                return new MLACitation(style);
            case RIS:
                return new RISCitation();
            case Default:
            default:
                return new DefaultCitation(style);
        }
    }

    /**
     * Creates a chooser with matched apps for sharing some text.
     *
     * @param context Current context
     * @param book    to cite
     * @param style   to apply
     *
     * @return the intent
     */
    @NonNull
    public static Intent getShareIntent(@NonNull final Context context,
                                        @NonNull final Book book,
                                        @NonNull final Style style) {

        final Citation citation = create(style);
        final String text = citation.cite(context, book);

        final Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);

        book.getImage(context, 0).ifPresent(file -> {
            try {
                final Uri uri = GenericFileProvider.createUri(file, book.getTitle());
                // read access to the input uri
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                      .putExtra(Intent.EXTRA_STREAM, uri);
            } catch (@NonNull final IllegalArgumentException e) {
                // Ignore the error, but log it. If the GenericFileProvider
                // is at fault, the user will be hit with this exception
                // when they add/edit covers.
                LoggerFactory.getLogger().e(TAG, e, file.getAbsolutePath());
            }
        });

        return Intent.createChooser(intent, context.getString(R.string.whichSendApplication));
    }
}
