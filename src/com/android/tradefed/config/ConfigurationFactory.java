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
package com.android.tradefed.config;

import com.android.ddmlib.Log;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ClassPathScanner;
import com.android.tradefed.util.ClassPathScanner.IClassPathFilter;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Factory for creating {@link IConfiguration}.
 */
public class ConfigurationFactory implements IConfigurationFactory {

	private static final String LOG_TAG = "ConfigurationFactory";
	private static IConfigurationFactory sInstance = null;
	private static final String CONFIG_SUFFIX = ".xml";
	private static final String CONFIG_PREFIX = "config/";

	private Map<String, ConfigurationDef> mConfigDefMap;

	/**
	 * A {@link IClassPathFilter} for configuration XML files.
	 */
	private class ConfigClasspathFilter implements IClassPathFilter {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean accept(String pathName) {
			// only accept entries that match the pattern, and that we don't
			// already know about
			return pathName.startsWith(CONFIG_PREFIX)
					&& pathName.endsWith(CONFIG_SUFFIX)
					&& !mConfigDefMap.containsKey(pathName);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String transform(String pathName) {
			// strip off CONFIG_PREFIX and CONFIG_SUFFIX
			int pathStartIndex = CONFIG_PREFIX.length();
			int pathEndIndex = pathName.length() - CONFIG_SUFFIX.length();
			return pathName.substring(pathStartIndex, pathEndIndex);
		}
	}

	/**
	 * A {@link Comparator} for {@link ConfigurationDef} that sorts by
	 * {@link ConfigurationDef#getName()}.
	 */
	private static class ConfigDefComparator implements
			Comparator<ConfigurationDef> {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int compare(ConfigurationDef d1, ConfigurationDef d2) {
			return d1.getName().compareTo(d2.getName());
		}

	}

	/**
	 * Implementation of {@link IConfigDefLoader} that tracks the included
	 * configurations from one root config, and throws an exception on circular
	 * includes.
	 */
	class ConfigLoader implements IConfigDefLoader {

		private final boolean mIsGlobalConfig;
		private Set<String> mIncludedConfigs = new HashSet<String>();

