/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5.verifiers;

import java.util.HashMap;
import java.util.Map;
import tigase.socks5.Socks5IOService;
import tigase.socks5.Socks5ProxyComponent;
import tigase.socks5.Stream;
import tigase.socks5.VerifierIfc;

/**
 *
 * @author andrzej
 */
public class DummyVerifier implements VerifierIfc {

        @Override
        public boolean isAllowed(Stream stream) {
                return true;
        }

        @Override
        public void updateTransfer(Socks5IOService service, boolean force) {
        }

        @Override
        public void setProxyComponent(Socks5ProxyComponent proxyComponent) {
                
        }

        @Override
        public Map<String, Object> getDefaults() {
                return new HashMap<String, Object>();
        }

        @Override
        public void setProperties(Map<String, Object> props) {
                
        }
        
}
