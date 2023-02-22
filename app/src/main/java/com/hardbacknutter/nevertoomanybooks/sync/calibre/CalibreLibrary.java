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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public class CalibreLibrary
        extends LibraryBase {

    /** {@link Parcelable}. */
    public static final Creator<CalibreLibrary> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public CalibreLibrary createFromParcel(@NonNull final Parcel in) {
            return new CalibreLibrary(in);
        }

        @Override
        @NonNull
        public CalibreLibrary[] newArray(final int size) {
            return new CalibreLibrary[size];
        }
    };

    private static final CalibreCustomField[] Z_CALIBRE_CUSTOM_FIELD = new CalibreCustomField[0];

    /** The physical Calibre library STRING id. */
    @NonNull
    private final String libraryStringId;

    /**
     * The custom fields <strong>present</strong> on the server.
     * This will be a subset of the supported fields.
     * <p>
     * Not stored locally. Only valid while importing/exporting.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private final Set<CalibreCustomField> calibreCustomFields = new HashSet<>();

    /** The list of virtual libs in this library. */
    private final ArrayList<CalibreVirtualLibrary> virtualLibraries = new ArrayList<>();

    /** The physical Calibre library uuid. */
    @NonNull
    private String uuid;
    @NonNull
    private String lastSyncDate;
    /** Not stored locally. Only valid while importing/exporting. */
    private int totalBooks;

    /**
     * Constructor without ID.
     *
     * @param uuid              the Calibre native UUID for the library
     * @param libraryStringId   the Calibre native {@code stringId} for the library
     * @param name              the Calibre name for the library
     * @param mappedBookshelfId the {@link Bookshelf} id this library is mapped to
     */
    public CalibreLibrary(@NonNull final String uuid,
                          @NonNull final String libraryStringId,
                          @NonNull final String name,
                          final long mappedBookshelfId) {
        super(name, mappedBookshelfId);

        this.libraryStringId = libraryStringId;
        this.uuid = uuid;
        lastSyncDate = "";
    }

    /**
     * Constructor without ID.
     *
     * @param uuid            the Calibre native UUID for the library
     * @param libraryStringId the Calibre native {@code stringId} for the library
     * @param name            the Calibre name for the library
     * @param mappedBookshelf the {@link Bookshelf} this library is mapped to
     */
    public CalibreLibrary(@NonNull final String uuid,
                          @NonNull final String libraryStringId,
                          @NonNull final String name,
                          @NonNull final Bookshelf mappedBookshelf) {
        super(name, mappedBookshelf);

        this.libraryStringId = libraryStringId;
        this.uuid = uuid;
        lastSyncDate = "";
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

        libraryStringId = rowData.getString(DBKey.CALIBRE_LIBRARY_STRING_ID);
        uuid = rowData.getString(DBKey.CALIBRE_LIBRARY_UUID);
        lastSyncDate = rowData.getString(DBKey.CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private CalibreLibrary(@NonNull final Parcel in) {
        super(in);

        //noinspection ConstantConditions
        libraryStringId = in.readString();
        //noinspection ConstantConditions
        uuid = in.readString();
        //noinspection ConstantConditions
        lastSyncDate = in.readString();

        ParcelUtils.readParcelableList(in, virtualLibraries,
                                       CalibreVirtualLibrary.class.getClassLoader());

        totalBooks = in.readInt();
        //noinspection ConstantConditions
        Arrays.stream(in.readParcelableArray(CalibreCustomField.class.getClassLoader()))
              .forEach(field -> calibreCustomFields.add((CalibreCustomField) field));
    }

    @NonNull
    public String getLibraryStringId() {
        return libraryStringId;
    }

    /**
     * Get the UUID for this library as defined/created by the Calibre Content Server.
     * <p>
     * <strong>Will be {@code ""} if our extension is not installed on the CSS</strong>
     *
     * @return CSS uuid, or ""
     */
    @NonNull
    public String getUuid() {
        return uuid;
    }

    public void setUuid(@NonNull final String uuid) {
        this.uuid = uuid;
    }

    @NonNull
    public String getLastSyncDateAsString() {
        return lastSyncDate;
    }

    @Nullable
    public LocalDateTime getLastSyncDate() {
        if (!lastSyncDate.isEmpty()) {
            return new ISODateParser(
                    ServiceLocator.getInstance().getSystemLocale()).parse(lastSyncDate);
        }

        return null;
    }

    public void setLastSyncDate(@NonNull final String lastSyncDate) {
        this.lastSyncDate = lastSyncDate;
    }

    void setLastSyncDate(@Nullable final LocalDateTime lastSyncDate) {
        if (lastSyncDate != null) {
            this.lastSyncDate = lastSyncDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } else {
            this.lastSyncDate = "";
        }
    }

    public int getTotalBooks() {
        return totalBooks;
    }

    void setTotalBooks(final int totalBooks) {
        this.totalBooks = totalBooks;
    }

    /**
     * Get the defined custom fields.
     *
     * @return an immutable Set
     */
    @NonNull
    Set<CalibreCustomField> getCustomFields() {
        return Set.copyOf(calibreCustomFields);
    }

    void setCustomFields(@NonNull final Set<CalibreCustomField> calibreCustomFields) {
        this.calibreCustomFields.clear();
        this.calibreCustomFields.addAll(calibreCustomFields);
    }

    @NonNull
    public ArrayList<CalibreVirtualLibrary> getVirtualLibraries() {
        return virtualLibraries;
    }

    public void setVirtualLibraries(@NonNull final List<CalibreVirtualLibrary> virtualLibraries) {
        this.virtualLibraries.clear();
        this.virtualLibraries.addAll(virtualLibraries);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);

        dest.writeString(libraryStringId);
        dest.writeString(uuid);
        dest.writeString(lastSyncDate);

        ParcelUtils.writeParcelableList(dest, virtualLibraries, flags);


        dest.writeInt(totalBooks);
        dest.writeParcelableArray(calibreCustomFields.toArray(Z_CALIBRE_CUSTOM_FIELD), flags);
    }

    @Override
    @NonNull
    public String toString() {
        return "CalibreLibrary{"
               + super.toString()
               + ", uuid=`" + uuid + '`'
               + ", libraryStringId=`" + libraryStringId + '`'
               + ", lastSyncDate=`" + lastSyncDate + '`'
               + ", totalBooks=" + totalBooks
               + ", virtualLibraries=" + virtualLibraries
               + '}';
    }

}
