package org.ros.rosjava.roslaunch.launching;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ros.rosjava.roslaunch.ArgumentParser;
import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.parsing.GroupTag;
import org.ros.rosjava.roslaunch.parsing.IncludeTag;
import org.ros.rosjava.roslaunch.parsing.LaunchFile;
import org.ros.rosjava.roslaunch.parsing.NodeTag;
import org.ros.rosjava.roslaunch.util.EnvVar;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.util.Util;

/**
 * The NodeManager class
 *
 * This class is responsible for dealing with NodeTags
 * defined within a launch file tree.
 */
public class NodeManager
{
	/**
	 * Get the List of NodeTags defined in the tree defined by
	 * the given List of LaunchFiles.
	 *
	 * @param launchFiles the List of LaunchFiles
	 * @return the List of NodeTags defined in the launch files
	 */
	public static List<NodeTag> getNodes(final List<LaunchFile> launchFiles)
	{
		List<NodeTag> nodes = new ArrayList<NodeTag>();

		for (LaunchFile launchFile : launchFiles)
		{
			if (launchFile.isEnabled())
			{
				List<NodeTag> launchNodes = getNodes(launchFile);
				nodes.addAll(launchNodes);
			}
		}

		return nodes;
	}

	/**
	 * Get the List of enabled NodeTags defined in the given LaunchFile.
	 *
	 * @param launchFile the LaunchFile
	 * @return the List of NodeTags defined in the LaunchFile
	 */
	public static List<NodeTag> getNodes(final LaunchFile launchFile)
	{
		List<NodeTag> nodes = new ArrayList<NodeTag>();

		// Stop if this lauch file is disabled
		if (!launchFile.isEnabled()) return nodes;

		// Add all nodes
		for (NodeTag node : launchFile.getNodes())
		{
			if (node.isEnabled()) {
				nodes.add(node);
			}
		}

		// Add nodes from all groups
		for (GroupTag group : launchFile.getGroups())
		{
			if (group.isEnabled())
			{
				List<NodeTag> groupNodes = getNodes(group.getLaunchFile());
				nodes.addAll(groupNodes);
			}
		}

		// Add nodes from all includes
		for (IncludeTag include : launchFile.getIncludes())
		{
			if (include.isEnabled())
			{
				List<NodeTag> includeNodes = getNodes(include.getLaunchFile());
				nodes.addAll(includeNodes);
			}
		}

		return nodes;
	}

	/**
	 * Print each of the given NodeTags to the screen.
	 *
	 * @param nodes the List of NodeTags
	 */
	public static void printNodes(final List<NodeTag> nodes)
	{
		// Create a map from namespace to the list of nodes contained
		// within that namespace
		Map<String, List<NodeTag> > namespaceMap = getNodeNamespaceMap(nodes);

		// Print the map of namespaces to list of nodes in the namespace
		printNamespaceMap(namespaceMap);
	}

	/**
	 * Get a Map from namespace to the List of NodeTags that are
	 * defined for that namespace.
	 *
	 * @param nodes the List of NodeTags
	 * @return the NodeTag namespace Map
	 */
	private static Map<String, List<NodeTag> > getNodeNamespaceMap(
			final List<NodeTag> nodes)
	{
		Map<String, List<NodeTag> > namespaceMap = new HashMap<String, List<NodeTag> >();

		for (NodeTag node : nodes)
		{
			if (node.isEnabled())
			{
				String namespace = node.getNamespace();

				// Convert relative namespaces to global
				if (!namespace.startsWith("/")) {
					namespace = "/" + namespace;
				}

				// Grab the current list of nodes for this namespace, and
				// handle the case where there aren't any yet
				List<NodeTag> namespaceNodes = namespaceMap.get(namespace);
				if (namespaceNodes == null) {
					namespaceNodes = new ArrayList<NodeTag>();
				}

				// Add the node to the namespace, and store the
				// new list in the map
				namespaceNodes.add(node);
				namespaceMap.put(namespace, namespaceNodes);
			}
		}

		return namespaceMap;
	}

