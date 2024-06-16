/**
 * The root directory maintains each file in a different
 * directory entry that contains its file name (30 chars
 * max = 60 bytes in Java) and the inode number.
 * 
 * The directory receives the max number of Inodes to be
 * created (max files) and keep track of which inode
 * numbers are in use.
 * 
 * Note: the directory itself is considered a file, its
 * contents are maintained by Inode 0 (the first 32 bytes)
 * of block 1.
 */
public class Directory {
	private static int maxChars = 30; // the max characters of each file name

	private int fsizes[]; // the actual size of each file name
	private char fnames[][]; // file names in characters

	/**
	 * Directory constructor that
	 * 
	 * @param maxInumber maximum number of inodes to be created,
	 *                   is also the maximum number of files.
	 */
	public Directory(int maxInumber) {
		// from the other classes, seems like maxInumber is 64 (?)
		fsizes = new int[maxInumber]; // maxInumber = max files

		// all file sizes set to 0
		for (int i = 0; i < maxInumber; i++)
			fsizes[i] = 0;
		fnames = new char[maxInumber][maxChars];

		String root = "/"; // entry(inode) 0 is "/"
		fsizes[0] = root.length();
		root.getChars(0, fsizes[0], fnames[0], 0);
	}

	/**
	 * Convert the byte array information retrieved from disk
	 * to this directory.
	 * 
	 * @param data the byte array that contains the info of
	 *             the directory stores in the disk.
	 */
	public void bytes2directory(byte data[]) {
		// assumes data[] contains directory information retrieved from disk
		// initialize the directory fsizes[] and fnames[] with this data[]

		// need to check first if the byte array has the
		// expected size
		int expectedSize = fsizes.length * 4 + fnames.length * maxChars * 2;
		if (data.length != expectedSize) {
			return;
		}

		// begin to parse info
		int offset = 0;

		// parse the file size first
		for (int i = 0; i < fsizes.length; i++, offset += 4) {
			fsizes[i] = SysLib.bytes2int(data, offset);
		}

		// then parse the file name
		for (int i = 0; i < fnames.length; i++, offset += maxChars * 2) {
			// copy the file names from bytes to String
			byte[] fileName = new byte[maxChars * 2];

			// this copy will have the trailing 0s
			// param: src arr, src start pos, dest arr, dest stat pos, len
			System.arraycopy(data, offset, fileName, 0, maxChars * 2);

			String name = (new String(fileName)).trim();

			// put all this parsed info into the directory
			fsizes[i] = name.length();
			name.getChars(0, fsizes[i], fnames[i], 0);
		}
	}

	/**
	 * Converts and return directory information into a plain byte
	 * array. This byte array will be written back to disk
	 * 
	 * @return a byte array that contains the directory information
	 */
	public byte[] directory2bytes() {
		// byte array that will store the directory info.
		// size: *4 because each int has 4 bytes, and *2 because each char
		// has 2 bytes.
		byte[] data = new byte[fsizes.length * 4 + fnames.length * maxChars * 2];

		int offset = 0;

		// store each file size into the byte array
		for (int i = 0; i < fsizes.length; i++, offset += 4)
			SysLib.int2bytes(fsizes[i], data, offset);

		// store each file name in the byte array
		for (int i = 0; i < fnames.length; i++, offset += maxChars * 2) {
			String tableEntry = new String(fnames[i], 0, fsizes[i]);
			byte[] bytes = tableEntry.getBytes();
			System.arraycopy(bytes, 0, data, offset, bytes.length);
		}

		return data;
	}

	/**
	 * Allocates a new inode number for this filename.
	 * 
	 * @param filename the name of a file to be created.
	 * @return the inode number allocated. Return -1 if no inode
	 *         is available (files are full).
	 */
	public short ialloc(String filename) {
		short i;
		// i = 0 is already used for "/"
		for (i = 1; i < fsizes.length; i++) {
			if (fsizes[i] == 0) {
				// truncate the filename if it's longer than max
				fsizes[i] = Math.min(filename.length(), maxChars);
				filename.getChars(0, fsizes[i], fnames[i], 0);
				return i;
			}
		}
		return -1;
	}

	/**
	 * Deallocates this inumber (inode number). The corresponding
	 * file will be deleted.
	 * 
	 * @param iNumber the inode number you want to delete.
	 * @return true if sucess and false otherwise.
	 */
	public boolean ifree(short iNumber) {
		// check for valid iNumber
		if (iNumber < 1 || iNumber >= fsizes.length) {
			return false;
		}

		// deallocate = clear the array and size
		fsizes[iNumber] = 0;
		fnames[iNumber] = new char[maxChars];

		return true;
	}

	/**
	 * Find the Inode number corresponding to the given filename.
	 * 
	 * @param filename the inode's filename you want to look for
	 * @return the inode number if found, -1 otherwise
	 */
	public short namei(String filename) {
		// returns the inumber corresponding to this filename
		short i;
		for (i = 0; i < fsizes.length; i++) {
			// check for equal length
			if (fsizes[i] == filename.length()) {
				// check for equal name
				String tableEntry = new String(fnames[i], 0, fsizes[i]);
				if (filename.compareTo(tableEntry) == 0)
					return i;
			}
		}
		return -1;
	}

}
