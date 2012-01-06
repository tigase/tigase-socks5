package tigase.socks5;

import java.io.IOException;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.stats.StatisticsList;

public abstract class Socks5ConnectionManager extends AbstractConnectionManager<Socks5IOService<?>> {

        private static final Logger log = Logger.getLogger(Socks5ConnectionManager.class.getCanonicalName());
        
        private static final long STREAM_CREATION_TIMEOUT = TimeUnit.MINUTES.toMillis(2);

	private ConcurrentHashMap<String,Stream> streams = new ConcurrentHashMap<String,Stream>();
        
        @Override
        public void serviceStarted(Socks5IOService<?> serv) {
                super.serviceStarted(serv);

                serv.setConnectionManager(this);
                
                addTimerTask(new AuthenticationTimer(serv.getUniqueId()), STREAM_CREATION_TIMEOUT);
        }
                
        /**
         * Register stream by creating it and assigning connection to it
         * 
         * @param sid
         * @param con 
         */
        public void registerStream(String sid, Socks5IOService con) {
		Stream stream = streams.get(sid);
		if(stream == null) {
			stream = new Stream(sid, this);
			streams.put(sid, stream);
		}
                
                if (log.isLoggable(Level.FINER)) {
                        log.log(Level.FINER, "registered connection = {0} for stream = {1}", new Object[] { con.toString(), stream.toString() });
                }
		stream.addConnection(con);
	}
	
        /**
         * Unregister stream
         * 
         * @param stream 
         */
	public void unregisterStream(Stream stream) {
		streams.remove(stream.getSID());
                
                if (log.isLoggable(Level.FINER)) {
                        log.log(Level.FINER, "unregistered connections for stream = {0}", new Object[] { stream.toString() });
                }
	}

        /**
         * Process stream after each time data from socket is processed
         * 
         * @param stream 
         */
        public void socketDataProcessed(Socks5IOService service) {
                
        }

	/**
	 * Generates the component statistics.
	 * 
	 * @param list
	 *          is a collection to put the component statistics in.
	 */
	@Override
	public void getStatistics(StatisticsList list) {
		super.getStatistics(list);
                
		list.add(getName(), "Open streams", streams.size(), Level.INFO);                
	}        
        
        /**
         * Get stream with specified id from map of registred streams
         * 
         * @param cid
         * @return 
         */
        public Stream getStream(String cid) {
                return streams.get(cid);
        }
        
        /**
         * Check if there is registered stream with specified id
         * 
         * @param cid
         * @return 
         */
        public boolean hasStream(String cid) {
                return streams.contains(cid);
        }
        
        @Override
        protected boolean isHighThroughput() {
                return true;
        }
        
        @Override
        protected Socks5IOService getIOServiceInstance() throws IOException {
                return new Socks5IOService();
        }

        @Override
        public void packetsReady(Socks5IOService service) throws IOException {
                // Not used
                throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void tlsHandshakeCompleted(Socks5IOService service) {
                // Not used
                throw new UnsupportedOperationException("Not supported yet.");
        }
        
        /**
         * Timer task used to close connection if it is not activated within 
         * specified time period
         */
        private class AuthenticationTimer extends TimerTask {

                private final String connId;
                
                public AuthenticationTimer(String connId) {
                        this.connId = connId;
                }
                
                @Override
                public void run() {
                        Socks5IOService serv = services.get(connId);
                        if (serv == null) {
                                return;
                        }
                        
                        if (serv.getState() != Socks5IOService.State.Active) {                                
                                if (log.isLoggable(Level.FINER)) {
                                        log.log(Level.FINER, "closing Socks5 connection not in active state with id  = {0}", serv.getUniqueId());
                                }
                                
                                services.remove(serv.getUniqueId());
                                
                                if (serv.isConnected()) {
                                        serv.stop();
                                }
                        }
                }
                
        }
}
