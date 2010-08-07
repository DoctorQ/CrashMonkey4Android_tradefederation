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
package com.android.tradefed.command;

import com.android.ddmlib.Log;
import com.android.tradefed.config.ConfigurationException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for file that contains set of configs.
 * <p/>
 * The syntax of the given file should be series of lines. Each line is one configuration plus its
 * options, delimited by whitespace:
 * <pre>
 *   [options] config-name
 *   [options] config-name2
 *   ...
 * </pre>
 */
class ConfigFileParser {

    /**
     * Parse configs contained in file and adds them to given scheduler.
     *
     * @param file the {@link File} to parse
     * @param scheduler the {@link ICommandScheduler} to add configs to
     * @throws IOException if failed to read file
     * @throws ConfigurationException if content of file could not be parsed
     */
    public void parseFile(File file, ICommandScheduler scheduler) throws IOException,
            ConfigurationException {
        BufferedReader fileReader = createConfigFileReader(file);
        String line = null;
        while ((line = fileReader.readLine()) != null) {
            line = line.trim();
            // ignore empty or commented lines
            if (line.length() > 0 && !line.startsWith("#")) {
                String[] args = tokenizeLine(line);
                scheduler.addConfig(args);
            }
        }
    }

    /**
     * Tokenizes the string, splitting on spaces.  Does not split between consecutive, unquoted
     * double-quote marks.
     * <p/>
     * How the tokenizer works:
     * <ol>
     *     <li> Split the string into "characters" where each "character" is either an escaped
     *          character like \" (that is, "\\\"") or a single real character like f (just "f").
     *     <li> For each "character"
     *     <ol>
     *         <li> If it's a space, finish a token unless we're being quoted
     *         <li> If it's a quotation mark, flip the "we're being quoted" bit
     *         <li> Otherwise, add it to the token being built
     *     </ol>
     *     <li> At EOL, we typically haven't added the final token to the (tokens) {@link ArrayList}
     *     <ol>
     *         <li> If the last "character" is an escape character, throw an exception; that's not
     *              valid
     *         <li> If we're in the middle of a quotation, throw an exception; that's not valid
     *         <li> Otherwise, add the final token to (tokens)
     *     </ol>
     *     <li> Return a String[] version of (tokens)
     * </ol>
     *
     * @param line A {@link String} to be tokenized
     * @return A tokenized version of the string
     * @throws ConfigurationException
     */
    private static String[] tokenizeLine(String line) throws ConfigurationException {
        ArrayList<String> tokens = new ArrayList<String>();
        StringBuilder token = new StringBuilder();
        // This pattern matches an escaped character or a character.  Escaped char takes precedence
        final Pattern charPattern = Pattern.compile("\\\\.|.");
        final Matcher charMatcher = charPattern.matcher(line);
        String aChar = "";
        boolean quotation = false;

        Log.d("TOKEN", String.format("Trying to tokenize the line '%s'", line));
        while (charMatcher.find()) {
            aChar = charMatcher.group();
            Log.v("TOKEN", String.format("Got a character: '%s'", aChar));

            if (" ".equals(aChar)) {
                if (quotation) {
                    // inside a quotation; treat spaces as part of the token
                    token.append(aChar);
                } else {
                    if (token.length() > 0) {
                        // this is the end of a non-empty token; dump it in our list of tokens,
                        // clear our temp storage, and keep rolling
                        Log.v("TOKEN", String.format("Finished token '%s'", token.toString()));
                        tokens.add(token.toString());
                        token.delete(0, token.length());
                    }
                    // otherwise, this is the non-first in a sequence of spaces; ignore.
                }
            } else if ("\"".equals(aChar)) {
                // unescaped quotation mark; flip quotation state
                Log.v("TOKEN", "Flipped quotation state");
                quotation ^= true;
            } else {
                // default case: add the character to the token being built
                Log.v("TOKEN", String.format("Adding character '%s' to token '%s'", aChar, token));
                token.append(aChar);
            }
        }

        if (quotation || "\\".equals(aChar)) {
            // We ended in a quotation or with an escape character; this is not valid
            throw new ConfigurationException("Unexpected EOL in a quotation or after an escape " +
                    "character");
        }

        // Add the final token to the tokens array.
        if (token.length() > 0) {
            Log.v("TOKEN", String.format("Finished final token '%s'", token.toString()));
            tokens.add(token.toString());
            token.delete(0, token.length());
        }

        String[] tokensArray = new String[tokens.size()];
        return tokens.toArray(tokensArray);
    }

    /**
     * Create a reader for the config file data.
     * <p/>
     * Exposed for unit testing.
     *
     * @param file the config data {@link File}
     * @return the {@link BufferedReader}
     * @throws IOException if failed to read data
     */
    BufferedReader createConfigFileReader(File file) throws IOException {
        return new BufferedReader(new FileReader(file));
    }
}
