/**
 * The entry of the File Table.
 * 
 * Stores the seek pointer, the inode number of a file,
 * a count of how many threads are sharing this, and the
 * current access mode.
 * 
 * Note that the class variables are public because this
 * is used internally by other classes and the user won't
 * be able to access it. Just reduces the redundant getters
 * and setters.
 */
public class FileTableEntry {
    public int seekPtr; // a file seek pointer
    public final Inode inode; // a reference to an inode
    public final short iNumber;// this inode number
    public int count; // a count to maintain #threads sharing this
    public final String mode; // "r", "w", "w+", or "a"

    /**
     * Constructor that initialize a File Table Entry with
     * the inode number, pointer the inode, and the access
     * mode. Unless the access mode is "a" (append), then
     * The seek pointer is set to the beginning of the file.
     * 
     * @param i       a reference to the inode
     * @param inumber the inode number
     * @param m       the access mode
     */
    FileTableEntry(Inode i, short inumber, String m) {
        seekPtr = 0; // the seek pointer is set to the file top.
        inode = i;
        iNumber = inumber;
        count = 1; // at least one thread is using this entry.
        mode = m; // once file access mode is set, it never changes.

        // append mode
        if (mode.compareTo("a") == 0)
            seekPtr = inode.length;
    }
}
