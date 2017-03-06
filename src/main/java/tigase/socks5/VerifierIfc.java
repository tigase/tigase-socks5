/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5;

import tigase.db.TigaseDBException;

/**
 *
 * @author andrzej
 */
public interface VerifierIfc {

        boolean isAllowed(Stream stream) throws TigaseDBException;
   
        void updateTransfer(Socks5IOService service, boolean force) throws TigaseDBException, QuotaException;
        
}
