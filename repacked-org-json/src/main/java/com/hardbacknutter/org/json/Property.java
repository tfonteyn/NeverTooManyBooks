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

package com.hardbacknutter.org.json;

/*
Copyright (c) 2002 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import java.util.Enumeration;
import java.util.Properties;

/**
 * Converts a Property file data into JSONObject and back.
 *
 * @author JSON.org
 * @version 2015-05-05
 */
public class Property {

    /**
     * Converts a property file object into a JSONObject.
     * The property file object is a table of name value pairs.
     *
     * @param properties java.util.Properties
     *
     * @return JSONObject
     *
     * @throws JSONException if a called function has an error
     */
    public static JSONObject toJSONObject(final java.util.Properties properties)
            throws JSONException {
        // can't use the new constructor for Android support
        // JSONObject jo = new JSONObject(properties == null ? 0 : properties.size());
        final JSONObject jo = new JSONObject();
        if (properties != null && !properties.isEmpty()) {
            final Enumeration<?> enumProperties = properties.propertyNames();
            while (enumProperties.hasMoreElements()) {
                final String name = (String) enumProperties.nextElement();
                jo.put(name, properties.getProperty(name));
            }
        }
        return jo;
    }

    /**
     * Converts the JSONObject into a property file object.
     *
     * @param jo JSONObject
     *
     * @return java.util.Properties
     *
     * @throws JSONException if a called function has an error
     */
    public static Properties toProperties(final JSONObject jo)
            throws JSONException {
        final Properties properties = new Properties();
        if (jo != null) {
            // Don't use the new entrySet API to maintain Android support
            for (final String key : jo.keySet()) {
                final Object value = jo.opt(key);
                if (!JSONObject.NULL.equals(value)) {
                    properties.put(key, value.toString());
                }
            }
        }
        return properties;
    }
}
