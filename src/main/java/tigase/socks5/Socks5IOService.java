/*
 * Socks5IOService.java
 *
 * Tigase Jabber/XMPP Server - SOCKS5 Proxy
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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

import tigase.net.IOService;
import tigase.net.SocketThread;
import tigase.xmpp.jid.JID;

import java.io.IOException;
import java.net.ProtocolException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author andrzej
 */
public class Socks5IOService<RefObject>
		extends IOService<RefObject> {

	private static final Logger log = Logger.getLogger(Socks5IOService.class.getCanonicalName());

	/**
	 * All possible states of Socks5 connection
	 */
	public static enum State {
		Welcome,
		Auth,
		Ready,
		Active,
		Closed
	}
	protected final ReentrantLock transferInProgress = new ReentrantLock();

	;
	private ByteBuffer buf = null;
	private int bytesReceived = 0;
	private int bytesSent = 0;
	private Socks5ConnectionType connectionType;
	private Socks5ConnectionManager manager;
	private State state = State.Welcome;
	private Stream stream;

	/**
	 * Activate service
	 *
	 * @return
	 */
	public boolean activate() {
		this.state = State.Active;
		return true;
	}

	/**
	 * Set ConnectionManager
	 *
	 * @param manager
	 */
	public void setConnectionManager(Socks5ConnectionManager manager) {
		this.manager = manager;
	}

	/**
	 * Set stream assigned with this service
	 *
	 * @param stream
	 */
	public void setStream(Stream stream) {
		this.stream = stream;
	}

	/**
	 * Returns jid of client connected by this service
	 *
	 * @return
	 */
	public JID getJID() {
		if (stream == null) {
			return null;
		}

		return this.connectionType == Socks5ConnectionType.Requester ? stream.getRequester() : stream.getTarget();
	}

	/**
	 * Returns current state of service
	 *
	 * @return
	 */
	public State getState() {
		return state;
	}

	/**
	 * Returns Socks5 connectionType (Requester/Target)
	 *
	 * @return
	 */
	public Socks5ConnectionType getSocks5ConnectionType() {
		return connectionType;
	}

	/**
	 * Set Socks5 connection type (Requester or Target connection)
	 *
	 * @param connectionType
	 */
	public void setSocks5ConnectionType(Socks5ConnectionType connectionType) {
		this.connectionType = connectionType;
	}

	/**
	 * Get all bytes received by this service
	 *
	 * @return
	 */
	public int getBytesReceived() {
		return bytesReceived;
	}

	/**
	 * Get all bytes sent by this service
	 *
	 * @return
	 */
	public int getBytesSent() {
		return bytesSent;
	}

	@Override
	public void processWaitingPackets() throws IOException {
		// No packets received from sockets so no need to process
	}

	@Override
	public boolean waitingToRead() {
		// we need to read data from socket only if buffer is empty
		// to prevent OutOfMemory if we read data faster than we can
		// send it
		return super.isInputBufferEmpty();
	}

	@Override
	public IOService<?> call() throws IOException {
		IOService<?> serv = super.call();

		if (!this.waitingToSend() && stream != null) {
			// we need to add other service if we sent all data from buffer
			Socks5IOService secondServ = stream.getSecondConnection(this);
			if (secondServ != null) {
//                                        secondServ.clearBuffer();
				SocketThread.addSocketService(secondServ);
			}
		}

		return serv;
	}

	@Override
	public void writeBytes(ByteBuffer buf) {
		if (buf != null) {
			bytesSent += buf.remaining();
		}

		transferInProgress.lock();
//                int remaining = waitingToSendSize() + buf.remaining();
		super.writeBytes(buf);
		//buf.compact();
//                log.log(Level.FINEST, "{0} written data: {1}, remaining: {2}", new Object[] { this, remaining - waitingToSendSize(), waitingToSendSize() });
		transferInProgress.unlock();
	}

	@Override
	public void forceStop() {
		if (state != State.Closed) {
			//Stream stream = this.stream;
			//this.stream = nul;
			state = State.Closed;
			if (stream != null) {
				stream.close();
			}
		}

//                try {
//                        log.log(Level.FINEST, "stopping connection " + this + " waiting data: " + waitingToSendSize());
//                        throw new IOException();
//                }
//                catch (Exception ex) {
//                        log.log(Level.WARNING, "closing stack trace", ex);
//                }

		super.forceStop();
	}

	/**
	 * Custom hashCode for better distribution Sender and recipient thread should be processed on same thread
	 *
	 * @return
	 */
	@Override
	public int hashCode() {
		return Math.abs(stream != null ? stream.hashCodeForStream() : super.hashCode());
	}

//        public void clearBuffer() {
//                // should not be needed any more
//                if (buf != null && !buf.hasRemaining()) {
//                        log.log(Level.FINEST, "{0} would clear buffer!", new Object[] { this });
////                        buf.clear();
//                }
//        }

	@Override
	protected ByteBuffer readBytes() throws IOException {
		ByteBuffer buf = super.readBytes();

		if (buf != null) {
			this.buf = buf;
			bytesReceived += buf.remaining();
		}

		return buf;
	}

	@Override
	protected void writeData(String data) {
		transferInProgress.lock();
//                int remaining = waitingToSendSize();
		Socks5IOService secondServ = (stream != null) ? stream.getSecondConnection(this) : null;
		if (secondServ != null && secondServ.buf.remaining() != secondServ.buf.limit()) {
			secondServ.buf.flip();
			super.writeData(data);
			secondServ.buf.compact();
		} else {
			super.writeData(data);
		}
//                log.log(Level.FINEST, "{0} written data: {1}, remaining: {2}", new Object[] { this, remaining - waitingToSendSize(), waitingToSendSize() });
		transferInProgress.unlock();
	}

	/**
	 * Handles data from socket
	 *
	 * @throws IOException
	 */
	@Override
	protected void processSocketData() throws IOException {
		// Ignore data on socket until it is in Active state - fixes
		// problems with discovering proxy by Gajim.
		if (state == State.Ready) {
			return;
		}

		// handle occasional exceptions when socket is already closed,
		// for example when sender closes connection after sending all
		// data, while we still need to send data to recipient
		if (!isConnected()) {
			super.forceStop();
			return;
		}

		Socks5IOService secondServ = stream != null ? stream.getSecondConnection(this) : null;
		if (secondServ != null) {
			secondServ.transferInProgress.lock();
		}

		ByteBuffer buffer = readBytes();
		if (buffer != null) {
			buffer = buf;
		}

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "{0} read data: {1}",
					new Object[]{this, ((buffer == null) ? "NULL" : buffer.remaining())});
		}

		if (buffer != null && buffer.hasRemaining()) {
			// if we are not in Active state we need to handle
			// using Socks5 protocol
			if (state != State.Active) {
				ByteBuffer buf = ByteBuffer.allocate(buffer.remaining());
				buf.put(buffer);
				buf.flip();

				try {
					switch (state) {
						case Welcome:
							handleWelcome(buf);
							break;
						case Auth:
							handleCommand(buf);
							break;

						default:
							break;
					}

					buffer.clear();
				} catch (BufferUnderflowException ex) {
					buffer.compact();
				}
				return;
			}

			// we are connected so write data to recipient connection
			if (stream != null) {
				try {
					// we do not need to copy data to another buffer
					// as it will not be used by reading thread
//                                        ByteBuffer buf = ByteBuffer.allocate(buffer.remaining());
//                                        buf.put(buffer);
//                                        buf.flip();
					stream.proxy(buffer, this);
//                                        buffer.clear();
//                                        if (!buffer.hasRemaining()) {
//                                                buffer.clear();
//                                        }
				} catch (IOException ex) {
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "stopping service after exception " + ex.getMessage(), ex);
					}
					forceStop();
				}
			}

		}

		if (secondServ != null) {
			secondServ.transferInProgress.unlock();
		}

		manager.socketDataProcessed(this);
