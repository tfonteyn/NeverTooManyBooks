/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PInt;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PIntList;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PString;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

public class ListStyleCoder
        implements JsonCoder<ListStyle> {

    /** The sub-tag for the array with the style settings. */
    private static final String STYLE_SETTINGS = "settings";

    @NonNull
    private final Context mContext;
    /** Database Access. */
    @Nullable
    private final DAO mDb;

    /**
     * Constructor. The {@link #encode} method does not need a database.
     *
     * @param context Current context
     */
    public ListStyleCoder(@NonNull final Context context) {
        mContext = context;
        mDb = null;
    }

    /**
     * Constructor. The {@link #decode} method <strong>does</strong>> need a database.
     *
     * @param context Current context
     * @param db      Database access
     */
    public ListStyleCoder(@NonNull final Context context,
                          @NonNull final DAO db) {
        mContext = context;
        mDb = db;
    }

    @NonNull
    @Override
    public JSONObject encode(@NonNull final ListStyle style)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBDefinitions.KEY_UUID, style.getUuid());
        out.put(DBDefinitions.KEY_STYLE_IS_PREFERRED, style.isPreferred());
        out.put(DBDefinitions.KEY_STYLE_MENU_POSITION, style.getMenuPosition());

        if (style instanceof UserStyle) {
            final JSONObject dest = new JSONObject();
            for (final PPref<?> source : style.getRawPreferences().values()) {
                if (source instanceof PInt
                    || source instanceof PBoolean
                    || source instanceof PString) {
                    dest.put(source.getKey(), source.getValue());

                } else if (source instanceof PIntList) {
                    dest.put(source.getKey(), new JSONArray(((PIntList) source).getValue()));
                }
            }
            out.put(STYLE_SETTINGS, dest);
        }
        return out;
    }

    @NonNull
    @Override
    public ListStyle decode(@NonNull final JSONObject data)
            throws JSONException {
        Objects.requireNonNull(mDb);

        final String uuid = data.getString(DBDefinitions.KEY_UUID);

        final ListStyle style;
        if (StyleDAO.BuiltinStyles.isBuiltin(uuid)) {
            style = Objects.requireNonNull(StyleDAO.getStyle(mContext, mDb, uuid));

        } else {
            style = UserStyle.createFromImport(mContext, uuid);

            // any element in the source which we don't know, will simply be ignored.
            final JSONObject source = data.getJSONObject(STYLE_SETTINGS);

            // This values list will have the 'Groups' preference itself,
            // but it will be empty, and hence the list will not have any group preferences
            // First copy the base preferences, including the Groups id list.
            for (final PPref<?> stylePref : style.getRawPreferences().values()) {
                if (source.has(stylePref.getKey())) {
                    transfer(source, stylePref);
                }
            }
            // The style will now have the 'Groups id list' preference set,
            // so read it, and collect the individual group prefs if we have them
            for (final BooklistGroup group : style.getGroups().getGroupList()) {
                for (final PPref<?> groupPref : group.getRawPreferences().values()) {
                    if (source.has(groupPref.getKey())) {
                        transfer(source, groupPref);
                    }
                }
            }
        }

        style.setPreferred(data.getBoolean(DBDefinitions.KEY_STYLE_IS_PREFERRED));
        style.setMenuPosition(data.getInt(DBDefinitions.KEY_STYLE_MENU_POSITION));

        return style;
    }

    private void transfer(@NonNull final JSONObject source,
                          @NonNull final PPref<?> dest)
            throws JSONException {
        if (dest instanceof PInt) {
            ((PInt) dest).set(source.getInt(dest.getKey()));

        } else if (dest instanceof PBoolean) {
            ((PBoolean) dest).set(source.getBoolean(dest.getKey()));

        } else if (dest instanceof PString) {
            ((PString) dest).set(source.getString(dest.getKey()));

        } else if (dest instanceof PIntList) {
            final JSONArray sourceArray = source.getJSONArray(dest.getKey());
            final ArrayList<Integer> list = new ArrayList<>();
            for (int i = 0; i < sourceArray.length(); i++) {
                list.add(sourceArray.getInt(i));
            }
            ((PIntList) dest).set(list);
        }
    }
}