	/**
	 * Print each of the NodeTags defined for a each known namespace
	 * to the screen.
	 *
	 * Note this assumes all nodes in the map are enabled.
	 *
	 * @param namespaceMap the NodeTag namespace Map
	 */
	private static void printNamespaceMap(final Map<String, List<NodeTag> > namespaceMap)
	{
		for (String namespace : namespaceMap.keySet())
		{
			List<NodeTag> nodes = namespaceMap.get(namespace);

			PrintLog.info("  " + namespace);

			for (NodeTag node: nodes)
			{
				// Label the node name, package, and type
				String label =
					node.getName() + " (" + node.getPackage() +	"/" + node.getType() + ")";

				PrintLog.info("    " + label);
			}
		}
	}

	/**
	 * Launch all of the given NodeTags.
	 *
	 * @param parsedArgs the parsed command line arguments
	 * @param nodes the List of NodeTags to launch
	 * @param uuid is the UUID for the launch process
	 * @param masterUri the URI to reach the ROS master server
	 * @param isCore true if these are core nodes
	 * @return the List of RosProcesses that were launched
	 */
	public static List<RosProcessIF> launchNodes(
			final ArgumentParser parsedArgs,
			final List<NodeTag> nodes,
			final String uuid,
			final String masterUri,
			final boolean isCore)
	{
		List<RosProcessIF> processes = new ArrayList<RosProcessIF>();

		// Launch all of the nodes contained in the list
		for (NodeTag node : nodes)
		{
			if (node.isEnabled())
			{
				RosLocalProcess rosProc = launchNode(
						parsedArgs, node, uuid, isCore, masterUri);
				if (rosProc != null) {
					processes.add(rosProc);
				}
			}
		}

		return processes;
	}

	/**
	 * Launch a single nodeTag.
	 *
	 * @param parsedArgs the parsed command line arguments
	 * @param node the NodeTag to launch
	 * @param uuid is the UUID for the launch process
	 * @param isCore true if this is a core node
	 * @param masterUri the URI to read the ROS master server
	 * @return the RosProcess that was launched
	 */
	private static RosLocalProcess launchNode(
			final ArgumentParser parsedArgs,
			final NodeTag node,
			final String uuid,
			final boolean isCore,
			final String masterUri)
	{
		RosLocalProcess rosProc = null;
		try {
			rosProc = createNodeProcess(
				parsedArgs, node, uuid, isCore, masterUri);
		}
		catch (IOException e)
		{
			PrintLog.error(
				"Failed to start node: " + node.getResolvedName() + ": " + e.getMessage());
			return null;
		}

		// Set the node to respawn if it is configured to do so
		if (node.shouldRespawn()) {
			rosProc.setRespawn(node.getRespawnDelay());
		}

		rosProc.printStartMessage();

		return rosProc;
	}

	/**
	 * Get the array of command line arguments to pass to a
	 * node executable
	 *
	 * @param node the NodeTag
	 * @param addNamespace true if the namespace should be added as
	 *        a prefix before the node executable or not
	 * @param logFile is the log file to add to the arguments, or null if it's ignored
	 * @return the array of command line arguments
	 */
	public static String[] getNodeCommandLine(
			final NodeTag node,
			final boolean addNamespace,
			final String logFile)
	{
		String name = node.getName();

		// Look up the path to the node executable
		File executable = node.getExecutable();

		// Create the list of arguments passed to the launch the node
		List<String> fullCommand = new ArrayList<String>();

		if (addNamespace)
		{
			// Get the environment variables for this node
			// Note: the master URI arg does not matter because we will
			// not be using the variable -- we're just going to get
			// the namespace variable and add it to the front of
			// the command
			String[] envp = getNodeEnvironment(node, "does/not/matter");
			for (String var : envp)
			{
				if (var.startsWith(EnvVar.ROS_NAMESPACE.name())) {
					fullCommand.add(var);
				}
			}
		}

		// If the node has a launch prefix add it here
		String launchPrefix = node.getLaunchPrefix();
		if (launchPrefix != null && launchPrefix.length() > 0)
		{
			// Add each arg as a separate entry otherwise it will fail
			for (String arg : launchPrefix.split(" ")) {
				fullCommand.add(arg);
			}
		}

		// Path to the node executable
		fullCommand.add(executable.getAbsolutePath());

		// Add all topic remappings to the command
		Map<String, String> remappings = node.getRemappings();
		for (String source : remappings.keySet())
		{
			String dest = remappings.get(source);
			fullCommand.add(source + ":=" + dest);
		}

		// Add the name of the node
		fullCommand.add("__name:=" + name);

		// Add all node args to the command
		for (String arg : node.getArgs()) {
			fullCommand.add(arg);
		}

		// Pass the log file to the node process if one is given,
		// and the node is logging to a file
		if (logFile != null && node.isLogOutput()) {
			fullCommand.add("__log:=" + logFile);
		}

		// Create the array to launch the command
		String[] command = new String[fullCommand.size()];
		fullCommand.toArray(command);

		return command;
	}

