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

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.QuotationAwareTokenizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for file that contains set of command lines.
 * <p/>
 * The syntax of the given file should be series of lines. Each line is a command; that is, a
 * configuration plus its options:
 * <pre>
 *   [options] config-name
 *   [options] config-name2
 *   ...
 * </pre>
 */
class CommandFileParser {

    /**
     * A pattern that matches valid macro usages and captures the name of the macro.
     * Macro names must start with an alpha character, and may contain alphanumerics, underscores,
     * or hyphens.
     */
    private static final Pattern mMacroPattern = Pattern.compile("([a-z][a-z0-9_-]*)\\(\\)",
            Pattern.CASE_INSENSITIVE);

    private Map<String, CommandLine> mMacros = new HashMap<String, CommandLine>();
    private Map<String, List<CommandLine>> mLongMacros = new HashMap<String, List<CommandLine>>();
    private List<CommandLine> mLines = new LinkedList<CommandLine>();

    private Collection<String> mIncludedFiles = new HashSet<String>();

    @SuppressWarnings("serial")
    private class CommandLine extends LinkedList<String> {
        CommandLine() {
            super();
        }

        CommandLine(Collection<? extends String> c) {
            super(c);
        }
    }

    /**
     * Represents a bitmask.  Useful because it caches the number of bits which are set.
     */
    static class Bitmask {
        private List<Boolean> mBitmask = new LinkedList<Boolean>();
        private int mNumBitsSet = 0;

        public Bitmask(int nBits) {
            this(nBits, false);
        }

        public Bitmask(int nBits, boolean initialValue) {
            for (int i = 0; i < nBits; ++i) {
                mBitmask.add(initialValue);
            }
            if (initialValue) {
                mNumBitsSet = nBits;
            }
        }

        /**
         * Return the number of bits which are set (rather than unset)
         */
        public int getSetCount() {
            return mNumBitsSet;
        }

        public boolean get(int idx) {
            return mBitmask.get(idx);
        }

        public boolean set(int idx) {
            boolean retVal = mBitmask.set(idx, true);
            if (!retVal) {
                mNumBitsSet++;
            }
            return retVal;
        }

        public boolean unset(int idx) {
            boolean retVal = mBitmask.set(idx, false);
            if (retVal) {
                mNumBitsSet--;
            }
            return retVal;
        }

        public boolean remove(int idx) {
            boolean retVal = mBitmask.remove(idx);
            if (retVal) {
                mNumBitsSet--;
            }
            return retVal;
        }

        public void add(int idx, boolean val) {
            mBitmask.add(idx, val);
            if (val) {
                mNumBitsSet++;
            }
        }

        /**
         * Insert a bunch of identical values in the specified spot in the mask
         *
         * @param idx the index where the first new value should be set.
         * @param count the number of new values to insert
         * @param val the parity of the new values
         */
        public void addN(int idx, int count, boolean val) {
            for (int i = 0; i < count; ++i) {
                add(idx, val);
            }
        }
    }

    /**
     * Checks if a line matches the expected format for a (short) macro:
     * MACRO (name) = (token) [(token)...]
     * This method verifies that:
     * <ol>
     *   <li>Line is at least four tokens long</li>
     *   <li>The first token is "MACRO" (case-sensitive)</li>
     *   <li>The third token is an equal-sign</li>
     * </ol>
     *
     * @return {@code true} if the line matches the macro format, {@false} otherwise
     */
    private static boolean isLineMacro(CommandLine line) {
        return line.size() >= 4 && "MACRO".equals(line.get(0)) && "=".equals(line.get(2));
    }

    /**
     * Checks if a line matches the expected format for the opening line of a long macro:
     * LONG MACRO (name)
     *
     * @return {@code true} if the line matches the long macro format, {@code false} otherwise
     */
    private static boolean isLineLongMacro(CommandLine line) {
        return line.size() == 3 && "LONG".equals(line.get(0)) && "MACRO".equals(line.get(1));
    }

    /**
     * Checks if a line matches the expected format for an INCLUDE directive
     *
     * @return {@code true} if the line is an INCLUDE directive, {@code false} otherwise
     */
    private static boolean isLineIncludeDirective(CommandLine line) {
        return line.size() == 2 && "INCLUDE".equals(line.get(0));
    }

    /**
     * Checks if a line should be parsed or ignored.  Basically, ignore if the line is commented
     * or is empty.
     *
     * @param line A {@see String} containing the line of input to check
     * @return {@code true} if we should parse the line, {@code false} if we should ignore it.
     */
    private static boolean shouldParseLine(String line) {
        line = line.trim();
        return !(line.isEmpty() || line.startsWith("#"));
    }

