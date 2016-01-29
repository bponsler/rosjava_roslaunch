package org.ros.rosjava.roslaunch.launching;

import java.util.List;

import org.ros.rosjava.roslaunch.parsing.ArgTag;
import org.ros.rosjava.roslaunch.parsing.LaunchFile;

/**
 * The ArgManager class
 *
 * This class is responsible for dealing with ArgTags
 * within a launch file tree.
 */
public class ArgManager
{
	/**
	 * Get the List of required and optional ArgTags defined
	 * in the launch tree defined by the List of LaunchFiles.
	 *
	 * @param launchFiles the List of LaunchFiles
	 * @param requiredArgs the List of required ArgTags
	 * @param optionalArgs the List of optional ArgTags
	 */
	public static void getArgs(
			final List<LaunchFile> launchFiles,
			List<ArgTag> requiredArgs,
			List<ArgTag> optionalArgs)
	{
		// Add args from all launch files
		for (LaunchFile launchFile : launchFiles)
		{
			for (ArgTag arg : launchFile.getArgs())
			{
				if (arg.isRequired()) {
					requiredArgs.add(arg);
				}
				else if (arg.isOptional()) {
					optionalArgs.add(arg);
				}
			}
		}
	}
}
