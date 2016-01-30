package org.ros.rosjava.roslaunch.launching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.parsing.GroupTag;
import org.ros.rosjava.roslaunch.parsing.IncludeTag;
import org.ros.rosjava.roslaunch.parsing.LaunchFile;
import org.ros.rosjava.roslaunch.parsing.NodeTag;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.util.StringLengthListComparator;
import org.ros.rosjava.roslaunch.xmlrpc.RosXmlRpcClient;

/**
 * The ClearParamsManager class
 *
 * This class is responsible for dealing with the clear params
 * attributes that can be specified on various types of XML tags.
 */
public class ClearParamsManager
{
	public static List<String> getClearParams(final List<LaunchFile> launchFiles)
	{
		List<String> clearParams = new ArrayList<String>();

		// Get clear params for the entire set of launch files
		for (LaunchFile launchFile : launchFiles)
		{
			if (launchFile.isEnabled())
			{
				List<String> launchParams = getClearParams(launchFile);
				clearParams.addAll(launchParams);
			}
		}

		return clearParams;
	}

	public static List<String> getClearParams(final LaunchFile launchFile)
	{
		List<String> clearParams = new ArrayList<String>();

		// Stop if this is a disabled launch file
		if (!launchFile.isEnabled()) return clearParams;

		// Check for clear params in all nodes
		for (NodeTag node : launchFile.getNodes())
		{
			if (node.isEnabled() && node.getClearParams())
			{
				// Add the resolved name of the node
				// Note: the NodeTag enforces the constraint that the
				//       name must be defined when using clear params
				String resolvedName = node.getResolvedName();
				clearParams.add(RosUtil.makeGlobalNamespace(resolvedName));
			}
		}

		// Check for clear params in all groups
		for (GroupTag group : launchFile.getGroups())
		{
			if (group.isEnabled())
			{
				if (group.getClearParams())
				{
					// Add the namespace for this group
					// Note: the GroupTag enforces the constraint that the
					//       namespace must be defined when using clear params
					String ns = group.getNamespace();
					clearParams.add(RosUtil.makeGlobalNamespace(ns));
				}

				// Recursively get clear params from the group
				List<String> groupParams = getClearParams(group.getLaunchFile());
				clearParams.addAll(groupParams);
			}
		}

		// Check for clear params in all includes
		for (IncludeTag include : launchFile.getIncludes())
		{
			if (include.isEnabled())
			{
				if (include.getClearParams())
				{
					// Add the namespace for this include
					// Note: the IncludeTag enforces the constraint that the
					//       namespace must be defined when using clear params
					String ns = include.getNamespace();
					clearParams.add(RosUtil.makeGlobalNamespace(ns));
				}

				// Recursively get clear params from the included launch file
				List<String> includeParams = getClearParams(include.getLaunchFile());
				clearParams.addAll(includeParams);
			}
		}

		return clearParams;
	}

	/**
	 * Print the List of namespaces to clear to the screen.
	 *
	 * @param clearParams the List of namespaces to clear
	 */
	public static void printClearParams(final List<String> clearParams)
	{
		if (clearParams.size() > 0)
		{
			PrintLog.info("CLEAR PARAMETERS");

			for (String namespace : clearParams) {
				PrintLog.info(" * " + namespace);
			}

			PrintLog.info("");  // Space between next section
		}
	}

	public static List<String> unifyClearParams(final List<String> clearParams)
	{
		List<String> unifiedParams = new ArrayList<String>();

		// Sort the set of namespaces by length of namespace in descending order
		// this reduces the number of comparisons we need to make
		List<String> sortedParams = new ArrayList<String>(clearParams);
		Collections.sort(sortedParams, new StringLengthListComparator());

		// Iterate over all of the namespaces in sorted order to generate the list
		// of unified namespaces to clear, which reduces the namespaces to the
		// shortest ancestor namespace that is contained in the list, e.g.,
		// if /foo/bar/, /foo/bar/bang/, and /foo/ are contained in this namespace
		// then this should only keep the /foo/ namespace which encompasses all
		// of the other namespaces)
		for (int i = 0; i < sortedParams.size(); ++i)
		{
			String paramI = sortedParams.get(i);

			String shortestPrefix = paramI;  // Assume current is the best match
			for (int j = i; j < sortedParams.size(); ++j)
			{
				String paramJ = sortedParams.get(j);

				// If the second param (j) is a prefix of the first param (i) then
				// it a parent namespace (or identical). If it is also shorter
				// than our current best prefix (which it is guaranteed to be at
				// least the same length due to the sorting order) then we found a
				// new shorter parent namespace that clears the namespaces for both
				// the first namespace and the most recent shortest prefix namespace
				if (paramI.startsWith(paramJ) && paramJ.length() < shortestPrefix.length()) {
					shortestPrefix = paramJ;  // Found a new best match
				}
			}

			// Only add the namespace once
			if (!unifiedParams.contains(shortestPrefix)) {
				unifiedParams.add(shortestPrefix);
			}
		}

		return unifiedParams;
	}

	/**
	 * Clear the given List of namespaces.
	 *
	 * @param clearParams the List of namespaces
	 * @param uri is the URI to reach the ROS master server
	 */
	public static void clearParams(
			final List<String> clearParams,
			final String uri)
	{
		RosXmlRpcClient client = new RosXmlRpcClient(uri);

		for (String namespace : clearParams)
		{
			try {
				client.deleteParam(namespace);
			}
			catch (Exception e) {
				// Ignore errors for mising params when clearing params
			}
		}
	}
}
