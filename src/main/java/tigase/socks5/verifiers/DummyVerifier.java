/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5.verifiers;

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
        public boolean checkLimit(Stream stream) {
                return true;
        }
        
}
