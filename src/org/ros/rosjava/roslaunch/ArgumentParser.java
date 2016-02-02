package org.ros.rosjava.roslaunch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: does not support the - option to read from stdin

/**
 * The ArgumentParser class is responsible for parsing
 * command line arguments for the roslaunch application.
 */
public class ArgumentParser
{
	private static final String ARGS_OPTION = "args";
	private static final String CHILD_OPTION = "child";
	private static final String CORE_OPTION = "core";
	private static final String DISABLE_TITLE_OPTION = "disable-title";
	private static final String DUMP_PARAMS_OPTION = "dump-params";
	private static final String FILES_OPTION = "files";
	private static final String FIND_NODE_OPTION = "find-node";
	private static final String HELP_OPTION = "help";
	private static final String LOCAL_OPTION = "local";
	private static final String NODES_OPTION = "nodes";
	private static final String NUM_WORKERS_OPTION = "numworkers";
	private static final String PID_OPTION = "pid";
	private static final String PORT_OPTION = "port";
	private static final String ROS_ARGS_OPTION = "ros-args";
	private static final String RUN_ID_OPTION = "run_id";
	private static final String SCREEN_OPTION = "screen";
	private static final String SERVER_URI_OPTION = "server_uri";
	private static final String SKIP_LOG_CHECK_OPTION = "skip-log-check";
	private static final String TIMEOUT_OPTION = "timeout";
	private static final String VERBOSE_OPTION = "verbose";
	private static final String WAIT_OPTION = "wait";

	// Special options
	private static final String HOSTNAME_SPECIAL_OPTION = "__hostname";
	private static final String IP_SPECIAL_OPTION = "__ip";

	/** The value of the port option. */
	private int m_port;
	/** The value of the num workers option. */
	private int m_numWorkers;
	/** The value of the timeout option. */
	private float m_timeout;
	/** True if the - option was specified, false otherwise */
	private boolean m_readStdin;

	/** The List of supported command line flags. */
	List<String> m_flags;
	/** The List of command line options that require values. */
	List<String> m_optionsWithValues;

	/** The List of launch files specified on the command line. */
	List<String> m_launchFiles;
	/** The Map of options specified on the command line. */
	Map<String, String> m_options;
	/** The Map of args specified on the command line. */
	Map<String, String> m_args;
	/** The Map of special args specified on the command line. */
	Map<String, String> m_specialArgs;

	/** Map of single dash options mapped to double dash option names. */
	Map<String, String> m_optionAliases;

	/** A List containing Lists of options that cannot be specified simultaneously */
	List<List<String>> m_invalidPairedOptions;

