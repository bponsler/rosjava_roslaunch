package org.ros.rosjava.roslaunch.launching;

/**
 * The RosProcessIF class provides an interface for running and
 * monitoring local or remote processes.
 */
public interface RosProcessIF
{
	/**
	 * Get the name of the process.
	 *
	 * @return the name
	 */
	public String getName();

	/**
	 * Set the name of the process.
	 *
	 * @param name the new name
	 */
	public void setName(final String name);

	/**
	 * Get the exit code for this process, or null if the
	 * process is still running.
	 *
	 * @return the exit code, or null if the process is still running
	 */
	public boolean isRunning();

	/**
	 * Determine if this is a required node.
	 *
	 * @return true if this is a required node
	 */
	public boolean isRequired();

	/**
	 * Determine if this is should be respawned when it dies.
	 *
	 * @return true if this node should be respawned
	 */
	public boolean shouldRespawn();

	/**
	 * Get the number of seconds to wait before respawning this process.
	 *
	 * @return the respawn delay (in seconds)
	 */
	public float getRespawnDelaySeconds();

	/**
	 * Restart the process.
	 *
	 * @throws Exception if there is an error while starting the process
	 */
	public void restart() throws Exception;

	/**
	 * Destroy this process (i.e., stop it from running).
	 */
	public void destroy();

	/**
	 * Wait for the process to finish running.
	 *
	 * @throws InterruptedException
	 */
	public void waitFor() throws Exception;

	/**
	 * Get a human readable description of the exit code for this process.
	 *
	 * @return the human readable description of the exit code
	 */
	public String getExitCodeDescription();
}
