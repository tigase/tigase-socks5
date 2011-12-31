/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5;

import tigase.net.IOService;

/**
 *
 * @author andrzej
 */
public interface ServiceChecker<IO extends IOService<?>> {
        
        void check(IO service);
        
}
