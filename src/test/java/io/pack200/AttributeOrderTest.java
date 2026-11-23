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
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Checks that the unpacker writes the InnerClasses attribute after the other
 * class attributes, even when it's transmitted explicitly in the archive.
 */
public class AttributeOrderTest {

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Nullable {}

    /**
     * Inspired by com.google.common.base.Equivalence. The class level type
     * annotation of Wrapper comes after its InnerClasses attribute in the
     * flag order, and the InnerClasses attribute is transmitted explicitly
     * because of the anonymous class generated for the private constructor.
     */
    static abstract class Equivalence<T> {
        <S extends T> Wrapper<S> wrap(S reference) {
            return new Wrapper<S>(this, reference);
        }

        static final class Wrapper<T extends @Nullable Object> {
            private final Equivalence<? super T> equivalence;
            private final T reference;

            private Wrapper(Equivalence<? super T> equivalence, T reference) {
                this.equivalence = equivalence;
                this.reference = reference;
            }
        }
    }

    @Test
    public void testInnerClassesAttributeLast() throws Exception {
        // jar with the classes of Equivalence
        File dir = new File("target/test-classes/io/pack200");
        String prefix = AttributeOrderTest.class.getSimpleName() + "$Equivalence";
        ByteArrayOutputStream jar = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(jar)) {
            for (File file : dir.listFiles()) {
                if (file.getName().startsWith(prefix)) {
                    out.putNextEntry(new JarEntry("io/pack200/" + file.getName()));
                    out.write(Files.readAllBytes(file.toPath()));
                }
            }
        }

        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        Pack200.newPacker().pack(new JarInputStream(new ByteArrayInputStream(jar.toByteArray())), packed);

        Pack200.Unpacker unpacker = Pack200.newUnpacker();
        unpacker.properties().put(Utils.DEBUG_DISABLE_NATIVE, "true");
        ByteArrayOutputStream unpacked = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(unpacked)) {
            unpacker.unpack(new ByteArrayInputStream(packed.toByteArray()), out);
        }

        boolean wrapperChecked = false;
        try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(unpacked.toByteArray()))) {
            for (JarEntry entry; (entry = in.getNextJarEntry()) != null; ) {
                if (entry.getName().endsWith(".class")) {
                    List<String> attributes = getClassAttributes(readAll(in));
                    if (attributes.contains("InnerClasses")) {
                        assertEquals("last attribute of " + entry.getName() + " " + attributes, "InnerClasses", attributes.get(attributes.size() - 1));
                    }
                    if (entry.getName().endsWith("$Wrapper.class")) {
                        assertTrue("type annotation of Wrapper " + attributes, attributes.contains("RuntimeVisibleTypeAnnotations"));
                        wrapperChecked = true;
                    }
                }
            }
        }
        assertTrue("Wrapper not found", wrapperChecked);
    }

    private static byte[] readAll(JarInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int n; (n = in.read(buffer)) != -1; ) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * Returns the names of the class attributes of a class file.
     */
    private static List<String> getClassAttributes(byte[] classfile) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(classfile));
        in.skipBytes(8); // magic, minor and major versions

        // constant pool
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1:  utf8[i] = in.readUTF(); break;                     // Utf8
                case 5: case 6:  in.skipBytes(8); i++; break;               // Long, Double
                case 7: case 8: case 16: case 19: case 20:  in.skipBytes(2); break; // Class, String, MethodType, Module, Package
                case 15:  in.skipBytes(3); break;                           // MethodHandle
                default:  in.skipBytes(4); break;                           // Integer, Float, refs, NameAndType, Dynamic, InvokeDynamic
            }
        }

        in.skipBytes(6); // access flags, this and super classes
        in.skipBytes(2 * in.readUnsignedShort()); // interfaces

        // fields and methods
        for (int k = 0; k < 2; k++) {
            int members = in.readUnsignedShort();
            for (int i = 0; i < members; i++) {
                in.skipBytes(6);
                int attributes = in.readUnsignedShort();
                for (int j = 0; j < attributes; j++) {
                    in.skipBytes(2);
                    in.skipBytes(in.readInt());
                }
            }
        }

        List<String> names = new ArrayList<>();
        int attributes = in.readUnsignedShort();
        for (int i = 0; i < attributes; i++) {
            names.add(utf8[in.readUnsignedShort()]);
            in.skipBytes(in.readInt());
        }
        return names;
    }
}
