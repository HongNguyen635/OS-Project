/**
 * This is the first disk block (block 0) that uses to
 * describe the number of disk blocks, inodes, and the
 * block number of the head block of the free list.
 * 
 * @author Hong Nguyen
 * @version 1
 */
public class SuperBlock {
	private final int defaultInodeBlocks = 64;
	public int totalBlocks; // number of disk blocks
	public int inodeBlocks; // number of inodes
	public int freeList; // block # of free list's head

	/**
	 * Initialize the SuperBlock with the number of disk blocks,
	 * inodes, and the free list's head block number.
	 * 
	 * @param diskSize the total number of blocks the disk has.
	 */
	public SuperBlock(int diskSize) {
		// read the superblock from disk
		byte[] superBlock = new byte[Disk.blockSize];
		SysLib.rawread(0, superBlock);

		// read from block 0 and store info
		totalBlocks = SysLib.bytes2int(superBlock, 0);
		inodeBlocks = SysLib.bytes2int(superBlock, 4);
		freeList = SysLib.bytes2int(superBlock, 8);

		// check if the read is valid
		// block 0 is for super block, block 1 is for inodes
		// so free list needs to start at least at block 2
		if (totalBlocks == diskSize && inodeBlocks > 0 && freeList >= 2) {
			// valid disk contents
			return;
		}

		// else = format the disk
		totalBlocks = diskSize;
		format();
	}

	/**
	 * Helper function to sync super block info here to disk
	 */
	void sync() {
		/*
		 * create a byte array with the disk block size
		 * Note: Java int = 32 bits = 4 bytes.
		 * The first 4 bytes store the total # of blocks
		 * The next 4 bytes store the # of inodes
		 * After that is the free list
		 * Write back to the superblock info to disk
		 */
		byte[] superBlock = new byte[Disk.blockSize];
		SysLib.int2bytes(totalBlocks, superBlock, 0);
		SysLib.int2bytes(inodeBlocks, superBlock, 4);
		SysLib.int2bytes(freeList, superBlock, 8);
		SysLib.rawwrite(0, superBlock);
		SysLib.cerr("Superblock synchronized\n");
	}

	/**
	 * Format the disk with the default number of files
	 * (64 inodes) and initialize the rest of the blocks
	 * as free list.
	 */
	void format() {
		// default format with 64 inodes
		format(defaultInodeBlocks);
	}

	/**
	 * Format the disk with the given number of files.
	 * The rest of the blocks are added to the free list.
	 * 
	 * @param files the maximum number of files to be created
	 */
	void format(int files) {
		// initialize the superblock
		this.inodeBlocks = files;
		short totalFiles = (short) files;

		// create the inodes first
		for (short i = 0; i < totalFiles; i++) {
			// all inodes are blank initially
			Inode inode = new Inode();
			inode.toDisk(i);
		}

		// calculate the first free block
		// if the inode is not a multiple of 16, then there will be
		// a internal fragmentation. The freelist always contains
		// a FULL block.
		freeList = files / 16 + 2; // bc 0 is the superblock

		// the rest of the blocks are free now, but according to
		// the slides, each 4 bytes (int) at the beginning will
		// contain the number of the next free block
		for (int i = freeList; i < this.totalBlocks; i++) {
			byte[] newBlock = new byte[Disk.blockSize];

			// write the next free block number to the beginning of
			// the current block
			SysLib.int2bytes(i + 1, newBlock, 0);

			// then write this block back to disk
			SysLib.rawwrite(i, newBlock);
		}
		// after this loop, the final block has the index of next free
		// block = total Blocks.

		// final step is to write this super block to disk
		// just like how the ThreadOS always do it
		sync();
	}

	/**
	 * Get the block number of the free block.
	 * 
	 * @return the block number if there is still an available
	 *         block, -1 otherwise.
	 */
	public int getFreeBlock() {
		// get a new free block from the freelist
		// freeList if full when index = totalBlocks
		if (freeList == totalBlocks) {
			return -1;
		}

		int returnBlockNumber = freeList;

		// read the block to get the index of the next free block
		byte[] returnBlock = new byte[Disk.blockSize];
		SysLib.rawread(returnBlockNumber, returnBlock);

		this.freeList = SysLib.bytes2int(returnBlock, 0);

		// clean the block first before returning it
		returnBlock = new byte[Disk.blockSize];
		SysLib.rawwrite(returnBlockNumber, returnBlock);

		return returnBlockNumber;
	}

	/**
	 * Return a block to the free list.
	 * 
	 * @param oldBlockNumber the block number of the block you
	 *                       want to free.
	 * @return true if sucess and false otherwise
	 */
	public boolean returnBlock(int oldBlockNumber) {
		// check for valid first
		int validStartBlock = inodeBlocks / 16 + 2;
		if (oldBlockNumber < validStartBlock || oldBlockNumber > totalBlocks) {
			return false;
		}

		// return it to the beginning of the list to save time
		// (like a stack)
		byte[] oldBlock = new byte[Disk.blockSize];

		SysLib.int2bytes(this.freeList, oldBlock, 0);
		freeList = oldBlockNumber;

		// write the block back to disk
		SysLib.rawwrite(oldBlockNumber, oldBlock);

		return true;
	}

}
