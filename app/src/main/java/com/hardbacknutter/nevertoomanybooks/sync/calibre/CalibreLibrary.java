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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

public class CalibreLibrary
        extends LibraryBase {

    public static final Creator<CalibreLibrary> CREATOR =
            new Creator<CalibreLibrary>() {
                @Override
                public CalibreLibrary createFromParcel(@NonNull final Parcel in) {
                    return new CalibreLibrary(in);
                }

                @Override
                public CalibreLibrary[] newArray(final int size) {
                    return new CalibreLibrary[size];
                }
            };

    /** The physical Calibre library STRING id. */
    @NonNull
    private final String mLibraryStringId;
    /**
     * The custom fields <strong>present</strong> on the server.
     * This will be a subset of the supported fields from {@link CustomFields}.
     * <p>
     * Not stored locally. Only valid while importing/exporting.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private final Set<CustomFields.Field> mCustomFields = new HashSet<>();
    private final ArrayList<CalibreVirtualLibrary> mVirtualLibraries = new ArrayList<>();
    /** The physical Calibre library uuid. */
    @NonNull
    private String mUuid;
    @NonNull
    private String mLastSyncDate;
    /** Not stored locally. Only valid while importing/exporting. */
    private int mTotalBooks;

    /**
     * Constructor without ID.
     */
    public CalibreLibrary(@NonNull final String uuid,
                          @NonNull final String libraryId,
                          @NonNull final String name,
                          final long mappedBookshelfId) {
        super(name, mappedBookshelfId);

        mLibraryStringId = libraryId;
        mUuid = uuid;
        mLastSyncDate = "";
    }

    /**
     * Full constructor.
     *
     * @param id      row id
     * @param rowData with data
     */
    public CalibreLibrary(final long id,
                          @NonNull final DataHolder rowData) {
        super(id, rowData);

        mLibraryStringId = rowData.getString(DBKeys.KEY_CALIBRE_LIBRARY_STRING_ID);
        mUuid = rowData.getString(DBKeys.KEY_CALIBRE_LIBRARY_UUID);
        mLastSyncDate = rowData.getString(DBKeys.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE);
    }

    private CalibreLibrary(@NonNull final Parcel in) {
        super(in);

        //noinspection ConstantConditions
        mLibraryStringId = in.readString();
        //noinspection ConstantConditions
        mUuid = in.readString();
        //noinspection ConstantConditions
        mLastSyncDate = in.readString();

        ParcelUtils.readParcelableList(in, mVirtualLibraries, getClass().getClassLoader());


        mTotalBooks = in.readInt();
        //noinspection ConstantConditions
        Arrays.stream(in.readParcelableArray(getClass().getClassLoader()))
              .forEach(field -> mCustomFields.add((CustomFields.Field) field));
    }

    @NonNull
    public String getLibraryStringId() {
        return mLibraryStringId;
    }

    @NonNull
    public String getUuid() {
        return mUuid;
    }

    public void setUuid(@NonNull final String uuid) {
        mUuid = uuid;
    }

    public void setLastSyncDate(@NonNull final String lastSyncDate) {
        mLastSyncDate = lastSyncDate;
    }

    public void setLastSyncDate(@Nullable final LocalDateTime lastSyncDate) {
        if (lastSyncDate != null) {
            mLastSyncDate = lastSyncDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } else {
            mLastSyncDate = "";
        }
    }

    @NonNull
    public String getLastSyncDateAsString() {
        return mLastSyncDate;
    }

    @Nullable
    public LocalDateTime getLastSyncDate(@NonNull final Context context) {
        if (!mLastSyncDate.isEmpty()) {
            return DateParser.getInstance(context).parseISO(mLastSyncDate);
        }

        return null;
    }

    public int getTotalBooks() {
        return mTotalBooks;
    }

    void setTotalBooks(final int totalBooks) {
        mTotalBooks = totalBooks;
    }

    @NonNull
    public Set<CustomFields.Field> getCustomFields() {
        return mCustomFields;
    }

    void setCustomFields(final Set<CustomFields.Field> customFields) {
        mCustomFields.clear();
        mCustomFields.addAll(customFields);
    }

    @NonNull
    public ArrayList<CalibreVirtualLibrary> getVirtualLibraries() {
        return mVirtualLibraries;
    }

    public void setVirtualLibraries(@NonNull final List<CalibreVirtualLibrary> virtualLibraries) {
        mVirtualLibraries.clear();
        mVirtualLibraries.addAll(virtualLibraries);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);

        dest.writeString(mLibraryStringId);
        dest.writeString(mUuid);
        dest.writeString(mLastSyncDate);

        ParcelUtils.writeParcelableList(dest, mVirtualLibraries, flags);


        dest.writeInt(mTotalBooks);
        //noinspection ZeroLengthArrayAllocation
        dest.writeParcelableArray(mCustomFields.toArray(new CustomFields.Field[0]), flags);
    }

    @Override
    @NonNull
    public String toString() {
        return "CalibreLibrary{"
               + super.toString()
               + ", mUuid=`" + mUuid + '`'
               + ", mLibraryId=`" + mLibraryStringId + '`'
               + ", mLastSyncDate=`" + mLastSyncDate + '`'
               + ", mTotalBooks=" + mTotalBooks
               + ", mVirtualLibraries=" + mVirtualLibraries
               + '}';
    }

}
