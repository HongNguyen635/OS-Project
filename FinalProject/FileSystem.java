/**
 * A single-level file system for the ThreadOS.
 * 
 * This file system supports formatting the disk,
 * seek, open, close, get file's size, read, write,
 * and delete files.
 * 
 * @author Hong Nguyen
 * @version 1
 */
public class FileSystem {
    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;

    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;

    /**
     * Constructor that initialize the File System with the
     * given number of diskBlocks.
     * 
     * The File System registers the root directory, creates
     * a new File Table, and rescontruct the root directory
     * as needed.
     * 
     * @param diskBlocks the total blocks on the disk
     */
    public FileSystem(int diskBlocks) {
        // create superblock, and format disk with 64 inodes in default
        superblock = new SuperBlock(diskBlocks);

        // create directory, and register "/" in directory entry 0
        directory = new Directory(superblock.inodeBlocks);

        // file table is created, and store directory in the file table
        filetable = new FileTable(directory);

        // directory reconstruction
        FileTableEntry dirEnt = open("/", "r");
        int dirSize = fsize(dirEnt);
        if (dirSize > 0) {
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }
        close(dirEnt);
    }

    /**
     * Sync the file system data with the disk.
     */
    void sync() {
        // directory synchronization
        // open the root directory for write
        // get all modified data in root directory to bytes
        // then write these back to inode 0
        // and sync the superblock

        FileTableEntry dirEnt = open("/", "w");
        byte[] dirData = directory.directory2bytes();
        write(dirEnt, dirData);
        close(dirEnt);

        // superblock synchronization
        superblock.sync();
    }

    /**
     * Format the file system by formating the superblock,
     * initialize the inodes, register the root directory,
     * and create the File Table.
     * 
     * @param files the maximum number of files to be created.
     * @return true if success and false otherwise.
     */
    boolean format(int files) {
        // wait until all filetable entries are destructed
        while (filetable.fempty() == false)
            ;

        // format superblock, initialize inodes, and create a free list
        superblock.format(files);

        // create directory, and register "/" in directory entry 0
        directory = new Directory(superblock.inodeBlocks);

        // file table is created, and store directory in the file table
        filetable = new FileTable(directory);

        return true;
    }

    /**
     * Opens the file specified by filename in the given mode.
     * The file is created is it does not exist in the mode
     * "w", "W+", or a. Return error if the file does not exist
     * in the "r" mode.
     * 
     * @param filename the name of the file you want to open.
     * @param mode     the mode of the file (r, w, w+, a).
     * @return return the File Table Entry allocated to this file.
     *         If an error occured, return null instead.
     */
    FileTableEntry open(String filename, String mode) {
        // filetable entry is allocated
        FileTableEntry newEntry = filetable.falloc(filename, mode);

        // for write mode, it'll write over all existing contents
        // so need to deallocate all blocks
        // reference from the CSS430 Note Slides
        if (mode.equals("w")) {
            if (deallocAllBlocks(newEntry) == false) {
                // free the entry
                filetable.ffree(newEntry);
                SysLib.cerr("Unable to open file for write\n");
                return null;
            }
        }

        return newEntry;
    }

    /**
     * Free File Table Entry. If other threads are using it,
     * the entry is not free yet, unless the thread count is
     * 0, then the entry fill be freed.
     * 
     * @param ftEnt the File Table Entry you want to close.
     * @return return true if success and false otherwise.
     */
    boolean close(FileTableEntry ftEnt) {
        // filetable entry is freed
        synchronized (ftEnt) {
            // need to decrement count; also: changing > 1 to > 0 below
            ftEnt.count--;
            if (ftEnt.count > 0) // my children or parent are(is) using it
                return true;
        }
        return filetable.ffree(ftEnt);
    }

    /**
     * Return the size in bytes of the file stored in the
     * File Table Entry.
     * 
     * @param ftEnt the File Table Entry of a file you want to
     *              know the size of.
     * @return the size in bytes of the file stored in the entry.
     *         Return -1 if something goes wrong.
     */
    int fsize(FileTableEntry ftEnt) {
        int size = -1;

        if (ftEnt == null)
            return size;

        // thread-safe
        synchronized (ftEnt) {
            size = ftEnt.inode.length;
        }

        return size;
    }

