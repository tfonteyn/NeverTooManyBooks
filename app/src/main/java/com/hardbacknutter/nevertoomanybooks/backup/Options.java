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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.Date;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;

public abstract class Options
        implements Parcelable {

    /**
     * options as to what should be imported/exported.
     * These are the common lower 8 bits of the 'options'.
     */
    public static final int NOTHING = 0;
    public static final int BOOK_CSV = 1;
    public static final int PREFERENCES = 1 << 1;
    public static final int BOOK_LIST_STYLES = 1 << 2;
    public static final int COVERS = 1 << 3;
    public static final int XML_TABLES = 1 << 4;
    //public static final int IMPORT_5 = 1 << 5;
    //public static final int IMPORT_6 = 1 << 6;
    //public static final int IMPORT_7 = 1 << 7;
    //public static final int DATABASE = 1 << 8;

    /** Last full backup date. */
    private static final String PREF_LAST_FULL_BACKUP_DATE = "backup.last.date";

    @Nullable
    private Uri mUri;
    /**
     * Bitmask.
     * Contains the user selected options before doing the import/export.
     * After the import/export, reflects the entities actually imported/exported.
     */
    private int mOptions;

    /**
     * Constructor.
     *
     * @param options what to import/export
     * @param uri     to read/write. <strong>can be {@code null}</strong> if instead we read/write
     *                to a stream
     */
    Options(final int options,
            @Nullable final Uri uri) {
        mOptions = options;
        mUri = uri;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    Options(@NonNull final Parcel in) {
        mOptions = in.readInt();
        mUri = in.readParcelable(getClass().getClassLoader());
    }

    /**
     * Store the date of the last full backup ('now') and reset the startup prompt-counter.
     *
     * @param context Current context
     */
    static void setLastFullBackupDate(@NonNull final Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(PREF_LAST_FULL_BACKUP_DATE,
                                    DateUtils.utcSqlDateTimeForToday())
                         .putInt(Prefs.PREF_STARTUP_BACKUP_COUNTDOWN,
                                 Prefs.STARTUP_BACKUP_COUNTDOWN)
                         .apply();
    }

    /**
     * Get the last time we made a full backup.
     *
     * @param context Current context
     *
     * @return Date in the UTC timezone.
     */
    @Nullable
    static Date getLastFullBackupDate(@NonNull final Context context) {
        String lastBackup = PreferenceManager.getDefaultSharedPreferences(context)
                                             .getString(PREF_LAST_FULL_BACKUP_DATE, null);

        if (lastBackup != null && !lastBackup.isEmpty()) {
            return DateUtils.parseSqlDateTime(lastBackup);
        }

        return null;
    }

    @NonNull
    public Uri getUri() {
        Objects.requireNonNull(mUri, ErrorMsg.NULL_URI);
        return mUri;
    }

    public void setUri(@Nullable final Uri uri) {
        this.mUri = uri;
    }

    public void setOption(final int optionBit,
                          final boolean isSet) {
        if (isSet) {
            mOptions |= optionBit;
        } else {
            mOptions &= ~optionBit;
        }
    }

    public boolean getOption(final int optionBit) {
        return (mOptions & optionBit) != 0;
    }

    public int getOptions() {
        return mOptions;
    }

    public void setOptions(final int options) {
        this.mOptions = options;
    }

    void validate() {
        Objects.requireNonNull(mUri, ErrorMsg.NULL_URI);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(mOptions);
        dest.writeParcelable(mUri, flags);
    }
}
