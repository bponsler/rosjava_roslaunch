package org.ros.rosjava.roslaunch.launching;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ros.rosjava.roslaunch.parsing.GroupTag;
import org.ros.rosjava.roslaunch.parsing.IncludeTag;
import org.ros.rosjava.roslaunch.parsing.LaunchFile;
import org.ros.rosjava.roslaunch.parsing.NodeTag;
import org.ros.rosjava.roslaunch.parsing.ParamTag;
import org.ros.rosjava.roslaunch.xmlrpc.RosXmlRpcClient;

/**
 * The ParamManager class
 *
 * This class is responsible for dealing with ParamTags
 * defined within a launch file tree.
 */
public class ParamManager
{
	/**
	 * Get the List of all ParamTags defined in the given tree defined
	 * by the List of LaunchFiles.
	 *
	 * @param launchFiles the List of LaunchFiles
	 * @return the List of ParamTags defined within the launch file tree
	 */
	public static List<ParamTag> getParams(final List<LaunchFile> launchFiles)
	{
		List<ParamTag> params = new ArrayList<ParamTag>();

		for (LaunchFile launchFile : launchFiles)
		{
			if (launchFile.isEnabled())
			{
				List<ParamTag> launchParams = getParams(launchFile);
				params.addAll(launchParams);
			}
		}

		return params;
	}

	/**
	 * Get the List of all ParamTags defined within the given LaunchFile.
	 *
	 * @param launchFile the LaunchFile
	 * @return the List of all ParamTags defined in the LaunchFile
	 */
	public static List<ParamTag> getParams(final LaunchFile launchFile)
	{
		List<ParamTag> params = new ArrayList<ParamTag>();

		// Stop if the launch file is disabled
		if (!launchFile.isEnabled()) return params;

		// Add all defined params
		for (ParamTag param : launchFile.getParameters())
		{
			// Only keep enabled params
			if (param.isEnabled()) {
				params.add(param);
			}
		}

		// Add params defined in all nodes
		for (NodeTag node : launchFile.getNodes())
		{
			if (node.isEnabled())
			{
				for (ParamTag param : node.getParams())
				{
					// Only keep enabled params
					if (param.isEnabled()) {
						params.add(param);
					}
				}
			}
		}

		// Add params defined in all groups
		for (GroupTag group : launchFile.getGroups())
		{
			if (group.isEnabled())
			{
				List<ParamTag> groupParams = getParams(group.getLaunchFile());
				params.addAll(groupParams);
			}
		}

		// Add params defined from all includes
		for (IncludeTag include : launchFile.getIncludes())
		{
			if (include.isEnabled())
			{
				List<ParamTag> includeParams = getParams(include.getLaunchFile());
				params.addAll(includeParams);
			}
		}

		return params;
	}

	/**
	 * Dump the List of ParamTags to the given Map of param name
	 * to param value.
	 *
	 * @param params the List of ParamTags
	 * @param paramMap the Map from param name to param value
	 */
	public static void dumpParameters(
			final List<ParamTag> params,
			Map<String, String> paramMap)
	{
		for (ParamTag param : params)
		{
			// Only dump enabled params
			if (param.isEnabled())
			{
				String value = param.getValue();
				paramMap.put(param.getResolvedName(), value);
			}
		}
	}

	/**
	 * Send a request to the ROS master server to set each
	 * of the given ParamTags
	 *
	 * @param params the List of ParamTags
	 * @param uri the URI to reach the ROS master server
	 * @throws Exception if a parameter failed to be set
	 */
	public static void setParameters(
			final List<ParamTag> params,
			final String uri) throws Exception
	{
		for (ParamTag param : params) {
			setParameter(param, uri);
		}
	}

	/**
	 * Send a request to the ROS master server to set
	 * a single ParamTag.
	 *
	 * @param param the ParamTag to set
	 * @param uri the URI to reach the ROS master server
	 * @throws Exception if a parameter failed to be set
	 */
	public static void setParameter(
			final ParamTag param,
			final String uri) throws Exception
	{
		// Only set enabled params
		if (param.isEnabled())
		{
			// Use the XMLRPC interface to set all parameters
			RosXmlRpcClient client = new RosXmlRpcClient(uri);
			client.setParam(param.getResolvedName(), param.getType(), param.getValue());
		}
	}
}
