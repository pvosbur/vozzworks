/*
============================================================================================

                                Copyright(c) 2000 - 2006 by

                       V o z z W a r e   L L C (Vw)

                                   All Rights Reserved

THIS PROGRAM IS PROVIDED UNDER THE TERMS OF THE Vozzware PUBLIC LICENSE VER 1.0 (�AGREEMENT�),
PROVIDED WITH THIS PROGRAM. ANY USE, REPRODUCTION OR DISTRIBUTION OF THE PROGRAM
CONSTITUTES RECEIPIENTS ACCEPTANCE OF THIS AGREEMENT.

Source Name: VwEncryptionUtil.java

============================================================================================
*/

package com.vozzware.util;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Random;

public class VwEncryptionUtil
{

  public static final String DESEDE_ENCRYPTION_SCHEME = "DESede";
  public static final String DES_ENCRYPTION_SCHEME = "DES";
  public static final String DEFAULT_ENCRYPTION_SCHEME = "DES";
  public static final String DEFAULT_ENCRYPTION_KEY  = "This is a fairly long phrase used to encrypt";

  private KeySpec m_keySpec;
  private SecretKeyFactory m_keyFactory;
  private Cipher m_cipher;

  private static final String UNICODE_FORMAT      = "UTF8";

  public VwEncryptionUtil() throws EncryptionException
  {
    this( DEFAULT_ENCRYPTION_SCHEME, DEFAULT_ENCRYPTION_KEY );
  }

  
  public VwEncryptionUtil( String encryptionScheme ) throws EncryptionException
  {
    this( encryptionScheme, DEFAULT_ENCRYPTION_KEY );
  }


  public VwEncryptionUtil( String encryptionScheme, String encryptionKey )
      throws EncryptionException
  {

    if ( encryptionKey == null )
        throw new IllegalArgumentException( "encryption key was null" );
    if ( encryptionKey.trim().length() < 24 )
        throw new IllegalArgumentException(
            "encryption key was less than 24 characters" );

    try
    {
      byte[] keyAsBytes = encryptionKey.getBytes( UNICODE_FORMAT );

      if ( encryptionScheme.equals( DESEDE_ENCRYPTION_SCHEME) )
      {
        m_keySpec = new DESedeKeySpec( keyAsBytes );
      }
      else if ( encryptionScheme.equals( DES_ENCRYPTION_SCHEME ) )
      {
        m_keySpec = new DESKeySpec( keyAsBytes );
      }
      else
      {
        throw new IllegalArgumentException( "Encryption scheme not supported: "
                          + encryptionScheme );
      }

      m_keyFactory = SecretKeyFactory.getInstance( encryptionScheme );
      m_cipher = Cipher.getInstance( encryptionScheme );

    }
    catch (InvalidKeyException e)
    {
      throw new EncryptionException( e );
    }
    catch (UnsupportedEncodingException e)
    {
      throw new EncryptionException( e );
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new EncryptionException( e );
    }
    catch (NoSuchPaddingException e)
    {
      throw new EncryptionException( e );
    }

  } // end VwEncryptionUtil()

  /**
   * Generate an Argon2 digest string
   *
   * @param strPassword The raw string password/data to generate the digest from
   *
   * @return The Argon2 digest
   */
  public static String genArgon2Digest( String strPassword )
  {
    Argon2PasswordEncoder encoder = new Argon2PasswordEncoder( 32, 64, 1, 15 * 1024, 2);
    return encoder.encode( strPassword );

  } // end genArgon2Digest()

  /**
   * Validates a match between 2 strings digests
   *
   * @param strPassword The raw string password
   * @param strDigest The argon2 disgets to test against
   *
   * @return true if the password matches the digest, false otherwise
   */
  public static boolean validateArgon2Digest( String strPassword, String strDigest )
  {
    Argon2PasswordEncoder encoder = new Argon2PasswordEncoder( 32, 64, 1, 15 * 1024, 2);
    return encoder.matches(strPassword, strDigest );

  } // end validateArgon2Digest()
  
