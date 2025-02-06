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
    private boolean wanted;
    private boolean owned;
    private boolean digital;
    /** The amount of copies of this book we have. */
    private int amount = 1;
    @Nullable
    private String lastSync;

    public StripInfoCollectionData() {
    }

    public StripInfoCollectionData(final long sid,
                                   final long collectionId,
                                   final boolean wanted,
                                   final boolean owned,
                                   final boolean digital,
                                   final int amount,
                                   @Nullable final String lastSyn) {
        this.sid = sid;
        this.collectionId = collectionId;
        this.wanted = wanted;
        this.owned = owned;
        this.digital = digital;
        this.amount = Math.max(amount, 1);
        this.lastSync = lastSyn;
    }

    public StripInfoCollectionData(@NonNull final DataHolder rowData) {
        sid = rowData.getLong(DBKey.STRIP_INFO.BOOK_ID);
        collectionId = rowData.getLong(DBKey.STRIP_INFO.COLLECTION_ID);
        wanted = rowData.getBoolean(DBKey.STRIP_INFO.WANTED);
        owned = rowData.getBoolean(DBKey.STRIP_INFO.OWNED);
        digital = rowData.getBoolean(DBKey.STRIP_INFO.DIGITAL);
        amount = rowData.getInt(DBKey.STRIP_INFO.AMOUNT);
        lastSync = rowData.getString(DBKey.STRIP_INFO.LAST_SYNC_DATE__UTC, null);
    }

    private StripInfoCollectionData(@NonNull final Parcel in) {
        sid = in.readLong();
        collectionId = in.readLong();
        wanted = in.readByte() != 0;
        owned = in.readByte() != 0;
        digital = in.readByte() != 0;
        amount = in.readInt();
        lastSync = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(sid);
        dest.writeLong(collectionId);
        dest.writeByte((byte) (wanted ? 1 : 0));
        dest.writeByte((byte) (owned ? 1 : 0));
        dest.writeByte((byte) (digital ? 1 : 0));
        dest.writeInt(amount);
        dest.writeString(lastSync);
    }

    @Override
    public int describeContents() {
        return 0;
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

    public void setAmount(@IntRange(from = 1) final int amount) {
        // sanity check, this object existence implies we have at least 1 copy of the book
        this.amount = Math.max(amount, 1);
    }


    @Nullable
    public String getLastSync() {
        return lastSync;
    }

    public void setLastSync(@Nullable final String lastSync) {
        this.lastSync = lastSync;
    }

    @Override
    @NonNull
    public String toString() {
        return "StripInfoCollectionData{"
               + "sid=" + sid
               + ", collectionId=" + collectionId
               + ", wanted=" + wanted
               + ", owned=" + owned
               + ", digital=" + digital
               + ", amount=" + amount
               + ", lastSync=" + lastSync
               + '}';
    }
}
