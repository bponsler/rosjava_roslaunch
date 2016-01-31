package org.ros.rosjava.roslaunch.launching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.ros.rosjava.roslaunch.logging.PrintLog;

/**
 * The ProcessMonitor class
 *
 * This class is responsible for monitoring a set of
 * running processes to determine if they have died
 * or not.
 */
public class ProcessMonitor
{
	/** The List of running RosProcesses that will be monitored. */
	List<RosProcess> m_processes;
	/** The List of running RosProcesses that are dead. */
	List<RosProcess> m_deadProcesses;
	/** The Map from running RosProcesses that are being respawned to time of death. */
	Map<RosProcess, Long> m_respawningProcesses;

	/** True if the process monitor has been shutdown, or detected that it needs to shutdown. */
	private boolean m_isShutdown;

	/** Semaphore for locking protecting process monitor data. */
	private Semaphore m_semaphore;

	/**
	 * Constructor
	 *
	 * Create a ProcessMonitor object.
	 */
	public ProcessMonitor()
	{
		m_isShutdown = false;
		m_semaphore = new Semaphore(1);
		m_processes = new ArrayList<RosProcess>();
		m_deadProcesses = new ArrayList<RosProcess>();
		m_respawningProcesses = new HashMap<RosProcess, Long>();
	}

	/**
	 * Add a List of processes to be monitored.
	 *
	 * @param process the List of RosProcesses to monitor
	 */
	public void addProcesses(final List<RosProcess> processes)
	{
		m_processes.addAll(processes);
	}

	/**
	 * Add a single process to be monitored.
	 *
	 * @param process the RosProcess to monitor
	 */
	public void addProcess(final RosProcess process)
	{
		m_processes.add(process);
	}

	/**
	 * Determine if the ProcessMonitor has detected the need
	 * to shutdown, or been shutdown itself.
	 *
	 * @return true if application should shutdown, false otherwise
	 */
	public boolean isShutdown()
	{
		return m_isShutdown;
	}

	/**
	 * Monitor all of the currently running processes.
	 */
	public void monitor()
	{
		// Do not monitor processes when we cannot acquire the lock
		if (!m_semaphore.tryAcquire()) {
			return;
		}

		// Attempt to monitor processes, and catch any errors
		try {
			monitorProcesses();
		}
		catch (Exception e) {
			PrintLog.error("ERROR: while monitoring processes: " + e.getMessage());
		}

		m_semaphore.release();  // Release before leaving
	}

	/**
	 * Stop all running processes.
	 */
	public void shutdown()
	{
		// Acquire the lock -- no matter how long it takes
		while (!m_semaphore.tryAcquire()) {
			try { Thread.sleep(100); } catch (Exception e) { /* Ignore sleep errors */ }
		}

		// Only shutdown once
		if (!m_isShutdown)
		{
			// Kill all running processes
			for (RosProcess proc : m_processes)
			{
				if (proc.isRunning()) {
					PrintLog.info("[" + proc.getName() + "] killing on exit");
					proc.destroy();
				}
			}

			// Wait for all processes to stop
			for (RosProcess proc : m_processes)
			{
				try {
					proc.waitFor();
				} catch (InterruptedException e) {
					PrintLog.error("Error while waiting for process to stop: " + e.getMessage());
				}
			}

			m_isShutdown = true;

			PrintLog.info("shutting down processing monitor...");
			PrintLog.info("... shutting down processing monitor complete");
			PrintLog.bold("done");
		}

		m_semaphore.release();  // Release the lock
	}

	/**
	 * Helper function to monitor all processes
	 */
	private void monitorProcesses()
	{
		// NOTE: It is assumed that the thread lock has been acquired
		//        prior to this function being called

		// Only monitor processes if we have not been shut down
		if (!m_isShutdown)
		{
			// Map dead processes to their time of death
			Map<RosProcess, Long> diedProcesses = findDiedProcesses();
			if (diedProcesses == null || m_isShutdown) {
				return;  // Likely lost a required process
			}

			// Handle all of the non-required processes that
			// have died during this cycle
			for (RosProcess deadProc : diedProcesses.keySet()) {
				handleNonReqDeadProcess(deadProc, diedProcesses.get(deadProc));
			}

			// Handle all respawning processes
			handleRespawningProcesses();
		}
	}

