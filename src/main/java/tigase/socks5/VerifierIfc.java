/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5;

import java.util.Map;
import tigase.db.TigaseDBException;

/**
 *
 * @author andrzej
 */
public interface VerifierIfc {

        void setProxyComponent(Socks5ProxyComponent proxyComponent);

        Map<String,Object> getDefaults();
        
        void setProperties(Map<String,Object> props);
        
        boolean isAllowed(Stream stream) throws TigaseDBException;
   
        void updateTransfer(Socks5IOService service, boolean force) throws TigaseDBException, QuotaException;
        
}
