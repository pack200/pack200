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

import java.io.File;
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
    public void testConstantPoolCountAboveIntegerMax() throws Exception {
        Pack200Builder archive = new Pack200Builder()
                .header(new HeaderBuilder()
                        .count(CONSTANT_Utf8, 1)
                        .count(CONSTANT_String, -1));   // 2^32 - 1 in the archive, -1 once read as an int

        IOException e = assertThrows(IOException.class, archive::unpack);
        assertEquals("Bad value count", e.getMessage());
    }

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

    @Test
    public void testClassInterfaceCountOverflow() throws Exception {
        // the total of the counts overflows to 1, which matches the number of interfaces
        Pack200Builder archive = new Pack200Builder()
                .header(new HeaderBuilder()
                        .count(CONSTANT_Utf8, 1)
                        .count(CONSTANT_Class, 1)
                        .classes(3))
                .band(UDELTA5, 0)                                        // cp_Class
                .band(DELTA5, 0, 0, 0)                                   // class_this
                .band(DELTA5, 0, 0, 0)                                   // class_super
                .band(DELTA5, Integer.MAX_VALUE, Integer.MAX_VALUE, 3)   // class_interface_count
                .band(DELTA5, 0);                                        // class_interface

        IOException e = assertThrows(IOException.class, archive::unpack);
        assertEquals("Value total overflow in class_interface_count", e.getMessage());
    }

    @Test
    public void testTooManySwitchCases() throws Exception {
        // the count is rejected before sizing the bands of the cases, whatever the size of the input
        Pack200Builder archive = classWithCode(_iload_0, _lookupswitch, _return, _return, _end_marker)
                .band(UNSIGNED5, 65535 / 4 + 1);   // bc_case_count

        IOException e = assertThrows(IOException.class, archive::unpack);
        assertEquals("Too many switch cases", e.getMessage());
    }

    @Test
    public void testBadEscapeReferenceSize() throws Exception {
        Pack200Builder archive = classWithCode(_ref_escape, _return, _end_marker)
                .band(UNSIGNED5, 0)    // bc_escref
                .band(UNSIGNED5, 3);   // bc_escrefsize, only 1 and 2 are valid

        IOException e = assertThrows(IOException.class, archive::unpack);
        assertEquals("Bad escape reference size", e.getMessage());
    }

    /**
     * Builds an archive containing the class A with the static method m(int), up to its bytecodes.
     * The operand bands following the bytecodes are left to the caller.
     */
    private Pack200Builder classWithCode(int... bytecodes) throws IOException {
        int[] chars = {
                '(', 'I', ')', 'V',
                'A',
                'j', 'a', 'v', 'a', '/', 'l', 'a', 'n', 'g', '/', 'O', 'b', 'j', 'e', 'c', 't',
                'm'};

        return new Pack200Builder()
                .header(new HeaderBuilder()
                        .count(CONSTANT_Utf8, 5)
                        .count(CONSTANT_Class, 2)
                        .count(CONSTANT_Signature, 1)
                        .count(CONSTANT_NameandType, 1)
                        .classes(1))
                .band(DELTA5, 0, 0, 0)            // cp_Utf8_prefix
                .band(UNSIGNED5, 4, 1, 16, 1)     // cp_Utf8_suffix, the first string is empty
                .band(CHAR3, chars)               // cp_Utf8_chars
                .band(UDELTA5, 2, 3)              // cp_Class: A and java/lang/Object
                .band(DELTA5, 1)                  // cp_Signature_form: (I)V
                .band(DELTA5, 4)                  // cp_Descr_name: m
                .band(UDELTA5, 0)                 // cp_Descr_type: (I)V
                .band(DELTA5, 0)                  // class_this: A
                .band(DELTA5, 1)                  // class_super: java/lang/Object
                .band(DELTA5, 0)                  // class_interface_count
                .band(DELTA5, 0)                  // class_field_count
                .band(DELTA5, 1)                  // class_method_count
                .band(MDELTA5, 0)                 // method_descr: m:(I)V
                .band(UNSIGNED5, 0x08 | (1 << 17))  // method_flags_lo: ACC_STATIC, the bit 17 declares the Code attribute
                .band(UNSIGNED5, 0x21)              // class_flags_lo: ACC_PUBLIC | ACC_SUPER
                .band(BYTE1, 2)                   // code_headers, the short form of a code with 1 local and no handler
                .band(BYTE1, bytecodes);          // bc_codes
    }
}
