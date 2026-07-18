/*
 * @Copyright 2018-2026 HardBackNutter
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

import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONArray;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IdentifierValueCoderTest
        extends BaseDBTest {

    private IdentifierValueCoder identifierValueCoder;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
        identifierValueCoder = new IdentifierValueCoder();
    }

    @Test
    void decode() {
        final List<Identifier.Value> values = List.of(
                new Identifier.Value(Identifier.SID_OCLC, "oclc1"),
                new Identifier.Value(Identifier.SID_DNB, "12345"),
                new Identifier.Value(Identifier.SID_DNB, "54321"),
                // Valid
                new Identifier.Value(Identifier.SID_BNF, "12345678"),
                // Valid, but with pre/suffix
                new Identifier.Value(Identifier.SID_BNF, "cb12213443x"),
                // Invalid
                new Identifier.Value(Identifier.SID_BNF, "xx11223344x")
        );

        final JSONArray encoded = identifierValueCoder.encode(values);
        assertEquals(6, encoded.length());

        final Collection<Identifier.Value> decoded = identifierValueCoder.decode(encoded);
        assertEquals(5, decoded.size());

        final List<Identifier.Value> nonBnF = decoded
                .stream()
                .filter(iv -> !Identifier.SID_BNF.equals(iv.getKey()))
                .toList();

        assertEquals(3, nonBnF.size());

        final List<String> bnfSids = decoded
                .stream()
                .filter(iv -> Identifier.SID_BNF.equals(iv.getKey()))
                .map(Identifier.Value::getSid)
                .toList();

        assertEquals(2, bnfSids.size());

        assertTrue(bnfSids.contains("12345678"));
        assertTrue(bnfSids.contains("12213443"));

        assertFalse(bnfSids.contains("11223344"));
        assertFalse(bnfSids.contains("xx11223344x"));
    }

}
