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
import java.io.OutputStream;

import static io.pack200.BandStructure.*;
import static io.pack200.Constants.*;

/**
 * Builds the segment header of a pack200 archive (magic number, version, options and counts).
 */
class HeaderBuilder {
    private final Package.Version version;
    private final int[] counts = new int[CONSTANT_Limit];
    private int classCount;

    HeaderBuilder() {
        this(JAVA7_PACKAGE_VERSION);
    }

    HeaderBuilder(Package.Version version) {
        this.version = version;
    }

    /**
     * Sets the number of constants with the given tag (CONSTANT_Utf8, CONSTANT_BootstrapMethod...).
     */
    HeaderBuilder count(byte tag, int count) {
        counts[tag] = count;
        return this;
    }

    HeaderBuilder classes(int count) {
        classCount = count;
        return this;
    }

    void writeTo(OutputStream out) throws IOException {
        // the MethodHandle, MethodType, BootstrapMethod and InvokeDynamic constants appeared in the version 170
        boolean extras = !version.lessThan(JAVA7_PACKAGE_VERSION);

        int options = AO_HAVE_CP_NUMBERS;
        if (extras) {
            options |= AO_HAVE_CP_EXTRAS;
        }

        out.write(new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xD0, (byte) 0x0D });
        write(out, version.minor, version.major, options);
        for (byte tag : ConstantPool.TAGS_IN_ORDER) {
            if (extras || !isExtra(tag)) {
                write(out, counts[tag]);
            }
        }
        write(out, 0, 0, 49, classCount); // ic_count, default_class_minver, default_class_majver, class_count
    }

    private static boolean isExtra(byte tag) {
        switch (tag) {
            case CONSTANT_MethodHandle:
            case CONSTANT_MethodType:
            case CONSTANT_BootstrapMethod:
            case CONSTANT_InvokeDynamic:
                return true;
            default:
                return false;
        }
    }

    private static void write(OutputStream out, int... values) throws IOException {
        UNSIGNED5.writeArrayTo(out, values, 0, values.length);
    }
}