	/**
	 * Constructor
	 *
	 * Create an ArgumentParser object.
	 *
	 * @param inputArgs the input command line arguments
	 */
	@SuppressWarnings("serial")
	public ArgumentParser(final String[] inputArgs)
	{
		m_launchFiles = new ArrayList<String>();
		m_options = new HashMap<String, String>();
		m_args = new HashMap<String, String>();
		m_specialArgs = new HashMap<String, String>();
		m_readStdin = false;

		// List of command line arguments that are flags, i.e.,
		// arguments that cannot have values specified with them
		// without generating an error
		m_flags = new ArrayList<String>();
		m_flags.add(CORE_OPTION);
		m_flags.add(DISABLE_TITLE_OPTION);
		m_flags.add(DUMP_PARAMS_OPTION);
		m_flags.add(FILES_OPTION);
		m_flags.add(HELP_OPTION);
		m_flags.add(LOCAL_OPTION);
		m_flags.add(NODES_OPTION);
		m_flags.add(ROS_ARGS_OPTION);
		m_flags.add(SCREEN_OPTION);
		m_flags.add(SKIP_LOG_CHECK_OPTION);
		m_flags.add(VERBOSE_OPTION);
		m_flags.add(WAIT_OPTION);

		// List of command line arguments that require a value
		// otherwise will generate an error
		m_optionsWithValues = new ArrayList<String>();
		m_optionsWithValues.add(ARGS_OPTION);
		m_optionsWithValues.add(CHILD_OPTION);
		m_optionsWithValues.add(FIND_NODE_OPTION);
		m_optionsWithValues.add(NUM_WORKERS_OPTION);
		m_optionsWithValues.add(PID_OPTION);
		m_optionsWithValues.add(PORT_OPTION);
		m_optionsWithValues.add(RUN_ID_OPTION);
		m_optionsWithValues.add(SERVER_URI_OPTION);
		m_optionsWithValues.add(TIMEOUT_OPTION);

		// Create a map from single dash options to their
		// double dash options
		m_optionAliases = new HashMap<String, String>();
		m_optionAliases.put("c", CHILD_OPTION);
		m_optionAliases.put("h", HELP_OPTION);
		m_optionAliases.put("p", PORT_OPTION);
		m_optionAliases.put("t", TIMEOUT_OPTION);
		m_optionAliases.put("u", SERVER_URI_OPTION);
		m_optionAliases.put("v", VERBOSE_OPTION);
		m_optionAliases.put("w", NUM_WORKERS_OPTION);


		// Create a list which contains lists of options that
		// cannot be used at the same time
		m_invalidPairedOptions = new ArrayList<List<String>>();
		m_invalidPairedOptions.add(new ArrayList<String>() {{
			add(NODES_OPTION);
			add(FIND_NODE_OPTION);
			add(ARGS_OPTION);
			add(ROS_ARGS_OPTION);
		}});
		m_invalidPairedOptions.add(new ArrayList<String>() {{
			add(WAIT_OPTION);
			add(CORE_OPTION);
		}});

		// Iterate over all of the arguments
		for (int index = 0; index < inputArgs.length; ++index)
		{
			String arg = inputArgs[index];
			if (arg.startsWith("--"))
			{
				// This is a double dash option
				handleDoubleDashOption(arg);
			}
			else if (arg.startsWith("-"))
			{
				// Check for the stdin argument
				if (arg.compareTo("-") == 0)
				{
					// Do not allow multiple - options
					if (m_readStdin) {
						throw new RuntimeException(
							"Only a single instance of the dash ('-') may be specified.");
					}
					m_readStdin = true;
					continue;
				}

				// This is a single dash option, grab the next argument
				// as its potential value
				String potentialValue = null;
				if (index < (inputArgs.length - 1)) {
					potentialValue = inputArgs[index + 1];
				}

				if (handleSingleDashOption(arg, potentialValue)) {
					index++;  // Skip the next argument
				}
			}
			else if (arg.contains(":="))
			{
				// Any arguments that start with a double underscore
				// are considered special, and set properties on the
				// roslaunch process itself rather than on nodes
				if (arg.startsWith("__")) {
					handleArgPair(arg, m_specialArgs);
				}
				else {
					handleArgPair(arg, m_args);
				}
			}
			else {
				// This is a launch file
				m_launchFiles.add(arg);
			}
		}

		getOptionValues();
	}

