/*
 * Copyright (c) 2026, Emmanuel Bourg
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package io.pack200;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import static io.pack200.BandStructure.*;
import static io.pack200.Constants.*;
import static org.junit.Assert.*;

/**
 * Checks that the Java unpacker rejects the malformed archives
 * rejected by the native unpacker.
 */
public class MalformedArchiveTest {

    @Test
    public void testUtf8PrefixAboveIntegerMax() throws Exception {
        Pack200Builder archive = new Pack200Builder()
                .header(new HeaderBuilder().count(CONSTANT_Utf8, 4))
                .band(DELTA5, 0, -1000)      // cp_Utf8_prefix: 2^32 - 1000 in the archive
                .band(UNSIGNED5, 1, 1, 1)    // cp_Utf8_suffix
                .band(CHAR3, 'a', 'b', 'c')  // cp_Utf8_chars
                .band(DELTA5);               // cp_Utf8_big_suffix

        IOException e = assertThrows(IOException.class, archive::unpack);
        assertEquals("Bad utf8 prefix", e.getMessage());
    }

    @Test
    public void testUtf8BigSuffixAboveIntegerMax() throws Exception {
        Pack200Builder archive = new Pack200Builder()
                .header(new HeaderBuilder().count(CONSTANT_Utf8, 4))
                .band(DELTA5, 0, 0)        // cp_Utf8_prefix
                .band(UNSIGNED5, 1, 0, 0)  // cp_Utf8_suffix
                .band(CHAR3, 'a')          // cp_Utf8_chars
                .band(DELTA5, 1, -1000);   // cp_Utf8_big_suffix: 2^32 - 1000 in the archive

        IOException e = assertThrows(IOException.class, archive::unpack);
        assertEquals("Bad utf8 suffix", e.getMessage());
    }

    @Test
    public void testUtf8SuffixTooLarge() throws Exception {
        Pack200Builder archive = new Pack200Builder()
                .header(new HeaderBuilder().count(CONSTANT_Utf8, 3))
                .band(DELTA5, 0)                     // cp_Utf8_prefix
                .band(UNSIGNED5, 1, 800_000_000);    // cp_Utf8_suffix

        IOException e = assertThrows(IOException.class, archive::unpack);
        assertEquals("Suffix too large", e.getMessage());
    }

    @Test
    public void testUtf8StringTooLarge() throws Exception {
        Pack200Builder archive = new Pack200Builder()
                .header(new HeaderBuilder().count(CONSTANT_Utf8, 3))
                .band(DELTA5, 0)                // cp_Utf8_prefix
                .band(UNSIGNED5, 1, 0)          // cp_Utf8_suffix
                .band(CHAR3, 'a')               // cp_Utf8_chars
                .band(DELTA5, 800_000_000);     // cp_Utf8_big_suffix

        IOException e = assertThrows(IOException.class, archive::unpack);
        assertEquals("String too large", e.getMessage());
    }

    @Test
    public void testUtf8PrefixLongerThanPreviousString() throws Exception {
        Pack200Builder archive = new Pack200Builder()
                .header(new HeaderBuilder().count(CONSTANT_Utf8, 4))
                .band(DELTA5, 0, 5)          // cp_Utf8_prefix
                .band(UNSIGNED5, 1, 1, 1)    // cp_Utf8_suffix
                .band(CHAR3, 'a', 'b', 'c')  // cp_Utf8_chars
                .band(DELTA5);               // cp_Utf8_big_suffix

        IOException e = assertThrows(IOException.class, archive::unpack);
        assertEquals("Utf8 prefix overflow", e.getMessage());
    }

    @Test
    public void testTooManySignatureClassReferences() throws Exception {
        int[] chars = new int[0xFFFF];
        Arrays.fill(chars, 'L');

        Pack200Builder archive = new Pack200Builder()
                .header(new HeaderBuilder()
                        .count(CONSTANT_Utf8, 2)
                        .count(CONSTANT_Signature, 1))
                .band(DELTA5)                    // cp_Utf8_prefix
                .band(UNSIGNED5, chars.length)   // cp_Utf8_suffix
                .band(CHAR3, chars)              // cp_Utf8_chars
                .band(DELTA5)                    // cp_Utf8_big_suffix
                .band(DELTA5, 1);                // cp_Signature_form

        IOException e = assertThrows(IOException.class, archive::unpack);
        assertEquals("Too many class references", e.getMessage());
    }

    @Test
    public void testTooManyBootstrapMethodArguments() throws Exception {
        int argc = 0xFFFF;

        Pack200Builder archive = new Pack200Builder()
                .header(new HeaderBuilder()
                        .count(CONSTANT_Utf8, 1)
                        .count(CONSTANT_BootstrapMethod, 1))
                .band(DELTA5, 0)               // cp_BootstrapMethod_ref
                .band(UDELTA5, argc)           // cp_BootstrapMethod_arg_count
                .band(DELTA5, new int[argc]);  // cp_BootstrapMethod_arg

        IOException e = assertThrows(IOException.class, archive::unpack);
        assertEquals("Too many arguments", e.getMessage());
    }
}
