package tigase.socks5;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.net.IOService;

/**
 * 
 * @author andrzej
 */
public class Socks5IOService<IO> extends IOService<IO> {

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
        };
        
        private State state = State.Welcome;
        private Stream stream;
        private Socks5ConnectionManager manager;
        private int bytesReceived = 0;
        private int bytesSent = 0;

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
         * Returns current state of service
         * 
         * @return 
         */
        public State getState() {
                return state;
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
        protected ByteBuffer readBytes() throws IOException {
                ByteBuffer buf = super.readBytes();

                if (buf != null) {
                        bytesReceived += buf.remaining();
                }

                return buf;
        }

        @Override
        public void writeBytes(ByteBuffer buf) {
                if (buf != null) {
                        bytesSent += buf.remaining();
                }

                super.writeBytes(buf);
        }

        @Override
        public void forceStop() {
                if (stream != null) {
                        Stream stream = this.stream;
                        this.stream = null;
                        stream.close();
                }

//                try {
//                        throw new IOException();
//                }
//                catch (Exception ex) {
//                        log.log(Level.WARNING, "closing stack trace", ex);
//                }

                super.forceStop();
        }

        /**
         * Custom hashCode for better distribution
         * Sender and recipient thread should be processed on same thread
         * 
         * @return 
         */
        @Override
        public int hashCode() {
                return Math.abs(stream != null ? stream.hashCodeForStream() : super.hashCode());
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

                ByteBuffer buffer = readBytes();

                if (buffer != null && buffer.hasRemaining()) {
                        // if we are not in Active state we need to handle
                        // using Socks5 protocol
                        if (state != State.Active) {
                                switch (state) {
                                        case Welcome:
                                                handleWelcome(buffer);
                                                break;
                                        case Auth:
                                                handleCommand(buffer);
                                                break;

                                        default:
                                                break;
                                }
                                buffer.clear();
                                //buf.compact();
                                return;
                        }

                        // we are connected so write data to recipient connection
                        if (stream != null) {
                                try {
                                        ByteBuffer buf = ByteBuffer.allocate(buffer.remaining());
                                        buf.put(buffer);
                                        buf.flip();
                                        stream.proxy(buf, this);
//                                        buffer.clear();
                                }
                                catch (IOException ex) {
                                        if (log.isLoggable(Level.FINEST)) {
                                                log.log(Level.FINEST, "stopping service after exception " + ex.getMessage(), ex);
                                        }
                                        forceStop();
                                }
                        }

                        buffer.clear();
                }

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
         * @throws IOException 
         */
        private void handleWelcome(ByteBuffer buf) throws IOException {
                byte ver = buf.get();
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
                }
                else {
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
         * @throws IOException 
         */
        private void handleCommand(ByteBuffer in) throws IOException {
                byte ver = in.get();
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
                }
                else {
                        if (log.isLoggable(Level.FINEST)) {
                                log.log(Level.FINEST, "stopping service {0} after failure during AUTHENTICATION step, version = {1}, cmd = {2}, atype = {3}", new Object[]{toString(), ver, cmd, atype});
                        }
                        forceStop();
                }

        }
}
