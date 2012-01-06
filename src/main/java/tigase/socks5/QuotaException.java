/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5;

/**
 *
 * @author andrzej
 */
public class QuotaException extends Socks5Exception {

        public QuotaException(String message) {
                super(message);
        }

        public QuotaException(String message, Throwable cause) {
                super(message, cause);
        }

}
