/*
 * AbstractConnectionManager.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */



package tigase.socks5;

//~--- non-JDK imports --------------------------------------------------------

import tigase.net.ConnectionOpenListener;
import tigase.net.ConnectionOpenThread;
import tigase.net.ConnectionType;
import tigase.net.IOService;
import tigase.net.IOServiceListener;
import tigase.net.SocketThread;
import tigase.net.SocketType;

import tigase.server.AbstractMessageReceiver;

import tigase.stats.StatisticsList;

//~--- JDK imports ------------------------------------------------------------

import java.io.IOException;

import java.net.InetSocketAddress;

import java.nio.channels.SocketChannel;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

/**
 * Class description
 *
 *
 * @param <IO>
 *
 * @version        5.2.0, 13/10/15
 * @author         <a href="mailto:andrzej.wojcik@tigase.org">Andrzej Wójcik</a>
 */
public abstract class AbstractConnectionManager<IO extends IOService<?>>
				extends AbstractMessageReceiver
				implements IOServiceListener<IO> {
	/** Field description */
	protected static final int NET_BUFFER_HT_PROP_VAL = 64 * 1024;

	/** Field description */
	protected static final String NET_BUFFER_PROP_KEY = "net-buffer";

	/** Field description */
	protected static final int NET_BUFFER_ST_PROP_VAL = 2 * 1024;

	/** Field description */
	protected static final String PORT_CLASS_PROP_KEY = "class";

	/** Field description */
	protected static final String PORT_IFC_PROP_KEY = "ifc";

	/** Field description */
	protected static final String[] PORT_IFC_PROP_VAL = { "*" };

	/** Field description */
	protected static final String PORT_KEY = "port-no";

	/** Field description */
	protected static final String PORT_SOCKET_PROP_KEY = "socket";

	/** Field description */
	protected static final String PORT_TYPE_PROP_KEY = "type";

	/** Field description */
	protected static final String PROP_KEY = "connections/";
	private static final Logger   log = Logger.getLogger(AbstractConnectionManager.class
			.getCanonicalName());
	private static ConnectionOpenThread connectThread = ConnectionOpenThread.getInstance();

	/** Field description */
	protected static final String PORTS_PROP_KEY = PROP_KEY + "ports";

	//~--- fields ---------------------------------------------------------------

	/** Field description */
	protected Map<String, IO>               services = new ConcurrentHashMap<String, IO>();
	private long                            bytesReceived  = 0;
	private long                            bytesSent      = 0;
	private int[]                           ports          = null;
	private long                            socketOverflow = 0;
	private LinkedList<Map<String, Object>> waitingTasks = new LinkedList<Map<String,
			Object>>();
	private Set<ConnectionListenerImpl> pending_open = Collections.synchronizedSet(
			new HashSet<ConnectionListenerImpl>());;
	private IOServiceStatisticsGetter ioStatsGetter = new IOServiceStatisticsGetter();
	private boolean                   initializationCompleted = false;

	/** Field description */
	protected int net_buffer = NET_BUFFER_ST_PROP_VAL;

	//~--- methods --------------------------------------------------------------

	/**
	 * Executed every minute to i.e. get statistics
	 *
	 */
	@Override
	public synchronized void everyMinute() {
		doForAllServices(ioStatsGetter);
		super.everyMinute();
	}

	/**
	 * Method description
	 *
	 */
	@Override
	public void initializationCompleted() {
		initializationCompleted = true;
		for (Map<String, Object> params : waitingTasks) {
			reconnectService(params, 2000);    // connectionDelay);
		}
		waitingTasks.clear();
		super.initializationCompleted();
	}

	/**
	 * Handle service after creation
	 *
	 * @param serv
	 */
	public void serviceStarted(IO serv) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "connection {0} started", serv.getUniqueId());
		}
		serv.setIOServiceListener((IOServiceListener) this);
		services.put(serv.getUniqueId(), serv);
	}

	/**
	 * Handle service after stopping
	 *
	 * @param serv
	 * @return
	 */
	@Override
	public boolean serviceStopped(IO serv) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "connection {0} stopped", serv.getUniqueId());
		}
		services.remove(serv.getUniqueId());

		return true;
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Returns map with default configuration based on parameters
	 *
	 * @param params
	 * @return
	 */
	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> props = super.getDefaults(params);

		props.put(NET_BUFFER_PROP_KEY, NET_BUFFER_HT_PROP_VAL);

		int[] ports = getDefaultPorts();

		props.put(PORTS_PROP_KEY, ports);
		for (int port : ports) {
			putDefPortParams(props, port, SocketType.plain);
		}

		return props;
	}

	/**
	 * Fill statistics list with statistics
	 *
	 * @param list
	 */
	@Override
	public void getStatistics(StatisticsList list) {
		super.getStatistics(list);
		list.add(getName(), "Open connections", services.size(), Level.INFO);
		if (list.checkLevel(Level.FINEST) || (services.size() < 1000)) {
			int waitingToSendSize = 0;

			for (IO serv : services.values()) {
				waitingToSendSize += serv.waitingToSendSize();
			}
			list.add(getName(), "Waiting to send", waitingToSendSize, Level.FINE);
		}
		list.add(getName(), "Bytes sent", bytesSent, Level.FINE);
		list.add(getName(), "Bytes received", bytesReceived, Level.FINE);
		list.add(getName(), "Socket overflow", socketOverflow, Level.FINE);
	}

	//~--- set methods ----------------------------------------------------------

	/**
	 * Configure instance based on properties
	 *
	 * @param props
	 */
	@Override
	public void setProperties(Map<String, Object> props) {
		super.setProperties(props);
		if (props.get(NET_BUFFER_PROP_KEY) != null) {
			net_buffer = (Integer) props.get(NET_BUFFER_PROP_KEY);
		}
		if (props.size() == 1) {

			// If props.size() == 1, it means this is a single property update and
			// ConnectionManager does not support it yet.
			return;
		}
		releaseListeners();
		ports = (int[]) props.get(PORTS_PROP_KEY);
		if (ports != null) {
			for (int i = 0; i < ports.length; i++) {
				Map<String, Object> port_props = new LinkedHashMap<String, Object>(20);

				for (Map.Entry<String, Object> entry : props.entrySet()) {
					if (entry.getKey().startsWith(PROP_KEY + ports[i])) {
						int    idx = entry.getKey().lastIndexOf('/');
						String key = entry.getKey().substring(idx + 1);

						log.log(Level.CONFIG, "Adding port property key: {0}={1}", new Object[] { key,
								entry.getValue() });
						port_props.put(key, entry.getValue());
					}    // end of if (entry.getKey().startsWith())
				}      // end of for ()
				port_props.put(PORT_KEY, ports[i]);
				addWaitingTask(port_props);

				// reconnectService(port_props, startDelay);
			}        // end of for (int i = 0; i < ports.length; i++)
		}          // end of if (ports != null)
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Assign task for delayed execution
	 *
	 * @param conn
	 */
	protected void addWaitingTask(Map<String, Object> conn) {
		if (initializationCompleted) {
			reconnectService(conn, 2000);    // connectionDelay);
		} else {
			waitingTasks.add(conn);
		}
	}

	/**
	 * Perform a given action defined by ServiceChecker for all active IOService
	 * objects (active network connections).
	 *
	 * @param checker
	 *          is a <code>ServiceChecker</code> instance defining an action to
	 *          perform for all IOService objects.
	 */
	protected void doForAllServices(ServiceChecker<IO> checker) {
		for (IO service : services.values()) {
			checker.check(service);
		}
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Returns array of defaults ports to bind
	 *
	 * @return
	 */
	protected abstract int[] getDefaultPorts();

	/**
	 * Returns new instance of service
	 *
	 * @return
	 * @throws IOException
	 */
	protected abstract IO getIOServiceInstance() throws IOException;

	/**
	 * Return array of ports
	 *
	 * @return a value of <code>int[]</code>
	 */
	protected int[] getPorts() {
		return this.ports;
	}

	/**
	 * Returns true if instance should handle high throughtput
	 *
	 * @return
	 */
	protected boolean isHighThroughput() {
		return false;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Generate default port properties
	 *
	 * @param props
	 * @param port
	 * @param sock
	 */
	private void putDefPortParams(Map<String, Object> props, int port, SocketType sock) {
		log.log(Level.CONFIG, "Generating defaults for port: {0}", port);
		props.put(PROP_KEY + port + "/" + PORT_TYPE_PROP_KEY, ConnectionType.accept);
		props.put(PROP_KEY + port + "/" + PORT_SOCKET_PROP_KEY, sock);
		props.put(PROP_KEY + port + "/" + PORT_IFC_PROP_KEY, PORT_IFC_PROP_VAL);

//  props.put(PROP_KEY + port + "/" + PORT_REMOTE_HOST_PROP_KEY,
//      PORT_REMOTE_HOST_PROP_VAL);
//  props.put(PROP_KEY + port + "/" + TLS_REQUIRED_PROP_KEY, TLS_REQUIRED_PROP_VAL);
//  Map<String, Object> extra = getParamsForPort(port);
//
//  if (extra != null) {
//    for (Map.Entry<String, Object> entry : extra.entrySet()) {
//      props.put(PROP_KEY + port + "/" + entry.getKey(), entry.getValue());
//    } // end of for ()
//  } // end of if (extra != null)
	}

	/**
	 * Reconnect service (bind port/connect)
	 *
	 * @param port_props
	 * @param delay
	 */
	private void reconnectService(final Map<String, Object> port_props, long delay) {
		if (log.isLoggable(Level.FINER)) {
			String cid = "" + port_props.get("local-hostname") + "@" + port_props.get(
					"remote-hostname");

			log.log(Level.FINER,
					"Reconnecting service for: {0}, scheduling next try in {1}secs, cid: {2}",
					new Object[] { getName(),
					delay / 1000, cid });
		}
		addTimerTask(new TimerTask() {
			@Override
			public void run() {
				int port = (Integer) port_props.get(PORT_KEY);

				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE, "Reconnecting service for component: {0}, on port: {1}",
							new Object[] { getName(),
							port });
				}
				startService(port_props);
			}
		}, delay);
	}

	/**
	 * Release listeners
	 */
	private void releaseListeners() {
		for (ConnectionListenerImpl cli : pending_open) {
			connectThread.removeConnectionOpenListener(cli);
		}
		pending_open.clear();
	}

	private void startService(Map<String, Object> port_props) {
		if (port_props == null) {
			throw new NullPointerException("port_props cannot be null.");
		}

		ConnectionListenerImpl cli = new ConnectionListenerImpl(port_props);

		if (cli.getConnectionType() == ConnectionType.accept) {
			pending_open.add(cli);
		}
		connectThread.addConnectionOpenListener(cli);
	}

	//~--- inner classes --------------------------------------------------------

	private class ConnectionListenerImpl
					implements ConnectionOpenListener {
		private Map<String, Object> port_props = null;

		//~--- constructors -------------------------------------------------------

		/**
		 * Constructs ...
		 *
		 *
		 * @param port_props
		 */
		public ConnectionListenerImpl(Map<String, Object> port_props) {
			this.port_props = port_props;
		}

		//~--- methods ------------------------------------------------------------

		/**
		 * Method description
		 *
		 *
		 * @param sc is a <code>SocketChannel</code>
		 */
		@Override
		public void accept(SocketChannel sc) {
			IO conn = null;

			try {
				conn = getIOServiceInstance();
				conn.setIOServiceListener(null);
				conn.accept(sc);
				serviceStarted(conn);
				SocketThread.addSocketService(conn);
			} catch (IOException ex) {
				log.log(Level.WARNING, "Can not accept connection.", ex);
				if (conn != null) {

//        try {
					conn.stop();

//        conn.close();
//                                        }
//                                        catch (IOException ex1) {
//        Logger.getLogger(AbstractConnectionManager.class.getName()).log(Level.SEVERE, null, ex1);
//                                        }
				}
			}
		}

		//~--- get methods --------------------------------------------------------

		/**
		 * Method description
		 *
		 *
		 * @return a value of <code>ConnectionType</code>
		 */
		@Override
		public ConnectionType getConnectionType() {
			String type = null;

			if (port_props.get(PORT_TYPE_PROP_KEY) == null) {
				log.warning(getName() + ": connection type is null: " + port_props.get(PORT_KEY)
						.toString());
			} else {
				type = port_props.get(PORT_TYPE_PROP_KEY).toString();
			}

			return ConnectionType.valueOf(type);
		}

		/**
		 * Method description
		 *
		 *
		 * @return a value of <code>String[]</code>
		 */
		@Override
		public String[] getIfcs() {
			return (String[]) port_props.get(PORT_IFC_PROP_KEY);
		}

		/**
		 * Method description
		 *
		 *
		 * @return a value of <code>int</code>
		 */
		@Override
		public int getPort() {
			return (Integer) port_props.get(PORT_KEY);
		}

		/**
		 * Method description
		 *
		 *
		 * @return a value of <code>int</code>
		 */
		@Override
		public int getReceiveBufferSize() {
			return net_buffer;
		}

		/**
		 * Method description
		 *
		 *
		 * @return a value of <code>InetSocketAddress</code>
		 */
		@Override
		public InetSocketAddress getRemoteAddress() {
			return (InetSocketAddress) port_props.get("remote-address");
		}

		/**
		 * Method description
		 *
		 *
		 * @return a value of <code>String</code>
		 */
		@Override
		public String getRemoteHostname() {
			return (String) port_props.get("remote-hostname");
		}

		/**
		 * Method description
		 *
		 *
		 * @return a value of <code>SocketType</code>
		 */
		@Override
		public SocketType getSocketType() {
			return SocketType.valueOf(port_props.get(PORT_SOCKET_PROP_KEY).toString());
		}

		/**
		 * Method description
		 *
		 *
		 * @return a value of <code>String</code>
		 */
		@Override
		public String getSRVType() {
			return "_socks5._tcp";
		}

		/**
		 * Method description
		 *
		 *
		 * @return a value of <code>int</code>
		 */
		@Override
		public int getTrafficClass() {
			if (isHighThroughput()) {
				return IPTOS_THROUGHPUT;
			} else {
				return DEF_TRAFFIC_CLASS;
			}
		}
	}


	/**
	 * Executed for each service to get statistics from it
	 */
	private class IOServiceStatisticsGetter
					implements ServiceChecker<IO> {
		private StatisticsList list = new StatisticsList(Level.ALL);

		//~--- methods ------------------------------------------------------------

		/**
		 * Method description
		 *
		 *
		 * @param service
		 */
		@Override
		public synchronized void check(IO service) {
			service.getStatistics(list, true);
			bytesReceived  += list.getValue("socketio", "Bytes received", -1l);
			bytesSent      += list.getValue("socketio", "Bytes sent", -1l);
			socketOverflow += list.getValue("socketio", "Buffers overflow", -1l);
		}
	}
}


//~ Formatted in Tigase Code Convention on 13/10/15
