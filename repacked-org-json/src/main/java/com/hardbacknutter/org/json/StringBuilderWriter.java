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

package com.hardbacknutter.org.json;

import java.io.IOException;
import java.io.Writer;

/**
 * Performance optimised alternative for {@link java.io.StringWriter}
 * using internally a {@link StringBuilder} instead of a {@link StringBuffer}.
 */
public class StringBuilderWriter
        extends Writer {
    private final StringBuilder builder;

    /**
     * Create a new string builder writer using the default initial string-builder buffer size.
     */
    public StringBuilderWriter() {
        builder = new StringBuilder();
        lock = builder;
    }

    /**
     * Create a new string builder writer using the specified initial string-builder buffer size.
     *
     * @param initialSize The number of {@code char} values that will fit into this buffer
     *                    before it is automatically expanded
     *
     * @throws IllegalArgumentException If {@code initialSize} is negative
     */
    public StringBuilderWriter(int initialSize) {
        builder = new StringBuilder(initialSize);
        lock = builder;
    }

    @Override
    public void write(int c) {
        builder.append((char) c);
    }

    @Override
    public void write(char[] cbuf,
                      int offset,
                      int length) {
        if ((offset < 0) || (offset > cbuf.length) || (length < 0) ||
            ((offset + length) > cbuf.length) || ((offset + length) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (length == 0) {
            return;
        }
        builder.append(cbuf, offset, length);
    }

    @Override
    public void write(String str) {
        builder.append(str);
    }

    @Override
    public void write(String str,
                      int offset,
                      int length) {
        builder.append(str, offset, offset + length);
    }

    @Override
    public StringBuilderWriter append(CharSequence csq) {
        write(String.valueOf(csq));
        return this;
    }

    @Override
    public StringBuilderWriter append(CharSequence csq,
                                      int start,
                                      int end) {
        if (csq == null) {
            csq = "null";
        }
        return append(csq.subSequence(start, end));
    }

    @Override
    public StringBuilderWriter append(char c) {
        write(c);
        return this;
    }

    @Override
    public String toString() {
        return builder.toString();
    }

    @Override
    public void flush() {
    }

    @Override
    public void close()
            throws IOException {
    }
}