//                else if (bytesRead == -1) {
//                        if (stream != null) {
//                                stream.close();
//                        }
//                        else {
//                                forceStop();
//                        }
//                }

		//buffer.compact();
//                buffer.clear();

		// Should not be needed until it is possible to write all data to output buffer
//                if (stream != null && bytesRead != -1) {
//                        Socks5IOService con = stream.getSecondConnection(this);
//                        if (con != null) {
//                                ByteBuffer obuf = con.getBuffer();
//                                obuf.flip();
//                                if (obuf.hasRemaining()) {
//                                        writeBytes(obuf);                                                
//                                }
//                                obuf.compact();
//                        }
//                        else {
//                                if (stream != null) {
//                                        stream.close();
//                                }
//                                else {
//                                        forceStop();
//                                }
//                        }
//                }
	}

	@Override
	protected int receivedPackets() {
		// return 0 as we do not receive packet to process
		return 0;
	}

	/**
	 * Handle Socks5 protocol WELCOME
	 *
	 * @param buf
	 *
	 * @throws IOException
	 */
	private void handleWelcome(ByteBuffer buf) throws IOException {
		byte ver = buf.get();
		if (ver != 0x05) {
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "stopping service {0} after detecting unsupported protocol", toString());
			}
			forceStop();
			return;
		}

		int count = (int) buf.get();
		boolean ok = false;
		for (int i = 0; i < count; i++) {
			if (buf.get() == 0x00) {
				ok = true;
				break;
			}
		}

		buf.clear();
		state = State.Auth;

		if (ok) {
			writeBytes(ByteBuffer.wrap(new byte[]{ver, 0x00}));
		} else {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "stopping service {0} after failure during WELCOME step", toString());
			}
			forceStop();
		}
	}

	/**
	 * Handle Socks5 protocol command
	 *
	 * @param buf
	 *
	 * @throws IOException
	 */
	private void handleCommand(ByteBuffer in) throws IOException {
		byte ver = in.get();
		if (ver != 0x05) {
			throw new ProtocolException("Bad protocol version");
		}

		byte cmd = in.get();
		in.get();
		byte atype = in.get();
		if (ver == 0x05 && cmd == 0x01 && atype == 0x03) {
			byte len = in.get();
			byte[] data = new byte[len];
			in.get(data);
			in.clear();
			ByteBuffer tmp = ByteBuffer.allocate(len + 7);
			tmp.put(ver);
			tmp.put((byte) 0x00);
			tmp.put((byte) 0x00);
			tmp.put(atype);
			tmp.put(len);
			tmp.put(data);
			tmp.put((byte) 0x00);
			tmp.put((byte) 0x00);
			tmp.flip();

			manager.registerStream(new String(data), this);
			state = State.Ready;

			writeBytes(tmp);
		} else {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST,
						"stopping service {0} after failure during AUTHENTICATION step, version = {1}, cmd = {2}, atype = {3}",
						new Object[]{toString(), ver, cmd, atype});
			}
			forceStop();
		}

	}
}
