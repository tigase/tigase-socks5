package tigase.socks5;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.net.ConnectionOpenListener;
import tigase.net.ConnectionOpenThread;
import tigase.net.ConnectionType;
import tigase.net.IOService;
import tigase.net.IOServiceListener;
import tigase.net.SocketThread;
import tigase.net.SocketType;
import tigase.server.AbstractMessageReceiver;
import tigase.stats.StatisticsList;

public abstract class AbstractConnectionManager<IO extends IOService<?>> extends AbstractMessageReceiver implements IOServiceListener<IO> {

	private static final Logger log = Logger.getLogger(AbstractConnectionManager.class.getCanonicalName());

	protected static final String PORT_KEY = "port-no";
	protected static final String PROP_KEY = "connections/";
	protected static final String PORTS_PROP_KEY = PROP_KEY + "ports";
	protected static final String PORT_TYPE_PROP_KEY = "type";
	protected static final String PORT_SOCKET_PROP_KEY = "socket";
	protected static final String PORT_IFC_PROP_KEY = "ifc";
	protected static final String PORT_CLASS_PROP_KEY = "class";

        protected static final String NET_BUFFER_PROP_KEY = "net-buffer";
	protected static final int NET_BUFFER_ST_PROP_VAL = 2 * 1024;
	protected static final int NET_BUFFER_HT_PROP_VAL = 64 * 1024;
        
        protected static final String[] PORT_IFC_PROP_VAL = { "*" };
        
        protected Map<String,IO> services = new ConcurrentHashMap<String,IO>();        
	private static ConnectionOpenThread connectThread = ConnectionOpenThread.getInstance();
	private LinkedList<Map<String, Object>> waitingTasks = new LinkedList<Map<String, Object>>();
	private Set<ConnectionListenerImpl> pending_open = Collections.synchronizedSet(new HashSet<ConnectionListenerImpl>());;

        private boolean initializationCompleted = false;
        private int[] ports = null;
        protected int net_buffer = NET_BUFFER_ST_PROP_VAL;
	private IOServiceStatisticsGetter ioStatsGetter = new IOServiceStatisticsGetter();
        
	private long bytesReceived = 0;
	private long bytesSent = 0;
	private long socketOverflow = 0;
	                
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
         * Returns map with default configuration based on parameters
         * 
         * @param params
         * @return 
         */
        @Override
        public Map<String,Object> getDefaults(Map<String,Object> params) {
                Map<String,Object> props = super.getDefaults(params);
                
                props.put(NET_BUFFER_PROP_KEY, NET_BUFFER_HT_PROP_VAL);
                
                int[] ports  = getDefaultPorts();
                props.put(PORTS_PROP_KEY, ports);
                for (int port : ports) {
                        putDefPortParams(props, port, SocketType.plain);
                }
                
                return props;
        }        
        
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
						int idx = entry.getKey().lastIndexOf('/');
						String key = entry.getKey().substring(idx + 1);

