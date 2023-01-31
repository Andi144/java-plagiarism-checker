package util;

import net.lingala.zip4j.ZipFile;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Stream;

public class SubmissionUnpacking {
	
	/**
	 * Same as {@link #unpackMoodleSubmissions(Path submissionsZip, Path unpackDir)} with <code>unpackDir</code> set to
	 * <code>submissionsZip</code> with the additional postfix <code>"_UNPACKED"</code>.
	 */
	public static List<String> unpackMoodleSubmissions(Path submissionsZip) throws IOException {
		return unpackMoodleSubmissions(submissionsZip, Path.of(submissionsZip + "_UNPACKED"));
	}
	
	/**
	 * Unpacks the ZIP file containing all Moodle submissions <code>submissionsZip</code> to the directory specified by
	 * <code>unpackDir</code> and returns a list of all student submission directories that each contain the individual
	 * student submission files (Java code, tests, etc.).
	 * <p>
	 * To actually get to the individual student submission files, a second, internal archive unpacking happens, since
	 * the initial submissions are just archive files again (course requirements of "Softwareentwicklung 1"). This
	 * happens automatically in this method call, so the returned list of student submission directories are ready to
	 * use.
	 * <p>
	 * This method deletes the directory <code>unpackDir</code> and all its content before the unpacking operation.
	 *
	 * @param submissionsZip The path to the ZIP file containing all Moodle submission
	 * @param unpackDir      The path to the directory where the ZIP file should be unpacked to
	 * @return A list of all student submission directories
	 * @throws IOException In case any of the IO operations fail (creating directory, unpacking, writing data)
	 */
	public static List<String> unpackMoodleSubmissions(Path submissionsZip, Path unpackDir) throws IOException {
		File unpackDirFile = unpackDir.toFile();
		if (unpackDirFile.exists()) {
			// Without setting the read-only attribute to false, an AccessDeniedException can potentially occur for some
			// files or directories when trying to delete the unpacking directory. However, we want to force the
			// deletion, since we are the ones that create the unpacking directory in the first place
			Files.walkFileTree(unpackDir, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Files.setAttribute(dir, "dos:readonly", false);
					return super.preVisitDirectory(dir, attrs);
				}
				
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.setAttribute(file, "dos:readonly", false);
					return super.visitFile(file, attrs);
				}
			});
			FileUtils.forceDelete(unpackDirFile);
		}
		
		// First, we need to unpack the main Moodle ZIP file that contains all submission directories. At the time of
		// writing this code, these submission directories have the format
		// <full name>_<internal ID>_assignsubmission_file_
		// and inside each of these directories, the actual student submissions are stored, which, for the course
		// "Softwareentwicklung 1", should be individual ZIP files that contain the actual data (Java code, tests, etc.)
		try (ZipFile zipFile = new ZipFile(submissionsZip.toString())) {
			zipFile.extractAll(unpackDir.toString());
		}
		
		// Second, we need to unpack these individual student submission ZIP files to get to the actual data. For each
		// such ZIP file, we will get a student submission directory where the unpacked contents are stored. These
		// directories are the one that we want to ultimately return
		Files.walkFileTree(unpackDir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				extract(file);
				// We do not need the student submission ZIP file, we only need the actual data
				Files.delete(file);
				return super.visitFile(file, attrs);
			}
		});
		
		// Finally, return all the unpacked student submission directories
		try (Stream<Path> dirs = Files.list(unpackDir)) {
			return dirs.map(path -> path.toAbsolutePath().toString()).toList();
		}
	}
	
	/**
	 * Extracts the archive specified by <code>archivePath</code> to the same directory where this archive resides in.
	 *
	 * @param archivePath The path to the archive file
	 * @throws IOException In case any of the IO operations fail (creating directory, unpacking, writing data)
	 * @implNote The code is based on example on
	 * <a href="https://sevenzipjbind.sourceforge.net/extraction_snippets.html">sevenzipjbind</a>
	 */
	private static void extract(Path archivePath) throws IOException {
		Path parent = archivePath.getParent();
		
		try (RandomAccessFile randomAccessFile = new RandomAccessFile(archivePath.toFile(), "r");
		     IInArchive inArchive = SevenZip.openInArchive(null, // Autodetect archive type
				     new RandomAccessFileInStream(randomAccessFile))) {
			ISimpleInArchive archive = inArchive.getSimpleInterface();
			
			for (ISimpleInArchiveItem item : archive.getArchiveItems()) {
				Path itemPath = parent.resolve(item.getPath());
				
				if (!item.isFolder()) {
					Files.createDirectories(itemPath.getParent());  // Create intermediate directories
					
					ExtractOperationResult result = item.extractSlow(data -> {
						try (FileOutputStream fos = new FileOutputStream(itemPath.toFile())) {
							fos.write(data);
						} catch (IOException e) {
							// Wrap in SevenZipException since this is the one that ISequentialOutStream.write throws
							throw new SevenZipException("writing data failed: " + e.getMessage(), e);
						}
						return data.length; // Return amount of consumed data
					});
					
					if (result != ExtractOperationResult.OK) {
						throw new SevenZipException("extraction operation failed: " + result);
					}
				}
			}
		}
	}
	
}
