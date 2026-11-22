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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import org.junit.Test;

import static io.pack200.Constants.*;
import static org.junit.Assert.*;

public class EmptyJarTest {

    @Test
    public void testPackUnpackEmptyJar() throws Exception {
        ByteArrayOutputStream jar = new ByteArrayOutputStream();
        new JarOutputStream(jar).close();

        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        Pack200.newPacker().pack(new JarInputStream(new ByteArrayInputStream(jar.toByteArray())), packed);

        // an archive without files has no file headers (the options are the 7th byte after the magic number and the version)
        byte[] archive = packed.toByteArray();
        assertEquals("archive options", 0, archive[6] & AO_HAVE_FILE_HEADERS);

        Pack200.Unpacker unpacker = Pack200.newUnpacker();
        unpacker.properties().put(Utils.DEBUG_DISABLE_NATIVE, "true");
        ByteArrayOutputStream unpacked = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(unpacked)) {
            unpacker.unpack(new ByteArrayInputStream(archive), out);
        }

        try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(unpacked.toByteArray()))) {
            assertNull("entry", in.getNextJarEntry());
        }
    }
}
