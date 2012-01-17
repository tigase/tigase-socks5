package tigase.socks5.repository;

import java.util.Map;
import tigase.db.TigaseDBException;
import tigase.socks5.Limits;
import tigase.socks5.Socks5ConnectionType;
import tigase.xmpp.BareJID;

/**
 *
 * @author andrzej
 */
public interface Socks5Repository {

        void initRepository(String connectionString, Map<String,String> params) throws TigaseDBException;
        
        Limits getTransferLimits() throws TigaseDBException;
        Limits getTransferLimits(String domain) throws TigaseDBException;
        Limits getTransferLimits(BareJID user_id) throws TigaseDBException;

        long getTransferUsed() throws TigaseDBException;
        long getTransferUsedByInstance(String instance) throws TigaseDBException;
        long getTransferUsedByDomain(String domain) throws TigaseDBException;
        long getTransferUsedByUser(BareJID user_id) throws TigaseDBException;
        
        long createTransferUsedByConnection(BareJID user_id, Socks5ConnectionType type, BareJID instance) throws TigaseDBException;
        void updateTransferUsedByConnection(long stream_id, long transferred_bytes) throws TigaseDBException;
}
