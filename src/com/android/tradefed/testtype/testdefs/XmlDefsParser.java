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
package com.android.tradefed.testtype.testdefs;

import com.android.ddmlib.Log;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Parses a test_defs.xml file.
 * <p/>
 * See development/testrunner/test_defs.xsd for expected format
 */
class XmlDefsParser {

    private static final String LOG_TAG = "XmlDefsParser";

    private Map<String, InstrumentationTestDef> mTestDefsMap;

    /**
     * Thrown if test defs input could not be parsed
     */
    @SuppressWarnings("serial")
    public static class ParseException extends Exception {
        public ParseException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * SAX callback object. Handles parsing data from the xml tags.
     */
    private class DefsHandler extends DefaultHandler {

        private static final String TEST_TAG = "test";

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes)
                throws SAXException {
            if (TEST_TAG.equals(localName)) {
                final String defName = attributes.getValue("name");
                InstrumentationTestDef def = new InstrumentationTestDef(defName,
                        attributes.getValue("package"));
                def.setClassName(attributes.getValue("class"));
                def.setRunner(attributes.getValue("runner"));
                def.setContinuous("true".equals(attributes.getValue("continuous")));
                mTestDefsMap.put(defName, def);
            }
        }
    }

    XmlDefsParser() {
        // Uses a LinkedHashmap to have predictable iteration order
        mTestDefsMap = new LinkedHashMap<String, InstrumentationTestDef>();
    }

    /**
     * Parses out test_defs data contained in given input.
     * <p/>
     * Currently performs limited error checking.
     *
     * @param xmlInput
     * @throws ParseException if input could not be parsed
     */
    void parse(InputStream xmlInput) throws ParseException  {
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            SAXParser parser;
            parser = parserFactory.newSAXParser();

            DefsHandler defsHandler = new DefsHandler();
            parser.parse(new InputSource(xmlInput), defsHandler);
        } catch (ParserConfigurationException e) {
            Log.e(LOG_TAG, e);
            throw new ParseException(e);
        } catch (SAXException e) {
            Log.e(LOG_TAG, e);
            throw new ParseException(e);
        } catch (IOException e) {
            Log.e(LOG_TAG, e);
            throw new ParseException(e);
        }
    }

    /**
     * Gets the list of parsed test definitions. The element order should be consistent with the
     * order of elements in the parsed input.
     */
    public Collection<InstrumentationTestDef> getTestDefs() {
        return mTestDefsMap.values();
    }
}
