import java.io.UnsupportedEncodingException;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Utility {
	
	public static String generateRandomString(Random rng, String characters, int length) {
	    char[] text = new char[length];
	    for (int i = 0; i < length; i++){
	        text[i] = characters.charAt(rng.nextInt(characters.length()));
	    }
	    return new String(text);
	}
	
	public static CompressedData compressString (String inputString, String compressionOption) throws UnsupportedEncodingException {
        byte[] input = inputString.getBytes("UTF-8");
        byte[] output = new byte[15000];
        Deflater compresser = new Deflater();
        
        if (compressionOption == "BEST_COMPRESSION")
     	    compresser = new Deflater(Deflater.BEST_COMPRESSION);
     	else if (compressionOption == "BEST_SPEED")
     		compresser = new Deflater(Deflater.BEST_SPEED);
     	else if (compressionOption == "DEFAULT_COMPRESSION")
     	    compresser = new Deflater(Deflater.DEFAULT_COMPRESSION);
    	
        compresser.setInput(input);
        compresser.finish();
        int compressedDataLength = compresser.deflate(output);        
        compresser.end();
        CompressedData compressedOutput = new CompressedData (output, compressedDataLength);
        return compressedOutput;
	}
	
	public static String decompressString (byte[] output, int compressedDataLength) throws UnsupportedEncodingException, DataFormatException {
       Inflater decompresser = new Inflater();
       decompresser.setInput(output, 0, compressedDataLength);
       byte[] result = new byte[15000];
       int resultLength = decompresser.inflate(result);
       decompresser.end();
       String outputString = new String(result, 0, resultLength, "UTF-8");
       return outputString;
	}
	
}
