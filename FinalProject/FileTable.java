import java.util.Vector;

/**
 * This is a system-maintained table share among all user threads.
 * It stores all the open file and keeps track of how many threads
 * are using this file.
 */
public class FileTable {
	// File Structure Table

	private Vector<FileTableEntry> table;// the entity of File Structure Table
	private Directory dir; // the root directory

	/**
	 * Constructor that initialize the File Table and initialize
	 * the given directory as a root directory.
	 * 
	 * @param directory the root directory
	 */
	public FileTable(Directory directory) {
		table = new Vector<FileTableEntry>();
		dir = directory;
	}

	/**
	 * Allocate a new FileTableEntry for this file name.
	 * 
	 * Allocate/retrieve and register the corresponding inode
	 * using the root dir and increment this inode's count.
	 * 
	 * Immediately write back this inode to the disk and return
	 * a reference to this FileTable Entry.
	 * 
	 * @param fname the name of the file you want to allocate
	 * @param mode  the access mode of the file
	 * @return a reference to the newly allocated FileTableEntry.
	 *         Return null if unsuccessful.
	 */
	public synchronized FileTableEntry falloc(String fname, String mode) {
		// find the inode first
		short iNodeNumber = dir.namei(fname);
		short flag = -1; // used later after finding the inode

		// if file doesn't exist and mode is different than read,
		// then create the file
		if (iNodeNumber == -1) {
			if (!mode.equals("r")) {
				iNodeNumber = dir.ialloc(fname);

				// check for success
				if (iNodeNumber == -1) {
					SysLib.cerr("Unable to allocate " + fname + "\n");
					return null;
				}

				// set flag to write only
				flag = 5;

			} else {
				SysLib.cerr("File " + fname + " doesn't exist for read\n");
				return null;
			}
		}

		// retrieve the inode
		Inode newInode = new Inode(iNodeNumber);

		/*
		 * 0: unused
		 * 1: used (r)
		 * 2: used (!r - currently used, but not being read from)
		 * 3: unused (wreg - available for writing)
		 * 4: used (r & wreg)
		 * 5: used (!r but wreg)
		 */

		// if it's being written to, then wait
		if (newInode.flag == 2 || newInode.flag == 4 || newInode.flag == 5) {
			try {
				wait();
			} catch (InterruptedException e) {
				SysLib.cerr(e.getMessage() + "\n");
			}
		}

		// set the file flag
		switch (mode) {
			case "r":
				flag = 1;
				break;

			case "w":
				flag = 2;
				break;

			case "w+":
				// if flag has been set (only when the file doesn't
				// exist), then don't reset it
				if (flag == -1) {
					flag = 4;
				}
				break;

			case "a":
				flag = 5;
				break;
		}

		// now, the rest is just initialize a new entry
		newInode.flag = flag;
		++newInode.count;

		// write all new info to disk immediately
		newInode.toDisk(iNodeNumber);

		// add entry to table
		FileTableEntry newEntry = new FileTableEntry(newInode, iNodeNumber, mode);
		table.add(newEntry);

		return newEntry;
	}

	/**
	 * Receive a FileTableEntry reference and save the corresponding
	 * inode to disk. Then free this entry.
	 * 
	 * @param e the FileTableEntry you want to free
	 * @return true if this entry is found and false otherwise
	 */
	public synchronized boolean ffree(FileTableEntry e) {
		// receive a file table entry
		// free the file table entry corresponding to this index
		if (table.removeElement(e) == true) { // find this file table entry
			e.inode.count--; // this entry no longer points to this inode

			/*
			 * 0: unused
			 * 1: used (r)
			 * 2: used (!r - currently used, but not being read from)
			 * 3: unused (wreg - available for writing)
			 * 4: used (r & wreg)
			 * 5: used (!r but wreg)
			 */
			switch (e.inode.flag) {
				case 1:
					e.inode.flag = 0;
					break;
				case 2:
					e.inode.flag = 0;
					break;
				case 4:
					e.inode.flag = 3;
					break;
				case 5:
					e.inode.flag = 3;
					break;
			}

			e.inode.toDisk(e.iNumber); // reflect this inode to disk
			e = null; // this file table entry is erased.
			notify();
			return true;
		} else
			return false;
	}

	/**
	 * Check if the file table is empty.
	 * 
	 * @return true if the table is empty and false otherwise.
	 */
	public synchronized boolean fempty() {
		return table.isEmpty(); // return if table is empty
	} // called before a format
}