    /**
     * Read up to the buffer's length bytes from the file
     * in the File Table Entry, starting at the position
     * currently pointed by the the seek pointer.
     * 
     * @param ftEnt  the entry that contains the file you
     *               want to read.
     * @param buffer the buffer you want to read info to.
     * @return the number of bytes has been read, or -1
     *         if an error occur.
     */
    int read(FileTableEntry ftEnt, byte[] buffer) {
        // file is not for read
        if (ftEnt.mode.equals("w") || ftEnt.mode.equals("a"))
            return -1;

        synchronized (ftEnt) {
            // repeat reading until no more data or reaching EOF
            int bufferOffset = 0; // buffer offset
            int bufferLeft = buffer.length; // the remaining data of this buffer

            /*
             * Algorithm:
             * - Get the current seek pointer
             * - Calculate which block it might be using:
             * seekptr / blocksize = block #
             * if > 11 -> look into indirect block
             * indirect block can store (blksize / 2) blocks
             * block offset = (seek ptr - 11 * blocksize) / blksize
             * 
             * blockoffset * 2 = offset from block beginning
             * 
             * - If bytes remaining is less than buffer, read whatever
             * you can.
             * - Increment the seek ptr, return # of bytes read.
             */

            // check for valid seek ptr
            if (ftEnt.seekPtr < 0 || ftEnt.seekPtr > ftEnt.inode.length) {
                return -1;
            }

            // calculate block
            boolean indirect = false;

            // for direct block, this is the index of direct blocks
            // currently reading. For indirect, this is the block offset
            // from the first block # of the indirect blocks data
            // (without * short's size)
            int currentBlock = ftEnt.seekPtr / Disk.blockSize;

            // check for overflow to indirect
            if (currentBlock >= Inode.directSize) {
                indirect = true;
                currentBlock = (ftEnt.seekPtr - Inode.directSize * Disk.blockSize) / Disk.blockSize;
            }

            // get the block
            byte[] indirectBlockData = new byte[Disk.blockSize];
            byte[] curBlockData = new byte[Disk.blockSize];
            int currentDataPosition = -1;

            if (indirect) {
                // 2 is size of short
                int indirectOffset = currentBlock * 2;
                currentDataPosition = (ftEnt.seekPtr - Inode.directSize * Disk.blockSize) % Disk.blockSize;

                // read data of indirect block
                SysLib.rawread(ftEnt.inode.indirect, indirectBlockData);

                // extract the block points by indirect block
                int currentBlockNumber = SysLib.bytes2short(indirectBlockData, indirectOffset);
                SysLib.rawread(currentBlockNumber, curBlockData);

            } else {
                // get direct block data
                currentDataPosition = ftEnt.seekPtr % Disk.blockSize;
                SysLib.rawread(ftEnt.inode.direct[currentBlock], curBlockData);
            }

            // a temp seek variable to keep track of EOF
            int tempSeek = ftEnt.seekPtr;
            int fileLength = ftEnt.inode.length;

            // read all data to buffer
            // until no more space left of EOF
            while (bufferLeft > 0 && tempSeek < fileLength) {

                // check if end of block has been reached
                if (currentDataPosition == Disk.blockSize) {
                    // reset data position
                    currentDataPosition = 0;

                    // check if already read all direct blocks
                    ++currentBlock;
                    if (!indirect && currentBlock == Inode.directSize) {
                        // change mode to indirect & read
                        indirect = true;
                        currentBlock = 0;

                        // store indirect block data
                        SysLib.rawread(ftEnt.inode.indirect, indirectBlockData);

                        // store the 1st block indirect points to
                        int currentBlockNumber = SysLib.bytes2short(indirectBlockData, 0);
                        SysLib.rawread(currentBlockNumber, curBlockData);

                    } else if (indirect) {
                        // for indirect block
                        int currentBlockNumber = SysLib.bytes2short(indirectBlockData, currentBlock * 2);
                        SysLib.rawread(currentBlockNumber, curBlockData);

                    } else {
                        // for direct block case
                        SysLib.rawread(ftEnt.inode.direct[currentBlock], curBlockData);
                    }
                }

                // read data to buffer
                buffer[bufferOffset] = curBlockData[currentDataPosition];

                // increment/decrement neccessary variables
                ++bufferOffset;
                ++currentDataPosition;
                ++tempSeek;
                --bufferLeft;
            }

            // finish loop = EOF or buffer full

            // update seek pointer
            ftEnt.seekPtr = tempSeek;

            return bufferOffset; // how many bytes read
        }
    }