   /* Encryots a string using the DES encryption
   * @param unencryptedString
   * @return
   * @throws EncryptionException
   */
  public String encryptDES( String unencryptedString ) throws EncryptionException
  {
    if ( unencryptedString == null || unencryptedString.trim().length() == 0 )
        throw new IllegalArgumentException(
            "unencrypted string was null or empty" );

    try
    {
      SecretKey key = m_keyFactory.generateSecret( m_keySpec );
      m_cipher.init( Cipher.ENCRYPT_MODE, key );
      byte[] cleartext = unencryptedString.getBytes( UNICODE_FORMAT );
      byte[] ciphertext = m_cipher.doFinal( cleartext );

      
      return new String( VwBase64.encode( ciphertext ));
    }
    catch (Exception e)
    {
      throw new EncryptionException( e );
    }

  } // end encryptDES()

  public String decryptDES( String encryptedString ) throws EncryptionException
  {
    if ( encryptedString == null || encryptedString.trim().length() <= 0 )
        throw new IllegalArgumentException( "encrypted string was null or empty" );
    try
    {
      SecretKey key = m_keyFactory.generateSecret( m_keySpec );
      m_cipher.init( Cipher.DECRYPT_MODE, key );
      byte[] encryptedBytes = encryptedString.getBytes( UNICODE_FORMAT );
      byte[] cleartext = VwBase64.decode( encryptedBytes );
      byte[] ciphertext = m_cipher.doFinal( cleartext );

      return new String( ciphertext );
    }
    catch (Exception e)
    {
      throw new EncryptionException( e );
    }
  } // end decryptDES()

  /**
   *  Decrypts a string that was encrypted and then convereted to base32
   * @param encryptedStringBase32
   * @return
   * @throws EncryptionException
   */
  public String decryptBase32( String encryptedStringBase32 ) throws Exception
  {
    String encryptedString = new String (VwBase32.decode( encryptedStringBase32 ));

    String decryptedString = decryptDES( encryptedString );

    return decryptedString;

  } // end decryptBase32()

  /**
   *  Encrypts a string using two way DES and then converts it to base32
   * @param unencryptedString
   * @return
   * @throws EncryptionException
   */
  public String encryptBase32( String unencryptedString ) throws Exception
  {
    String encryptedString = encryptDES( unencryptedString );

    String base32EncryptedString = new String(VwBase32.encode( encryptedString.getBytes() ));

    return  base32EncryptedString;

  } // end encryptBase32()

  /**
   * Generate an MD5 digest from a string of data
   *
   * @param strData The data to create the MD5 digest from
   * @return
   * @throws Exception
   */
  public static byte[] generateMD5Digest( String strData ) throws Exception
  {
    MessageDigest md = MessageDigest.getInstance( "MD5" );
    md.update( strData.getBytes() );

    return md.digest();

  } // end generateMD5Digest)

  /**
   * Generates rsa public/private keypair using a ranomizer
   * @return
   * @throws Exception
   */
   public static KeyPair generateRSAKeyPair() throws Exception
   {

     KeyPairGenerator keyGen = KeyPairGenerator.getInstance( "RSA" );
     SecureRandom random = SecureRandom.getInstance( "SHA1PRNG", "SUN" );
     keyGen.initialize(1024, random);
     return keyGen.generateKeyPair();

   } // end generateRSAKeyPair()


  /**
   * Generates a PublicKey from a binary encoded array
   *
   * @param abEncodedPubKey encoded array from a previously created public key
   * @return
   * @throws Exception
   */
   public static PublicKey generatePublicKeyFromEncoded( byte[] abEncodedPubKey ) throws Exception
   {
     KeyFactory keyFactory =  KeyFactory.getInstance( "RSA" );

     X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec( abEncodedPubKey );

     return keyFactory.generatePublic( pubKeySpec );

   } // end generatePublicKeyFromEncoded()


