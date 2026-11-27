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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.JarOutputStream;

/**
 * Builds a pack200 archive band by band, to test the unpacker with malformed archives.
 */
class Pack200Builder {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /**
     * Writes the segment header.
     */
    Pack200Builder header(HeaderBuilder header) throws IOException {
        header.writeTo(out);
        return this;
    }

    Pack200Builder band(Coding coding, int... values) throws IOException {
        coding.writeArrayTo(out, values.clone(), 0, values.length);
        return this;
    }

    void writeTo(File file) throws IOException {
        Files.write(file.toPath(), out.toByteArray());
    }

    /**
     * Unpacks the archive with the Java unpacker.
     */
    void unpack() throws IOException {
        Pack200.Unpacker unpacker = Pack200.newUnpacker();
        unpacker.properties().put(Utils.DEBUG_DISABLE_NATIVE, "true");
        unpacker.unpack(new ByteArrayInputStream(out.toByteArray()), new JarOutputStream(new ByteArrayOutputStream()));
    }
}