    /**
     * Write the contents of the buffer to the file that the
     * File Table Entry points to starting at the position
     * indicated by the seek pointer.
     * 
     * @param ftEnt  the Entry that contains the file you want
     *               to write.
     * @param buffer the buffer that contains data to write.
     * @return the number of bytes that have been written, or
     *         a negative number (-1) upon an error.
     */
    int write(FileTableEntry ftEnt, byte[] buffer) {
        // at this point, ftEnt is only the one to modify the inode
        if (ftEnt.mode.equals("r"))
            return -1;

        synchronized (ftEnt) {
            int bufferOffset = 0; // buffer offset
            int bufferLeft = buffer.length; // the remaining data of this buffer

            int currentDataPosition = -1;
            boolean indirect = false;

            // blocks' data
            byte[] indirectBlockData = new byte[Disk.blockSize];
            byte[] curBlockData = new byte[Disk.blockSize];

            // for direct block, this is the index of direct blocks
            // currently reading. For indirect, this is the block offset
            // from the first block # of the indirect blocks data
            // (without * short's size)
            int currentBlock = -1;

            boolean overflow = false; // check if goes beyond current size
            int additionalSize = 0; // if goes beyond current file size

            // max = size direct blocks + all blocks indirect points to
            int maximumSize = (Inode.directSize * Disk.blockSize) + (Disk.blockSize / 2 * Disk.blockSize);

            // if mode is "w", it's easy
            if (ftEnt.mode.equals("w")) {
                currentDataPosition = 0;

                // get new Block
                ftEnt.inode.direct[0] = (short) superblock.getFreeBlock();

                // no more block to write
                if (ftEnt.inode.direct[0] == -1) {
                    SysLib.cerr("Unable to allocate more space for file\n");
                    return bufferOffset;
                }

                // prepare for write
                currentBlock = 0;

            } else {
                // for "a" and "w+"

                // append, seek = EOF
                if (ftEnt.mode.equals("a")) {
                    ftEnt.seekPtr = ftEnt.inode.length;
                }

                currentBlock = ftEnt.seekPtr / Disk.blockSize;

                // essential the same strategy as read

                // check for overflow to indirect
                if (currentBlock >= Inode.directSize) {
                    indirect = true;
                    currentBlock = (ftEnt.seekPtr - Inode.directSize * Disk.blockSize) / Disk.blockSize;
                }

                if (indirect) {
                    // 2 is size of short
                    int indirectOffset = currentBlock * 2;
                    currentDataPosition = (ftEnt.seekPtr - Inode.directSize * Disk.blockSize) % Disk.blockSize;

                    // read data of indirect block
                    SysLib.rawread(ftEnt.inode.indirect, indirectBlockData);

                    // extract the block points by indirect block
                    int currentBlockNumber = SysLib.bytes2short(indirectBlockData, indirectOffset);

                    // EOF = this block # = 0 bc of default byte array number
                    // (can't be super block)
                    if (currentBlockNumber == 0) {
                        // get new Block
                        currentBlockNumber = superblock.getFreeBlock();

                        // error
                        if (currentBlockNumber == -1) {
                            SysLib.cerr("Unable to allocate more space for file\n");
                            return bufferOffset;
                        }

                        SysLib.short2bytes((short) currentBlockNumber, indirectBlockData, indirectOffset);

                        // write back immediately
                        SysLib.rawwrite(ftEnt.inode.indirect, indirectBlockData);

                        overflow = true;
                    }

                    // get the data
                    SysLib.rawread(currentBlockNumber, curBlockData);

                } else {
                    // get direct block data
                    currentDataPosition = ftEnt.seekPtr % Disk.blockSize;

                    // same thing, check for EOF = direct is -1
                    if (ftEnt.inode.direct[currentBlock] == -1) {
                        // get new Block
                        ftEnt.inode.direct[currentBlock] = (short) superblock.getFreeBlock();

                        // error
                        if (ftEnt.inode.direct[currentBlock] == -1) {
                            SysLib.cerr("Unable to allocate more space for file\n");
                            return bufferOffset;
                        }

                        overflow = true;
                    }

                    // get data
                    SysLib.rawread(ftEnt.inode.direct[currentBlock], curBlockData);
                }

            }

            // can start writing now
            int tempSeek = ftEnt.seekPtr;

            // still has data to write and has not reached max size
            while (bufferLeft > 0 && tempSeek < maximumSize) {
                // check EOF
                if (tempSeek == ftEnt.inode.length) {
                    overflow = true;
                }

                // check if end of block has been reached
                if (currentDataPosition == Disk.blockSize) {
                    // reset data position
                    currentDataPosition = 0;

                    // check if already read all direct blocks
                    ++currentBlock;
                    if (!indirect && currentBlock == Inode.directSize) {
                        // write back data first
                        SysLib.rawwrite(ftEnt.inode.direct[currentBlock - 1], curBlockData);

                        // change mode to indirect & read
                        indirect = true;
                        currentBlock = 0;

                        // check not valid indirect block
                        if (ftEnt.inode.indirect == -1) {
                            // allocate new block
                            ftEnt.inode.indirect = (short) superblock.getFreeBlock();

                            // error
                            if (ftEnt.inode.indirect == -1) {
                                SysLib.cerr("Unable to allocate more space for file\n");
                                return bufferOffset;
                            }
                        }

                        // get free block for indirect
                        short freeBlockNumber = (short) superblock.getFreeBlock();

                        // error
                        if (freeBlockNumber == -1) {
                            SysLib.cerr("Unable to allocate more space for file\n");
                            return bufferOffset;
                        }

                        // put to indirect block & write back immediately
                        SysLib.short2bytes(freeBlockNumber, indirectBlockData, 0);
                        SysLib.rawwrite(ftEnt.inode.indirect, indirectBlockData);

                        // store the 1st block indirect points to
                        SysLib.rawread(freeBlockNumber, curBlockData);

                    } else if (indirect) {
                        // for indirect block
                        // write back data first
                        int currentBlockNumber = SysLib.bytes2short(indirectBlockData, (currentBlock - 1) * 2);
                        SysLib.rawwrite(currentBlockNumber, curBlockData);

                        // get next indirect block number
                        currentBlockNumber = SysLib.bytes2short(indirectBlockData, currentBlock * 2);

                        // invalid block number (can't be super block)
                        if (currentBlockNumber == 0) {
                            // get new Block
                            currentBlockNumber = superblock.getFreeBlock();

                            // error
                            if (currentBlockNumber == -1) {
                                SysLib.cerr("Unable to allocate more space for file\n");
                                return bufferOffset;
                            }

                            SysLib.short2bytes((short) currentBlockNumber, indirectBlockData, currentBlock * 2);

                            // write back immediately
                            SysLib.rawwrite(ftEnt.inode.indirect, indirectBlockData);
                        }

                        SysLib.rawread(currentBlockNumber, curBlockData);

                    } else {
                        // for direct block case
                        // write back data first
                        SysLib.rawwrite(ftEnt.inode.direct[currentBlock - 1], curBlockData);

                        // check for invalid
                        if (ftEnt.inode.direct[currentBlock] == -1) {
                            // get new Block
                            ftEnt.inode.direct[currentBlock] = (short) superblock.getFreeBlock();

                            // error
                            if (ftEnt.inode.direct[currentBlock] == -1) {
                                SysLib.cerr("Unable to allocate more space for file\n");
                                return bufferOffset;
                            }
                        }

                        SysLib.rawread(ftEnt.inode.direct[currentBlock], curBlockData);
                    }
                }

                // write data
                curBlockData[currentDataPosition] = buffer[bufferOffset];

                // increment/decrement neccessary variables
                ++bufferOffset;
                ++currentDataPosition;
                ++tempSeek;
                --bufferLeft;

                if (overflow)
                    ++additionalSize;
            }

            // finish loop, buffer empty or maxSize reached
            // write back remaining data
            if (indirect) {
                int blockNumberFromIndirect = SysLib.bytes2short(indirectBlockData, currentBlock * 2);
                SysLib.rawwrite(blockNumberFromIndirect, curBlockData);
            } else {
                SysLib.rawwrite(ftEnt.inode.direct[currentBlock], curBlockData);
            }

            // update fileSize & seekPtr & write inode to disk
            ftEnt.inode.length += additionalSize;
            ftEnt.seekPtr = tempSeek;
            ftEnt.inode.toDisk(ftEnt.iNumber);

            return bufferOffset; // how many bytes written
        }
    }

