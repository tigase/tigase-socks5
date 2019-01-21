/**
 * Tigase Socks5 Component - SOCKS5 proxy component for Tigase
 * Copyright (C) 2011 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */


package tigase.socks5;

//~--- non-JDK imports --------------------------------------------------------

import tigase.stats.StatisticsList;

//~--- JDK imports ------------------------------------------------------------

import java.io.IOException;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.TimerTask;

/**
 * Class description
 *
 *
 * @version        5.2.0, 13/10/15
 * @author         <a href="mailto:andrzej.wojcik@tigase.org">Andrzej Wójcik</a>
 */
public abstract class Socks5ConnectionManager
				extends AbstractConnectionManager<Socks5IOService<?>> {
	private static final Logger log = Logger.getLogger(Socks5ConnectionManager.class
			.getCanonicalName());
	private static final long STREAM_CREATION_TIMEOUT = TimeUnit.MINUTES.toMillis(2);

	//~--- fields ---------------------------------------------------------------

	private ConcurrentHashMap<String, Stream> streams = new ConcurrentHashMap<String,
			Stream>();
	private AtomicLong servicesCompleted = new AtomicLong(0);
	private AtomicLong kbytesTransferred = new AtomicLong(0);

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param service is a <code>Socks5IOService</code>
	 *
	 * @throws IOException
	 */
	@Override
	public void packetsReady(Socks5IOService service) throws IOException {

		// Not used
		throw new UnsupportedOperationException("Not supported yet.");
	}

	/**
	 * Register stream by creating it and assigning connection to it
	 *
	 * @param sid
	 * @param con
	 */
	public void registerStream(String sid, Socks5IOService con) {
		Stream stream = streams.get(sid);

		if (stream == null) {
			stream = new Stream(sid, this);
			streams.put(sid, stream);
		}
		if (log.isLoggable(Level.FINER)) {
			log.log(Level.FINER, "registered connection = {0} for stream = {1}", new Object[] {
					con.toString(),
					stream.toString() });
		}
		stream.addConnection(con);
	}

	/**
	 * Method description
	 *
	 *
	 * @param serv is a <code>Socks5IOService<?></code>
	 */
	@Override
	public void serviceStarted(Socks5IOService<?> serv) {
		super.serviceStarted(serv);
		serv.setConnectionManager(this);
		addTimerTask(new AuthenticationTimer(serv.getUniqueId()), STREAM_CREATION_TIMEOUT);
	}

	/**
	 * Method description
	 *
	 *
	 * @param serv is a <code>Socks5IOService<?></code>
	 *
	 * @return a value of <code>boolean</code>
	 */
	@Override
	public boolean serviceStopped(Socks5IOService<?> serv) {
		long bytesTransferred = serv.getBytesReceived() + serv.getBytesSent();

		this.kbytesTransferred.addAndGet(bytesTransferred / 1024);
		this.servicesCompleted.incrementAndGet();

		return super.serviceStopped(serv);
	}

	/**
	 * Process stream after each time data from socket is processed
	 *
	 *
	 * @param service is a <code>Socks5IOService</code>
	 */
	public void socketDataProcessed(Socks5IOService service) {}

	/**
	 * Method description
	 *
	 *
	 * @param service is a <code>Socks5IOService</code>
	 */
	@Override
	public void tlsHandshakeCompleted(Socks5IOService service) {

		// Not used
		throw new UnsupportedOperationException("Not supported yet.");
	}

	/**
	 * Unregister stream
	 *
	 * @param stream
	 */
	public void unregisterStream(Stream stream) {
		streams.remove(stream.getSID());
		if (log.isLoggable(Level.FINER)) {
			log.log(Level.FINER, "unregistered connections for stream = {0}", new Object[] {
					stream.toString() });
		}
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Generates the component statistics.
	 *
	 * @param list
	 *          is a collection to put the component statistics in.
	 */
	@Override
	public void getStatistics(StatisticsList list) {
		super.getStatistics(list);

		long servicesCompleted = this.servicesCompleted.get();
		long kbytesTransferred = this.kbytesTransferred.get();

		list.add(getName(), "Open streams", streams.size(), Level.INFO);
		list.add(getName(), "KBytes tranferred", kbytesTransferred, Level.INFO);
		list.add(getName(), "Transfers completed", servicesCompleted / 2, Level.INFO);
		if (servicesCompleted == 0) {
			servicesCompleted = 1;
		}
		list.add(getName(), "Average transfer size in KB", kbytesTransferred /
				servicesCompleted, Level.INFO);
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

	/**
	 * Method description
	 *
	 *
	 * @return a value of <code>Socks5IOService</code>
	 *
	 * @throws IOException
	 */
	@Override
	protected Socks5IOService getIOServiceInstance() throws IOException {
		return new Socks5IOService();
	}

	/**
	 * Method description
	 *
	 *
	 * @return a value of <code>boolean</code>
	 */
	@Override
	protected boolean isHighThroughput() {
		return true;
	}

	//~--- inner classes --------------------------------------------------------

	/**
	 * Timer task used to close connection if it is not activated within
	 * specified time period
	 */
	private class AuthenticationTimer
					extends TimerTask {
		private final String connId;

		//~--- constructors -------------------------------------------------------

		/**
		 * Constructs ...
		 *
		 *
		 * @param connId
		 */
		public AuthenticationTimer(String connId) {
			this.connId = connId;
		}

		//~--- methods ------------------------------------------------------------

		/**
		 * Method description
		 *
		 */
		@Override
		public void run() {
			Socks5IOService serv = services.get(connId);

			if (serv == null) {
				return;
			}
			try {
				if (serv.getState() != Socks5IOService.State.Active) {
					if (log.isLoggable(Level.FINER)) {
						log.log(Level.FINER,
								"closing Socks5 connection not in active state with id  = {0}", serv
								.getUniqueId());
					}
					services.remove(serv.getUniqueId());
					if (serv.isConnected()) {
						serv.stop();
					}
				}
			} catch (Exception ex) {
				log.log(Level.FINE, "exception while checking for if service " + serv
						.getUniqueId() + " timed out before activation", ex);
			}
		}
	}
}


//~ Formatted in Tigase Code Convention on 13/10/15
