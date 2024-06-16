import java.util.*; // SysLib_org.java

/**
 * A class that implements the system calls for the ThreadOS.
 */
public class SysLib {
    public static int exec(String args[]) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.EXEC, 0, args);
    }

    public static int join() {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.WAIT, 0, null);
    }

    public static int boot() {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.BOOT, 0, null);
    }

    public static int exit() {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.EXIT, 0, null);
    }

    public static int sleep(int milliseconds) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.SLEEP, milliseconds, null);
    }

    public static int disk() {
        return Kernel.interrupt(Kernel.INTERRUPT_DISK,
                0, 0, null);
    }

    public static int cin(StringBuffer s) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.READ, 0, s);
    }

    public static int cout(String s) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.WRITE, 1, s);
    }

    public static int cerr(String s) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.WRITE, 2, s);
    }

    public static int rawread(int blkNumber, byte[] b) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.RAWREAD, blkNumber, b);
    }

    public static int rawwrite(int blkNumber, byte[] b) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.RAWWRITE, blkNumber, b);
    }

    public static int sync() {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.SYNC, 0, null);
    }

    public static int cread(int blkNumber, byte[] b) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.CREAD, blkNumber, b);
    }

    public static int cwrite(int blkNumber, byte[] b) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.CWRITE, blkNumber, b);
    }

    public static int flush() {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.CFLUSH, 0, null);
    }

    public static int csync() {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.CSYNC, 0, null);
    }

    public static String[] stringToArgs(String s) {
        StringTokenizer token = new StringTokenizer(s, " ");
        String[] progArgs = new String[token.countTokens()];
        for (int i = 0; token.hasMoreTokens(); i++) {
            progArgs[i] = token.nextToken();
        }
        return progArgs;
    }

    public static void short2bytes(short s, byte[] b, int offset) {
        b[offset] = (byte) (s >> 8);
        b[offset + 1] = (byte) s;
    }

    public static short bytes2short(byte[] b, int offset) {
        short s = 0;
        s += b[offset] & 0xff;
        s <<= 8;
        s += b[offset + 1] & 0xff;
        return s;
    }

    public static void int2bytes(int i, byte[] b, int offset) {
        b[offset] = (byte) (i >> 24);
        b[offset + 1] = (byte) (i >> 16);
        b[offset + 2] = (byte) (i >> 8);
        b[offset + 3] = (byte) i;
    }

    public static int bytes2int(byte[] b, int offset) {
        int n = ((b[offset] & 0xff) << 24) + ((b[offset + 1] & 0xff) << 16) +
                ((b[offset + 2] & 0xff) << 8) + (b[offset + 3] & 0xff);
        return n;
    }

    // ========= Final Project Additional System Calls =========

    /**
     * Format the disk with the maximum number of files.
     * 
     * @param files The maximum number of files to be created
     * @return 0 if success and -1 otherwise
     */
    public static int format(int files) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.FORMAT, files, null);
    }

    /**
     * Open the files specified by the fileName in the given mode.
     * 
     * @param fileName the file you want to open.
     * @param mode     the mode you want to open the file with.
     * @return 0 if success and -1 otherwise
     */
    public static int open(String fileName, String mode) {
        String[] args = { fileName, mode };
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.OPEN, 0, args);
    }

    /**
     * Reads up to the buffer's length butes from the file
     * indicated by the file descriptor fd, starting at the
     * position currently pointed to by the seek pointer.
     * 
     * @param fd     the file descriptor you want to read.
     * @param buffer the buffer to read contents into.
     * @return the number of bytes that have been read, or
     *         a negative number upon error.
     */
    public static int read(int fd, byte buffer[]) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.READ, fd, buffer);
    }

    /**
     * Write the contents of the buffer to the file indicated
     * by the file descriptor, starting at the position pointed
     * to by the seek pointer.
     * 
     * @param fd     the file descriptor you want to write to.
     * @param buffer the buffer that contains information
     *               to write.
     * @return the number of bytes that has been written, or
     *         a negative number upon error.
     */
    public static int write(int fd, byte buffer[]) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.WRITE, fd, buffer);
    }

    /**
     * Updates the seek pointer corresponding to the file.
     * If:
     * - whence = 0: seek ptr is offset from beginning of file.
     * - whence = 1: set to current value + offset
     * - whence = 2: set to size of file + offset
     * 
     * @param fd     the file descriptor you want to seek.
     * @param offset the offset from the position that is dependent
     *               of the "whence" option.
     * @param whence where do you want the seek pointer to offset from.
     * @return the offset location of the seek pointer (relative to)
     *         the file beginning. Return -1 if whence or the file
     *         is invalid.
     */
    public static int seek(int fd, int offset, int whence) {
        int[] args = { offset, whence };
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.SEEK, fd, args);
    }

    /**
     * Close the file corresponding to the file descriptor
     * and commits all file transactions on this file. Also
     * unregistered fd from the user file descriptor table
     * of the calling thread's TCB.
     * 
     * @param fd the file you want to close.
     * @return 0 if success and -1 otherwise.
     */
    public static int close(int fd) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.CLOSE, fd, null);
    }

    /**
     * Delete the file specified by fileName. If the file is
     * currently open, it is not destroyed until the last open
     * on it is closed, but new attempts to open it will fail.
     * 
     * @param fileName the file you want to delete.
     * @return 0 if success and -1 otherwise.
     */
    public static int delete(String fileName) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.DELETE, 0, fileName);
    }

    /**
     * Return the size in bytes of the file indicated by the
     * file descriptor fd.
     * 
     * @param fd the file you want to know the size of.
     * @return the size in bytes of the file indicated by fd.
     */
    public static int fsize(int fd) {
        return Kernel.interrupt(Kernel.INTERRUPT_SOFTWARE,
                Kernel.SIZE, fd, null);
    }
}