  /**
   * Generates a PublicKey from a binary encoded array
   *
   * @param abEncodedPrivKey encoded array from a previously created private key
   * @return
   * @throws Exception
   */
   public static PrivateKey generatePrivateKeyFromEncoded( byte[] abEncodedPrivKey ) throws Exception
   {
     KeyFactory keyFactory =  KeyFactory.getInstance( "RSA" );

     PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(abEncodedPrivKey);
     return keyFactory.generatePrivate( privKeySpec );

   } // end generatePrivateKeyFromEncoded()


  /**
   * Encrypts a String  using RSA
   *
   * @param strData
   * @param key
   * @return
   * @throws Exception
   */
  public static byte[] encryptRSA( String strData, PublicKey key) throws Exception
  { return encryptRSA( strData.getBytes(), key );  }


  /**
   * Encrypts a block of data using RSA
   * @param abDataToEncrypt
   * @param key
   * @return
   * @throws Exception
   */
  public static byte[] encryptRSA( byte[] abDataToEncrypt, PublicKey key) throws Exception
  {
    byte[] cipherText = null;
    try
    {
      // get an RSA cipher object and print the provider

      Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");

      // encrypt the plaintext using the public key
      cipher.init(Cipher.ENCRYPT_MODE, key );
      cipherText = cipher.doFinal(abDataToEncrypt);
    }
    catch (Exception e)
    {
      throw e;
    }

    return cipherText;

  } // end encryptRSA()

  /**
   * Decrypts a block of encrypted data
   * @param abData A previously encrypted array of data
   * @param key
   * @return
   * @throws Exception
   */
  public static byte[] decryptRSA( byte[] abData, PrivateKey key) throws Exception
  {
    byte[] dectyptedText = null;
    try
    {
      // decrypt the text using the private key
      Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
      cipher.init( Cipher.DECRYPT_MODE, key);
      try
      {
        dectyptedText = cipher.doFinal(abData);
      }
      catch(Exception e)
      {
        e.printStackTrace();
     }

    }
    catch (Exception e)
    {
      throw e;
    }

    return dectyptedText;

  } // ebd decryptRSA()


  public static class EncryptionException extends Exception
  {
    public EncryptionException( Throwable t )
    {
      super( t );
    }

  } // end EncryptionException{}

  /**
   * returns an Sha3-512 hash
   *
   * @param  strMsgToHash The msg to hash
   * @return a byte array
   * @throws Exception
   */
  public static byte[] genSha3_512Digest( String strMsgToHash ) throws Exception
  {
    MessageDigest crypt = MessageDigest.getInstance("SHA3-512");
    crypt.update(strMsgToHash.getBytes( StandardCharsets.UTF_8));

    return crypt.digest();

  } // end sha3512Hash()

  /**
   * Generate an AES SecretKey of size 256 bits
   *
   * @return A SecretKey instance for encryption/decryption
   * @throws Exception
   */
  public static SecretKey genAes256SecretKey() throws Exception
  {
    KeyGenerator keyGenerator = KeyGenerator.getInstance( "AES");
    SecureRandom rand = new SecureRandom();

    keyGenerator.init( 256, rand );
    SecretKey key = keyGenerator.generateKey();

    return key;

  } // end genAesSecretKey()

  public static IvParameterSpec genIv4() throws Exception
  {
    byte[] iv = new byte[16];
    new SecureRandom().nextBytes(iv);
    return new IvParameterSpec(iv);

  } // end IvParameterSpec()


  /**
   * Encrypts a string using AES 256
   * @param key The secret key
   * @param iv4 the initialaztion vector
   * @param strDataToEncrypt The string to encrypt
   * @return
   * @throws Exception
   */
  public static String encryptAes256( SecretKey key, IvParameterSpec iv4, String strDataToEncrypt ) throws Exception
  {
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, key, iv4 );
    byte[] cipherText = cipher.doFinal(strDataToEncrypt.getBytes());

