package org.ros.rosjava.roslaunch.launching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	
	/**
	 * Constructor
	 *
	 * Create a ProcessMonitor object.
	 */
	public ProcessMonitor()
	{
		m_isShutdown = false;
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
		// Only monitor processes if we have not been shut down
		if (!m_isShutdown)
		{
			// Map dead processes to their time of death
			Map<RosProcess, Long> diedProcesses = new HashMap<RosProcess, Long>(); 
			
			// Monitor all known processes
			for (RosProcess proc : m_processes)
			{
				if (!proc.isRunning())
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
						
						System.out.println(bar);						
						System.out.println("REQUIRED process [" + proc.getName() + "] has died!");
						System.out.println(exitCodeDesc);
						System.out.println("Initiating shutdown!");
						System.out.println(bar);
						
						// Stop all other processes
						this.shutdown();
						return;
					}
					else
					{					
						// Ignore processes that are being respawned
						if (!m_respawningProcesses.containsKey(proc))
						{
							//// Lost a non-required node
							System.out.println("[" + proc.getName() + "]: " + exitCodeDesc);
							
							diedProcesses.put(proc, timeOfDeath);
						}
					}
				}
			}  // end of loop over processes
			
			// Handle all of the non-required processes that
			// have died during this cycle
			for (RosProcess deadProc : diedProcesses.keySet())
			{
				//Long timeOfDeath = diedProcesses.get(deadProc);
				
				// Determine if this node should be respawned
				if (deadProc.shouldRespawn())
				{
					//// Node should be respawned
					//m_respawningProcesses.put(deadProc, timeOfDeath);
					
					// TODO: finish implementing respawning of nodes
					System.err.println(
						"WARNING: respawning nodes is not yet implemented!");
					deadProc.destroy();
					m_deadProcesses.add(deadProc);
				}
				else
				{
					//// Node should not be respawned
					
					// TODO: unregister the node

					// Stop the process
					deadProc.destroy();
					
					// Save process to fully dead list
					m_deadProcesses.add(deadProc);
				}
			}  // end of loop over dead processes
			
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
					System.out.println("[" + respawn.getName() + "] restarting process");

					// Restart the process
					try {
						respawn.restart();
					}
					catch (Exception e)
					{
						System.err.println(
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
			
		}  // end of if !shutdown
	}
	
	/**
	 * Stop all running processes.
	 */
	public void shutdown()
	{		
		// TODO: mutex between stop and monitor?
		
		// Kill all running processes
		for (RosProcess proc : m_processes)
		{
			if (proc.isRunning()) {
				System.out.println("[" + proc.getName() + "] killing on exit");
				proc.destroy();
			}
		}
		
		// Wait for all processes to stop
		for (RosProcess proc : m_processes)
		{
			try {
				proc.waitFor();
			} catch (InterruptedException e) {
				System.out.println("Error while waiting for process to stop: " + e.getMessage());
			}
		}
		
		m_isShutdown = true;
	}
}
