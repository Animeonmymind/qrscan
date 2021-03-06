package nl.lcs.qrscan.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * This task performs the thing mentioned in ScanTask. However, after all PDFs
 * are scanned for QR codes, the PDFs are then moved to the output directory and
 * renamed.
 *
 * <p>
 * For example, if a PDF file had the QR code '001', it is now renamed to
 * 001_1.pdf and moved to a sub directory 001 in the output directory (i.e.
 * outputDir/001/001.pdf). The suffix '_1' is to avoid collisions with renaming
 * (e.g. two files had the QR code '001'). The next file with QR code '001' will
 * be saved as 001_2.pdf, et cetera, all in the same sub directory /001/.
 * </p>
 *
 * <p>
 * If no QR code was found, no renaming or moving of that file is attempted.
 * </p>
 *
 * <p>
 * A CSV log file is created, including the old and new file paths.
 * </p>
 *
 * <p>
 * Empty directories in the input directory are not deleted.
 * </p>
 *
 * @author Lars Steggink
 *
 */
public class RenameTask extends ScanTask {

	final static private String LSEP = System.lineSeparator();
	private Path outputDir;

	/**
	 * This task performs the thing mentioned in ScanTask. However, after all
	 * PDFs are scanned for QR codes, the PDFs are then moved to the output
	 * directory and renamed.
	 * 
	 * @param inputDir
	 *            input directory with PDF files
	 * @param outputDir
	 *            main output directory for renamed PDF files
	 * @param qrCodePage
	 *            page where QR codes are expected in each PDF
	 * @param writeFileAttributes
	 *            whether to write a custom file attribute after a QR code has
	 *            been recognised
	 * @param useFileAttributes
	 *            whether to use the custom file attribute to get stored QR
	 *            codes instead of (slow) scanning
	 * @param openLogFile
	 *            whether to open the CSV log file at the end
	 */
	public RenameTask(Path inputDir, Path outputDir, int qrCodePage, boolean useFileAttributes,
			boolean writeFileAttributes, boolean openLogFile) {
		super(inputDir, qrCodePage, useFileAttributes, writeFileAttributes, openLogFile);
		this.outputDir = outputDir;
	}

	/**
	 * Iterates over every file, scans for QR codes, then renames.
	 * 
	 * <p>
	 * Note: first, all files are scanned for QR codes, only then renaming
	 * starts.
	 * </p>
	 * 
	 * @return list of results
	 */
	@Override
	protected List<SingleResult> call() {
		List<QrPdf> pdfs = findInputFiles(inputDir);
		List<SingleResult> results = scanInputFiles(pdfs);
		try {
			results = renameScanResults(results);
		} catch (IOException e) {
			updateMessage("!Unable to create or use output path.");
		}

		logResults(results, outputDir);
		return results;
	}

	/**
	 * Finds an unique filename within the main output directory.
	 *
	 * @param scanResult
	 *            scan result
	 * @throws IOException
	 *             if unable to create sub directory
	 *             @return chosen path
	 */
	private Path findTargetPath(SingleResult scanResult) throws IOException {
		String qr = scanResult.getQrCode();

		// Create sub directory within main output directory.
		Path subOutputDir = outputDir.resolve(qr);
		if (!Files.isDirectory(subOutputDir)) {
			Files.createDirectory(subOutputDir);
		}

		// Find a file name that is not yet taken by appending _1, _2, _3, etc.
		// With a low number of files in every directory, this is probably the
		// fastest approach.
		for (int i = 1; i <= (Files.list(subOutputDir).count() + 1); i++) {
			Path checkPath = subOutputDir.resolve(qr + "_" + i + ".pdf");
			if (Files.notExists(checkPath)) {
				return checkPath;
			}
		}
		// This should never happen. If it does, rename to current name.
		return scanResult.getInputFilePath();
	}

	/**
	 * Renames the PDFs based on the scan results.
	 * 
	 * @param scanResults
	 *            the scan results
	 * @return updated scan results, including the old and new file path
	 * @throws IOException
	 *             if unable to create or use output directory
	 */
	private List<SingleResult> renameScanResults(List<SingleResult> scanResults) throws IOException {
		int fileCount = scanResults.size();
		int success = 0;
		int failed = 0;
		int noQR = 0;
		int current = 0;
		updateMessage("Renaming starts now." + LSEP + "  Output directory: " + outputDir.getFileName());
		updateProgress(current, fileCount);

		// Create output directory.
		if (!Files.exists(outputDir)) {
			Files.createDirectory(outputDir);
			updateMessage("Output directory did not exist and has been created.");
		}

		// Iterate over every scanned file.
		for (SingleResult scanResult : scanResults) {
			current++;
			
			if(!scanResult.isQRCodeFound()) {
				// Skip this file.
				noQR++;
				continue;
			}
			
			// Find suitable renamed path and file name.
			Path outputPath = findTargetPath(scanResult);
			try {
				Path resultPath = Files.move(scanResult.getInputFilePath(), outputPath);
				scanResult.setOutputFilePath(resultPath);
				success++;
			} catch (IOException e) {
				// Exception raised during move.
				updateMessage("!Unable to rename " + scanResult.getInputFilePath().getFileName() + ".");
				failed++;
			}
		}

		updateMessage("Summary: tried renaming " + fileCount + " files, " + success + " successful, " + failed
				+ " unsuccesful, " + noQR + " not attempted (unable to find QR code).");
		return scanResults;
	}
}
