package org.ros.rosjava.roslaunch.launching;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.ros.rosjava.roslaunch.ArgumentParser;
import org.ros.rosjava.roslaunch.logging.FileLog;
import org.ros.rosjava.roslaunch.logging.FileLog.FileLogger;
import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.parsing.GroupTag;
import org.ros.rosjava.roslaunch.parsing.IncludeTag;
import org.ros.rosjava.roslaunch.parsing.LaunchFile;
import org.ros.rosjava.roslaunch.parsing.MachineTag;
import org.ros.rosjava.roslaunch.parsing.NodeTag;
import org.ros.rosjava.roslaunch.parsing.ParamTag;
import org.ros.rosjava.roslaunch.parsing.RosParamTag;
import org.ros.rosjava.roslaunch.util.EnvVar;
import org.ros.rosjava.roslaunch.util.RosUtil;

/**
 * The LaunchConfig class
 *
 * This class is responsible for performing all of the logic
 * of launching nodes contained within a launch file tree. This
 * includes optionally launching a ROS master if one is not
 * already running.
 */
public class LaunchConfig
{
	/** The run id for this process. */
	private String m_runId;
	/** The parsed command line arguments. */
	private ArgumentParser m_parsedArgs;
	/** The MachineManager object. */
	private MachineManager m_machineManager;

	/** The URI to reach the ROS master server. */
	private String m_uri;

	/** The core roslaunch LaunchFile. */
	private LaunchFile m_rosCoreLaunch;
	/** The List of non-core LaunchFiles. */
	private List<LaunchFile> m_launchFiles;
	/** The List of all core and non-core LaunchFiles. */
	private List<LaunchFile> m_allLaunchFiles;

	/** The List of all core ROS nodes. */
	private List<NodeTag> m_coreNodes;
	/** The List of all non-core ROS nodes. */
	private List<NodeTag> m_nodes;
	/** The List of all non-core local ROS nodes. */
	private List<NodeTag> m_localNodes;
	/** The List of all non-core remote ROS nodes. */
	private List<NodeTag> m_remoteNodes;

	/** The List of all ParamTags defined in the launch file tree. */
	private List<ParamTag> m_params;
	/** The List of all RosParamTags defined in the launch file tree. */
	private List<RosParamTag> m_rosParams;
	/** The Map of rosparam name value pairs for 'load' rosparams. */
	private Map<String, String> m_loadRosParamsMap;

	/** The List of namespaces to clear based on clear params settings. */
	private List<String> m_clearParams;
	/** The List of unified namespaces to clear based on clear params settings. */
	private List<String> m_unifiedClearParams;

	/** FileLogger for this class. */
	private FileLogger m_logger;

	/**
	 * Constructor
	 *
	 * Create a LaunchConfig object.
	 *
	 * @param parsedArgs the parsed command line arguments
	 * @param launchFiles the List of non-core LaunchFiles
	 */
	public LaunchConfig(
			final String runId,
			final ArgumentParser parsedArgs,
			final List<LaunchFile> launchFiles)
	{
		m_runId = runId;
		m_parsedArgs = parsedArgs;
		m_launchFiles = launchFiles;

		m_logger = FileLog.getLogger("roslaunch.config");

		// Check for the ROS master URI in the environment
		m_uri = EnvVar.ROS_MASTER_URI.getReqNonEmpty();

		// Create the machine manager object
		m_machineManager = new MachineManager();

		// Parse the rosmaster.xml config file
		parseRosMasterConfig();

		// Create the list of core and non-core launch files
		m_allLaunchFiles = new ArrayList<LaunchFile>();
		m_allLaunchFiles.add(m_rosCoreLaunch);
		m_allLaunchFiles.addAll(m_launchFiles);

		// Grab the list of nodes defined in the core launch file
		m_coreNodes = NodeManager.getNodes(m_rosCoreLaunch);

		// Get the list of nodes contained in the launch tree
		m_nodes = NodeManager.getNodes(m_launchFiles);

		// Get the list of params contained in the launch tree
		// including the core nodes
		m_params = ParamManager.getParams(m_allLaunchFiles);

		// Get the list of rosparams contained in the launch tree
		// including the core nodes
		m_rosParams = RosParamManager.getRosParams(m_allLaunchFiles);

		// Get the map from param name to value for all of the rosparams
		// that will be set based on the launch tree
		m_loadRosParamsMap = RosParamManager.getLoadRosParamsMap(m_allLaunchFiles);

		// Get the List of namespaces to clear based on used clear params settings
		m_clearParams = ClearParamsManager.getClearParams(m_allLaunchFiles);

		// Reduce the total list of namespaces to clear to only the shortest ancestor
		// namespaces for all (e.g., if /foo/bar/bang/, /foo/bar/, and /foo/ are
		// all being cleared, then reduce the list to just /foo which encompasses
		// of all of the others as well).
		m_unifiedClearParams = ClearParamsManager.unifyClearParams(m_clearParams);

		// Assign machines to all nodes
		assignMachines();

		// Keep a list of nodes that are local to this machine
		m_localNodes = getLocalNodes();

		// Keep a list of nodes that correspond to remote machines
		m_remoteNodes = getRemoteNodes();
	}