	/**
	 * Create and run the executable for a node.
	 *
	 * @param parsedArgs the parsed command line arguments
	 * @param node the NodeTag to run
	 * @param uuid is the UUID for the launch process
	 * @param isCore true if this is a core process, false otherwise
	 * @param masterUri the URI to reach the ROS master server
	 * @return the corresponding RosProcess
	 * @throws IOException
	 */
	private static RosLocalProcess createNodeProcess(
			final ArgumentParser parsedArgs,
			final NodeTag node,
			final String uuid,
			final boolean isCore,
			final String masterUri) throws IOException
	{

		// Add a counter to the name to avoid collisions between names
		String processName = ProcessMonitor.getProcessName(node.getResolvedName());

		// Grab the path to the log file for this process
		String logFile = RosUtil.getProcessLogFile(processName, uuid);

		// Get the command line call to execute the node
		String[] command = getNodeCommandLine(node, false, logFile);

		// Get the environment variables for this node
		String[] envp = getNodeEnvironment(node, masterUri);

		// Handle the cwd attribute for the node
		File workingDir = node.getCwd();

		// If the screen argument is active, then the output of all
		// nodes is logged to the screen
		// Also print streams for all core nodes
		boolean logStreams = node.isLogOutput();
		if (parsedArgs.hasScreen() || isCore) {
			logStreams = false;
		}

		// Launch the node with its environment and the working directory
		Process proc = launchNodeProcess(
				processName, uuid, command, envp, workingDir, logStreams);

		RosLocalProcess rosProc = new RosLocalProcess(
				processName,
				proc,
				command,
				uuid,
				isCore,
				node.isRequired(),
				logStreams);

		// Set the environment and working directory that
		// were used to run the process
		rosProc.setEnvironment(envp);
		rosProc.setWorkingDir(workingDir);

		return rosProc;
	}

	/**
	 * Launch a node process.
	 *
	 * @param processName the name of the process
	 * @param uuid the UUID for the process
	 * @param command the command to execute
	 * @param envp the environment variables
	 * @param workingDir the working directory
	 * @param isLogOutput true if the process should log its output
	 * @return the started Process
	 * @throws IOException if starting the Process fails
	 */
	public static Process launchNodeProcess(
			final String processName,
			final String uuid,
			final String[] command,
			final String[] envp,
			final File workingDir,
			final boolean isLogOutput) throws IOException
	{
		// Linux configures processes to flush stdout on a per line basis
		// whenever the process is connected to a terminal due to the
		// assumption that the user wants to see immediate output. When
		// the process is connected to a file (e.g., command > /tmp/file.txt)
		// stdout is configured to be buffered with a 4-kiB buffer. In order
		// to receive output from a Process Java uses files which causes the
		// stdout to be buffered (note that stderr is always flushed on a
		// per line basis). This means that the output of any process that
		// writes to stdout will not be received immediately unless the
		// process explicity:
		//     - flushes stdout
		//     - has disabled buffering (e.g., setbuf(stdout, NULL))
		//
		// This is not acceptable for the roslaunch process as output
		// from nodes needs to be visible to the user immediately. The
		// workaround is to use either the 'unbuffer' or the 'stdbuf'
		// programs which both effectively do the same thing and can
		// be used as follows:
		//     - unbuffer my_command cmd_args
		//     - stdbuf -oL my_command cmd_args   (output will be line buffered)
		//     - stdbuf -o0 my_command cmd_args   (output will be unbuffered)
		//
		// These applications can be installed by doing:
		//     sudo apt-get install expect-dev
		//
		// Once the application is passed through one of these options its
		// output to stdout will be received as configured.
		List<String> unbufferedCommand = new ArrayList<String>();
		unbufferedCommand.add("stdbuf");
		unbufferedCommand.add("-oL");
		for (String arg : command) {
			unbufferedCommand.add(arg);
		}

		// Convert the list of command args back to an array
		String[] fullCommand = Util.listToArray(unbufferedCommand);

		ProcessBuilder builder = new ProcessBuilder(fullCommand);

		// Set the working directory for the process
		builder.directory(workingDir);

		// Update the environment for the process
		if (envp != null)
		{
			Map<String, String> bEnv = builder.environment();
			for (String envVar : envp)
			{
				// Each envVar is of the form: VAR=VAL
				int index = envVar.indexOf("=");
				if (index != -1)
				{
					String name = envVar.substring(0, index);
					String value = envVar.substring(index + 1);
					bEnv.put(name, value);
				}
			}
		}

		// Configure the output of the process either to log to a file,
		// or log directly to the screen
		if (isLogOutput)
		{
			// Create names for both stdout and stderr log files
			String logName = RosUtil.getProcessLogFile(processName, uuid);
			String stdoutLogFile = logName.replace(".log", "-stdout.log");
			String stderrLogFile = logName.replace(".log", "-stderr.log");

			builder.redirectOutput(new File(stdoutLogFile));
			builder.redirectError(new File(stderrLogFile));
		}
		else
		{
			// Inherit allows the output of this process to be displayed
			// directly in the terminal for the roslaunch process
			builder.redirectError(Redirect.INHERIT);
			builder.redirectOutput(Redirect.INHERIT);
		}

		return builder.start();
	}

