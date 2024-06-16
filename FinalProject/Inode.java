/**
 * An Inode describes a file. Includes 12 pointers of the index
 * block. The first 11 pointers point to direct blocks, and the
 * last pointer points to an indirect block.
 * 
 * Each Inode has:
 * - The file's length.
 * - Number of file table entries that point to this inode.
 * - A flag to indicate if it's unused (0), used (1), or others.
 * 
 * Note: 16 Inodes can be stored in 1 block because 1 inode
 * has 32 bytes, 1 block = 512 bytes -> 512 / 32 = 16 inodes.
 */
public class Inode {
	public final static int iNodeSize = 32; // fixed to 32 bytes
	public final static int directSize = 11; // # direct pointers

	public final static int NoError = 0;
	public final static int ErrorBlockRegistered = -1;
	public final static int ErrorPrecBlockUnused = -2;
	public final static int ErrorIndirectNull = -3;

	public int length; // file size in bytes
	public short count; // # file-table entries pointing to this
	public short flag; // 0 = unused, 1 = used(r), 2 = used(!r),
						// 3=unused(wreg), 4=used(r,wreq), 5= used(!r,wreg)
	public short direct[] = new short[directSize]; // direct pointers
	public short indirect; // an indirect pointer

	/**
	 * Default constructor to initialize all values to default.
	 */
	Inode() {
		length = 0;
		count = 0;
		flag = 1;
		for (int i = 0; i < directSize; i++)
			direct[i] = -1;
		indirect = -1;
	}

	/**
	 * Constructor that retrieves an existing inode from disk to
	 * memory.
	 * 
	 * @param iNumber the inode number
	 */
	Inode(short iNumber) {
		// inodes start from block #1
		// 16 inodes per block
		int blkNumber = 1 + iNumber / 16;

		byte[] data = new byte[Disk.blockSize];
		SysLib.rawread(blkNumber, data); // get the inode block
		int offset = (iNumber % 16) * iNodeSize; // locate the inode top

		length = SysLib.bytes2int(data, offset); // retrieve all data members
		offset += 4; // from data (4 bytes = int)
		count = SysLib.bytes2short(data, offset);
		offset += 2;
		flag = SysLib.bytes2short(data, offset);
		offset += 2;

		// read the direct pointers
		for (int i = 0; i < directSize; i++) {
			direct[i] = SysLib.bytes2short(data, offset);
			offset += 2;
		}

		// read the indirect pointer
		indirect = SysLib.bytes2short(data, offset);
		offset += 2;

		/*
		 * System.out.println( "Inode[" + iNumber + "]: retrieved " +
		 * " length = " + length +
		 * " count = " + count +
		 * " flag = " + flag +
		 * " direct[0] = " + direct[0] +
		 * " indirect = " + indirect );
		 */
	}

	/**
	 * Saving this inode to disk.
	 * 
	 * @param iNumber the inode number
	 */
	void toDisk(short iNumber) {
		// essentially the same as the constructor but instead
		// of reading from disk, this is reading to disk.

		// find the block number (start from block 1) + offset
		int blockNumber = 1 + iNumber / 16;
		int offset = (iNumber % 16) * iNodeSize;

		// get all the data in here into the temp array
		byte[] data = new byte[Disk.blockSize];
		SysLib.rawread(blockNumber, data); // read block data first

		SysLib.int2bytes(length, data, offset);

		offset += 4; // beginning of count
		SysLib.short2bytes(count, data, offset);

		offset += 2; // beginning of flag
		SysLib.short2bytes(flag, data, offset);

		offset += 2; // beginning of direct blocks

		// use loop to read info
		for (int i = 0; i < directSize; i++) {
			SysLib.short2bytes(direct[i], data, offset);
			offset += 2;
		}

		// read the indirect pointer
		SysLib.short2bytes(indirect, data, offset);

		// write everything back to disk
		SysLib.rawwrite(blockNumber, data);
	}
}