						log.log(Level.CONFIG, "Adding port property key: {0}={1}", new Object[] {
								key, entry.getValue() });
						port_props.put(key, entry.getValue());
					} // end of if (entry.getKey().startsWith())
				} // end of for ()

				port_props.put(PORT_KEY, ports[i]);
				addWaitingTask(port_props);

				// reconnectService(port_props, startDelay);
			} // end of for (int i = 0; i < ports.length; i++)
		} // end of if (ports != null)
	}

        @Override
	public void initializationCompleted() {
		initializationCompleted = true;

		for (Map<String, Object> params : waitingTasks) {
			reconnectService(params, 2000);//connectionDelay);
		}

		waitingTasks.clear();
                
                super.initializationCompleted();
	}
        
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
         * Return array of ports
         */
        protected int[] getPorts() {
                return this.ports;
        }
        
        /**
         * Returns array of defaults ports to bind
         * 
         * @return 
         */
        protected abstract int[] getDefaultPorts();
        
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

        /**
         * Fill statistics list with statistics
         * 
         * @param list 
         */
        @Override
	public void getStatistics(StatisticsList list) {
                super.getStatistics(list);
                
		list.add(getName(), "Open connections", services.size(), Level.INFO);

		if (list.checkLevel(Level.FINEST) || services.size() < 1000) {
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
//		props.put(PROP_KEY + port + "/" + PORT_REMOTE_HOST_PROP_KEY,
//				PORT_REMOTE_HOST_PROP_VAL);
//		props.put(PROP_KEY + port + "/" + TLS_REQUIRED_PROP_KEY, TLS_REQUIRED_PROP_VAL);

//		Map<String, Object> extra = getParamsForPort(port);
//
//		if (extra != null) {
//			for (Map.Entry<String, Object> entry : extra.entrySet()) {
//				props.put(PROP_KEY + port + "/" + entry.getKey(), entry.getValue());
//			} // end of for ()
//		} // end of if (extra != null)
	}
        
        /**
         * Assign task for delayed execution
         * 
         * @param conn 
         */
	protected void addWaitingTask(Map<String, Object> conn) {
		if (initializationCompleted) {
			reconnectService(conn, 2000);//connectionDelay);
		} else {
			waitingTasks.add(conn);
		}
	}

        /**
         * Returns new instance of service
         * 
         * @return
         * @throws IOException 
         */        
        protected abstract IO getIOServiceInstance() throws IOException;
                
        /**
         * Returns true if instance should handle high throughtput
         * 
         * @return 
         */
	protected boolean isHighThroughput() {
		return false;
	}
        
        /**
         * Reconnect service (bind port/connect)
         * 
         * @param port_props
         * @param delay 
         */
	private void reconnectService(final Map<String, Object> port_props, long delay) {
		if (log.isLoggable(Level.FINER)) {
			String cid =
					"" + port_props.get("local-hostname") + "@" + port_props.get("remote-hostname");

			log.log(Level.FINER,
					"Reconnecting service for: {0}, scheduling next try in {1}secs, cid: {2}",
					new Object[] { getName(), delay / 1000, cid });
		}

		addTimerTask(new TimerTask() {
			@Override
			public void run() {
				int port = (Integer) port_props.get(PORT_KEY);

				if (log.isLoggable(Level.FINE)) {
					log.log(
							Level.FINE,
							"Reconnecting service for component: {0}, on port: {1}",
							new Object[] { getName(), port });
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
                
        private class ConnectionListenerImpl implements ConnectionOpenListener {

		private Map<String, Object> port_props = null;

                public ConnectionListenerImpl(Map<String,Object> port_props) {
                        this.port_props = port_props;
                }
                
                @Override
                public void accept(SocketChannel sc) {
                        IO conn = null;
                        try {
                                conn = getIOServiceInstance();
                                conn.setIOServiceListener(null);
                                conn.accept(sc);
				serviceStarted(conn);
				SocketThread.addSocketService(conn);                                
                        }
                        catch (IOException ex) {
                                log.log(Level.WARNING, "Can not accept connection.", ex);
                                
                                if (conn != null) {
//                                        try {
                                        conn.stop();
//                                                conn.close();
//                                        }
//                                        catch (IOException ex1) {
//                                                Logger.getLogger(AbstractConnectionManager.class.getName()).log(Level.SEVERE, null, ex1);
//                                        }
                                }
                        }
                }

                @Override
		public int getPort() {
			return (Integer) port_props.get(PORT_KEY);
		}

                @Override
                public String[] getIfcs() {
			return (String[]) port_props.get(PORT_IFC_PROP_KEY);
                }

                @Override
		public ConnectionType getConnectionType() {
			String type = null;

			if (port_props.get(PORT_TYPE_PROP_KEY) == null) {
				log.warning(getName() + ": connection type is null: "
						+ port_props.get(PORT_KEY).toString());
			} else {
				type = port_props.get(PORT_TYPE_PROP_KEY).toString();
			}

			return ConnectionType.valueOf(type);
		}

                @Override
                public int getReceiveBufferSize() {
                        return net_buffer;
                }

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
	private class IOServiceStatisticsGetter implements ServiceChecker<IO> {
		private StatisticsList list = new StatisticsList(Level.ALL);

		/**
		 * Method description
		 * 
		 * 
		 * @param service
		 */
		@Override
		public synchronized void check(IO service) {
			service.getStatistics(list, true);
			bytesReceived += list.getValue("socketio", "Bytes received", -1l);
			bytesSent += list.getValue("socketio", "Bytes sent", -1l);
			socketOverflow += list.getValue("socketio", "Buffers overflow", -1l);
		}
	}        
}
