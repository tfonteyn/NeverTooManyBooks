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
package com.hardbacknutter.nevertoomanybooks.goodreads.api;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

public final class ApiUtils {

    private ApiUtils() {
    }

    /**
     * Construct a full or partial date string based on the y/m/d fields.
     * <p>
     * If successful, <strong>removes</strong> the individual keys
     *
     * @param source    Bundle to use
     * @param yearKey   bundle key
     * @param monthKey  bundle key
     * @param dayKey    bundle key
     * @param resultKey key to write the full/partial date to
     */
    public static void buildDate(@NonNull final Bundle source,
                                 @NonNull final String yearKey,
                                 @NonNull final String monthKey,
                                 @NonNull final String dayKey,
                                 @Nullable final String resultKey) {

        if (source.containsKey(yearKey)) {
            String date = String.format(Locale.ENGLISH, "%04d", source.getLong(yearKey));
            if (source.containsKey(monthKey)) {
                date += '-' + String.format(Locale.ENGLISH, "%02d", source.getLong(monthKey));
                if (source.containsKey(dayKey)) {
                    date += '-' + String.format(Locale.ENGLISH, "%02d", source.getLong(dayKey));
                }
            }
            if (resultKey != null && !date.isEmpty()) {
                source.putString(resultKey, date);
                source.remove(yearKey);
                source.remove(monthKey);
                source.remove(dayKey);
            }
        }
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
        final String largeImage = goodreadsData.getString(keyLargeImageUrl);
        final String smallImage = goodreadsData.getString(keySmallImageUrl);

        final String url;
        final ImageFileInfo.Size size;

        if (hasCover(largeImage)) {
            size = ImageFileInfo.Size.Large;
            url = largeImage;

        } else if (hasCover(smallImage)) {
            size = ImageFileInfo.Size.Small;
            url = smallImage;

        } else {
            return null;
        }

        final ImageDownloader imageDownloader = new ImageDownloader();

        imageDownloader.setTimeouts(GoodreadsManager.CONNECTION_TIMEOUT_MS,
                                    GoodreadsManager.READ_TIMEOUT_MS);
        imageDownloader.setThrottler(GoodreadsSearchEngine.THROTTLER);

        try {
            final File tmpFile = imageDownloader.createTmpFile(
                    context, GoodreadsManager.FILENAME_SUFFIX,
                    String.valueOf(goodreadsData.getLong(
                            DBKeys.KEY_ESID_GOODREADS_BOOK)),
                    0, size);

            final File file = imageDownloader.fetch(context, url, tmpFile);
            if (file != null) {
                return file.getAbsolutePath();
            }
        } catch (@NonNull final ExternalStorageException ignore) {
            // ignore
        }

        return null;
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
        final String name = url.toLowerCase(AppLocale.getInstance().getSystemLocale());
        // these string can be part of an image 'name' indicating there is no cover image.
        return !name.contains("nophoto") && !name.contains("nocover");
    }
}
