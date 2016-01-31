package org.ros.rosjava.roslaunch.launching;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ros.rosjava.roslaunch.logging.FileLog;
import org.ros.rosjava.roslaunch.parsing.GroupTag;
import org.ros.rosjava.roslaunch.parsing.IncludeTag;
import org.ros.rosjava.roslaunch.parsing.LaunchFile;
import org.ros.rosjava.roslaunch.parsing.MachineTag;
import org.ros.rosjava.roslaunch.parsing.NodeTag;
import org.ros.rosjava.roslaunch.util.RosUtil;

/**
 * The MachineManager class
 *
 * This class is responsible for dealing with MachineTags
 * within a launch file tree.
 */
public class MachineManager
{
	/** The name of the local machine. */
	private static String LOCAL_MACHINE = "local";

	/** The Map from machine name to MachineTag objects. */
	private Map<String, MachineTag> m_machines;
	/** The Map from duplicate machine name to singular MachineTag object. */
	private Map<String, MachineTag> m_overrides;

	/** The List of InetAddresses for the local machine. */
	private List<InetAddress> m_localIpAddresses;

	/**
	 * Constructor
	 *
	 * Create a MachineManager object.
	 */
	public MachineManager()
	{
		m_machines = new HashMap<String, MachineTag>();
		m_overrides = new HashMap<String, MachineTag>();

		// Add a machine to correspond to the local machine
		addMachine(new MachineTag(LOCAL_MACHINE, "localhost"));

		// Create a list of local IP addresses
		m_localIpAddresses = RosUtil.getLocalAddresses();
	}

	/**
	 * Add a machine.
	 *
	 * @param machine the MachineTag to add
	 */
	public void addMachine(final MachineTag machine)
	{
		// Only add machines that are enabled
		if (machine.isEnabled()) {
			m_machines.put(machine.getName(), machine);
		}
	}

	/**
	 * Add all the MachineTags defined in the given LaunchFiles.
	 *
	 * @param launchFiles the List of LaunchFiles
	 */
	public void addMachines(final List<LaunchFile> launchFiles)
	{
		for (LaunchFile launchFile : launchFiles)
		{
			if (launchFile.isEnabled()) {
				addMachines(launchFile);
			}
		}
	}

	/**
	 * Add all the MachineTags defined in the given LaunchFile.
	 *
	 * @param launchFile the LaunchFile
	 */
	public void addMachines(final LaunchFile launchFile)
	{
		// Stop if this launch file is disabled
		if (!launchFile.isEnabled()) return;

		// Add all machines specified in the launch file itself
		for (MachineTag machine : launchFile.getMachines()) {
			if (machine.isEnabled()) {
				addMachine(machine);
			}
		}

		// Add all machines defined in any groups
		for (GroupTag group : launchFile.getGroups())
		{
			if (group.isEnabled()) {
				addMachines(group.getLaunchFile());
			}
		}

		// Add all machines defined in any included launch files
		for (IncludeTag include : launchFile.getIncludes())
		{
			if (include.isEnabled()) {
				addMachines(include.getLaunchFile());
			}
		}
	}

	/**
	 * Get a MachineTag by its name.
	 *
	 * @param name the name of the machine
	 * @return the MachineTag, or null if it is not defined
	 */
	public MachineTag getMachine(final String name)
	{
		// If the machine name exists within the overrides map, then
		// it is a duplicate and its replacement machine should be
		// returned instead
		if (m_overrides.containsKey(name)) {
			return m_overrides.get(name);
		}

		// If no overrides are present, check in the main map
		// of machines for the machine
		if (m_machines.containsKey(name)) {
			return m_machines.get(name);
		}
		else {
			return null;
		}
	}

	/**
	 * Get the MachineTag for the local machine.
	 *
	 * @return the local MachineTag
	 */
	public MachineTag getLocalMachine()
	{
		return getMachine(LOCAL_MACHINE);
	}