	/**
	 * Get the URI to reach the ROS master server.
	 *
	 * @return the URI
	 */
	public String getUri()
	{
		return m_uri;
	}

	/**
	 * Set the URI to reach the ROS master server.
	 *
	 * @param uri the URI
	 */
	public void setUri(final String uri)
	{
		m_uri = uri;
	}

	/**
	 * Print the summary of nodes and parameters to the screen.
	 */
	public void printSummary()
	{
		PrintLog.info("SUMMARY");
		PrintLog.info("========");
		PrintLog.info("");

		//// Print clear params
		ClearParamsManager.printClearParams(m_clearParams);

		//// Print parameters
		//
		printAllParams();

		//// Print nodes
		//
		PrintLog.info("NODES");

		// Do not include the core nodes in the print of nodes started
		// Note: do not print non-local nodes
		NodeManager.printNodes(m_localNodes);
		PrintLog.info("");

		//// Print deprecated launch files
		for (LaunchFile launch : m_allLaunchFiles) {
			printDeprecatedLaunchFile(launch);
		}
	}

	/**
	 * Print all parameters (rosparams and params) to the screen.
	 */
	public void printAllParams()
	{
		// Create a single map that contains all rosparams and params
		Map<String, String> paramMap = new HashMap<String, String>();
		paramMap.putAll(m_loadRosParamsMap);
		ParamManager.dumpParameters(m_params, paramMap);

		PrintLog.info("PARAMETERS");

		// Iterate over the parameters in sorted order so they are printed
		// together with the rest of the params for their namespace
		SortedSet<String> paramNames = new TreeSet<String>(paramMap.keySet());
		for (String name : paramNames)
		{
			String value = paramMap.get(name);

			// Only display the first 20 characters, if the param
			// value is very long
			if (value.length() > 20) {
				value = value.substring(0, 20) + "...";
			}

			// Remove carriage returns and new lines for display purposes
			value = value.replace("\r", "").replace("\n", "");

			PrintLog.info(" * " + name + ": " + value);
		}

		PrintLog.info("");
	}

	/**
	 * Set all of the RosParamTags and ParamTags defined
	 * in the launch file tree.
	 *
	 * @throws Exception if one of the params failed to be set
	 */
	public void setParameters() throws Exception
	{
		// Create a list of all of the launch files, including
		// the roslaunch core launch file
		List<LaunchFile> launchFiles = new ArrayList<LaunchFile>();
		launchFiles.add(m_rosCoreLaunch);
		launchFiles.addAll(m_launchFiles);

		// Execute all delete rosparams first
		RosParamManager.deleteParameters(m_rosParams, m_uri);

		// Execute all dump rosparams next
		RosParamManager.dumpParameters(m_rosParams, m_uri);

		// Clear all parameters -- use the unified set of parameters to
		// prevent errors from occurring
		ClearParamsManager.clearParams(m_unifiedClearParams, m_uri);

		// Set all of the rosparams
		// NOTE: use the actual RosParamTag objects here so that it
		//       has access to the associated Object for the param
		//       content rather than just the string representation.
		//       Otherwise, it will set param types incorrectly
		RosParamManager.setParameters(m_rosParams, m_uri);

		// Set all of the standard params
		ParamManager.setParameters(m_params, m_uri);
	}

	/**
	 * Launch all of the core nodes.
	 *
	 * @return the List of corresponding RosProcesses launched
	 */
	public List<RosProcessIF> launchCoreNodes()
	{
		return NodeManager.launchNodes(
				m_parsedArgs,
				m_coreNodes,
				m_runId,
				m_uri,
				true);  // core nodes
	}

	/**
	 * Launch all of the non-core nodes corresponding to remote machines.
	 *
	 * @return the List of corresponding RosProcesses launched
	 */
	public List<RosProcessIF> launchRemoteNodes()
	{
		List<RosProcessIF> processes = new ArrayList<RosProcessIF>();

		if (!m_parsedArgs.hasLocal())
		{
			// Build a map of unique machines we are going to launch on
			Map<String, MachineTag> machines = new HashMap<String, MachineTag>();
			for (NodeTag remoteNode : m_remoteNodes)
			{
				MachineTag machine = remoteNode.getMachine();
				machines.put(machine.getName(), machine);
			}

			// Launch remote machines
			int machineIndex = 0;
			for (String name : machines.keySet())
			{
				MachineTag machine = machines.get(name);

				// Create a name for the machine
				String machineName = machine.getAddress() + "-" + machineIndex;

				String msg = "remote[" + machineName + "] starting roslaunch";
				m_logger.info(msg);
				PrintLog.info(msg);

				// Create the remote process
				RosRemoteProcess remoteProc = new RosRemoteProcess(
						machineName, m_runId, m_uri, machine);

				// Start the process
				boolean success = remoteProc.start();
				if (!success) {
					throw new RuntimeException(
						"unable to start remote roslaunch child: " + machineName);
				}

				machineIndex += 1;
				processes.add(remoteProc);
			}
		}
		else
		{
			PrintLog.bold("LOCAL");
			PrintLog.bold("local only launch specified, will not launch remote nodes");
			PrintLog.bold("LOCAL");
		}

        return processes;
	}

