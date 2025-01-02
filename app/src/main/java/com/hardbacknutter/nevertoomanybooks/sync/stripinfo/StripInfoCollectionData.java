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

package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public class StripInfoCollectionData
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<StripInfoCollectionData> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public StripInfoCollectionData createFromParcel(@NonNull final Parcel in) {
            return new StripInfoCollectionData(in);
        }

        @Override
        @NonNull
        public StripInfoCollectionData[] newArray(final int size) {
            return new StripInfoCollectionData[size];
        }
    };
    private static final String TAG = "StripInfoCollectionData";
    public static final String BKEY = TAG + ":data";
    /** StripInfo book-id; i.e our external-id. */
    private long sid;
    /**
     * The "CollectieId"; a secondary id used by the website for all books flagged
     * as being in the users collection.
     */
    private long collectionId;
    /** Our/local book-id. */
    private long bookId;
    private boolean wanted;
    private boolean owned;
    private boolean digital;
    /** The amount of copies of this book we have. */
    private int amount;
    @Nullable
    private LocalDateTime lastSync;

    public StripInfoCollectionData(@IntRange(from = 0) final long bookId) {
        this.bookId = bookId;
    }

    public StripInfoCollectionData(@IntRange(from = 0) final long bookId,
                                   @NonNull final DataHolder rowData) {
        this.bookId = bookId;

        sid = rowData.getLong(DBKey.STRIP_INFO_BOOK_ID);
        collectionId = rowData.getLong(DBKey.STRIP_INFO_COLL_ID);
        wanted = rowData.getBoolean(DBKey.STRIP_INFO_WANTED);
        owned = rowData.getBoolean(DBKey.STRIP_INFO_OWNED);
        digital = rowData.getBoolean(DBKey.STRIP_INFO_DIGITAL);
        amount = rowData.getInt(DBKey.STRIP_INFO_AMOUNT);
        final String lastSynStr = rowData.getString(DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC, null);
        if (lastSynStr != null) {
            lastSync = new ISODateParser(ServiceLocator.getInstance().getSystemLocaleList().get(0))
                    .parse(lastSynStr).orElseThrow();
        }
    }

    public StripInfoCollectionData(@NonNull final Parcel in) {
        sid = in.readLong();
        collectionId = in.readLong();
        bookId = in.readLong();
        wanted = in.readByte() != 0;
        owned = in.readByte() != 0;
        digital = in.readByte() != 0;
        amount = in.readInt();
        final String lastSynStr = in.readString();
        if (lastSynStr != null) {
            lastSync = new ISODateParser(ServiceLocator.getInstance().getSystemLocaleList().get(0))
                    .parse(lastSynStr).orElseThrow();
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(sid);
        dest.writeLong(collectionId);
        dest.writeLong(bookId);
        dest.writeByte((byte) (wanted ? 1 : 0));
        dest.writeByte((byte) (owned ? 1 : 0));
        dest.writeByte((byte) (digital ? 1 : 0));
        dest.writeInt(amount);
        dest.writeString(lastSync != null
                         ? lastSync.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                         : null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public long getBookId() {
        return bookId;
    }

    public void setBookId(final long bookId) {
        this.bookId = bookId;
    }

    public long getSid() {
        return sid;
    }

    public void setSid(final long sid) {
        this.sid = sid;
    }

    public long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(final long collectionId) {
        this.collectionId = collectionId;
    }

    public boolean isWanted() {
        return wanted;
    }

    public void setWanted(final boolean wanted) {
        this.wanted = wanted;
    }

    public boolean isOwned() {
        return owned;
    }

    public void setOwned(final boolean owned) {
        this.owned = owned;
    }


    public boolean isDigital() {
        return digital;
    }

    public void setDigital(final boolean digital) {
        this.digital = digital;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(final int amount) {
        this.amount = amount;
    }


    @Nullable
    public LocalDateTime getLastSync() {
        return lastSync;
    }

    public void setLastSync(@Nullable final LocalDateTime lastSync) {
        this.lastSync = lastSync;
    }

    @Override
    @NonNull
    public String toString() {
        return "StripInfoCollectionData{"
               + "sid=" + sid
               + ", collectionId=" + collectionId
               + ", bookId=" + bookId
               + ", wanted=" + wanted
               + ", owned=" + owned
               + ", digital=" + digital
               + ", amount=" + amount
               + ", lastSync=" + lastSync
               + '}';
    }
}
