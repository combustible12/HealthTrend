import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.HexFormat;

/** Verifies CI signing inputs without exposing private key material or passwords. */
public class VerifySigning {
 public static void main(String[] args) throws Exception {
  Path path=Path.of(System.getenv("HEALTHTREND_KEYSTORE_PATH"));
  System.out.println("Keystore SHA-256: "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
  KeyStore store=KeyStore.getInstance("JKS");
  try(InputStream input=Files.newInputStream(path)) {
   store.load(input,System.getenv("HEALTHTREND_STORE_PASSWORD").toCharArray());
  }
  System.out.println("Keystore integrity verified");
  String alias=System.getenv("HEALTHTREND_KEY_ALIAS");
  Key key=store.getKey(alias,System.getenv("HEALTHTREND_KEY_PASSWORD").toCharArray());
  if(!(key instanceof PrivateKey)) throw new IllegalStateException("Configured entry has no private key");
  var certificate=store.getCertificate(alias);
  Signature signer=Signature.getInstance("SHA256withRSA");
  byte[] message="HealthTrend persistent signing validation".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  signer.initSign((PrivateKey)key);signer.update(message);
  byte[] proof=signer.sign();
  signer.initVerify(certificate.getPublicKey());signer.update(message);
  if(!signer.verify(proof)) throw new IllegalStateException("Signing key does not match certificate");
  System.out.println("Private key recovered and signature verified");
  System.out.println("Certificate SHA-256: "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())));
 }
}
