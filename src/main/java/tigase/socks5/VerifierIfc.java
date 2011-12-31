/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5;

/**
 *
 * @author andrzej
 */
public interface VerifierIfc {

        public boolean isAllowed(Stream stream);
   
        public boolean checkLimit(Stream stream);
        
}
