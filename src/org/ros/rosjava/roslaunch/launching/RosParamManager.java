package org.ros.rosjava.roslaunch.launching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ros.rosjava.roslaunch.parsing.GroupTag;
import org.ros.rosjava.roslaunch.parsing.IncludeTag;
import org.ros.rosjava.roslaunch.parsing.LaunchFile;
import org.ros.rosjava.roslaunch.parsing.NodeTag;
import org.ros.rosjava.roslaunch.parsing.RosParamTag;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.xmlrpc.ObjectToXml;
import org.ros.rosjava.roslaunch.xmlrpc.RosXmlRpcClient;

/**
 * The RosParamManager class
 *
 * This class is responsible for dealing with RosParamTags
 * defined within a launch file tree.
 */
public class RosParamManager
{
	//////////////////////////////////////////////
	// get params functions
	//
	
	/**
	 * Get the List of RosParamTags defined within the tree of
	 * launch files defined by the given List of LaunchFiles.
	 *
	 * @param launchFiles the List of LaunchFiles
	 * @return the List of RosParamTags
	 */
	public static List<RosParamTag> getRosParams(final List<LaunchFile> launchFiles)
	{
		List<RosParamTag> rosParams = new ArrayList<RosParamTag>();
		
		for (LaunchFile launchFile : launchFiles) {
			List<RosParamTag> launchParams = getRosParams(launchFile);
			rosParams.addAll(launchParams);
		}
		
		return rosParams;
	}
	
	/**
	 * Get the List of RosParamTags defined within the given LaunchFile.
	 *
	 * @param launchFiles the LaunchFile
	 * @return the List of RosParamTags
	 */
	public static List<RosParamTag> getRosParams(final LaunchFile launchFile)
	{
		List<RosParamTag> rosParams = new ArrayList<RosParamTag>();

		// Add all rosparams defined in the launch tree
		for (RosParamTag param : launchFile.getRosParams()) {
			rosParams.add(param);
		}
		
		// Add all rosparams defined by nodes
		for (NodeTag node : launchFile.getNodes()) {
			rosParams.addAll(node.getRosParams());
		}
		
		// Add all rosparams defined by groups
		for (GroupTag group : launchFile.getGroups())
		{
			if (group.isEnabled())
			{
				List<RosParamTag> groupParams = getRosParams(group.getLaunchFile());
				rosParams.addAll(groupParams);
			}
		}
		
		// Add all rosparams defined in includes
		for (IncludeTag include : launchFile.getIncludes())
		{
			if (include.isEnabled())
			{
				List<RosParamTag> includeParams = getRosParams(include.getLaunchFile());
				rosParams.addAll(includeParams);
			}
		}
		
		return rosParams;
	}
	
	/**
	 * Get the Map of rosparam names to rosparam values for all RosParamTags
	 * that load a parameter for the given launch file tree.
	 *
	 * @param launchFiles the List of LaunchFiles
	 * @return the Map of 'load' rosparam name value pairs
	 */
	public static Map<String, String> getLoadRosParamsMap(final List<LaunchFile> launchFiles)
	{
		Map<String, String> loadParams = new HashMap<String, String>();
		
		for (LaunchFile launchFile : launchFiles) {
			getLoadRosParamsMap(launchFile, loadParams);
		}
		
		return loadParams;
	}
	
	/**
	 * Get the Map of rosparam names to rosparam values for all RosParamTags
	 * that load a parameter for the given LaunchFile.
	 *
	 * @param launchFiles the LaunchFile
	 * @param loadParams the Map of 'load' rosparam name value pairs
	 */
	public static void getLoadRosParamsMap(
			final LaunchFile launchFile,
			Map<String, String> loadParams)
	{
		// Add all rosparams defined in the launch tree
		for (RosParamTag param : launchFile.getRosParams())
		{
			if (param.isLoadCommand()) {
				getLoadRosParam(param, loadParams);
			}
		}
		
		// Add all rosparams defined by nodes
		for (NodeTag node : launchFile.getNodes())
		{
			for (RosParamTag param : node.getRosParams())
			{
				if (param.isLoadCommand()) {
					getLoadRosParam(param, loadParams);
				}
			}
		}
		
		// Add all rosparams defined by groups
		for (GroupTag group : launchFile.getGroups())
		{
			if (group.isEnabled()) {
				getLoadRosParamsMap(group.getLaunchFile(), loadParams);
			}
		}
		
		// Add all rosparams defined in includes
		for (IncludeTag include : launchFile.getIncludes())
		{
			if (include.isEnabled())
			{
				getLoadRosParamsMap(include.getLaunchFile(), loadParams);
			}
		}
	}
	/**
	 * Get a map of rosparam name value pairs for a single 'load' RosParamTag.
	 *
	 * @param rosParam the RosParamTag
	 * @param loadParams the Map of 'load' rosparam name value pairs
	 */
	@SuppressWarnings("unchecked")
	public static void getLoadRosParam(
			final RosParamTag rosParam,
			Map<String, String> loadParams)
	{
		///// must be a load command
		String resolved = rosParam.getResolvedName();
		Object yamlObj = rosParam.getYamlObject();
		
		String content = rosParam.getYamlContent();

		if (resolved.length() > 0 && content.length() > 0)
		{
			// Handle dumping dictionary parameters, which end up
			// dumping parameters based on the layout of the dictionary
			if (yamlObj != null && ObjectToXml.isMap(yamlObj))
			{
				getLoadRosParamDict(
						resolved,
						(Map<String, Object>)yamlObj,
						loadParams);
				return;
			}
			else {
				// Store the non-dictionary parameter
				loadParams.put(resolved, content);
			}
		}
	}
	
