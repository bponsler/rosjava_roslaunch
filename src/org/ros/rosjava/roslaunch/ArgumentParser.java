package org.ros.rosjava.roslaunch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: does not support the - option to read from stdin

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
		
	private int m_port;
	private int m_numWorkers;
	private float m_timeout;
		
	List<String> m_flags;
	List<String> m_argsWithValues;
	
	List<String> m_launchFiles;
	Map<String, String> m_options;
	Map<String, String> m_args;
	Map<String, String> m_specialArgs;
	
	Map<String, String> m_optionAliases;
	
	List<List<String>> m_invalidPairedOptions;
	
	@SuppressWarnings("serial")
	public ArgumentParser(final String[] inputArgs)
	{
		m_launchFiles = new ArrayList<String>();
		m_options = new HashMap<String, String>();
		m_args = new HashMap<String, String>();
		m_specialArgs = new HashMap<String, String>();
		
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
		m_argsWithValues = new ArrayList<String>();
		m_argsWithValues.add(ARGS_OPTION);
		m_argsWithValues.add(CHILD_OPTION);
		m_argsWithValues.add(FIND_NODE_OPTION);
		m_argsWithValues.add(NUM_WORKERS_OPTION);
		m_argsWithValues.add(PID_OPTION);
		m_argsWithValues.add(PORT_OPTION);
		m_argsWithValues.add(RUN_ID_OPTION);
		m_argsWithValues.add(SERVER_URI_OPTION);
		m_argsWithValues.add(TIMEOUT_OPTION);
		
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
			if (!hasCore() && !hasChild() && !hasServerUri()) {
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
	
	public int getNumLaunchFiles()
	{
		return m_launchFiles.size();
	}
	
	public List<String> getLaunchFiles()
	{
		return m_launchFiles;
	}
	
	public Map<String, String> getArgs()
	{
		return m_args;
	}
	
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
	
	public boolean hasHelp()
	{
		return hasOption(HELP_OPTION);
	}
	
	public boolean hasFiles()
	{
		return hasOption(FILES_OPTION);
	}
	
	public boolean hasNodeArgs()
	{
		return hasOption(ARGS_OPTION);
	}
	
	public String getNodeArgs()
	{
		return getOption(ARGS_OPTION);
	}
	
	public boolean hasNodes()
	{
		return hasOption(NODES_OPTION);
	}
	
	public boolean hasFindNode()
	{
		return hasOption(FIND_NODE_OPTION);
	}
	
	public String getFindNode()
	{
		return getOption(FIND_NODE_OPTION);
	}
	
	public boolean hasChild()
	{
		return hasOption(CHILD_OPTION);
	}
	
	public String getChild()
	{
		return getOption(CHILD_OPTION);
	}
	
	public boolean hasLocal()
	{
		return hasOption(LOCAL_OPTION);
	}
	
	public boolean hasScreen()
	{
		return hasOption(SCREEN_OPTION);
	}
	
	public boolean hasServerUri()
	{
		return hasOption(SERVER_URI_OPTION);
	}
	
	public String getServerUri()
	{
		return getOption(SERVER_URI_OPTION);
	}
	
	public boolean hasRunId()
	{
		return hasOption(RUN_ID_OPTION);
	}
	
	public String getRunId()
	{
		return getOption(RUN_ID_OPTION);
	}
	
	public boolean hasWait()
	{
		return hasOption(WAIT_OPTION);
	}
	
	public boolean hasPort()
	{
		return hasOption(PORT_OPTION);
	}
	
	public int getPort()
	{
		return m_port;
	}
	
	public boolean hasCore()
	{
		return hasOption(CORE_OPTION);
	}
	
	public boolean hasPid()
	{
		return hasOption(PID_OPTION);
	}
	
	public String getPid()
	{
		return getOption(PID_OPTION);
	}
	
	public boolean hasVerbose()
	{
		return hasOption(VERBOSE_OPTION);
	}
	
	public boolean hasDumpParams()
	{
		return hasOption(DUMP_PARAMS_OPTION);
	}
	
	public boolean hasSkipLogCheck()
	{
		return hasOption(SKIP_LOG_CHECK_OPTION);
	}
	
	public boolean hasRosArgs()
	{
		return hasOption(ROS_ARGS_OPTION);
	}
	
	public boolean hasDisableTitle()
	{
		return hasOption(DISABLE_TITLE_OPTION);
	}
	
	public int getNumWorkers()
	{
		return m_numWorkers;
	}
	
	public float getTimeout()
	{
		return m_timeout;
	}
	
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
			else if (m_argsWithValues.contains(alias))
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
			else if (!m_argsWithValues.contains(optionName)) {
				throw new RuntimeException("no such option: --" + optionName);
			}
			
			m_options.put(optionName, optionValue);
		}
		else
		{
			// This argument does not have a value specified with it
			if (m_argsWithValues.contains(cleanOption))
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
	
	private String getCleanOption(final String option)
	{
		// Find the first index that is not a dash
		int startIndex = 0;
		while (option.charAt(startIndex) == '-') {
			startIndex++;
		}
		
		return option.substring(startIndex);
	}
	
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
