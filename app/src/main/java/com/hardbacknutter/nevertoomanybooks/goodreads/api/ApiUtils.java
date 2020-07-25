/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.goodreads.api;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public final class ApiUtils {

    private ApiUtils() {
    }

    /**
     * Construct a full or partial date string based on the y/m/d fields.
     *
     * @param source    Bundle to use
     * @param yearKey   bundle key
     * @param monthKey  bundle key
     * @param dayKey    bundle key
     * @param resultKey key to write to formatted date to
     *
     * @return the date string, or {@code null} if invalid
     */
    @Nullable
    public static String buildDate(@NonNull final Bundle source,
                                   @NonNull final String yearKey,
                                   @NonNull final String monthKey,
                                   @NonNull final String dayKey,
                                   @Nullable final String resultKey) {

        String date = null;
        if (source.containsKey(yearKey)) {
            date = String.format(Locale.ENGLISH, "%04d", source.getLong(yearKey));
            if (source.containsKey(monthKey)) {
                date += '-' + String.format(Locale.ENGLISH, "%02d", source.getLong(monthKey));
                if (source.containsKey(dayKey)) {
                    date += '-' + String.format(Locale.ENGLISH, "%02d", source.getLong(dayKey));
                }
            }
            if (resultKey != null && !date.isEmpty()) {
                source.putString(resultKey, date);
            }
        }
        return date;
    }

    /**
     * Extract an image url from the bundle, fetch and save it.
     *
     * @param context          Current context
     * @param goodreadsData    to read the url from
     * @param keyLargeImageUrl key for a large image
     * @param keySmallImageUrl key for a small image
     *
     * @return fileSpec, or {@code null} if no image found.
     */
    @Nullable
    public static String handleThumbnail(@NonNull final Context context,
                                         @NonNull final Bundle goodreadsData,
                                         @NonNull final String keyLargeImageUrl,
                                         @NonNull final String keySmallImageUrl) {
        // first check what the "best" image is that we have.
        final String url;
        final String sizeSuffix;

        final String largeImage = goodreadsData.getString(keyLargeImageUrl);
        final String smallImage = goodreadsData.getString(keySmallImageUrl);

        if (hasCover(largeImage)) {
            sizeSuffix = keyLargeImageUrl;
            url = largeImage;
        } else if (hasCover(smallImage)) {
            sizeSuffix = keySmallImageUrl;
            url = smallImage;
        } else {
            return null;
        }

        // We have an image, save it using the Goodreads book id as base name.
        final long grId = goodreadsData.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK);
        return ImageUtils.saveImage(
                context, url, String.valueOf(grId),
                GoodreadsSearchEngine.FILENAME_SUFFIX + "_" + sizeSuffix,
                0,
                GoodreadsManager.CONNECTION_TIMEOUT_MS,
                GoodreadsManager.READ_TIMEOUT_MS,
                GoodreadsSearchEngine.THROTTLER);
    }

    /**
     * Check the url for certain keywords that would indicate a cover is, or is not, present.
     *
     * @param url to check
     *
     * @return {@code true} if the url indicates there is an actual image.
     */
    private static boolean hasCover(@Nullable final String url) {
        if (url == null) {
            return false;
        }
        final String name = url.toLowerCase(LocaleUtils.getSystemLocale());
        // these string can be part of an image 'name' indicating there is no cover image.
        return !name.contains("nophoto") && !name.contains("nocover");
    }
}