    /**
     * Deallocate all blocks that allocated for the current file.
     * 
     * @param ftEnt the File Table Entry that contains the file
     *              you want all blocks to be deallocated.
     * @return true if success and false otherwise.
     */
    private boolean deallocAllBlocks(FileTableEntry ftEnt) {
        Inode currentInode = ftEnt.inode;

        // if other threads are using this besides the calling
        // thread, do not deallocate it
        if (currentInode.count > 1)
            return false;

        /*
         * Algorithm:
         * Direct blocks:
         * - Clear all of those by clear the block's contents
         * and return the block to freeList.
         * - Set direct pointer to invalid (-1)
         * 
         * Indirect:
         * - Go over the blocks pointed by the indirect block.
         * - Recall that block # uses short
         * - Set pointer to invalid
         */

        for (int i = 0; i < currentInode.direct.length; i++) {
            // direct ptr part
            // skip unused block (-1)
            if (currentInode.direct[i] != -1) {
                clearAndFreeBlock(currentInode.direct[i]);

                currentInode.direct[i] = -1; // reset
            }
        }

        // indirect prt part
        // skip invalid ptr (-1)
        if (currentInode.indirect != -1) {
            // get the block first
            byte[] indirectBlock = new byte[Disk.blockSize];
            SysLib.rawread(currentInode.indirect, indirectBlock);

            // go over the block and clear
            int offset = 0;
            for (int i = 0; i < Disk.blockSize / 2; i++, offset += 2) {
                int currentBlockNumber = SysLib.bytes2int(indirectBlock, offset);

                // check for valid
                // cant't free superblock
                if (currentBlockNumber == -1 || currentBlockNumber == 0) {
                    continue;
                }

                clearAndFreeBlock(currentBlockNumber);
            }

            // reset
            currentInode.indirect = -1;
        }

        return true;
    }

