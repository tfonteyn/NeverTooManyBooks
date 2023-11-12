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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.graphics.BitmapFactory;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public final class CoverFileSpecArray {
    /**
     * List of front/back cover file specs as collected during the search.
     * <p>
     * <br>type: {@code ArrayList<String>}
     */
    static final String[] BKEY_FILE_SPEC_ARRAY = {
            "fileSpec_array:0",
            "fileSpec_array:1"
    };

    private CoverFileSpecArray() {
    }

    /**
     * Pick the largest image from the given list, and delete all others.
     *
     * @param imageList a list of images
     *
     * @return fileSpec of cover found, or {@code null} for none.
     */
    @Nullable
    private static String getBestImage(@NonNull final List<String> imageList) {

        // biggest size based on height * width
        long bestImageSize = -1;
        // index of the file which is the biggest
        int bestFileIndex = -1;

        // Just read the image files to get file size
        final BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;

        // Loop, finding biggest image
        for (int i = 0; i < imageList.size(); i++) {
            final String fileSpec = imageList.get(i);
            if (new File(fileSpec).exists()) {
                BitmapFactory.decodeFile(fileSpec, opt);
                // If no size info, assume file bad and skip
                if (opt.outHeight > 0 && opt.outWidth > 0) {
                    final long size = (long) opt.outHeight * (long) opt.outWidth;
                    if (size > bestImageSize) {
                        bestImageSize = size;
                        bestFileIndex = i;
                    }
                }
            }
        }

        // Delete all but the best one.
        // Note there *may* be no best one, so all would be deleted. This is fine.
        for (int i = 0; i < imageList.size(); i++) {
            if (i != bestFileIndex) {
                FileUtils.delete(new File(imageList.get(i)));
            }
        }

        if (bestFileIndex >= 0) {
            return imageList.get(bestFileIndex);
        }

        return null;
    }

    /**
     * Filter the fileSpec <strong>lists</strong> present in the book,
     * selecting only the best image for each index,
     * and store those in {@link Book#BKEY_TMP_FILE_SPEC}.
     * This may result in removing ALL images if none are found suitable.
     *
     * @param book to update
     */
    public static void process(@NonNull final Book book) {
        for (int cIdx = 0; cIdx < 2; cIdx++) {
            if (book.contains(BKEY_FILE_SPEC_ARRAY[cIdx])) {
                final List<String> list = book.getStringArrayList(BKEY_FILE_SPEC_ARRAY[cIdx]);
                if (!list.isEmpty()) {
                    // ALWAYS call even if we only have 1 image...
                    // We want to remove bad ones if needed.
                    final String fileSpec = getBestImage(list);
                    if (fileSpec != null) {
                        book.putString(Book.BKEY_TMP_FILE_SPEC[cIdx], fileSpec);
                    }
                }
            }
            book.remove(BKEY_FILE_SPEC_ARRAY[cIdx]);
        }
    }

    /**
     * TESTING ONLY.
     * Get the list of cover fileSpecs for the given cover index.
     *
     * @param book to update
     * @param cIdx 0..n image index
     *
     * @return list
     */
    @VisibleForTesting
    @NonNull
    public static List<String> getList(@NonNull final Book book,
                                       @IntRange(from = 0, to = 1) final int cIdx) {
        if (book.contains(BKEY_FILE_SPEC_ARRAY[cIdx])) {
            return book.getStringArrayList(BKEY_FILE_SPEC_ARRAY[cIdx]);
        } else {
            return List.of();
        }
    }

    /**
     * Set the given fileSpec as the first/only element of the list of cover fileSpecs.
     *
     * @param book     to update
     * @param cIdx     0..n image index
     * @param fileSpec to set; use {@code null} to remove any previous
     */
    public static void setFileSpec(@NonNull final Book book,
                                   @IntRange(from = 0, to = 1) final int cIdx,
                                   @Nullable final String fileSpec) {
        if (fileSpec != null && !fileSpec.isEmpty()) {
            final ArrayList<String> fileSpecs = new ArrayList<>();
            fileSpecs.add(fileSpec);
            book.putStringArrayList(BKEY_FILE_SPEC_ARRAY[cIdx], fileSpecs);
        } else {
            book.remove(BKEY_FILE_SPEC_ARRAY[cIdx]);
        }
    }
}