		public ConfigLoader(boolean isGlobalConfig) {
			mIsGlobalConfig = isGlobalConfig;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public ConfigurationDef getConfigurationDef(String name)
				throws ConfigurationException {
			if (mIncludedConfigs.contains(name)) {
				throw new ConfigurationException(
						String.format(
								"Circular configuration include: config '%s' is already included",
								name));
			}
			mIncludedConfigs.add(name);
			// first attempt to load cached config def
			ConfigurationDef def = mConfigDefMap.get(name);
			if (def == null) {
				// not found - load from file,解析cts.xml配置文件里的信息
				def = loadConfiguration(name);
				mConfigDefMap.put(name, def);
			}
			return def;
		}

		/**
		 * Loads a configuration.
		 *
		 * @param name
		 *            the name of a built-in configuration to load or a file
		 *            path to configuration xml to load
		 * @return the loaded {@link ConfigurationDef}
		 * @throws ConfigurationException
		 *             if a configuration with given name/file path cannot be
		 *             loaded or parsed
		 */
		ConfigurationDef loadConfiguration(String name)
				throws ConfigurationException {
			Log.i(LOG_TAG, String.format("Loading configuration '%s'", name));
			BufferedInputStream bufStream = getConfigStream(name);
			ConfigurationXmlParser parser = new ConfigurationXmlParser(this);
			return parser.parse(name, bufStream);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isGlobalConfig() {
			return mIsGlobalConfig;
		}
	}

	ConfigurationFactory() {
		mConfigDefMap = new Hashtable<String, ConfigurationDef>();
	}

	/**
	 * Get the singleton {@link IConfigurationFactory} instance.
	 */
	public static IConfigurationFactory getInstance() {
		if (sInstance == null) {
			sInstance = new ConfigurationFactory();
		}
		return sInstance;
	}

	/**
	 * Retrieve the {@link ConfigurationDef} for the given name
	 *
	 * @param name
	 *            the name of a built-in configuration to load or a file path to
	 *            configuration xml to load
	 * @return {@link ConfigurationDef}
	 * @throws ConfigurationException
	 *             if an error occurred loading the config
	 */
	private ConfigurationDef getConfigurationDef(String name, boolean isGlobal)
			throws ConfigurationException {
		return new ConfigLoader(isGlobal).getConfigurationDef(name);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IConfiguration createConfigurationFromArgs(String[] arrayArgs)
			throws ConfigurationException {
		List<String> listArgs = new ArrayList<String>(arrayArgs.length);
		IConfiguration config = internalCreateConfigurationFromArgs(arrayArgs,
				listArgs);
		config.setOptionsFromCommandLineArgs(listArgs);

		return config;
	}

	/**
	 * Creates a {@link Configuration} from the name given in arguments.
	 * <p/>
	 * Note will not populate configuration with values from options
	 *
	 * @param arrayArgs
	 *            the full list of command line arguments, including the config
	 *            name
	 * @param listArgs
	 *            an empty list, that will be populated with the remaining
	 *            option arguments
	 * @return
	 * @throws ConfigurationException
	 */
	private IConfiguration internalCreateConfigurationFromArgs(
			String[] arrayArgs, List<String> optionArgsRef)
			throws ConfigurationException {
		if (arrayArgs.length == 0) {
			throw new ConfigurationException(
					"Configuration to run was not specified");
		}
		optionArgsRef.addAll(Arrays.asList(arrayArgs));
		// first arg is config name
		final String configName = optionArgsRef.remove(0);
		ConfigurationDef configDef = getConfigurationDef(configName, false);
		return configDef.createConfiguration();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IGlobalConfiguration createGlobalConfigurationFromArgs(
			String[] arrayArgs, List<String> remainingArgs)
			throws ConfigurationException {
		List<String> listArgs = new ArrayList<String>(arrayArgs.length);
		// 读取tf_global_config.xml配置文件里的信息
		IGlobalConfiguration config = internalCreateGlobalConfigurationFromArgs(
				arrayArgs, listArgs);
		remainingArgs.addAll(config.setOptionsFromCommandLineArgs(listArgs));

		return config;
	}

	/**
	 * Creates a {@link Configuration} from the name given in arguments.
	 * <p/>
	 * Note will not populate configuration with values from options
	 *
	 * @param arrayArgs
	 *            the full list of command line arguments, including the config
	 *            name
	 * @param listArgs
	 *            an empty list, that will be populated with the remaining
	 *            option arguments
	 * @return
	 * @throws ConfigurationException
	 */
	private IGlobalConfiguration internalCreateGlobalConfigurationFromArgs(
			String[] arrayArgs, List<String> optionArgsRef)
			throws ConfigurationException {
		if (arrayArgs.length == 0) {
			throw new ConfigurationException(
					"Configuration to run was not specified");
		}
		optionArgsRef.addAll(Arrays.asList(arrayArgs));
		// first arg is config name
		final String configName = optionArgsRef.remove(0);
		// 读取tf_global_config.xml中内容,option属性存到mOptionList中,类存到mObjectClassMap中
		ConfigurationDef configDef = getConfigurationDef(configName, false);
		return configDef.createGlobalConfiguration();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void printHelp(PrintStream out) {
		// print general help
		// TODO: move this statement to Console
		out.println("Use 'run command <configuration_name> --help' to get list of options for a "
				+ "configuration");
		out.println("Use 'dump config <configuration_name>' to display the configuration's XML "
				+ "content.");
		out.println();
		out.println("Available configurations include:");
		try {
			loadAllConfigs(true);
		} catch (ConfigurationException e) {
			// ignore, should never happen
		}
		// sort the configs by name before displaying
		SortedSet<ConfigurationDef> configDefs = new TreeSet<ConfigurationDef>(
				new ConfigDefComparator());
		configDefs.addAll(mConfigDefMap.values());
		for (ConfigurationDef def : configDefs) {
			out.printf("  %s: %s", def.getName(), def.getDescription());
			out.println();
		}
	}

	/**
	 * Loads all configurations found in classpath.
	 *
	 * @param discardExceptions
	 *            true if any ConfigurationException should be ignored. Exposed
	 *            for unit testing
	 * @throws ConfigurationException
	 */
	void loadAllConfigs(boolean discardExceptions)
			throws ConfigurationException {
		ClassPathScanner cpScanner = new ClassPathScanner();
		Set<String> configNames = cpScanner
				.getClassPathEntries(new ConfigClasspathFilter());
		for (String configName : configNames) {
			try {
				ConfigurationDef configDef = getConfigurationDef(configName,
						false);
				mConfigDefMap.put(configName, configDef);
			} catch (ConfigurationException e) {
				Log.e(LOG_TAG, String.format(
						"Failed to load configuration '%s'. Reason: %s",
						configName, e.toString()));
				if (!discardExceptions) {
					throw e;
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void printHelpForConfig(String[] args, boolean importantOnly,
			PrintStream out) {
		try {
			IConfiguration config = internalCreateConfigurationFromArgs(args,
					new ArrayList<String>(args.length));
			config.printCommandUsage(importantOnly, out);
		} catch (ConfigurationException e) {
			// config must not be specified. Print generic help
			printHelp(out);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dumpConfig(String configName, PrintStream out) {
		try {
			InputStream configStream = getConfigStream(configName);
			StreamUtil.copyStreams(configStream, out);
		} catch (ConfigurationException e) {
			Log.e(LOG_TAG, e);
		} catch (IOException e) {
			Log.e(LOG_TAG, e);
		}
	}

	/**
	 * Return the path prefix of config xml files on classpath
	 * <p/>
	 * Exposed so unit tests can mock.
	 * 
	 * @return {@link String} path with trailing /
	 */
	String getConfigPrefix() {
		return CONFIG_PREFIX;
	}

	/**
	 * Loads an InputStream for given config name
	 *
	 * @param name
	 *            the configuration name to load
	 * @return a {@link BufferedInputStream} for reading config contents
	 * @throws ConfigurationException
	 *             if config could not be found
	 */
	private BufferedInputStream getConfigStream(String name)
			throws ConfigurationException {
		InputStream configStream = getClass()
				.getResourceAsStream(
						String.format("/%s%s%s", getConfigPrefix(), name,
								CONFIG_SUFFIX));
		String configFile = System.getProperty("CTS_ROOT") + File.separator
				+ "android-cts/tools/" + File.separator + CONFIG_PREFIX + name
				+ CONFIG_SUFFIX;
		if (configStream == null) {
			// now try to load from file
			try {
				configStream = new FileInputStream(configFile);
			} catch (FileNotFoundException e) {
				throw new ConfigurationException(String.format(
						"Could not find configuration '%s'", name));
			}
		}
		// buffer input for performance - just in case config file is large
		return new BufferedInputStream(configStream);
	}
}
