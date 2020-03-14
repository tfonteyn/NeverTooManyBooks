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
package com.hardbacknutter.nevertoomanybooks.backup.archive;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

/**
 * Archive formats (partially) supported.
 */
public enum ArchiveType {
    /** The archive we tried to read from was not identified. */
    Unknown,

    /** The default, fully supported for export/import, archive type. */
    Tar,
    /** Not supported yet. */
    Zip,
    /** XML <strong>EXPORT</strong> format. */
    Xml;

    /**
     * Determine which type the input file is.
     *
     * @param context Current context
     * @param uri     file to read
     *
     * @return type
     */
    static ArchiveType getType(@NonNull final Context context,
                               @NonNull final Uri uri) {

        // we read the "magic bytes": https://en.wikipedia.org/wiki/List_of_file_signatures
        byte[] bytes = new byte[0x200];

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                int b = is.read(bytes);
                // tar file: offset 0x101, the string "ustar" at
                if (b > 0x110) {
                    if (bytes[0x101] == 0x75 && bytes[0x102] == 0x73 && bytes[0x103] == 0x74
                        && bytes[0x104] == 0x61 && bytes[0x105] == 0x72) {
                        return Tar;
                    }
                }
                // xml file, offset 0, the string "<?xml "
                if (bytes[0] == 0x3c && bytes[1] == 0x3f && bytes[2] == 0x78
                    && bytes[3] == 0x6d && bytes[4] == 0x6c && bytes[5] == 0x20) {
                    return Zip;
                }

                // zip file, offset 0, "PK.."
                if (bytes[0] == 0x50 && bytes[1] == 0x4B
                    && bytes[2] == 0x03 && bytes[3] == 0x04) {
                    return Zip;
                }

            }

        } catch (@NonNull final IOException ignore) {
            // ignore
        }
        return Unknown;
    }

    /**
     * Get the proposed/default Uri (file) name to export to.
     * The user will be able to edit it before the export starts.
     *
     * @param context Current context
     *
     * @return name
     */
    public String getDefaultOutputUriName(@NonNull final Context context) {
        String name = context.getString(R.string.app_name) + '-'
                      + DateUtils.localSqlDateForToday()
                                 .replace(" ", "-")
                                 .replace(":", "");

        switch (this) {
            case Tar:
            case Zip:
                return name + ".ntmb";
            case Xml:
                return name + ".xml";

            case Unknown:
            default:
                throw new UnexpectedValueException(name());
        }
    }
}
