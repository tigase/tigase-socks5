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
        
        private static final int MB = 1024*1024;
        
        // Configuration keys
        private static final String TRANSFER_UPDATE_QUANTIZATION_KEY = "transfer-update-quantization";
        private static final String DEFAULT_TRANSFER_LIMIT_PER_FILE_KEY = "default-file-limit";
        private static final String DEFAULT_TRANSFER_LIMIT_PER_USER_KEY = "default-user-limit";
        private static final String DEFAULT_TRANSFER_LIMIT_PER_DOMAIN_KEY = "default-domain-limit";
        private static final String TRANSFER_GLOBAL_LIMIT_KEY = "global-limit";
        private static final String TRANSFER_INSTANCE_LIMIT_KEY = "instance-limit";

        // Default values
        private static final int TRANSFER_UPDATE_QUANTIZATION_VAL = 1024 * 1024;
        private static final long DEFAULT_TRANSFER_LIMIT_PER_FILE_VAL = 10 * MB;
        private static final long DEFAULT_TRANSFER_LIMIT_PER_USER_VAL = 0 * MB;
        private static final long DEFAULT_TRANSFER_LIMIT_PER_DOMAIN_VAL = 0 * MB;
        private static final long TRANSFER_GLOBAL_LIMIT_VAL = 0 * MB;
        private static final long TRANSFER_INSTANCE_LIMIT_VAL = 0 * MB;
        
        // local variables
        private Socks5ProxyComponent proxyComponent;
        private int transferUpdateQuantization = TRANSFER_UPDATE_QUANTIZATION_VAL;
        
        private long defaultTransferLimitPerFile = DEFAULT_TRANSFER_LIMIT_PER_FILE_VAL;
        private long defaultTransferLimitPerUser = DEFAULT_TRANSFER_LIMIT_PER_USER_VAL;
        private long defaultTransferLimitPerDomain = DEFAULT_TRANSFER_LIMIT_PER_DOMAIN_VAL;
        private long transferGlobalLimit = TRANSFER_GLOBAL_LIMIT_VAL;
        private long transferInstanceLimit = TRANSFER_INSTANCE_LIMIT_VAL;
        
        @Override
        public void setProxyComponent(Socks5ProxyComponent proxyComponent) {
                this.proxyComponent = proxyComponent;
        }

        @Override
        public Map<String, Object> getDefaults() {
                Map<String, Object> props = new HashMap<String, Object>();
                props.put(TRANSFER_UPDATE_QUANTIZATION_KEY, TRANSFER_UPDATE_QUANTIZATION_VAL);
                
                props.put(DEFAULT_TRANSFER_LIMIT_PER_FILE_KEY, DEFAULT_TRANSFER_LIMIT_PER_FILE_VAL);
                props.put(DEFAULT_TRANSFER_LIMIT_PER_USER_KEY, DEFAULT_TRANSFER_LIMIT_PER_USER_VAL);
                props.put(DEFAULT_TRANSFER_LIMIT_PER_DOMAIN_KEY, DEFAULT_TRANSFER_LIMIT_PER_DOMAIN_VAL);
                
                props.put(TRANSFER_GLOBAL_LIMIT_KEY, TRANSFER_GLOBAL_LIMIT_VAL);
                props.put(TRANSFER_INSTANCE_LIMIT_KEY, TRANSFER_INSTANCE_LIMIT_VAL);
                return props;
        }
        
        @Override
        public void setProperties(Map<String, Object> props) {
                if (props.containsKey(TRANSFER_UPDATE_QUANTIZATION_KEY)) {
                        transferUpdateQuantization = (Integer) props.get(TRANSFER_UPDATE_QUANTIZATION_KEY);
                }                        
                
                if (props.get(DEFAULT_TRANSFER_LIMIT_PER_FILE_KEY) != null) {
                    defaultTransferLimitPerFile = (Long) props.get(DEFAULT_TRANSFER_LIMIT_PER_FILE_KEY);
                }
                
                if (props.get(DEFAULT_TRANSFER_LIMIT_PER_USER_KEY) != null) {
                    defaultTransferLimitPerUser = (Long) props.get(DEFAULT_TRANSFER_LIMIT_PER_USER_KEY);
                }
                
                if (props.get(DEFAULT_TRANSFER_LIMIT_PER_DOMAIN_KEY) != null) {
                    defaultTransferLimitPerDomain = (Long) props.get(DEFAULT_TRANSFER_LIMIT_PER_DOMAIN_KEY);
                }
                
                if (props.get(TRANSFER_GLOBAL_LIMIT_KEY) != null) {
                    transferGlobalLimit = (Long) props.get(TRANSFER_GLOBAL_LIMIT_KEY);
                }

                if (props.get(TRANSFER_INSTANCE_LIMIT_KEY) != null) {
                    transferInstanceLimit = (Long) props.get(TRANSFER_INSTANCE_LIMIT_KEY);
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
                
                if (limits.getTransferLimitPerFile() == -1 || limits.getTransferLimitPerUser() == -1 
                        || limits.getTransferLimitPerDomain() == -1) {
                        
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

                if (limits.getTransferLimitPerFile() != 0 
                                && limits.getTransferLimitPerFile() < transferred) {
                        
                        updateTransferUsedByConnection(repo, service, transferred, force);
                        if (!force) {
                                throw new QuotaException("Stream closed due to exceeded quota for single file transfer");
                        }
                }

                if (!updateTransferUsedByConnection(repo, service, transferred, force)) {
                        return;
                }
                
                if (limits.getTransferLimitPerUser() != 0
                                && limits.getTransferLimitPerUser() < repo.getTransferUsedByUser(jid)) {
                        
                        if (!force) {
                                throw new QuotaException("Stream closed due to exceeded transfer quota for user "+jid.toString());
                        }
                }
                
                if (limits.getTransferLimitPerDomain() != 0 
                                && limits.getTransferLimitPerDomain() < repo.getTransferUsedByDomain(jid.getDomain())) {
                        
                        if (!force) {
                                throw new QuotaException("Stream closed due to exceeded transfer quota for domain "+jid.getDomain());
                        }
                }
                
                if (transferInstanceLimit != 0 
                                && transferInstanceLimit < repo.getTransferUsedByInstance(proxyComponent.getDefHostName().toString())) {
                        
                        if (!force) {
                                throw new QuotaException("Stream closed due to exceeded transfer quota for instance "+proxyComponent.getDefHostName());
                        }                        
                }
                
                if (transferGlobalLimit != 0 && transferGlobalLimit < repo.getTransferUsed()) {
                        if (!force) {
                                throw new QuotaException("Stream closed due to exceeded global transfer quota");
                        }
                }
        }

        private void updateTransfer(Stream stream) throws TigaseDBException, QuotaException {
                updateTransfer(stream.getConnection(Socks5ConnectionType.Requester), false);
                updateTransfer(stream.getConnection(Socks5ConnectionType.Target), false);
        }
        /**
         * Update transferred bytes by connection
         * 
         * @param repo
         * @param service
         * @param transferred
         * @param force
         * @return true - if update was done
         * @throws TigaseDBException 
         */
        private boolean updateTransferUsedByConnection(Socks5Repository repo, Socks5IOService service, 
                long transferred, boolean force) throws TigaseDBException {
                
                Long lastTransferred = (Long) service.getSessionData().get(LAST_TRANSFERRED_BYTES_KEY);
                
                if (lastTransferred == null) {
                        lastTransferred = 0L;
                        force = true;
                }
                
                if (!force && (lastTransferred/transferUpdateQuantization == transferred/transferUpdateQuantization)) 
                        return false;
                
                Long conn_id = (Long) service.getSessionData().get(CONN_ID_KEY);
                boolean isNew = false;
                
                if (conn_id == null) {
                        conn_id = repo.createTransferUsedByConnection(service.getJID().getBareJID(), service.getSocks5ConnectionType(), 
                                proxyComponent.getDefHostName());
                        
                        service.getSessionData().put(CONN_ID_KEY, conn_id);                        
                        isNew = true;
                }
                
                if (!isNew || force) {
                        repo.updateTransferUsedByConnection(conn_id, transferred);
                }
                
                service.getSessionData().put(LAST_TRANSFERRED_BYTES_KEY, transferred);
                
                return true;
        }
        
        private Limits getLimits(BareJID jid) throws TigaseDBException {
                Socks5Repository repo = proxyComponent.getSock5Repository();
                // get limits for user
                Limits limits = repo.getTransferLimits(jid);
                
                // get limits for domain if needed
                // We need this always as per domain limit is specified for domain!
                if (limits.getTransferLimitPerFile() == 0 || limits.getTransferLimitPerUser() == 0 
                        || limits.getTransferLimitPerDomain() == 0) {
                        
                        Limits domainLimits = repo.getTransferLimits(jid.getDomain());

                        if (limits.getTransferLimitPerFile() == 0) {
                                limits.setTransferLimitPerFile(domainLimits.getTransferLimitPerFile());
                        }

                        if (limits.getTransferLimitPerUser() == 0) {
                                limits.setTransferLimitPerUser(domainLimits.getTransferLimitPerUser());
                        }

                        if (limits.getTransferLimitPerDomain() == 0) {
                                limits.setTransferLimitPerDomain(domainLimits.getTransferLimitPerDomain());
                        }
                }
                
                // get default limits if needed                        
                if (limits.getTransferLimitPerFile() == 0) {
                        limits.setTransferLimitPerFile(defaultTransferLimitPerFile);
                }

                if (limits.getTransferLimitPerUser() == 0) {
                        limits.setTransferLimitPerUser(defaultTransferLimitPerUser);
                }
                
                if (limits.getTransferLimitPerDomain() == 0) {
                        limits.setTransferLimitPerDomain(defaultTransferLimitPerDomain);
                }

                return limits;
        }
}