    /**
     * Does a single pass of the input CommandFile, storing input lines as macros, long macros, or
     * commands.
     *
     * Note that this method may call itself recursively to handle the INCLUDE directive.
     */
    private void scanFile(File file) throws IOException, ConfigurationException {
        if (mIncludedFiles.contains(file.getAbsolutePath())) {
            // Repeated include; ignore
            CLog.v("Skipping repeated include of file %s.", file.toString());
            return;
        } else {
            mIncludedFiles.add(file.getAbsolutePath());
        }

        BufferedReader fileReader = createCommandFileReader(file);
        String inputLine = null;
        try {
            while ((inputLine = fileReader.readLine()) != null) {
                inputLine = inputLine.trim();
                if (shouldParseLine(inputLine)) {
                    CommandLine lArgs = null;
                    try {
                        String[] args = QuotationAwareTokenizer.tokenizeLine(inputLine);
                        lArgs = new CommandLine(Arrays.asList(args));
                    } catch (IllegalArgumentException e) {
                        throw new ConfigurationException(e.getMessage());
                    }

                    if (isLineMacro(lArgs)) {
                        // Expected format: MACRO <name> = <token> [<token>...]
                        String name = lArgs.get(1);
                        CommandLine expansion = new CommandLine(lArgs.subList(3, lArgs.size()));
                        CommandLine prev = mMacros.put(name, expansion);
                        if (prev != null) {
                            CLog.e("Overwrote short macro '%s' while parsing file %s", name, file);
                            CLog.e("value '%s' replaced previous value '%s'", expansion, prev);
                        }
                    } else if (isLineLongMacro(lArgs)) {
                        // Expected format: LONG MACRO <name>\n(multiline expansion)\nEND MACRO
                        String name = lArgs.get(2);
                        List<CommandLine> expansion = new LinkedList<CommandLine>();

                        inputLine = fileReader.readLine();
                        while (!"END MACRO".equals(inputLine)) {
                            if (inputLine == null) {
                                // Syntax error
                                throw new ConfigurationException(String.format(
                                        "Syntax error: Unexpected EOF while reading definition " +
                                        "for LONG MACRO %s.", name));
                            }
                            if (shouldParseLine(inputLine)) {
                                // Store the tokenized line
                                CommandLine line = new CommandLine(Arrays.asList(
                                        QuotationAwareTokenizer.tokenizeLine(inputLine)));
                                expansion.add(line);
                            }

                            // Advance
                            inputLine = fileReader.readLine();
                        }
                        CLog.d("Parsed %d-line definition for long macro %s", expansion.size(),
                                name);

                        List<CommandLine> prev = mLongMacros.put(name, expansion);
                        if (prev != null) {
                            CLog.e("Overwrote long macro %s while parsing file %s", name, file);
                            CLog.e("%d-line definition replaced previous %d-line definition",
                                    expansion.size(), prev.size());
                        }
                    } else if (isLineIncludeDirective(lArgs)) {
                        File toScan = new File(lArgs.get(1));
                        if (toScan.isAbsolute()) {
                            CLog.d("Got an include directive for absolute path %s.", lArgs.get(1));
                        } else {
                            File parent = file.getParentFile();
                            toScan = new File(parent, lArgs.get(1));
                            CLog.d("Got an include directive for relative path %s, using '%s' " +
                                    "for parent dir", lArgs.get(1), parent);
                        }
                        scanFile(toScan);
                    } else {
                        mLines.add(lArgs);
                    }
                }
            }
        } finally {
            fileReader.close();
        }
    }

    /**
     * Parses the commands contained in {@code file}, doing macro expansions as necessary, and adds
     * them to {@code scheduler}.
     *
     * @param file the {@link File} to parse
     * @param scheduler the {@link ICommandScheduler} to add commands to
     * @throws IOException if failed to read file
     * @throws ConfigurationException if content of file could not be parsed
     */
    public void parseFile(File file, ICommandScheduler scheduler) throws IOException,
            ConfigurationException {
        List<String> empty = Collections.emptyList();
        parseFile(file, scheduler, empty);
    }

