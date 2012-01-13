/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5.verifiers;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import tigase.db.TigaseDBException;
import tigase.socks5.Limits;
import tigase.socks5.QuotaException;
import tigase.socks5.Socks5ConnectionType;
import tigase.socks5.Socks5IOService;
import tigase.socks5.Socks5ProxyComponent;
import tigase.socks5.Stream;
import tigase.socks5.VerifierIfc;
import tigase.socks5.repository.Socks5Repository;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 *
 * @author andrzej
 */
public class LimitsVerifier implements VerifierIfc {

        private static final Logger log = Logger.getLogger(LimitsVerifier.class.getCanonicalName());
        
        private static final String CONN_ID_KEY = "conn-id-key";
        private static final String LAST_TRANSFERRED_BYTES_KEY = "last-transferred-bytes";
        private static final String TRANSFER_UPDATE_QUANTIZATION_KEY = "transfer-update-quantization";
        private static final int TRANSFER_UPDATE_QUANTIZATION_VAL = 1024 * 1024;
        
        private Socks5ProxyComponent proxyComponent;
        private int transferUpdateQuantization = TRANSFER_UPDATE_QUANTIZATION_VAL;
        
        @Override
        public void setProxyComponent(Socks5ProxyComponent proxyComponent) {
                this.proxyComponent = proxyComponent;
        }

        @Override
        public Map<String, Object> getDefaults() {
                Map<String, Object> props = new HashMap<String, Object>();
                props.put(TRANSFER_UPDATE_QUANTIZATION_KEY, TRANSFER_UPDATE_QUANTIZATION_VAL);
                
                return props;
        }
        
        @Override
        public void setProperties(Map<String, Object> props) {
                if (props.containsKey(TRANSFER_UPDATE_QUANTIZATION_KEY)) {
                        transferUpdateQuantization = (Integer) props.get(TRANSFER_UPDATE_QUANTIZATION_KEY);
                }                        
        }
        
        @Override
        public boolean isAllowed(Stream stream) throws TigaseDBException {
                if (!proxyComponent.isLocalDomain(stream.getRequester().getDomain()) 
                     && !proxyComponent.isLocalDomain(stream.getTarget().getDomain())) {
                        //@todo Throw exception?? 
                        return false;
                }

                try {
                        updateTransfer(stream);
                        return true;
                }
                catch (QuotaException ex) {
                        return false;
                }
        }

        @Override
        public void updateTransfer(Socks5IOService service, boolean force) throws TigaseDBException, QuotaException {
                JID fullJID = service.getJID();
                if (fullJID == null) {
                        // someone closed connection without authentication done, so no JID
                        return;
                }
                
                BareJID jid = service.getJID().getBareJID();
                String key = "limits-"+jid.toString();
                Limits limits = (Limits) service.getSessionData().get(key);
                //Limits limits = (Limits) stream.getData(key);
                
                if (limits == null) {
                        limits = getLimits(jid);
                        service.getSessionData().put(key, limits);
                        //stream.setData(key, jid);
                }
                
                if (limits.getFilesizeLimit() == -1 || limits.getTransferLimit() == -1) {
                        if (!force) {
                                throw new QuotaException("Transfer denied");
                        }
                }
                
                long transferred = service.getBytesReceived() + service.getBytesSent();        
                if (log.isLoggable(Level.FINEST)) {
                        log.log(Level.FINEST, "updating service " + service.getUniqueId() 
                                + " transfer data received = " + service.getBytesReceived()
                                + " sent = " + service.getBytesSent() + " transferred = " + transferred);
                }
                Socks5Repository repo = proxyComponent.getSock5Repository();

                if (limits.getFilesizeLimit() < transferred) {
                        updateTransferUsedByConnection(repo, service, transferred, force);
                        if (!force) {
                                throw new QuotaException("Stream closed due to exceeded quota for single file transfer");
                        }
                }

                updateTransferUsedByConnection(repo, service, transferred, force);
                
                if (limits.getTransferLimit() < repo.getTransferUsed(jid)) {
                        if (!force) {
                                throw new QuotaException("Stream closed due to exceeded quota for transfer");
                        }
                }
        }

        private void updateTransfer(Stream stream) throws TigaseDBException, QuotaException {
                updateTransfer(stream.getConnection(Socks5ConnectionType.Requester), false);
                updateTransfer(stream.getConnection(Socks5ConnectionType.Target), false);
        }
        
        private void updateTransferUsedByConnection(Socks5Repository repo, Socks5IOService service, 
                long transferred, boolean force) throws TigaseDBException {
                
                Long lastTransferred = (Long) service.getSessionData().get(LAST_TRANSFERRED_BYTES_KEY);
                
                if (lastTransferred == null) {
                        lastTransferred = 0L;
                }
                
                if (!force && (lastTransferred/transferUpdateQuantization == transferred/transferUpdateQuantization)) 
                        return;
                
                Long conn_id = (Long) service.getSessionData().get(CONN_ID_KEY);
                boolean isNew = false;
                
                if (conn_id == null) {
                        conn_id = repo.createTransferUsedByConnection(service.getJID().getBareJID(), service.getSocks5ConnectionType());
                        service.getSessionData().put(CONN_ID_KEY, conn_id);                        
                        isNew = true;
                }
                
                if (!isNew || force) {
                        repo.updateTransferUsedByConnection(conn_id, transferred);
                }
                
                service.getSessionData().put(LAST_TRANSFERRED_BYTES_KEY, transferred);
        }
        
        private Limits getLimits(BareJID jid) throws TigaseDBException {
                Socks5Repository repo = proxyComponent.getSock5Repository();
                Limits limits = repo.getTransferLimits(jid);
                
                if (limits.getFilesizeLimit() == 0 || limits.getTransferLimit() == 0) {
                        Limits domainLimits = repo.getTransferLimits(jid.getDomain());
                        
                        if (limits.getFilesizeLimit() == 0) {
                                limits.setFilesizeLimit(domainLimits.getFilesizeLimit());
                        }
                        
                        if (limits.getTransferLimit() == 0) {
                                limits.setTransferLimit(domainLimits.getTransferLimit());
                        }
                }
                
                if (limits.getFilesizeLimit() == 0 || limits.getTransferLimit() == 0) {
                        Limits globalLimits = repo.getTransferLimits();
                        
                        if (limits.getFilesizeLimit() == 0) {
                                limits.setFilesizeLimit(globalLimits.getFilesizeLimit());
                        }
                        
                        if (limits.getTransferLimit() == 0) {
                                limits.setTransferLimit(globalLimits.getTransferLimit());
                        }
                }
                
                return limits;
        }
}
