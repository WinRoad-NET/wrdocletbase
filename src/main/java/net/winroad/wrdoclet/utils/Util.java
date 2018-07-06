package net.winroad.wrdoclet.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

public class Util {
	private static Logger logger = LoggerFactory.getLogger(Util.class);
	private static YUICompressorWrapper compressor = new YUICompressorWrapper();

	public static String combineFilePath(String path1, String path2) {
		return new File(path1, path2).toString();
	}

	public static boolean ensureDirectoryExist(String directoryName) {
		File theDir = new File(directoryName);

		// if the directory does not exist, create it
		if (!theDir.exists()) {
			return theDir.mkdir();
		}
		return false;
	}

	public static void outputFile(InputStream inputStream, String filename)
			throws IOException {
		File file = new File(filename);
		if (!file.exists()) {
			if(file.createNewFile()) {
				logger.debug("File created: "+ filename);
			} else {
				logger.debug("File already exists when creating: " + filename);
			}
		}
		OutputStream outputStream = new FileOutputStream(filename);
		try {
			int byteCount = 0;
			byte[] bytes = new byte[1024];
			while ((byteCount = inputStream.read(bytes)) != -1) {
				outputStream.write(bytes, 0, byteCount);
			}
		} finally {
			outputStream.close();
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}

	public static void copyResourceFolder(String resourceFolder, String destDir)
			throws IOException {
		final File jarFile = new File(Util.class.getProtectionDomain()
				.getCodeSource().getLocation().getPath());
		if (jarFile.isFile()) { // Run with JAR file
			resourceFolder = StringUtils.strip(resourceFolder, "/");
			final JarFile jar = new JarFile(jarFile);
			// gives ALL entries in jar
			final Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry element = entries.nextElement();
				final String name = element.getName();
				// filter according to the path
				if (name.startsWith(resourceFolder + "/")) {
					String resDestDir = Util.combineFilePath(destDir,
							name.replaceFirst(resourceFolder + "/", ""));
					if (element.isDirectory()) {
						File newDir = new File(resDestDir);
						if (!newDir.exists()) {
							boolean mkdirRes = newDir.mkdirs();
							if (!mkdirRes) {
								logger.error("Failed to create directory "
										+ resDestDir);
							}
						}
					} else {
						InputStream inputStream = null;
						try {
							inputStream = Util.class.getResourceAsStream("/"
									+ name);
							if (inputStream == null) {
								logger.error("No resource is found:" + name);
							} else {
								Util.outputFile(inputStream, resDestDir);
							}

							/* compress js files */
							inputStream = Util.class.getResourceAsStream("/"
									+ name);
							compressor.compress(inputStream, name, destDir);
						} finally {
							if (inputStream != null) {
								inputStream.close();
							}
						}
					}
				}
			}
			jar.close();
		} else { // Run with IDE
			final URL url = Util.class.getResource(resourceFolder);
			if (url != null) {
				try {
					final File src = new File(url.toURI());
					File dest = new File(destDir);
					FileUtils.copyDirectory(src, dest);
					Util.compressFilesInDir(src, destDir);
				} catch (URISyntaxException ex) {
					logger.error(ex);
				}
			}
		}
	}

	public static void compressFilesInDir(File file, String destDir)
			throws IOException {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if(files != null) {
				for (File f : files) {
					compressFilesInDir(
							f,
							f.isDirectory() ? Util.combineFilePath(destDir,
									f.getName()) : destDir);
				}
			}
		} else {
			compressor.compress(file, destDir);
		}
	}

	public static String urlConcat(String url1, String url2) {
		if (url1 == null) {
			return url2;
		}
		if (url2 == null) {
			return url1;
		}
		if (!url1.endsWith("/") && !url2.startsWith("/")) {
			url1 = url1 + "/";
		}
		return url1 + url2;
	}

	public static String uncapitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		int capitalIndex = -1;
		for (int i = 0; i < str.length(); i++) {
			if (str.charAt(i) >= 'A' && str.charAt(i) <= 'Z') {
				capitalIndex = i;
			} else {
				break;
			}
		}
		if (capitalIndex < 0) {
			return str;
		} else if (capitalIndex == str.length() - 1) {
			return str.toLowerCase();
		} else {
			return str.substring(0, capitalIndex + 1).toLowerCase()
					+ str.substring(capitalIndex + 1);
		}
	}
	
	public static String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase()
				+ str.substring(1);
	}	
	
    public static String decodeUnicode(String theString) {
        char aChar;
        int len = theString.length();
        StringBuffer outBuffer = new StringBuffer(len);
        for (int x = 0; x < len;) {
            aChar = theString.charAt(x++);
            if (aChar == '\\') {
                aChar = theString.charAt(x++);
                if (aChar == 'u') {
                    // Read the xxxx
                    int value = 0;
                    for (int i = 0; i < 4; i++) {
                        aChar = theString.charAt(x++);
                        switch (aChar) {
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                            value = (value << 4) + aChar - '0';
                            break;
                        case 'a':
                        case 'b':
                        case 'c':
                        case 'd':
                        case 'e':
                        case 'f':
                            value = (value << 4) + 10 + aChar - 'a';
                            break;
                        case 'A':
                        case 'B':
                        case 'C':
                        case 'D':
                        case 'E':
                        case 'F':
                            value = (value << 4) + 10 + aChar - 'A';
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Malformed   \\uxxxx   encoding.");
                        }
                    }
                    outBuffer.append((char) value);
                } else {
                    if (aChar == 't')
                        aChar = '\t';
                    else if (aChar == 'r')
                        aChar = '\r';
                    else if (aChar == 'n')
                        aChar = '\n';
                    else if (aChar == 'f')
                        aChar = '\f';
                    outBuffer.append(aChar);
                }
            } else
                outBuffer.append(aChar);
        }
        return outBuffer.toString();
    }
    
    public static Set<String> parseStringSet(String str) {
    	str = StringUtils.strip(str, "{} ");
    	String[] arr = str.split(",");
    	Set<String> result = new HashSet<>();
    	for(String s : arr) {
    		result.add(StringUtils.strip(s, "\" "));
    	}
    	return result;
    }
    
	public static void main(String[] args) {
		String str="sno \\u5b66\\u53f7 should not be empty"; 		
		System.out.println(decodeUnicode(str));
		
		String strs = "{\"aaa\", \"bb\"}";
		System.out.println(parseStringSet(strs));
	}
}