	/**
	 * Get the array of environment variables for a NodeTag. Each
	 * entry in the array is a single environment variable in the
	 * following format:
	 *
	 *     VAR_NAME=VALUE
	 *
	 * @param node the NodeTag
	 * @param masterUri the URI to reach the ROS master server
	 * @return the array of environment variables
	 */
	private static String[] getNodeEnvironment(final NodeTag node, final String masterUri)
	{
		// Create a copy of the environment variables
		Map<String, String> env = new HashMap<String, String>(System.getenv());

		// Set the URI to the maser node
		env.put(EnvVar.ROS_MASTER_URI.name(), masterUri);

		// Remove the ROS namespace if one exists
		if (env.containsKey(EnvVar.ROS_NAMESPACE.name())) {
			env.remove(EnvVar.ROS_NAMESPACE.name());
		}

		// Set the namespace of the node, if one exists
		String namespace = node.getNamespace();
		if (namespace != null && namespace.length() > 0)
		{
			// Remove the ending slash from the namespace, if there is one
			if (namespace.charAt(namespace.length() - 1) == '/') {
				namespace = namespace.substring(0, namespace.length() - 1);
			}

			// Add the namespace to the environment, if there is one
			if (namespace.length() > 0) {
				env.put(EnvVar.ROS_NAMESPACE.name(), namespace);
			}
		}

		// Add all of the node's environment variables to the environment
		Map<String, String> nodeEnv = node.getEnv();
		for (String envName : nodeEnv.keySet()) {
			env.put(envName, nodeEnv.get(envName));
		}

		// Create the array of strings for the environment variables
		String[] envp = new String[env.size()];
		int index = 0;
		for (String key : env.keySet()) {
			envp[index++] = key + "=" + env.get(key);
		}

		return envp;
	}

	/**
	 * Ensure that no two nodes are defined with the same name.
	 *
	 * @param launchFiles is the List of LaunchFiles
	 * @throws RuntimeException if two nodes have the same name
	 */
	public static void checkForDuplicateNodeNames(final List<LaunchFile> launchFiles)
	{
		List<NodeTag> nodes = NodeManager.getNodes(launchFiles);

		// Add each node to the map of nodes, and if it already exists
		// then there are two nodes with duplicate names
		Map<String, String> allNodes = new HashMap<String, String>();
		for (NodeTag node : nodes)
		{
			if (node.isEnabled())
			{
				String name = node.getResolvedName();
				String filename = node.getFilename();

				// If the name is already contained, then we found a duplicate
				if (allNodes.containsKey(name))
				{
					String otherFilename = allNodes.get(name);

					// Generate the error message
					String msg = "roslaunch file contains multiple nodes named [" + name + "].\n";
					msg += "Please check all <node> 'name' attributes to make sure they are unique.\n";
					msg += "Also check that $(anon id) use different ids.\n";

					// Add info about the files containing the nodes
					if (filename.compareTo(otherFilename) != 0) {
						msg += "The nodes were found in [" + filename + "] and [" + otherFilename + "]";
					}
					else {
						msg += "Both nodes were found in [" + filename + "]";
					}

					throw new RuntimeException(msg);
				}
				allNodes.put(name, filename);
			}
		}
	}
}