	/**
	 * Launch all of the non-core nodes corresponding to the local machine.
	 *
	 * @return the List of corresponding RosProcesses launched
	 */
	public List<RosProcessIF> launchLocalNodes()
	{
		// Only launch local nodes
		return NodeManager.launchNodes(
				m_parsedArgs,
				m_localNodes,
				m_runId,
				m_uri,
				false);  // non-core nodes
	}

	/**
	 * Determine if the ROS master server is running.
	 *
	 * @return true if it is running, false otherwise
	 */
	public boolean isMasterRunning()
	{
		return RosUtil.isMasterRunning(m_uri);
	}

	/**
	 * Assign machines to all of the nodes defined in the launch tree.
	 */
	private void assignMachines()
	{
		// Add all machines defined in the launch tree to
		// the machine manager
		m_machineManager.addMachines(m_rosCoreLaunch);  // Just in case
		m_machineManager.addMachines(m_launchFiles);

		// Now, consolidate all duplicate machine configs to
		// reduce the number of different machines we have
		// to deal with
		m_machineManager.consolidateMachines();

		// Assign machines to all of the core nodes
		m_machineManager.assignMachinesToNodes(m_coreNodes, true);

		// Assign machines to all non-core nodes
		m_machineManager.assignMachinesToNodes(m_nodes, false);
	}

	/**
	 * Get all of the nodes corresponding to the local machine.
	 *
	 * @return the List of local NodeTags
	 */
	private List<NodeTag> getLocalNodes()
	{
		List<NodeTag> localNodes = new ArrayList<NodeTag>();

		for (NodeTag node : m_nodes)
		{
			MachineTag machine = node.getMachine();
			if (machine == null || m_machineManager.isLocal(machine)) {
				localNodes.add(node);
			}
		}

		return localNodes;
	}

	/**
	 * Get all of the nodes corresponding to remote machines.
	 *
	 * @return the List of remote NodeTags
	 */
	private List<NodeTag> getRemoteNodes()
	{
		List<NodeTag> remoteNodes = new ArrayList<NodeTag>();

		for (NodeTag node : m_nodes)
		{
			MachineTag machine = node.getMachine();
			if (machine != null && !m_machineManager.isLocal(machine)) {
				remoteNodes.add(node);
			}
		}

		return remoteNodes;
	}

	/**
	 * Parse the roslaunch master config LaunchFile.
	 */
	private void parseRosMasterConfig()
	{
		// Find the roscore.xml file
		String rosCoreXml = this.getRoscoreConfigFile();
		if (rosCoreXml == null) {
			throw new RuntimeException("Failed to find roscore.xml!");
		}

		// We can assume this file exists, because the getRoscoreConfigFile
		// checks that for us
		File file = new File(rosCoreXml);

		FileLog.info("roslaunch", "loading roscore config file " + rosCoreXml);

		// It's just a launch file, so load it
		m_rosCoreLaunch = new LaunchFile(file);
		m_rosCoreLaunch.parseFile();
	}

	/**
	 * Get the path to the roslaunch master config file.
	 *
	 * @return the path to the roslaunch master config file
	 */
	private String getRoscoreConfigFile()
	{
		// Check for the file at /etc/ros/roscore.xml
		File fd = new File("/etc/ros/roscore.xml");
		if (fd.exists() && fd.isFile()) {
			return fd.getAbsolutePath();
		}

		// Otherwise, check in ROS_ROOT
		// Look up the path to the node executable
		String rosRoot = EnvVar.ROS_ROOT.getReqNonEmpty();

		File root = new File(rosRoot, "../roslaunch/resources/roscore.xml");
		if (root.exists() && root.isFile()) {
			return root.getAbsolutePath();
		}

		return null;  // No file found
	}

	private void printDeprecatedLaunchFile(final LaunchFile launchFile)
	{
		if (launchFile.isEnabled())
		{
			String deprecated = launchFile.getDeprecated();
			if (deprecated != null && deprecated.length() > 0)
			{
				PrintLog.error(
					"WARNING: [" + launchFile.getFilename() + "] DEPRECATED: " + deprecated);
			}
		}

		// Check all groups
		for (GroupTag group : launchFile.getGroups())
		{
			if (group.isEnabled()) {
				printDeprecatedLaunchFile(group.getLaunchFile());
			}
		}

		// Check all includes
		for (IncludeTag include : launchFile.getIncludes())
		{
			if (include.isEnabled()) {
				printDeprecatedLaunchFile(include.getLaunchFile());
			}
		}
	}
}