	/**
	 * Get a Map of rosparam name value pairs for all the parameters
	 * that will be loaded by a RosParamTag with a dictionary (i.e., Map)
	 * value.
	 *
	 * @param namespace the namespace of the RosParamTag
	 * @param map the Map value of the RosParamTag
	 * @param loadParams the Map of rosparam name value pairs
	 */
	@SuppressWarnings("unchecked")
	private static void getLoadRosParamDict(
			final String namespace,
			final Map<String, Object> map,
			Map<String, String> loadParams)
	{
		for (Object key : map.keySet())
		{
			Object value = map.get(key);
			String resolvedKey = RosUtil.joinNamespace(namespace, key.toString());
			
			if (ObjectToXml.isMap(value))
			{
				// Recurse to handle this dictionary
				getLoadRosParamDict(
					resolvedKey, (Map<String, Object>)value, loadParams);				
			}
			else
			{
				// Found a parameter, print it
				loadParams.put(resolvedKey, value.toString());
			}
		}
	}
	
	
	//////////////////////////////////////////////
	// print functions
	//
	
	/**
	 * Print each of the given rosparam name value pairs to the screen.
	 *
	 * @param rosParamsMap the Map of rosparam name value pairs to print
	 */
	public static void printParameters(final Map<String, String> rosParamsMap)
	{
		for (String name : rosParamsMap.keySet())
		{
			// Only display the first 20 characters, if the param
			// value is very long
			String value = rosParamsMap.get(name);
			if (value.length() > 20) {
				value = value.substring(0, 20) + "...";
			}
			
			// Remove carriage returns and new lines for display purposes
			value = value.replace("\r", "").replace("\n", "");
		
			System.out.println(" * " + name + ": " + value);
		}
	}
	
	
	//////////////////////////////////////////////
	// set functions
	//
	
	/**
	 * Send a request to the ROS master server to set all of
	 * the rosparams defined in the given List of RosParamTags.
	 *
	 * @param rosParams the List of RosParamTags
	 * @param uri the URI to reach the ROS master server
	 * @throws Exception if one of the rosparams failed to set
	 */
	public static void setParameters(
			final List<RosParamTag> rosParams,
			final String uri) throws Exception
	{
		for (RosParamTag rosParam: rosParams) {
			if (rosParam.isLoadCommand()) {
				setRosParam(rosParam, uri);
			}
		}
	}
	
	/**
	 * Send a request to the ROS master server to set the value of
	 * a single RosParamTag.
	 *
	 * @param rosparam the RosParamTag
	 * @param uri the URI to reach the ROS master server
	 * @throws Exception if the rosparam failed to be set
	 */
	private static void setRosParam(final RosParamTag rosparam, final String uri) throws Exception
	{
		///// must be a load command
		String resolved = rosparam.getResolvedName();
		Object yamlObj = rosparam.getYamlObject();
		
		if (resolved.length() > 0 && yamlObj != null)
		{		
			RosXmlRpcClient client = new RosXmlRpcClient(uri);
			client.setYamlParam(rosparam);			
		}
	}
	
	
	//////////////////////////////////////////////
	// rosparam delete functions
	//
	
	/**
	 * Send a request to the ROS master server to delete all
	 * of the RosParamTags defined in the given List.
	 *
	 * @param rosParams the List of RosParamTags
	 * @param uri the URI to reach the ROS master server
	 */
	public static void deleteParameters(
			final List<RosParamTag> rosParams,
			final String uri)
	{
		for (RosParamTag rosParam : rosParams)
		{
			if (rosParam.isDeleteCommand()) {
				deleteRosParam(rosParam, uri);
			}
		}
	}

	/**
	 * Send a request to the ROS master server to delete a
	 * single RosParamTag.
	 *
	 * @param rosParams the RosParamTag
	 * @param uri the URI to reach the ROS master server
	 */
	private static void deleteRosParam(final RosParamTag rosparam, final String uri)
	{
		String param = rosparam.getResolvedName();
		
		System.out.println("running rosparam delete " + param);
		
		RosXmlRpcClient client = new RosXmlRpcClient(uri);
		
		try
		{
			// Handle the generic delete differently than
			// normal delete params
			if (param.compareTo("/") == 0) {
				client.clearParam(param);
			}
			else {
				client.deleteParam(param);
			}
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}
	
	
	//////////////////////////////////////////////
	// rosparam dump functions
	//
	
	/**
	 * Run all of the 'dump' RosParamTags defined in the given
	 * List of RosParamTags.
	 *
	 * @param rosParams the List of RosParamTags
	 * @param uri the URI to reach the ROS master server
	 */
	public static void dumpParameters(
			final List<RosParamTag> rosParams,
			final String uri)
	{
		// TODO: finish this
		System.err.println(
			"WARNING: rosparam dump commands are not yet implemented!");
	}
}
