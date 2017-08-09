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

import tigase.kernel.beans.*;
import tigase.kernel.beans.config.ConfigField;
import tigase.kernel.beans.config.ConfigurationChangedAware;
import tigase.kernel.core.Kernel;
import tigase.net.*;
import tigase.server.AbstractMessageReceiver;
import tigase.stats.StatisticsList;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

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
				implements IOServiceListener<IO>, RegistrarBean {
	/** Field description */
	protected static final int NET_BUFFER_HT_PROP_VAL = 64 * 1024;

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
	private long                            socketOverflow = 0;
	private LinkedList<Map<String, Object>> waitingTasks = new LinkedList<Map<String,
			Object>>();
	private Set<ConnectionListenerImpl> pending_open = Collections.synchronizedSet(
			new HashSet<ConnectionListenerImpl>());;
	private IOServiceStatisticsGetter ioStatsGetter = new IOServiceStatisticsGetter();
	private boolean                   initializationCompleted = false;

	/** Field description */
	@ConfigField(desc = "Size of a network buffer", alias = "net-buffer")
	protected int net_buffer = NET_BUFFER_ST_PROP_VAL;

	@Inject
	private PortsConfigBean		portsConfigBean = null;

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

	@Override
	public void register(Kernel kernel) {

	}

	@Override
	public void start() {
		super.start();
		connectWaitingTasks();
	}

	@Override
	public void stop() {
		portsConfigBean.stop();
		super.stop();
	}

	@Override
	public void unregister(Kernel kernel) {
		
	}

	//~--- methods --------------------------------------------------------------

	protected void connectWaitingTasks() {
		if (log.isLoggable(Level.FINER)) {
			log.log(Level.FINER, "Connecting waitingTasks: {0}",
					new Object[]{waitingTasks});
		}

		for (Map<String, Object> params : waitingTasks) {
			reconnectService(params, 2000);
		}
		waitingTasks.clear();
		portsConfigBean.start();
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
		Set<Integer> ports = this.portsConfigBean.getPorts();
		if (ports == null) {
			return new int[0];
		}
		return ports.stream().mapToInt(i -> i).toArray();
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

	protected void releaseListener(ConnectionOpenListener toStop) {
		pending_open.remove(toStop);
		connectThread.removeConnectionOpenListener(toStop);
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

	private ConnectionListenerImpl startService(Map<String, Object> port_props) {
		if (port_props == null) {
			throw new NullPointerException("port_props cannot be null.");
		}

		ConnectionListenerImpl cli = new ConnectionListenerImpl(port_props);

		if (cli.getConnectionType() == ConnectionType.accept) {
			pending_open.add(cli);
		}
		connectThread.addConnectionOpenListener(cli);

		return cli;
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

	public static class PortConfigBean implements ConfigurationChangedAware, Initializable, UnregisterAware {

		@Inject
		private AbstractConnectionManager connectionManager;
		private ConnectionOpenListener connectionOpenListener = null;

		@ConfigField(desc = "Port")
		private Integer name;

		@ConfigField(desc = "Port type")
		protected ConnectionType type = ConnectionType.accept;

		@ConfigField(desc = "Socket type")
		protected SocketType socket = SocketType.plain;

		@ConfigField(desc = "Interface to listen on")
		protected String[] ifc = null;

		public PortConfigBean() {

		}

		@Override
		public void beanConfigurationChanged(Collection<String> changedFields) {
			if (connectionManager == null || !connectionManager.isInitializationComplete())
				return;

			if (connectionOpenListener != null)
				connectionManager.releaseListener(connectionOpenListener);

			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "connectionManager: {0}, changedFields: {1}, props: {2}",
						new Object[]{connectionManager, changedFields, getProps()});
			}

			connectionOpenListener = connectionManager.startService(getProps());
		}

		@Override
		public void beforeUnregister() {
			if (connectionOpenListener != null)
				connectionManager.releaseListener(connectionOpenListener);
		}

		protected Map<String, Object> getProps() {
			Map<String, Object> props = new HashMap<>();
			props.put(PORT_KEY, name);
			props.put(PORT_TYPE_PROP_KEY, type);
			props.put(PORT_SOCKET_PROP_KEY, socket);
			if (ifc == null)
				props.put(PORT_IFC_PROP_KEY, connectionManager.PORT_IFC_PROP_VAL);
			else
				props.put(PORT_IFC_PROP_KEY, ifc);
			return props;
		}

		@Override
		public void initialize() {
			beanConfigurationChanged(Collections.emptyList());
		}
	}
	
	@Bean(name = "connections", parent = AbstractConnectionManager.class, active = true, exportable = true)
	public static class PortsConfigBean implements RegistrarBeanWithDefaultBeanClass, Initializable {

		@Inject
		private AbstractConnectionManager connectionManager;

		@Inject(nullAllowed = true)
		private PortConfigBean[] portsBeans;

		@ConfigField(desc = "Ports to enable", alias = "ports")
		private HashSet<Integer> ports;

		private Kernel kernel;

		public PortsConfigBean() {

		}

		@Override
		public Class<?> getDefaultBeanClass() {
			return PortConfigBean.class;
		}

		public Set<Integer> getPorts() {
			return ports;
		}

		@Override
		public void register(Kernel kernel) {
			this.kernel = kernel;
			if (kernel.getParent() != null) {
				String connManagerBean = kernel.getParent().getName();
				this.kernel.getParent().ln("service", kernel, connManagerBean);
			}
		}

		@Override
		public void unregister(Kernel kernel) {
			this.kernel = null;
		}

		@Override
		public void initialize() {
			if (ports == null) {
				int[] tmp = connectionManager.getDefaultPorts();
				if (tmp != null) {
					ports = new HashSet<>();
					for (int i=0; i<tmp.length; i++) {
						ports.add(tmp[i]);
					}
				}
			}
			
			for (Integer port : ports) {
				String name = String.valueOf(port);
				if (kernel.getDependencyManager().getBeanConfig(name) == null) {
					Class cls = getDefaultBeanClass();
					kernel.registerBean(name).asClass(cls).exec();
				}
			}
		}

		public void start() {
			if (portsBeans != null) {
				Arrays.stream(portsBeans).forEach(portBean -> portBean.initialize());
			}
		}

		public void stop() {
			// nothing to do for now
		}

	}

}


//~ Formatted in Tigase Code Convention on 13/10/15
