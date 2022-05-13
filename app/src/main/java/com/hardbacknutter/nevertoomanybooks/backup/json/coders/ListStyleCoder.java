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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Styles;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PInt;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PIntList;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PString;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class ListStyleCoder
        implements JsonCoder<ListStyle> {

    /** The sub-tag for the array with the style settings. */
    private static final String STYLE_SETTINGS = "settings";

    private static final String STYLE_NAME = "name";

    @NonNull
    private final Context context;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public ListStyleCoder(@NonNull final Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public JSONObject encode(@NonNull final ListStyle style)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBKey.STYLE_UUID, style.getUuid());
        out.put(DBKey.STYLE_IS_PREFERRED, style.isPreferred());
        out.put(DBKey.STYLE_MENU_POSITION, style.getMenuPosition());

        if (style.isUserDefined()) {
            final UserStyle userStyle = (UserStyle) style;

            out.put(STYLE_NAME, userStyle.getName());

            final JSONObject dest = new JSONObject();
            for (final PPref<?> source : userStyle.getRawPreferences()) {
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

        final String uuid = data.getString(DBKey.STYLE_UUID);

        final Styles styles = ServiceLocator.getInstance().getStyles();

        if (BuiltinStyle.isBuiltin(uuid)) {
            // It's a builtin style
            final ListStyle style = styles.getStyle(context, uuid);
            //noinspection ConstantConditions
            style.setPreferred(data.getBoolean(DBKey.STYLE_IS_PREFERRED));
            style.setMenuPosition(data.getInt(DBKey.STYLE_MENU_POSITION));
            return style;

        } else {
            final UserStyle userStyle = UserStyle.createFromImport(context, uuid);
            userStyle.setName(data.getString(STYLE_NAME));
            userStyle.setPreferred(data.getBoolean(DBKey.STYLE_IS_PREFERRED));
            userStyle.setMenuPosition(data.getInt(DBKey.STYLE_MENU_POSITION));

            // any element in the source which we don't know, will simply be ignored.
            final JSONObject source = data.getJSONObject(STYLE_SETTINGS);

            // This values list will have the 'Groups' preference itself,
            // but it will be empty, and hence the list will not have any group preferences
            // First copy the base preferences, including the Groups id list.
            userStyle.getRawPreferences()
                     .stream()
                     .filter(stylePref -> source.has(stylePref.getKey()))
                     .forEach(stylePref -> copyValues(source, stylePref));

            // The style will now have the 'Groups id list' preference set,
            // so read it, and collect the individual group prefs if we have them
            userStyle.getGroups()
                     .getGroupList()
                     .stream()
                     .flatMap(group -> group.getRawPreferences().stream())
                     .filter(groupPref -> source.has(groupPref.getKey()))
                     .forEach(groupPref -> copyValues(source, groupPref));
            return userStyle;

        }
    }

    private void copyValues(@NonNull final JSONObject source,
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
            final List<Integer> list = IntStream.range(0, sourceArray.length())
                                                .mapToObj(sourceArray::getInt)
                                                .collect(Collectors.toList());
            ((PIntList) dest).set(list);
        }
    }
}