	/**
	 * Determine if a MachineTag corresponds to a local machine
	 *
	 * @param machine the MachineTag
	 * @return true if it is a local machine, false otherwise
	 */
	public boolean isLocal(final MachineTag machine)
	{
		// If the machine is not specified, then it's a local node
		if (machine == null) return true;

		boolean isLocal = false;  // Assume not local

		// Determine if the IP address for this machine is found
		// in the list of local IP addresses
		InetAddress machineAddress = machine.getInetAddress();
		for (InetAddress localAddress : m_localIpAddresses)
		{
			if (localAddress.equals(machineAddress)) {
				isLocal = true;
				break;
			}
		}

		String username = machine.getUsername();
		if (isLocal && username != null && username.length() > 0)
		{
			// If the assigned username is different than the
			// current local username, then this is not a local node
			String localUsername = System.getProperty("user.name");
			isLocal = (username.compareTo(localUsername) == 0);
		}

		return isLocal;
	}

	/**
	 * Consolidate the known machines to override names of machines
	 * that refer to another machine with an identical configuration.
	 */
	public void consolidateMachines()
	{
		Set<String> machineNamesSet = m_machines.keySet();

		// Convert the set of names to an array to allow us to
		// access elements in the set
		String[] machineNames = new String[machineNamesSet.size()];
		machineNamesSet.toArray(machineNames);

		// Iterate over all machine names
		for (int index1 = 0; index1 < machineNames.length; ++index1)
		{
			// Grab the first name and machine
			String name1 = machineNames[index1];
			MachineTag machine1 = m_machines.get(name1);

			// Skip any that have already been processed
			if (m_overrides.containsKey(name1)) {
				continue;
			}

			// Iterate over all other machines to check for duplicates of
			// the first machine
			for (int index2 = index1; index2 < machineNames.length; ++index2)
			{
				// Grab the second name and machine
				String name2 = machineNames[index2];
				MachineTag machine2 = m_machines.get(name2);

				// Skip any that have already been processed
				if (m_overrides.containsKey(name2)) {
					continue;
				}

				// Check if these machines are duplicates
				if (machine1.equals(machine2))
				{
					// Found a duplicate machine -- add the machine to
					// the map of overrides which will return the
					// first machine tag for uses of the second machine
					// name
					m_overrides.put(name2, machine1);

					FileLog.info("roslaunch",
						"... changing machine assignment from [" + machine2 +
						"] to [" + machine1 + "] as they are equivalent");
				}
			}
		}
	}

	/**
	 * Assign a machine to every node in the List of NodeTags. Core
	 * nodes are all defined to be on the local machine.
	 *
	 * @param nodes the List of NodesTags
	 * @param areCoreNodes true if they are core nodes, false otherwise
	 */
	public void assignMachinesToNodes(
			final List<NodeTag> nodes,
			final boolean areCoreNodes)
	{
		// Assign machines to all configured nodes
		for (NodeTag node : nodes)
		{
			if (node.isEnabled()) {
				assignMachineToNode(node, areCoreNodes);
			}
		}
	}

	/**
	 * Assign a MachineTag to a NodeTag. Core nodes are all defined
	 * to be on the local machine.
	 *
	 * @param node the NodeTag
	 * @param isCoreNode true if it is a core node, false otherwise
	 */
	public void assignMachineToNode(final NodeTag node, final boolean isCoreNode)
	{
		// If the node does not specify a machine name, then
		// it will be connected to the local machine by default
		MachineTag machine = getLocalMachine();

		// Core nodes are assigned to the local machine
		if (!isCoreNode)
		{
			String machineName = node.getMachineName();
			if (machineName != null && machineName.length() > 0)
			{
				// Use the machine defined by the node
				machine = getMachine(machineName);

				// Make sure the machine is defined
				if (machine == null)
				{
					throw new RuntimeException(
						"ERROR: unknown machine name [" + machineName + "]");
				}
			}
		}

		if (machine != null)  // Just for safety
		{
			FileLog.info("roslaunch",
				"... selected machine [" + machine.getName() +
				"] for node of type [" + node.getPackage() +
				"/" + node.getType() + "]");

			node.setMachine(machine);
		}
	}
}