    /**
     * A helper method for the deallocAllBlocks method.
     * This method clear the content of the block, and return
     * that block to the free list manages by the superblock.
     * 
     * @param blockNumber the block number you want to clear
     *                    and return to the free list.
     */
    private void clearAndFreeBlock(int blockNumber) {
        byte[] newContent = new byte[Disk.blockSize];
        SysLib.rawwrite(blockNumber, newContent);
        superblock.returnBlock(blockNumber);
    }

    /**
     * Destroys the file specified by fileName. If the file
     * is currently open, it's not destroyed until the last
     * open on it is closed, but new attempts to open it wil
     * fail.
     * 
     * @param filename the file name you want to delete
     * @return true if success and false otherwise
     */
    boolean delete(String filename) {
        // this statement effectively clear all blocks
        FileTableEntry ftEnt = open(filename, "w");

        // failed to deallocate blocks
        if (ftEnt == null)
            return false;

        short iNumber = ftEnt.iNumber;

        // attempt to close, but will only success if
        // there's no more thread using it
        return close(ftEnt) && directory.ifree(iNumber);
    }

    /**
     * Updates the seek pointer corresponding to the file.
     * If:
     * - whence = 0: seek ptr is offset from beginning of file.
     * - whence = 1: set to current value + offset
     * - whence = 2: set to size of file + offset
     * 
     * @param ftEnt  the File Table Entry that contains the file
     *               you want to set the seek pointer.
     * @param offset the offset from the position that is dependent
     *               of the "whence" option.
     * @param whence where do you want the seek pointer to offset from.
     * @return the offset location of the seek pointer (relative to)
     *         the file beginning. Return -1 if whence or the file
     *         table entry is invalid.
     */
    int seek(FileTableEntry ftEnt, int offset, int whence) {
        // invalid entry
        if (ftEnt == null)
            return -1;

        synchronized (ftEnt) {
            /*
             * System.out.println( "seek: offset=" + offset +
             * " fsize=" + fsize( ftEnt ) +
             * " seekptr=" + ftEnt.seekPtr +
             * " whence=" + whence );
             */

            switch (whence) {
                case SEEK_SET:
                    // seek ptr is offset from beginning of file
                    ftEnt.seekPtr = offset;
                    break;

                case SEEK_CUR:
                    // set to current value + offset
                    ftEnt.seekPtr += offset;
                    break;

                case SEEK_END:
                    // set to size of file + offset
                    ftEnt.seekPtr = ftEnt.inode.length + offset;
                    break;

                default:
                    // invalid whence
                    return -1;
            }

            // if attemp to set to the negative number, then
            // clamp to the beginning of file
            if (ftEnt.seekPtr < 0) {
                ftEnt.seekPtr = 0;

            } else if (ftEnt.seekPtr > ftEnt.inode.length) {
                // attemp to set beyond the file size, then set
                // to the end of the file
                ftEnt.seekPtr = ftEnt.inode.length;
            }

        }

        // return the offset from beginning of file
        return ftEnt.seekPtr;
    }
}