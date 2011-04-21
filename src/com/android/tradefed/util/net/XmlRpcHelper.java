/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tradefed.util.net;

import org.kxml2.io.KXmlSerializer;

import java.io.IOException;

/**
 * A mechanism to simplify writing XmlRpc.  Deals with XML and XmlRpc boilerplate.
 * <p/>
 * Call semantics:
 * <ol>
 * <li>Call an "Open" method</li>
 * <li>Construct the value on the serializer.  This may involve calling other helper methods,
 *     perhaps recursively.</li>
 * <li>Call a respective "Close" method</li>
 * </ol>
 * <p/>
 * It is the caller's responsibility to ensure that "Open" and "Close" calls are matched properly.
 * The helper methods do not check this.
 */
public class XmlRpcHelper {
    /**
     * Write the opening of a method call to the serializer.
     *
     * @param serializer the {@link KXmlSerializer}
     * @param ns the namespace
     * @param name the name of the XmlRpc method to invoke
     */
    public static void writeOpenMethodCall(KXmlSerializer serializer, String ns, String name)
            throws IOException {
        serializer.startTag(ns, "methodCall");
        serializer.startTag(ns, "methodName");
        serializer.text(name);
        serializer.endTag(ns, "methodName");

        serializer.startTag(ns, "params");
    }

    /**
     * Write the end of a method call to the serializer.
     *
     * @param serializer the {@link KXmlSerializer}
     * @param ns the namespace
     */
    public static void writeCloseMethodCall(KXmlSerializer serializer, String ns)
            throws IOException {
        serializer.endTag(ns, "params");
        serializer.endTag(ns, "methodCall");
    }

    /**
     * Write the opening of a method argument to the serializer.  After calling this function, the
     * caller should send the argument value directly to the serializer.
     *
     * @param serializer the {@link KXmlSerializer}
     * @param ns the namespace
     * @param valueType the XmlRpc type of the method argument
     */
    public static void writeOpenMethodArg(KXmlSerializer serializer, String ns, String valueType)
            throws IOException {
        serializer.startTag(ns, "param");
        serializer.startTag(ns, "value");
        serializer.startTag(ns, valueType);
    }

    /**
     * Write the end of a method argument to the serializer.
     *
     * @param serializer the {@link KXmlSerializer}
     * @param ns the namespace
     * @param valueType the XmlRpc type of the method argument
     */
    public static void writeCloseMethodArg(KXmlSerializer serializer, String ns, String valueType)
            throws IOException {
        serializer.endTag(ns, valueType);
        serializer.endTag(ns, "value");
        serializer.endTag(ns, "param");
    }

    /**
     * Write a full method argument to the serializer.  This function is not paired with any other
     * function.
     *
     * @param serializer the {@link KXmlSerializer}
     * @param ns the namespace
     * @param valueType the XmlRpc type of the method argument
     * @param value the value of the method argument
     */
    public static void writeFullMethodArg(KXmlSerializer serializer, String ns, String valueType,
            String value) throws IOException {
        serializer.startTag(ns, "param");
        serializer.startTag(ns, valueType);
        serializer.text(value);
        serializer.endTag(ns, valueType);
        serializer.endTag(ns, "param");
    }

    /**
     * Write the opening of a struct member to the serializer.  After calling this function, the
     * caller should send the member value directly to the serializer.
     *
     * @param serializer the {@link KXmlSerializer}
     * @param ns the namespace
     * @param name the name of the XmlRpc member
     * @param valueType the XmlRpc type of the member
     */
    public static void writeOpenStructMember(KXmlSerializer serializer, String ns, String name,
            String valueType) throws IOException {
        serializer.startTag(ns, "member");
        serializer.startTag(ns, "name");
        serializer.text(name);
        serializer.endTag(ns, "name");

        serializer.startTag(ns, "value");
        serializer.startTag(ns, valueType);
    }

    /**
     * Write the end of a struct member to the serializer.
     *
     * @param serializer the {@link KXmlSerializer}
     * @param ns the namespace
     * @param valueType the XmlRpc type of the member
     */
    public static void writeCloseStructMember(KXmlSerializer serializer, String ns,
            String valueType) throws IOException {
        serializer.endTag(ns, valueType);
        serializer.endTag(ns, "value");
        serializer.endTag(ns, "member");
    }
}