    return Base64.getEncoder().encodeToString(cipherText);

  } // end encryptAes256()

  /**
   * Encrypt a File useing AES 256
   * @param key The secret key
   * @param iv4 the initialaztion vector
   * @param fileToEncrypt The File to encrypt
   * @return
   * @throws Exception
   */
  public static void encryptAes256( SecretKey key, IvParameterSpec iv4, File fileToEncrypt, File fileEncrypted ) throws Exception
  {
    FileInputStream fis= new FileInputStream( fileToEncrypt );
    FileOutputStream fos = new FileOutputStream( fileEncrypted);

    encryptAes256( key, iv4, fis, fos );

   } // end encryptAes256()


  /**
   * Encrypts from already created input and outpu streams
   *
   * @param key   The secret key
   * @param iv4   The initialzation vector
   *
   * @param insToDecrypt   The inputstram to encrypt
   * @param outsEncrypted  The outputstream to place the encrypted stream
   * @throws Exception
   */
  public static void encryptAes256( SecretKey key, IvParameterSpec iv4, InputStream insToDecrypt, OutputStream outsEncrypted ) throws Exception
  {
    Cipher cipher = Cipher.getInstance( "AES/CBC/PKCS5Padding" );
    cipher.init( Cipher.ENCRYPT_MODE, key, iv4 );

    CipherOutputStream out = new CipherOutputStream(outsEncrypted, cipher);

    byte[] abBlock = new byte[512000];
    int nRead = -1;
    while( ( nRead = insToDecrypt.read( abBlock) ) != -1 )
    {
      out.write( abBlock, 0, nRead );
    }

    out.flush();;
    out.close();

    insToDecrypt.close();
    outsEncrypted.close();

  } // end encryptAes256()


  /**
   * Decrypt file
   *
   * @param key   The secret key
   * @param iv4   The initialzation vector
   * @param fileToDecrypt The file to decrypt
   * @param fileDecrypted The file that will to written to (Decrypted)
   * @throws Exception
   */
  public static void decryptAes256( SecretKey key, IvParameterSpec iv4, File fileToDecrypt, File fileDecrypted ) throws Exception
  {

    FileInputStream fis= new FileInputStream( fileToDecrypt );
    FileOutputStream fos = new FileOutputStream( fileDecrypted);

    decryptAes256( key, iv4, fis, fos);

  } // end encryptAes256()

  public static void decryptAes256( SecretKey key, IvParameterSpec iv4, InputStream insToDescypt, File fileDecrypted ) throws Exception
  {

    FileOutputStream fos = new FileOutputStream( fileDecrypted);

    decryptAes256( key, iv4, insToDescypt, fos);

  } // end encryptAes256()

  /**
   * Decryptes from an
   *
   * @param key The secret key
   * @param iv4 The Initialization Vector
   * @param insToDecrypt The input stream to decrypt
   * @param outsDecrypted The output stream of the decrypted
   * @throws Exception
   */
  public static void decryptAes256( SecretKey key, IvParameterSpec iv4, InputStream insToDecrypt, OutputStream outsDecrypted ) throws Exception
  {
    Cipher cipher = Cipher.getInstance( "AES/CBC/PKCS5Padding" );
    cipher.init( Cipher.DECRYPT_MODE, key, iv4 );

    CipherOutputStream cypherOut = new CipherOutputStream(outsDecrypted, cipher);

    byte[] abBlock = new byte[512000];
    int nRead = -1;
    while( ( nRead = insToDecrypt.read( abBlock) ) != -1 )
    {
      cypherOut.write( abBlock, 0, nRead );
    }

    cypherOut.flush();;
    cypherOut.close();

    insToDecrypt.close();
    outsDecrypted.close();


  } // end decryptAes256()

  /**
   * Decrypt an encrypted string
   *
   * @param key the secret key
   * @param iv4 the initialazation vector
   * @param strDataToDecrypt The encryptes data to decrypt
   * @return
   * @throws Exception
   */
  public static String decryptAes256( SecretKey key, IvParameterSpec iv4, String strDataToDecrypt ) throws Exception
  {
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.DECRYPT_MODE, key, iv4 );
    byte[] abPlainText = cipher.doFinal(Base64.getDecoder()
                                          .decode( strDataToDecrypt ));
    return new String(abPlainText);

  } // end encryptAes256()


  /**
   * Encrypts a a string usibg a proprietary scheme. This is used for browser URLs and returned JSON data used in conjunction
   * <br/>with the cpm.vozzware.service.manager.VwEncryptionFilter
   *
   * @param strStringToEncrypt Th string to be encrypted
   * @return
   * @throws Exception
   */
  public static String enCrypt( String strStringToEncrypt ) throws Exception
  {
    // First Basse64 encode

    String[] astrSignitures = new String[]{"X32","MOV", "JWS", "KFV", "PBV", "RVS", "LML"};

    String strB64Encoded = new String( VwBase64.encode( strStringToEncrypt.getBytes() ) );

    strB64Encoded = VwExString.replace( strB64Encoded, "/", "@");

    int ndx = getRandomInt( 0, astrSignitures.length ) % astrSignitures.length;

    ArrayList<String> listSigs = new ArrayList<String>();
    listSigs.add( astrSignitures[ ndx ] );

    // Generate random hex bytes around the secret 3 byte signiture
    for ( int x = 0; x < 4; x++ )
    {
      int nbr = getRandomInt( 1, 255 );

      String strHex = Integer.toHexString( nbr ).toUpperCase();

      if ( strHex.length() < 2 )
      {
        strHex += strHex;
      }

      listSigs.add( strHex );
    }

    shuffleArray( listSigs );

    String strResult = "";

    for ( int x = 0; x < 5; x++ )
    {
      strResult += listSigs.get( x );
    }

    // Parse the sbase64 string into 4 chunks

    int nChunkLen = strB64Encoded.length() / 4 ;
    int nChunkRemain = strB64Encoded.length() % 4;

    String[] astrChunks = new String[4];
    for ( int x = 0; x < 3; x++ )
    {
      astrChunks[ x ] = strB64Encoded.substring( x * nChunkLen, nChunkLen * (x + 1));
    }

    astrChunks[ 3 ] = strB64Encoded.substring( nChunkLen * 3 );

    ArrayList<Integer>listOrder = new ArrayList<Integer>(  );
    listOrder.add( 0 );
    listOrder.add( 1 );
    listOrder.add( 2 );
    listOrder.add( 3 );

    shuffleArray( listOrder );

    String strDict = "";
    String strMsgChunks = "";

    for( int x = 0; x < 4; x++ )
    {
      int nChunkNbr = listOrder.get( x );

      int nLen = nChunkLen;

      if ( nChunkNbr == 3 )
      {
        nLen = nChunkLen + nChunkRemain;
      }

      String  strChunkLen = VwExString.lpad( Integer.toHexString( nLen ), '0', 6 );


      strDict += "0" + nChunkNbr + strChunkLen;

      strMsgChunks += astrChunks[ nChunkNbr ] + getChunkFiller();

    }

    String strDictB64 = new String( VwBase64.encode( strDict.getBytes() ) );

    String strDictLen = VwExString.lpad( Integer.toHexString( strDictB64.length() ), '0', 2 );

    strResult += strDictLen + strMsgChunks + strDictB64;

    // Create an MD5 Digents of the data
    MessageDigest md = MessageDigest.getInstance( "MD5" );

    md.update( strResult.getBytes(), 0, strResult.length() );

    String strHash = new BigInteger(1,md.digest()).toString( 16 );

    while (strHash.length() < 32)
    {
       strHash = "0" + strHash;
    }

    strResult += strHash;

    return strResult;

  } // end enCrypt()

  /**
   * Shuffle an array using a Random number generaotr
   * @param list
   */
  private static void shuffleArray( ArrayList list )
  {
     Random rand = new Random(  );
     for (int i = list.size() - 1; i > 0; i--)
     {
       int j = rand.nextInt( (i + 1) );
       Object temp = list.get(i);
       list.set(i, list.get(j) );
       list.set(j, temp );
     }
  }  // end shuffleArray()

  /**
   * Formats a string chunk using random numbers
   * @return
   * @throws Exception
   */
  private static String getChunkFiller() throws Exception
  {
    String strFiller = "";

    for ( int x = 0; x < 2; x++ )
    {
      int nbr = getRandomInt( 1, 255 );
      strFiller += VwExString.lpad( Integer.toHexString( nbr ), '0', 2 );
    }

    return strFiller;

  } // end getChunkFiller()

  /**
   * Generate a randon number between min and max
   *
   * @param nMin The min value
   * @param nMax The max value
   * @returns {*}
   */
  private static int getRandomInt( int nMin,  int nMax )  throws Exception
  {
    Random rand = new Random( );
    return rand.nextInt((nMax - nMin + 1)) + nMin;

  } // end getRandomInt()

  /**
   * Decrypts a string encrypted with the enCrypt method
   * @param strStringToDecrypt The string to decrypt
   * @return
   * @throws Exception
   */
  public static String deCrypt( String strStringToDecrypt ) throws Exception
  {
    if ( strStringToDecrypt.length() < 43 )
    {
      return null;  // String is less than the prefix - invalid and hash
    }

    int nHashPos = strStringToDecrypt.length() - 32;

    String strHash = strStringToDecrypt.substring( nHashPos );

    String strMsg = strStringToDecrypt.substring( 0, nHashPos );

    MessageDigest md = MessageDigest.getInstance( "MD5" );

    md.update( strMsg.getBytes(), 0, strMsg.length() );

    BigInteger biHash  = new BigInteger( 1,md.digest() );

    String strMsgHash  = biHash.toString(16);


    while (strMsgHash.length() < 32)
    {
       strMsgHash = "0" + strMsgHash;
    }

    if ( !strMsgHash.equals( strHash ) )
    {
      return null;               // Msg invalid
    }

    // Get dictionary Length

    int nDictLength = hex2Int( strMsg.substring( 11, 13 ) );

    int nPos =  strStringToDecrypt.length() - 32; // 32 bytes for hash

    nPos -= nDictLength; // Dictionary is 24 bytes

    if ( nPos < 11 )
    {
      return null;          // if its less that 11 we are pointing to message prefix or its out of bounds so its invalid
    }

    String strDictionary =  strStringToDecrypt.substring( nPos, nPos + nDictLength );

    // convert from base 64

    strDictionary = new String( VwBase64.decode( strDictionary.getBytes() ) );

    String[] aChunkArray = new String[]{"","","",""};

    // go through the dictionary and reconstruct the base64 message chunks

    int nChunkPos = 13;
    for ( int x = 0; x < 4; x++ )
    {
      String strEntry = strDictionary.substring( 0, 8 );
      strDictionary = strDictionary.substring( 8);

      // first 2 bytes are the chunk index - strip off first

      int nChunkNdx = Integer.parseInt( strEntry.substring( 0, 2 ));
      int nChunkLen = hex2Int( strEntry.substring( 2 ) );

      String strMsgChunk = strMsg.substring( nChunkPos, nChunkPos + nChunkLen );

      aChunkArray[ nChunkNdx ] = strMsgChunk;

      nChunkPos += nChunkLen + 4;  // 4 is the filler we don't care about

    }

    String strB64Msg = "";

    for ( int x = 0; x < 4; x++ )
    {
      strB64Msg += aChunkArray[ x ];
    }

    strB64Msg = VwExString.replace( strB64Msg, "@", "/");

    return new String( VwBase64.decode( strB64Msg.getBytes() ) );

  } // end deCrypt()

  /**
   * Converts a hexidecimal string to an integer
   *
   * @param strHexValue  The hex string to convert
   * @return
   */
  private static int hex2Int( String strHexValue )
  {
    int nStartPos = 0;

    for ( int x = 0, nLen = strHexValue.length(); x < nLen; x++ )
    {
      if ( strHexValue.charAt( x ) != '0' )
      {
        break;
      }

      ++nStartPos;
    }

    // Length is in hex

    String strHexLen = strHexValue.substring( nStartPos );

    int nLen = Integer.parseInt( strHexLen, 16 );

    return nLen;

  } // end hex2Int()

} // end VwEncryptionFilter{}