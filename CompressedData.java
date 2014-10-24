
public class CompressedData {
	byte[] compressedData = new byte [5000];
	int compressedDataLength;
	
	public CompressedData(byte[] compressedData, int compressedDataLength) {
		this.compressedData = compressedData;
		this.compressedDataLength = compressedDataLength;
	}

	public byte[] getCompressedData() {
		return compressedData;
	}

	public void setCompressedData(byte[] compressedData) {
		this.compressedData = compressedData;
	}

	public int getCompressedDataLength() {
		return compressedDataLength;
	}

	public void setCompressedDataLength(int compressedDataLength) {
		this.compressedDataLength = compressedDataLength;
	}
}
