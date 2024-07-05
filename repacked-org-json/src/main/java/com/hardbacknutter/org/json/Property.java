/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.org.json;

/*
Public Domain.
*/

import java.util.Enumeration;
import java.util.Properties;

/**
 * Converts a Property file data into JSONObject and back.
 * @author JSON.org
 * @version 2015-05-05
 */
@SuppressWarnings("ALL")
public class Property {

    /**
     * Constructs a new Property object.
     */
    public Property() {
    }

    /**
     * Converts a property file object into a JSONObject. The property file object is a table of name value pairs.
     * @param properties java.util.Properties
     * @return JSONObject
     * @throws JSONException if a called function has an error
     */
    public static JSONObject toJSONObject(Properties properties)
            throws JSONException {
        // can't use the new constructor for Android support
        // JSONObject jo = new JSONObject(properties == null ? 0 : properties.size());
        JSONObject jo = new JSONObject();
        if (properties != null && !properties.isEmpty()) {
            Enumeration<?> enumProperties = properties.propertyNames();
            while (enumProperties.hasMoreElements()) {
                String name = (String) enumProperties.nextElement();
                jo.put(name, properties.getProperty(name));
            }
        }
        return jo;
    }

    /**
     * Converts the JSONObject into a property file object.
     * @param jo JSONObject
     * @return java.util.Properties
     * @throws JSONException if a called function has an error
     */
    public static Properties toProperties(JSONObject jo)
            throws JSONException {
        Properties properties = new Properties();
        if (jo != null) {
            // Don't use the new entrySet API to maintain Android support
            for (final String key : jo.keySet()) {
                Object value = jo.opt(key);
                if (!JSONObject.NULL.equals(value)) {
                    properties.put(key, value.toString());
                }
            }
        }
        return properties;
    }
}
