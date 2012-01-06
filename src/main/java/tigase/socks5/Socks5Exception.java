/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5;

/**
 *
 * @author andrzej
 */
public class Socks5Exception extends Exception {

        public Socks5Exception(String message) {
                super(message);
        }

        public Socks5Exception(String message, Throwable cause) {
                super(message, cause);
        }
 
}
