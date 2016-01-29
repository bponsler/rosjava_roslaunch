package org.ros.rosjava.roslaunch.xmlrpc;

/**
 * The SystemStateResponse class
 *
 * This class is responsible for encapsulating the XMLRPC response
 * data for the SystemState XMLRPC request.
 */
public class SystemStateResponse extends RosXmlRpcResponseBase
{
	/** The response code. */
	protected int m_code;
	/** The status message. */
	protected String m_statusMessage;
	/** The system state value. */
	protected String m_systemState;
	
	/**
	 * Constructor
	 *
	 * Create a SystemStateResponse object from an XMLRPC response.
	 * 
	 * @param other is the RosXmlRpcResponseBase object
	 */
	public SystemStateResponse(final RosXmlRpcResponseBase other)
	{
		super(other);

		//// XML response should look something like:
		//
		// <?xml version='1.0'?>
		// <methodResponse>
		//   <params>
		//     <param>
		//       <value>
		//         <array>
		//           <data>
		//             <value><int>1</int></value>
		//             <value><string>parameter /my_bool_param set</string></value>
		//             <value><int>1</int></value>
		//           </data>
		//         </array>
		//       </value>
		//     </param>
		//   </params>
		// </methodResponse>
		
		// The return parameters are as follows:
		//     status code, status message, and system state
		m_code = -1;
		m_statusMessage = "Failed to parse";
		m_systemState = "";
		
		// There should be exactly three response items
		if (m_responseData.size() == 3)
		{
			m_code = Integer.parseInt(m_responseData.get(0));
			m_statusMessage = m_responseData.get(1);
			m_systemState = m_responseData.get(2);
		}
		
		// Check for success
		if (m_code != 1) {
			throw new RuntimeException("ERROR: " + m_statusMessage);
		}
	}
	
	/**
	 * Get the response code.
	 * 
	 * @return the response code
	 */
	public int getCode()
	{
		return m_code;
	}
	
	/**
	 * Get the status message.
	 * 
	 * @return the status message
	 */
	public String getStatusMessage()
	{
		return m_statusMessage;
	}
	
	/**
	 * Get the system state value.
	 * 
	 * @return the system state
	 */
	public String getSystemState()
	{
		// System state is in the form of a list, e.g.:
		//     [publishers, subscribers, services]
		//
		//     publishers is in the form:
		//         [ [topic1, [topic1Publisher1...topic1PublisherN]] ... ]
		//
		//     subscribers is in the form:
		//         [ [topic1, [topic1Subscriber1...topic1SubscriberN]] ... ]
		//
		//     services is in the form:
		//         [ [service1, [service1Provider1...service1ProviderN]] ... ]
		return m_systemState;
	}
}