	/**
	 * Validate all of the command line arguments.
	 */
	public void validateArgs()
	{
		// Determine if any options were used together when they are
		// not allowed to be
		for (List<String> options : m_invalidPairedOptions)
		{
			int index = 0;
			int numSpecified = 0;
			String optionsStr = "";
			for (String option : options)
			{
				if (hasOption(option)) numSpecified++;

				// Generate a string containing all invalid paired options
				if (index++ > 0) optionsStr += ", ";  // Add separator between options
				optionsStr += "--" + option;
			}

			// If two of the invalid paired options are used at the same time
			// then we need to throw an error
			if (numSpecified > 1) {
				throw new RuntimeException(
					"only one of [" + optionsStr + "] may be specified");
			}
		}

		// The --child and --server_uri options are required together
		if (hasChild() != hasServerUri()) {
			throw new RuntimeException(
				"--child option requires --server_uri to be set as well");
		}

		// Other child option requirements
		if (hasChild())
		{
			// Child required run id
			if (!hasRunId()) {
				throw new RuntimeException(
					"--child option requires --run_id to be set as well");
			}

			// Port cannot be used with child
			if (hasPort()) {
				throw new RuntimeException(
					"--port option cannot be used with the --child option");
			}

			if (getNumLaunchFiles() > 0)
			{
				throw new RuntimeException(
					"Input files are not allowed when running in child mode");
			}
		}

		// Various core option requirements
		if (hasCore())
		{
			// Cannot specify files when using the --core option
			// Note: that this constraint allows the remaining code to function exactly
			// the same when running with the --core option and without because it
			// is guaranteed that there will be no launch files to parse thus it
			// will only have core nodes to run
			if (getNumLaunchFiles() > 0) {
				throw new RuntimeException("Input files are not allowed when launching core");
			}

			// Run id should not be used when using the core option
			if (hasRunId()) {
				throw new RuntimeException(
					"--run-id should only be set when using the --child option");
			}
		}

		// If there are no launch files, make sure there aren't any options
		// that allow there to be no launch files
		if (getNumLaunchFiles() == 0)
		{
			// The following options accept no launch files:
			//     --core
			//     --child
			//     --server_uri
			//     - (read file from stdin)
			if (!hasCore() && !hasChild() && !hasServerUri() && !hasReadStdin()) {
				throw new RuntimeException("you must specify at least on input file");
			}
		}

		// Find node must have a valid node name
		if (hasFindNode() && getFindNode().length() == 0) {
			throw new RuntimeException("the --find-node option must include a node name");
		}

		// Node args option must have a valid node name
		if (hasNodeArgs() && getNodeArgs().length() == 0) {
			throw new RuntimeException("the --args option must include a node name");
		}

		// TODO: finish supporting child mode
		if (hasChild()) {
			throw new RuntimeException(
				"the --child option is not yet supported!");
		}
	}

	/**
	 * Determine if the command line arguments are such that the user has
	 * requested some information to be printed to the screen.
	 *
	 * @return true if information is being requested, false otherwise
	 */
	public boolean hasInfoRequest()
	{
		return (hasFiles() || hasNodes() || hasDumpParams() || hasFindNode() ||
				hasRosArgs() || hasNodeArgs());
	}

	/**
	 * Get the number of launch files specified on the command line.
	 *
	 * @return the number of launch files specified on the command line
	 */
	public int getNumLaunchFiles()
	{
		return m_launchFiles.size();
	}

	/**
	 * Get the launch files specified on the command line.
	 *
	 * @return the List of launch files
	 */
	public List<String> getLaunchFiles()
	{
		return m_launchFiles;
	}

	/**
	 * Get the command line specified arg name value pairs.
	 *
	 * @return the Map of args
	 */
	public Map<String, String> getArgs()
	{
		return m_args;
	}

	/**
	 * Get the value of the given option.
	 *
	 * @param option the option name
	 * @return the value of the option
	 */
	public String getOption(final String option)
	{
		String cleanOption = getCleanOption(option);

		if (m_options.containsKey(cleanOption)) {
			return m_options.get(cleanOption);
		}
		else {
			return null;
		}
	}