	/**
	 * Find all of the processes that have died since the last check.
	 *
	 * @return the Map of RosProcesses to their time of death (in nano time)
	 */
	private Map<RosProcess, Long> findDiedProcesses()
	{
		// Map dead processes to their time of death
		Map<RosProcess, Long> diedProcesses = new HashMap<RosProcess, Long>();

		// Monitor all known processes
		for (RosProcess proc : m_processes)
		{
			// Ignore fully dead processes, and still running processes
			if (!m_deadProcesses.contains(proc) && !proc.isRunning())
			{
				Long timeOfDeath = System.nanoTime();
				String exitCodeDesc = proc.getExitCodeDescription();  // Should be non-null

				// Determine what type of node was lost
				if (proc.isRequired())
				{
					//// Lost a required node!

					// Create a horizontal bar for printing
					String bar = "";
					for (int i = 0; i < 80; ++i) bar += "=";

					PrintLog.error(bar);
					PrintLog.error("REQUIRED process [" + proc.getName() + "] has died!");
					PrintLog.error(exitCodeDesc);
					PrintLog.error("Initiating shutdown!");
					PrintLog.error(bar);

					// Stop all other processes
					m_semaphore.release();  // Release before entering shutdown
					this.shutdown();
					return null;
				}
				else
				{
					// Ignore processes that are being respawned
					if (!m_respawningProcesses.containsKey(proc))
					{
						//// Lost a non-required node
						PrintLog.error("[" + proc.getName() + "]: " + exitCodeDesc);

						diedProcesses.put(proc, timeOfDeath);
					}
				}
			}
		}  // end of loop over processes

		return diedProcesses;
	}

	/**
	 * Handle the death of a non-required process.
	 *
	 * @param deadProc the non-required process that died
	 * @param timeOfDetah the nano time when the process died
	 */
	private void handleNonReqDeadProcess(final RosProcess deadProc, final Long timeOfDeath)
	{
		// Determine if this node should be respawned
		if (deadProc.shouldRespawn())
		{
			//// Node should be respawned
			m_respawningProcesses.put(deadProc, timeOfDeath);
		}
		else
		{
			//// Node should not be respawned

			// Remove the fully dead process from the set of
			// processes to monitor
			m_processes.remove(deadProc);

			// Stop the process
			deadProc.destroy();

			// Save process to fully dead list
			m_deadProcesses.add(deadProc);
		}
	}

	private void handleRespawningProcesses()
	{
		// Check all respawning processes
		Map<RosProcess, Long> stillRespawning = new HashMap<RosProcess, Long>();
		for (RosProcess respawn : m_respawningProcesses.keySet())
		{
			Long timeOfDeath = m_respawningProcesses.get(respawn);

			// Determine number of nanoseconds, and seconds since the process died
			Long nanosSinceDeath = System.nanoTime() - timeOfDeath;
			double secondsSinceDeath = nanosSinceDeath * 1e-9;

			// Determine if enough time has elapsed since this process
			// has died so that it can be restarted
			float respawnDelaySeconds = respawn.getRespawnDelaySeconds();
			if (secondsSinceDeath >= respawnDelaySeconds)
			{
				PrintLog.info("[" + respawn.getName() + "] restarting process");

				// Restart the process
				try {
					respawn.restart();
				}
				catch (Exception e)
				{
					PrintLog.error(
						"Restart of process [" + respawn.getName() + "] failed: " + e.getMessage());
				}
			}
			else {
				// The process needs more time before it can be restarted
				stillRespawning.put(respawn, timeOfDeath);
			}
		}

		// Keep the list of processes that are still respawning around
		// for the next cycle
		m_respawningProcesses = stillRespawning;
	}
}
