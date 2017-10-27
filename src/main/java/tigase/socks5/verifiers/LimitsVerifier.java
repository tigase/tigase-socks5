/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5.verifiers;

import tigase.db.TigaseDBException;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.socks5.*;
import tigase.socks5.repository.Socks5Repository;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author andrzej
 */
public class LimitsVerifier implements VerifierIfc {

        private static final Logger log = Logger.getLogger(LimitsVerifier.class.getCanonicalName());
        
        private static final String CONN_ID_KEY = "conn-id-key";
        private static final String LAST_TRANSFERRED_BYTES_KEY = "last-transferred-bytes";
        
        private static final int MB = 1024*1024;
        
        // Default values
        private static final int TRANSFER_UPDATE_QUANTIZATION_VAL = 1024 * 1024;
        private static final long DEFAULT_TRANSFER_LIMIT_PER_FILE_VAL = 10 * MB;
        private static final long DEFAULT_TRANSFER_LIMIT_PER_USER_VAL = 0 * MB;
        private static final long DEFAULT_TRANSFER_LIMIT_PER_DOMAIN_VAL = 0 * MB;
        private static final long TRANSFER_GLOBAL_LIMIT_VAL = 0 * MB;
        private static final long TRANSFER_INSTANCE_LIMIT_VAL = 0 * MB;
        
        // local variables
        @Inject
        private Socks5ProxyComponent proxyComponent;
        @ConfigField(desc = "Quantization", alias = "transfer-update-quantization")
        private int transferUpdateQuantization = TRANSFER_UPDATE_QUANTIZATION_VAL;
        
        @ConfigField(desc = "Transfer limit per file", alias = "default-file-limit")
        private long defaultTransferLimitPerFile = DEFAULT_TRANSFER_LIMIT_PER_FILE_VAL;
        @ConfigField(desc = "Transfer limit per user", alias = "default-user-limit")
        private long defaultTransferLimitPerUser = DEFAULT_TRANSFER_LIMIT_PER_USER_VAL;
        @ConfigField(desc = "Transfer limit per domain", alias = "default-domain-limit")
        private long defaultTransferLimitPerDomain = DEFAULT_TRANSFER_LIMIT_PER_DOMAIN_VAL;
        @ConfigField(desc = "Global transfer limit", alias = "global-limit")
        private long transferGlobalLimit = TRANSFER_GLOBAL_LIMIT_VAL;
        @ConfigField(desc = "Instance transfer limit", alias = "instance-limit")
        private long transferInstanceLimit = TRANSFER_INSTANCE_LIMIT_VAL;
        
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
                if (service == null) {
                        // should not happend!
                        return;
                }
                
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
                        repo.updateTransferUsedByConnection(service.getJID().getBareJID(), conn_id, transferred);
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