	/**
	 * Determine if the given option was specified on the command line.
	 *
	 * @param option the option name
	 * @return true if specified, false otherwise
	 */
	public boolean hasOption(final String option)
	{
		String cleanOption = getCleanOption(option);

		for (String opt : m_options.keySet())
		{
			if (opt.startsWith(cleanOption)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Determine if the --help option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasHelp()
	{
		return hasOption(HELP_OPTION);
	}

	/**
	 * Determine if the - option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasReadStdin()
	{
		return m_readStdin;
	}

	/**
	 * Determine if the --files option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasFiles()
	{
		return hasOption(FILES_OPTION);
	}

	/**
	 * Determine if the --args option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasNodeArgs()
	{
		return hasOption(ARGS_OPTION);
	}

	/**
	 * Get the value of the --args option, or null if not specified.
	 *
	 * @return the find node value
	 */
	public String getNodeArgs()
	{
		return getOption(ARGS_OPTION);
	}

	/**
	 * Determine if the --nodes option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasNodes()
	{
		return hasOption(NODES_OPTION);
	}

	/**
	 * Determine if the --find-node option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasFindNode()
	{
		return hasOption(FIND_NODE_OPTION);
	}

	/**
	 * Get the value of the --find-node option, or null if not specified.
	 *
	 * @return the find node value
	 */
	public String getFindNode()
	{
		return getOption(FIND_NODE_OPTION);
	}

	/**
	 * Determine if the --child option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasChild()
	{
		return hasOption(CHILD_OPTION);
	}

	/**
	 * Get the value of the --child option, or null if not specified.
	 *
	 * @return the child value
	 */
	public String getChild()
	{
		return getOption(CHILD_OPTION);
	}

	/**
	 * Determine if the --local option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasLocal()
	{
		return hasOption(LOCAL_OPTION);
	}

	/**
	 * Determine if the --screen option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasScreen()
	{
		return hasOption(SCREEN_OPTION);
	}

	/**
	 * Determine if the --server-uri option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasServerUri()
	{
		return hasOption(SERVER_URI_OPTION);
	}

	/**
	 * Get the value of the --server-uri option, or null if not specified.
	 *
	 * @return the run id value
	 */
	public String getServerUri()
	{
		return getOption(SERVER_URI_OPTION);
	}

	/**
	 * Determine if the --run-id option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasRunId()
	{
		return hasOption(RUN_ID_OPTION);
	}

	/**
	 * Get the value of the --run-id option, or null if not specified.
	 *
	 * @return the run id value
	 */
	public String getRunId()
	{
		return getOption(RUN_ID_OPTION);
	}

	/**
	 * Determine if the --wait option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasWait()
	{
		return hasOption(WAIT_OPTION);
	}

	/**
	 * Determine if the --port option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasPort()
	{
		return hasOption(PORT_OPTION);
	}

	/**
	 * Get the value of the --port option, or null if not specified.
	 *
	 * @return the port value
	 */
	public int getPort()
	{
		return m_port;
	}

	/**
	 * Determine if the --core option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasCore()
	{
		return hasOption(CORE_OPTION);
	}

	/**
	 * Determine if the --pid option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasPid()
	{
		return hasOption(PID_OPTION);
	}

	/**
	 * Get the value of the --pid option, or null if not specified.
	 *
	 * @return the pid value
	 */
	public String getPid()
	{
		return getOption(PID_OPTION);
	}

	/**
	 * Determine if the -v option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasVerbose()
	{
		return hasOption(VERBOSE_OPTION);
	}

	/**
	 * Determine if the --dump-params option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasDumpParams()
	{
		return hasOption(DUMP_PARAMS_OPTION);
	}

	/**
	 * Determine if the --skip-log-check option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasSkipLogCheck()
	{
		return hasOption(SKIP_LOG_CHECK_OPTION);
	}

	/**
	 * Determine if the --ros-args option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasRosArgs()
	{
		return hasOption(ROS_ARGS_OPTION);
	}

	/**
	 * Determine if the --disable-title option is specified.
	 *
	 * @return true if specified, false otherwise
	 */
	public boolean hasDisableTitle()
	{
		return hasOption(DISABLE_TITLE_OPTION);
	}

	/**
	 * Get the value of the number of workers option.
	 *
	 * @return the num workers option
	 */
	public int getNumWorkers()
	{
		return m_numWorkers;
	}

	/**
	 * Get the value of the timeout option.
	 *
	 * @return the timeout option
	 */
	public float getTimeout()
	{
		return m_timeout;
	}

	/**
	 * Get the value of the hostname option specified on the command line,
	 * or null if it was not provided.
	 *
	 * @return the value of the hostname option
	 */
	public String getHostname()
	{
		String option = HOSTNAME_SPECIAL_OPTION;
		if (m_specialArgs.containsKey(option)) {
			return m_specialArgs.get(option);
		}
		else {
			return null;
		}
	}

	/**
	 * Get the value of the IP option specified on the command line,
	 * or null if it was not provided.
	 *
	 * @return the value of the IP option
	 */
	public String getIp()
	{
		String option = IP_SPECIAL_OPTION;
		if (m_specialArgs.containsKey(option)) {
			return m_specialArgs.get(option);
		}
		else {
			return null;
		}
	}

	/**
	 * Handle a command line option that starts with a single dash.
	 *
	 * @param option the option (including the dash)
	 */
	private boolean handleSingleDashOption(
			final String option,
			final String potentialValue)
	{
		String cleanOption = getCleanOption(option);

		// Get the alias for this single dash option
		if (m_optionAliases.containsKey(cleanOption))
		{
			String alias = m_optionAliases.get(cleanOption);

			// Determine if this is a flag
			if (m_flags.contains(alias))
			{
				// This flag has no corresponding value
				m_options.put(alias, "");
				return false;  // Do not skip the next argument
			}
			else if (m_optionsWithValues.contains(alias))
			{
				// This argument has a corresponding value
				if (potentialValue == null) {
					throw new RuntimeException(
						"the -" + cleanOption + " option requires a value");
				}

				m_options.put(alias, potentialValue);
				return true;  // Skip the next value
			}
		}

		throw new RuntimeException(
			"no such option: -" + cleanOption);
	}

	/**
	 * Handle a command line option that starts with a double dash.
	 *
	 * @param option the option (including dashes)
	 */
	private void handleDoubleDashOption(final String option)
	{
		String cleanOption = getCleanOption(option);

		String[] items = cleanOption.split("=");
		if (items.length == 2)
		{
			String optionName = items[0];
			String optionValue = items[1];

			// This argument has a value specified with it
			if (m_flags.contains(optionName))
			{
				throw new RuntimeException(
						"the --" + optionName + " option does not take a value");
			}
			else if (!m_optionsWithValues.contains(optionName)) {
				throw new RuntimeException("no such option: --" + optionName);
			}

			m_options.put(optionName, optionValue);
		}
		else
		{
			// This argument does not have a value specified with it
			if (m_optionsWithValues.contains(cleanOption))
			{
				throw new RuntimeException(
					"the --" + cleanOption + " option requires a value");
			}
			else if (!m_flags.contains(cleanOption)) {
				throw new RuntimeException("no such option: --" + cleanOption);
			}
			m_options.put(cleanOption, "");
		}
	}

	/**
	 * Handle arg key value pair specified on the command line.
	 *
	 * @param arg the arg key value pair
	 * @param argMap the Map of defined args
	 */
	private void handleArgPair(final String arg, Map<String, String> argMap)
	{
		String[] parts = arg.split(":=");
		if (parts.length == 2)
		{
			// The argument has a defined value
			argMap.put(parts[0], parts[1]);
		}
		else if (parts.length == 1)
		{
			// The argument has an empty value
			argMap.put(parts[0], "");
		}
		else {
			throw new RuntimeException("Invalid remapping argument '" + arg + "'");
		}
	}

	/**
	 * Return the cleaned name of the given option which removes
	 * all leading dashes.
	 *
	 * @param option the option
	 * @return the cleaned option name
	 */
	private String getCleanOption(final String option)
	{
		// Find the first index that is not a dash
		int startIndex = 0;
		while (option.charAt(startIndex) == '-') {
			startIndex++;
		}

		return option.substring(startIndex);
	}

	/**
	 * Grab actual values for some specific options.
	 */
	private void getOptionValues()
	{
		// Handle the port option
		m_port = -1;  // Not specified
		if (hasOption(PORT_OPTION))
		{
			String portStr = getOption(PORT_OPTION);
			try {
				m_port = Integer.parseInt(portStr);
			}
			catch (Exception e) {
				throw new RuntimeException(
					"invalid integer --" + PORT_OPTION + " value: " + portStr);
			}
		}

		// Handle the num workers option
		m_numWorkers = -1;  // Not specified
		if (hasOption(NUM_WORKERS_OPTION))
		{
			String workersStr = getOption(NUM_WORKERS_OPTION);
			try {
				m_numWorkers = Integer.parseInt(workersStr);
			}
			catch (Exception e) {
				throw new RuntimeException(
					"invalid integer --" + NUM_WORKERS_OPTION + "value: " + workersStr);
			}
		}

		// Handle the timeout option
		m_timeout = -1;  // Not specified
		if (hasOption(TIMEOUT_OPTION))
		{
			String timeoutStr = getOption(TIMEOUT_OPTION);
			try {
				m_timeout = Float.parseFloat(timeoutStr);
			}
			catch (Exception e) {
				throw new RuntimeException(
					"invalid float --" + TIMEOUT_OPTION + "value: " + timeoutStr);
			}
		}
	}
}