    /**
     * Parses the commands contained in {@code file}, doing macro expansions as necessary, and adds
     * them to {@code scheduler}.
     *
     * @param file the {@link File} to parse
     * @param scheduler the {@link ICommandScheduler} to add commands to
     * @param args A {@link List} of {@link String} arguments to append to each command
     * @throws IOException if failed to read file
     * @throws ConfigurationException if content of file could not be parsed
     */
    public void parseFile(File file, ICommandScheduler scheduler, List<String> args)
            throws IOException, ConfigurationException {
        scanFile(file);

        // Now perform macro expansion
        /**
         * inputBitmask is used to stop iterating when we're sure there are no more macros to
         * expand.  It is a bitmask where the (k)th bit represents the (k)th element in
         * {@code mLines.}
         * <p>
         * Each bit starts as {@code true}, meaning that each line in mLines may have macro calls to
         * be expanded.  We set bits of {@code inputBitmask} to {@code false} once we've determined
         * that the corresponding lines of {@code mLines} have been fully expanded, which allows us
         * to skip those lines on subsequent scans.
         * <p>
         * {@code inputBitmaskCount} stores the quantity of {@code true} bits in
         * {@code inputBitmask}.  Once {@code inputBitmaskCount == 0}, we are done expanding macros.
         */
        Bitmask inputBitmask = new Bitmask(mLines.size(), true);

        // Do a maximum of 10 iterations of expansion
        // FIXME: make this configurable
        for (int iCount = 0; iCount < 10 && inputBitmask.getSetCount() > 0; ++iCount) {
            CLog.d("### Expansion iteration %d", iCount);

            int inputIdx = 0;
            while (inputIdx < mLines.size()) {
                if (!inputBitmask.get(inputIdx)) {
                    // Skip this line; we've already determined that it doesn't contain any macro
                    // calls to be expanded.
                    CLog.d("skipping input line %s", mLines.get(inputIdx));
                    ++inputIdx;
                    continue;
                }

                CommandLine line = mLines.get(inputIdx);
                boolean sawMacro = expandMacro(line);
                List<CommandLine> longMacroExpansion = expandLongMacro(line, !sawMacro);

                if (longMacroExpansion == null) {
                    if (sawMacro) {
                        // We saw and expanded a short macro.  This may have pulled in another macro
                        // to expand, so leave inputBitmask alone.
                    } else {
                        // We did not find any macros (long or short) to expand, thus all expansions
                        // are done for this CommandLine.  Update inputBitmask appropriately.
                        inputBitmask.unset(inputIdx);
                    }

                    // Finally, advance.
                    ++inputIdx;
                } else {
                    // We expanded a long macro.  First, actually insert the expansion in place of
                    // the macro call
                    mLines.remove(inputIdx);
                    inputBitmask.remove(inputIdx);
                    mLines.addAll(inputIdx, longMacroExpansion);
                    inputBitmask.addN(inputIdx, longMacroExpansion.size(), true);

                    // And advance past the end of the expanded macro
                    inputIdx += longMacroExpansion.size();
                }
            }
        }

        for (CommandLine commandLine : mLines) {
            CLog.v("Adding line with parts: %s + %s", commandLine.toString(), args.toString());
            String[] aryCmdLine = new String[commandLine.size() + args.size()];
            int outIdx = 0;
            for (;outIdx < commandLine.size(); ++outIdx) {
                aryCmdLine[outIdx] = commandLine.get(outIdx);
            }
            for (int i = 0; i < args.size(); ++outIdx, ++i) {
                aryCmdLine[outIdx] = args.get(i);
            }
            CLog.d("Adding line: %s", Arrays.toString(aryCmdLine));
            scheduler.addCommand(aryCmdLine);
        }
    }

    /**
     * Performs one level of macro expansion for the first macro used in the line
     */
    private List<CommandLine> expandLongMacro(CommandLine line, boolean checkMissingMacro)
            throws ConfigurationException {
        for (int idx = 0; idx < line.size(); ++idx) {
            String token = line.get(idx);
            Matcher matchMacro = mMacroPattern.matcher(token);
            if (matchMacro.matches()) {
                // we hit a macro; expand it
                List<CommandLine> expansion = new LinkedList<CommandLine>();
                String name = matchMacro.group(1);
                List<CommandLine> longMacro = mLongMacros.get(name);
                if (longMacro == null) {
                    if (checkMissingMacro) {
                        // If the expandMacro method hits an unrecognized macro, it will leave it in
                        // the stream for this method.  If it's not recognized here, throw an
                        // exception
                        throw new ConfigurationException(String.format(
                                "Macro call '%s' does not match any macro definitions.", name));
                    } else {
                        // At this point, it may just be a short macro
                        CLog.d("Macro call '%s' doesn't match any long macro definitions.", name);
                        return null;
                    }
                }

                CommandLine prefix = new CommandLine(line.subList(0, idx));
                CommandLine suffix = new CommandLine(line.subList(idx, line.size()));
                suffix.remove(0);
                for (CommandLine macroLine : longMacro) {
                    CommandLine expanded = new CommandLine();
                    expanded.addAll(prefix);
                    expanded.addAll(macroLine);
                    expanded.addAll(suffix);
                    expansion.add(expanded);
                }

                // Only expand a single macro usage at a time
                return expansion;
            }
        }
        return null;
    }

    /**
     * Performs one level of macro expansion for every macro used in the line
     *
     * @return {@code true} if a macro was found and expanded, {@code false} if no macro was found
     */
    private boolean expandMacro(CommandLine line) {
        boolean sawMacro = false;

        int idx = 0;
        while (idx < line.size()) {
            String token = line.get(idx);
            Matcher matchMacro = mMacroPattern.matcher(token);
            if (matchMacro.matches() && mMacros.containsKey(matchMacro.group(1))) {
                // we hit a macro; expand it
                String name = matchMacro.group(1);
                CommandLine macro = mMacros.get(name);
                CLog.d("Gotcha!  Expanding macro '%s' to '%s'", name, macro);
                line.remove(idx);
                line.addAll(idx, macro);
                idx += macro.size();
                sawMacro = true;
            } else {
                ++idx;
            }
        }
        return sawMacro;
    }

    /**
     * Create a reader for the command file data.
     * <p/>
     * Exposed for unit testing.
     *
     * @param file the command {@link File}
     * @return the {@link BufferedReader}
     * @throws IOException if failed to read data
     */
    BufferedReader createCommandFileReader(File file) throws IOException {
        return new BufferedReader(new FileReader(file));
    }
}
