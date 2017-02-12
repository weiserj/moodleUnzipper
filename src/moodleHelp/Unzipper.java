package moodleHelp;

import java.io.*;
import java.util.*;
import java.util.regex.*;


/**
 * Zum Entzippen von Moodle AUfgaben. Basis ist ein von Moodle ehruntergeladenes
 * zip-Archiv mit allen Abgaben. Ergebnis sind die entpackten Aufgaben, sodass
 * sie einfach begutachtet und bewertet werden können. 
 * @author Johann Weiser
 */
public class Unzipper {

	/**
	 * Datentyp zur Info, warum gewisse Daten gewisser Schüler nicht vorhanden sind.
	 */
	private static enum FileInfo {
		fileNotFound, wrongSufix, tooBig, fileOkay
	}

	private static enum FileStructureType {
		/* kein Directory, Dateien heißen wie der zugehörige Name des Schülers + korrekte Endung
		 * Siehe auch Methode moveDirTreeDirect.
		 */
		noDir, 
		/* Es wird ein Directory angelegt und alles "flach", d.h ohne Directory-Struktur des
		 * zip-Files in dieses Directory kopiert. Der name des directories ist der Name des 
		 * Schülers.
		 * Siehe auch Methdoe moveDirTreeFlat.
		 */
		singleDir, 
		/* Kopiert alle Dateien und directories auf oberster Ebene in Dateien/Direcotries, die
		 * nach dem Schülernamen benannt sind (plus Endung). Weitere Directorystruktur bleibt erhalten 
		 * Siehe auch Methode moveDirTreeUnchanged
		 */
		dirTree, 
		/* Kopiert die gesamte Directorystruktur in ein subdirectory, welches nach dem Schüler
		 * benannt ist.
		 */
		dirTreePrefix
	}

	/**
	 *	Die Daten eines Schülers. 
	 */
	private static class StudentName {
		String name;
		String vorName;
		boolean ignore = false;
		// directory name for this name
		String subDir;
		String moodleName;
		FileInfo fileInfo = FileInfo.fileNotFound;
		
		StudentName(String name) {
			this.name = name;
			this.subDir = name.toLowerCase();
			moodleName = name.toLowerCase();
		}
		String getName() {return name + " " + (vorName==null?"":vorName);}
		String getAllString() { return getName() + ": " + (ignore?"ignore, ":"") +
			(name.equals(subDir)?"":subDir+", ") + (name.equals(moodleName)?"":moodleName+", " +
			fileInfo);
		}
	}
	
	/**
	 *  Allgemeine Konfigurationsdaten. Zuerst werden die Werte durch den Konstruktor 
	 *  gesetzt, dann gibt es einige Update-Methoden, welche diese klassenspezifisch ändern
	 *  zuletzt wird update() selbst aufgerufen um die fehlenden Daten mit 
	 *  Defaultwerten zu ergänzen.
	 *  
	 *  Enthält auch die Liste der Schüler!
	 */
	private static class ConfigurationData {
		boolean deleteHelp = true;
		boolean deleteUnzipDir = true;
		int maxFileSize=50_000_000;
		FileStructureType fst = FileStructureType.noDir;
		String dir7zip = "C:\\Programme\\7-Zip";
		// Enthält Dateien mit den Namen der Schüler
		String nameDir = "C:\\Weiser\\workspace1\\MoodleUnzipper\\data";
		String nameFile = null;
		String nameFileFull;
		//String unzipDirBase = "C:\\Weiser\\SJ1314\\Klasse-2CHIT";
		String unzipDirBase = "C:\\Weiser\\SJ1314\\Klasse-1CHIT\\SEW";
		String unzipSubdir = "A02";
		String zipDir = null;
		String unzipDir = null;
		String zipFile =null;
		String zipFileFull;
		String helpDir = null;
		TreeSet<StudentName> studentList;
		String[] suffixList = null;
		String klasse = null;
		
		/**
		 * Ergänzen der fehlenden Datenmit defaultwerten.
		 */
		private void update() {
			zipFile = unzipSubdir +  ".zip";
			if (unzipDir == null) {
				unzipDir = unzipDirBase + "\\" + unzipSubdir;
			}
			if (zipDir == null) {
				zipDir = unzipDirBase;
			}
			zipFileFull = zipDir + "\\" + zipFile;
			nameFileFull = nameDir + "\\" + nameFile;
			if (helpDir == null) {
				helpDir = zipDir + "\\help\\" + unzipSubdir;
			}
			studentList = new TreeSet<StudentName>(comp);
		}
		
		/**
		 * Ausgabe der Konfigurationsdaten (ohne Schüler).
		 */
		void print() {
			System.out.println("\nConfiguration Data:");
			System.out.println("   nameFileFull: " + nameFileFull);
			System.out.println("   zipFileFull:  " + zipFileFull);
			System.out.println("   helpDir:      " + helpDir);
			System.out.println("   unzipDirBase: " + unzipDirBase);
			System.out.println("   unzipSubdir:  " + unzipSubdir);
			System.out.println("   unzipDir:     " + unzipDir);
			System.out.println("   klasse:       " + klasse);
			System.out.print("   suffixList:   " + (suffixList==null?"null":""));
			if (suffixList != null) {
				for (int i=0;i<suffixList.length;i++) {
					System.out.print((i==0?"":", ")+suffixList[i]);
				}
			}
			System.out.println();
		}
		
		/**
		 * Setzt das ignore-Flag für einen Namen, wird eigentlich nicht benutzt
		 * @param name
		 * @return
		 */
		@SuppressWarnings("unused")
		boolean ignoreName(String name) {
			for (StudentName sn:studentList) {
				if (sn.name.contains(name) || sn.name.contains(name) || sn.name.contains(name)) {
					sn.ignore = true;
					System.out.println(sn.getName()+ " ignored!");
					return true;
				}
			}
			return false;
		}
		
		/**
		 * Schreibt Liste der Schüler auf stdout.
		 */
		void writeNames() {
			int noOkay = 0, noNotFound = 0, noWrongSuffix = 0, noTooBig = 0;
			System.out.println("\nList of include names:");
			for (StudentName sn:studentList) {
				System.out.println(sn.getAllString());
				switch (sn.fileInfo) {
				case fileNotFound: 
					noNotFound++;
					break;
				case fileOkay: 
					noOkay++;
					break;
				case tooBig: 
					noTooBig++;
					break;
				case wrongSufix: 
					noWrongSuffix++;
					break;
					
				}
			}
			System.out.println("okay:" + noOkay + ",   wrong suffix:" +  noWrongSuffix + 
				",   too big:" + noTooBig + ",   not found:" + noNotFound + ",   total:" + 
				(noOkay+noWrongSuffix+noTooBig+noNotFound));
		}
		
		/**
		 * Eine Hilfsklasse zum Sortieren der Schüler.
		 */
		private Comparator<StudentName> comp = new Comparator<StudentName>() {
			private int compareName(StudentName o1, StudentName o2) {
				//System.out.println("o1="+o1+ ", o2="+ o2);
				//System.out.println("o1.n="+o1.nachName+ ", o2.n="+ o2.nachName);
				int result = o1.name.compareTo(o2.name);
				if (result == 0) {
					return o1.vorName.compareTo(o2.vorName);
				}
				return result;
			}
			@Override
			public int compare(StudentName o1, StudentName o2) {
				return compareName(o1, o2);
			}
			
		};
	}
	
	/**
	 * Eine einfache Exception, welche bei fehlern verwendet wird.
	 */
	@SuppressWarnings("serial")
	static class UnzipException extends RuntimeException {
		UnzipException(String text) {
			super(text);
		}
		UnzipException(String text, Exception e) {
			super(text, e);
		}
	}
	
	private ConfigurationData cd = new ConfigurationData();

	
	/**
	 * File enthält die Namen der Schüler und danach eventuell ein ignore-Flag, das zu 
	 * erzeugende subdir und den Namen in moodle.
	 * Trennzeichen ist der Strichpunkt.
	 * @param fileName
	 */
	void readNameFile(ConfigurationData cd) {
		BufferedReader f = null;
		try {
			f = new BufferedReader(new InputStreamReader(new FileInputStream(cd.nameFileFull),"UTF-8"));
			String line = f.readLine();
			while (line != null) {
				body1: {
					if (line.startsWith("*")) {
						// Kommentarzeile
						break body1;
					}
					line = line.trim();
					if (line.length()==0) {
						// Leerzeile
						break body1;
					}
					String[] token = line.split(";");
					for (int i=0;i<token.length;i++) 
						token[i] = token[i].trim();
					if (token.length < 1) {
						// vielleicht eine Leerzeile, jedenfalls  nichts vernünftiges
						break body1;
					}
					StudentName t = new StudentName(token[0]);
					if ((token.length>2)&&(token[2].length()>0)) {
						t.ignore = true;
					}
					if ((token.length>1)&&(token[1].length()>0)) {
						t.vorName = token[1];
					}
					if ((token.length>3)&&(token[3].length()>0)) {
						t.subDir = token[3];
					}
					if ((token.length>4)&&(token[4].length()>0)) {
						t.moodleName = token[4];
					}
					cd.studentList.add(t);
					System.out.println("added: " + t.name);
				} // end of body1
				line = f.readLine();
			}
			f.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Entzippt ein Archiv-File mit 7zip.
	 * @param fileName
	 * @param destDir
	 */
	private void unzipFile(String fileName, String destDir) {
		try {
			String params = "/C " + cd.dir7zip + "\\7z.exe" + " x -o\"" + destDir + "\" \"" + fileName + "\"";
			String program = "cmd.exe";
			//params = " x -o" + destDir + " " + fileName;
			//program = dir7zip + "\\7z.exe";
			System.out.println(program + " " + params); 
			ProcessBuilder pb = new ProcessBuilder(program, params);
			pb.inheritIO();
			Process p = pb.start();
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Entzippt das Moodlefile in ein Hilfsdirectory.
	 */
	private void unzipMoodleFile(ConfigurationData cd) {
		File f = new File(cd.zipFileFull);
		if (!f.exists() || !f.isFile()) {
			throw new UnzipException("zipFile \"" + cd.zipFileFull + "\" does not exist!");
		}
		unzipFile(cd.zipFileFull, cd.helpDir);
	}
	
	/**
	 * Löschtr ein directory und den darunterliegenden Directory/File baum vollständig.
	 * @param dir
	 */
	private void deleteDir(String dir) {
		try {
			Process p = new ProcessBuilder("cmd.exe", "/C rmdir /s /q " + dir).start();
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Löscht Hilfs- und Zieldirectory - am Beginn einens neuen Uzipper-Laufes.
	 * @param cd
	 */
	private void init(ConfigurationData cd) {
		cd.update();
		if (cd.deleteHelp) {
			deleteDir(cd.helpDir);
			new File(cd.helpDir).mkdirs();
		}
		if (cd.deleteUnzipDir) {
			deleteDir(cd.unzipDir);
			new File(cd.unzipDir).mkdirs();
		}
		copyFilter = new MyFilenameFilter(cd);
	}

	/**
	 * Sucht Schüler in Schülerliste der Konfigurationsdaten.
	 * @param fileName
	 * @param cd
	 * @return
	 */
	private StudentName findStudent(String fileName, ConfigurationData cd) {
		String name = fileName.toLowerCase();
		// für Gruppenabgaben, aber "Gruppe" dürfte nur die deutsche Version unterstützen. 
		if (name.startsWith("gruppe ")) {
			int i = name.indexOf('-');
			if (i>=0) {
				name = name.substring(i+1);
			}
		}
		//System.out.println("Name: " + name);
		for (StudentName t: cd.studentList) {
			if (name.startsWith(t.moodleName)) {
				return t;
			}
			/*if (name.contains(t.packageName)) {
				return t;
			}*/
		}
		return null;
	}

	private void copyFileOrDir(String fromFile, String toFile) {
		File f = new File(fromFile);
		if (!f.isDirectory()) {
			copyFile(fromFile, toFile);
		} else {
			copyDir(fromFile, toFile);
		}
	}

	/**
	 * Kopiert ein einzelnes File.
	 * @param fromFile
	 * @param toFile
	 */
	private void copyFile(String fromFile, String toFile) {
		//System.out.println("Copy from \"" + fromFile + "\" to \"" + toFile + "\".");
		try {
			String params = "/C copy /Y \"" + fromFile + "\" \"" + toFile + "\"";
			String program = "cmd.exe";
			//System.out.println(program + " " + params); 
			ProcessBuilder pb = new ProcessBuilder(program, params);
			//pb.inheritIO();
			Process p = pb.start();
			int rc = p.waitFor();
			if (rc != 0) {
				System.out.println("Copy Command failed with rc = " + rc + ", command:" +
					"\n   " + program + " " + params);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * Kopiert ein diretory.
	 * @param fromFile
	 * @param toFile
	 */
	private void copyDir(String fromFile, String toFile) {
		//System.out.println("Copy from \"" + fromFile + "\" to \"" + toFile + "\".");
		try {
			String param = "xcopy /Y /E /I /H /Q \"" + fromFile + "\" \"" + toFile + "\"";
			String program = "cmd.exe";
			System.out.println(program + " " + param); 
			ProcessBuilder pb = new ProcessBuilder(program, "/S", "/C", param);
			pb.inheritIO();
			Process p = pb.start();
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
		
	private void renameFile(String fromFile, String toFile) {
		File tf = new File(toFile);
		try {
			String params = "/C rename \"" + fromFile + "\" \"" + tf.getName() + "\"";
			String program = "cmd.exe";
			//System.out.println(program + " " + params); 
			ProcessBuilder pb = new ProcessBuilder(program, params);
			//pb.inheritIO();
			Process p = pb.start();
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Ein Dateifilter für diverse Dateien, damit nicht alles kopiert wird.
	 */
	private MyFilenameFilter copyFilter;
	private static class MyFilenameFilter implements FilenameFilter {
		boolean withZipFiles = true;
		boolean standardExclusions = true;
		String[] includeList = null;
		public MyFilenameFilter(ConfigurationData cd) {
			includeList = cd.suffixList;
			includeListToLower();
		}
		
		public MyFilenameFilter(boolean withZipFiles, boolean standardExclusions, String[] includeList) {
			this.withZipFiles = withZipFiles;
			this.standardExclusions = standardExclusions;
			this.includeList = includeList;
			includeListToLower();
		}

		private void includeListToLower() {
			if (includeList == null) 
				return;
			for (int i=0;i<includeList.length;i++)
				includeList[i] = includeList[i].toLowerCase();
		}
		
		@Override
		public boolean accept(File dir, String name) {
			//System.out.println("acceptJF: dir=" + dir + ", name=" + name);
			if (new File(dir, name).isDirectory()) {
				return true;
			}
			String name1 = name.toLowerCase();
			if (standardExclusions) {
				if (name1.endsWith(".class") || name1.endsWith(".exe") || name1.endsWith(".bin") ||
					name1.endsWith(".dll") || name1.endsWith(".ctxt") || name1.endsWith(".bluej")) {
					return false;
				}
			}
			if (withZipFiles && isZipFile(name1)) {
				return true;
			}
			if (includeList == null || includeList.length==0) {
				return true;
			}
			// suffixList ist eine Positivliste!!!
			for (String suffix:includeList) {
				if (name1.endsWith("."+suffix))
					return true;
			}
			return false;
		}
	};

	/**
	 * Kopiert alle Files (mit Ausnahme einiger Filetypen (siehe copyFilter)) 
	 * vom dirTree in das destDir. Die Directory Struktur
	 * im dirTree wird ignoriert. 
	 * @param dirTree Das Basisdirectory des directory-Baums, von dem kopiert wird.
	 * @param destDir das Destination Directory.
	 */
	private void moveDirTreeFlat(String dirTree, String destDir) {
		File of = new File(dirTree);
		boolean copyZip = (cd.suffixList==null || cd.suffixList.length == 0);
		MyFilenameFilter cf = new MyFilenameFilter(copyZip, true, cd.suffixList);
		String[] fileList = of.list(cf);
		if (fileList == null) 
			return;
		for (String fn: fileList) {
			if (new File(dirTree, fn).isDirectory()) {
				moveDirTreeFlat(dirTree + "\\" +fn, destDir);
			} else {
				copySpecial(dirTree + "\\" + fn, destDir+"\\" + fn);
			}
		}

	}
	
	
	/**
	 * Ändert den package Namen in allen Java Files des angeführten Directories.
	 * @param Das Directory mit den java Files
	 * @param baseDir Das Basis-Sourcedirectory. Die nachfolgenden Subdirectories bilden den package-tree. 
	 */
	private void updatePackage(String dir, String baseDir) {	
		String packageName = null;
		if (dir.length() > (baseDir.length()+1)) {
			packageName = dir.substring(baseDir.length()+1);
			packageName = packageName.replace(File.separatorChar, '.');
		}
		String[] suffixList = {"java"};
		MyFilenameFilter cf = new MyFilenameFilter(false, true, suffixList);
		File fDir = new File(dir);
		String[] fileList = fDir.list(cf);
		if (fileList == null) 
			return;
		for (String fn: fileList) {
			if (new File(dir, fn).isDirectory()) {
				updatePackage(dir + "\\" +fn, baseDir);
			} else {
				updatePackageSingle(dir + "\\" + fn, packageName);
			}
		}
	}
	
	private void updatePackageSingle(String fileName, String packageName) {
		FileReader f = null;
		PrintWriter w = null;
		try {
			char[] cbuf = new char[(int)(new File(fileName).length())];
			f = new FileReader(fileName);
			f.read(cbuf);
			f.close();
			BufferedReader br = new BufferedReader(new StringReader(new String(cbuf)));
			w = new PrintWriter(new FileWriter(fileName));
			String line = br.readLine();
			boolean packageWritten = false;
			boolean classNameFound = false;
			String className = null;
			boolean isComment = false;
			String pattern = ".*(^|\\s)class(\\s+)([a-zA-Z]\\w*).*";
			Pattern p = Pattern.compile(pattern);
			while (line != null) {
				writePackage:
				if (!packageWritten||!classNameFound) {
					String l1 = line.trim();
					if (l1.length() == 0) {
						break writePackage;
					}
					if (!isComment) {
						if (l1.startsWith("//")) {
							break writePackage;
						}
						if (l1.startsWith("/*") && !l1.contains("*/")) {
							isComment = true;
							break writePackage;
						}
						if (!packageWritten) {
							if (l1.contains("package ")) {
								line = packageName==null?"":("package " + packageName + ";");
							} else {
								line = (packageName==null?"":("package " + packageName + ";\n")) + line;
							}
							packageWritten = true;
						}
						if (!classNameFound) {
							Matcher m = p.matcher(l1);
							if (m.matches()) {
								className = m.group(3);
								classNameFound = true;
							}
						}
					} else {
						if (l1.contains("*/")) {
							isComment = false;
						}
					}
				}
				w.println(line);
				line = br.readLine();
			}
			w.close();
			br.close();
			if (classNameFound) {
				File fi1 = new File(fileName);
				if (!fi1.getName().startsWith(className)) {
					fi1.renameTo(new File(fi1.getParentFile(), className+".java"));
				}
			}
			
		} catch (IOException e) {
			
		}
		
	}
	
	/**
	 * Copies a file or directory, adds a counter, if the file/directory
	 * already exists.
	 * @param fromName
	 * @param toName
	 */
	private void copySpecial(String fromName, String toName) {
		String tn;
		try {
			tn = new File(toName).getCanonicalPath();
			File tf = new File(tn);
			String tDir = tf.getParent();
			String tName = tf.getName();
			String tBaseName, tExtension;
			int i = tName.lastIndexOf(".");
			if (i == -1) {
				tBaseName = tName;
				tExtension = "";
			} else {
				tBaseName = tName.substring(0,i);
				tExtension = tName.substring(i);
			}
			String tName1 = tBaseName + "1" + tExtension;
			File tf1 = new File(tDir, tName1);
			if (!tf.exists()) {
				if (!tf1.exists()) {
					copyFileOrDir(fromName, toName);
				} else {
					i = 2;
					searchLoop:
					while (true) {
						File tf2 = new File(tDir, tBaseName + i + tExtension);
						if (!tf2.exists()) {
							copyFileOrDir(fromName, tf2.getCanonicalPath());
							break searchLoop;
						}
						i = i + 1;
					}
				} 
			} else {
				//renameFile(fromName, tBaseName + "1" + tExtension);
				//copyFileOrDir(fromName, tBaseName + "2" + tExtension);
				renameFile(toName, tf1.getCanonicalPath());
				File tf2 = new File(tDir, tBaseName + '2' + tExtension);
				copyFileOrDir(fromName, tf2.getCanonicalPath());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	/**
	 * Kopiert alle Files (mit Ausnahme einiger Filetypen (siehe copyFilter)) 
	 * vom dirTree in das Basisdirectory. Die Directory Struktur
	 * im dirTree wird ignoriert. Die Dateien heißen dann <name>.<ext> bzw. 
	 * <name><n>.<ext>. <n> ist eine fortlaufende Nummer bei mehreren Files mit
	 * gleicher Extension, <name> ist der subDir - Eintrag bei maktuellen Schüler.
	 * @param dirTree Das Basisdirectory des directory-Baums, von dem kopiert wird.
	 */
	private void moveDirTreeDirect(String dirTree, ConfigurationData cd,
		StudentName sn) {
		File of = new File(dirTree);
		boolean copyZip = (cd.suffixList==null || cd.suffixList.length == 0);
		MyFilenameFilter cf = new MyFilenameFilter(copyZip, true, cd.suffixList);
		String[] fileList = of.list(cf);
		if (fileList == null) 
			return;
		for (String fn: fileList) {
			if (new File(dirTree, fn).isDirectory()) {
				moveDirTreeDirect(dirTree + "\\" +fn, cd , sn);
			} else {
				String extension = "";
				int li = fn.lastIndexOf('.');
				if (li >= 0) {
					extension = fn.substring(li);
				}
				copySpecial(dirTree + "\\" + fn, cd.unzipDir + "\\" + sn.subDir+extension);
			}
		}
	}



	/**
	 * Kopiert alle Files (mit Ausnahme einiger Filetypen (siehe copyFilter)) 
	 * vom dirTree in das Basisdirectorry. Die Directory Struktur
	 * im dirTree wird ignoriert. Die Dateien heißen dann <name>.<ext> bzw. 
	 * <name><n>.<ext>. <n> ist eine fortlaufende Nummer bei mehreren Files mit
	 * gleicher Extension, <name> ist der subDir - Eintrag bei maktuellen Schüler.
	 * @param dirTree Das Basisdirectory des directory-Baums, von dem kopiert wird.
	 */
	private void moveDirTreeUnchanged(String dirTree, ConfigurationData cd,
		StudentName sn) {
		File of = new File(dirTree);
		// Change onJan. 14th, 2016, wj
		String[] fileList = of.list(null/*copyFilter*/);
		if (fileList == null) 
			return;
		for (String fn: fileList) {
			//System.out.println("Copyspecial: " + fn);
			copySpecial(dirTree + "\\" + fn, cd.unzipDir + "\\" + sn.subDir + "\\" + fn);
		}
	}
	
	/**
	 * Stellt fest, ob eine Datei ein Archiv ist, welches it 7-zip entpackt werden kann.
	 * @param name
	 * @return
	 */
	private static boolean isZipFile(String name) {
		if (name.endsWith(".zip")) 
			return true;
		if (name.endsWith(".rar")) 
			return true;
		if (name.endsWith(".7z")) 
			return true;
		if (name.endsWith(".war")) 
			return true;
		if (name.endsWith(".ear")) 
			return true;
		if (name.endsWith(".jar")) 
			return true;
		return false;
	}

	private void moveFiles(ConfigurationData cd) {
		moveFilesNew(cd);
	}

	/**
	 * Optionally unpack and move the files transmitted to moodle to the correct 
	 * package directory.
	 * Version for Moodle up to 06/2016
	 */
	private void moveFilesOld(ConfigurationData cd) {
		File of = new File(cd.helpDir);
		String[] fileList = of.list(copyFilter);
		for (String name: fileList) {
			//System.out.println("Filename: " + name);
			String fullName = cd.helpDir + "\\" + name;
			File fullNameFile = new File(fullName);
			StudentName t = findStudent(name, cd);
			if (t == null) { 
				System.out.println("File \"" + name + "\" has not been attached to a student!");
				continue;
			}
			if (fullNameFile.length() > cd.maxFileSize) {
				System.out.println("User " + t.getName() + ": File too big:\n   " + fullName);
				t.fileInfo = FileInfo.tooBig;
				continue;
			}
		   if (isZipFile(fullName)) {
				String dirName = fullName.substring(0, fullName.length()-4).trim();
				unzipFile(fullName, dirName);
				String destDir = cd.unzipDir + "\\" + t.subDir;
				/*if (cd.fst == FileStructureType.singleDir) {
					// Spezialsubdir für diesen Unpack-Typ!!
					destDir = cd.unzipDir + "\\kl" + cd.klasse + "\\" +
						cd.unzipSubdir + "\\" + t.subDir;
				}*/
				if (cd.fst == FileStructureType.noDir) {
					moveDirTreeDirect(dirName, cd , t);
				} else if (cd.fst == FileStructureType.singleDir) {
					new File(destDir).mkdirs();
					moveDirTreeFlat(dirName, destDir);
					updatePackage(destDir, cd.unzipDir);
				} else if (cd.fst == FileStructureType.dirTree) {
					new File(destDir).mkdirs();
					moveDirTreeUnchanged(dirName, cd , t);
					//System.out.println("dirTree " + t.name);
				} else {
					new File(destDir).mkdirs();
					copySpecial(dirName, destDir+ "\\" + t.subDir);
				}
				t.fileInfo = FileInfo.fileOkay;
		   } else {
				String actDir = cd.unzipDir;
				new File(actDir).mkdirs();
				String extension = "";
				int lastIndex = fullName.lastIndexOf('.');
				if (lastIndex > 0) {
					extension = fullName.substring(lastIndex);
				}
				if (cd.fst == FileStructureType.noDir) {
					copySpecial(fullName, actDir+"\\" + t.subDir + extension);
				} else if (cd.fst == FileStructureType.singleDir) {
					new File(actDir + "\\" + t.subDir).mkdirs();
					copySpecial(fullName, actDir + "\\" + t.subDir+ "\\" + t.subDir  + extension);
					updatePackage(actDir + "\\" + t.subDir, cd.unzipDir);
				} else if (cd.fst == FileStructureType.dirTree) {
					new File(actDir + "\\" + t.subDir).mkdirs();
					copySpecial(fullName, actDir + "\\" + t.subDir+ "\\" + t.subDir  + extension);
				} else {
					new File(actDir + "\\" + t.subDir).mkdirs();
					copySpecial(fullName, actDir + "\\" + t.subDir+ "\\" + t.subDir  + extension);
				}
				t.fileInfo = FileInfo.fileOkay;
			}
		}
	}

	/**
	 * Optionally unpack and move the files transmitted to moodle to the correct 
	 * package directory.
	 * Version for new Moodle Version 09/2016
	 */
	private void moveFilesNew(ConfigurationData cd) {
		File of = new File(cd.helpDir);
		
		// new intermediate directory structure
		// the intermediate files are all directories!!!!!
		String[] intermediateFileList = of.list();
		for (String intermediateFile:intermediateFileList) {
			StudentName t = findStudent(intermediateFile, cd);
			if (t == null) { 
				System.out.println("File \"" + intermediateFile + "\" has not been attached to a student!");
				continue;
			}
			String fullIntermediateName = cd.helpDir + "\\" + intermediateFile;
			File fullIntermediateNameFile = new File(fullIntermediateName);
			
		
			String[] fileList = fullIntermediateNameFile.list(copyFilter);
			for (String name: fileList) {
				System.out.println("Filename: " + name);
				String fullName = fullIntermediateName + "\\" + name;
				File fullNameFile = new File(fullName);
				if (fullNameFile.length() > cd.maxFileSize) {
					System.out.println("User " + t.getName() + ": File too big:\n   " + fullName);
					t.fileInfo = FileInfo.tooBig;
					continue;
				}
			   if (isZipFile(fullName)) {
					//String dirName = fullName.substring(0, fullName.length()-4).trim();
					String dirName = fullName.substring(0, fullName.lastIndexOf('.')).trim();
					unzipFile(fullName, dirName);
					String destDir = cd.unzipDir + "\\" + t.subDir;
					/*if (cd.fst == FileStructureType.singleDir) {
						// Spezialsubdir für diesen Unpack-Typ!!
						destDir = cd.unzipDir + "\\kl" + cd.klasse + "\\" +
							cd.unzipSubdir + "\\" + t.subDir;
					}*/
					if (cd.fst == FileStructureType.noDir) {
						moveDirTreeDirect(dirName, cd , t);
					} else if (cd.fst == FileStructureType.singleDir) {
						new File(destDir).mkdirs();
						moveDirTreeFlat(dirName, destDir);
						updatePackage(destDir, cd.unzipDir);
					} else if (cd.fst == FileStructureType.dirTree) {
						new File(destDir).mkdirs();
						moveDirTreeUnchanged(dirName, cd , t);
						//System.out.println("dirTree " + t.name);
					} else {
						new File(destDir).mkdirs();
						copySpecial(dirName, destDir+ "\\" + t.subDir);
					}
					t.fileInfo = FileInfo.fileOkay;
			   } else {
					String actDir = cd.unzipDir;
					new File(actDir).mkdirs();
					String extension = "";
					int lastIndex = fullName.lastIndexOf('.');
					if (lastIndex > 0) {
						extension = fullName.substring(lastIndex);
					}
					if (cd.fst == FileStructureType.noDir) {
						copySpecial(fullName, actDir+"\\" + t.subDir + extension);
					} else if (cd.fst == FileStructureType.singleDir) {
						new File(actDir + "\\" + t.subDir).mkdirs();
						copySpecial(fullName, actDir + "\\" + t.subDir+ "\\" + t.subDir  + extension);
						updatePackage(actDir + "\\" + t.subDir, cd.unzipDir);
					} else if (cd.fst == FileStructureType.dirTree) {
						new File(actDir + "\\" + t.subDir).mkdirs();
						copySpecial(fullName, actDir + "\\" + t.subDir+ "\\" + t.subDir  + extension);
					} else {
						new File(actDir + "\\" + t.subDir).mkdirs();
						copySpecial(fullName, actDir + "\\" + t.subDir+ "\\" + t.subDir  + extension);
					}
					t.fileInfo = FileInfo.fileOkay;
				}
			}
			
		} // end of intermediate file list examination
	}

	/**
	 * Ausführen eines Unzip-Auftrages.
	 */
	public void execute() {
		cd.update();
		init(cd);
		cd.print();
		readNameFile(cd);
		//u.cd.ignoreName(name)
		cd.writeNames();
		unzipMoodleFile(cd);
		moveFiles(cd);
		cd.writeNames();

	}
	
	
	public static void help () {
		System.out.println(
			"Moodle-File Unzipper Version 1.0, from February 15th, 2014\n" +
			"   Unzips all  deliveries of a single moodle task and stores them in various ways\n" +
			"   in order to enable a quick and simple correction process.\n" +
			"Parameters: \n" + 
			"-h: Print this help output.\n" +
			"-p: Print only parameters do not perform an unzip.\n" +
			"-w <workingDir>: The basic working directory. All data are per default within this working \n" +
			"   directory.\n" +
			"-c <class>: The name of the corresponding class. Per default all data of a class are assumed to \n" + 
			"   be within the subdirectory \"Klasse-<class>\" of the basic working directory.\n" +
			"-a <taskname>: The name of a specific task. A corresponding zip file must be available in the \n" +
			"   class directory and a corresponding task directory will be created. This task directory contains \n"+
			"   alldeliveries of the corresponding task. Existing data in this directory are lost.\n"+
			"-n <nameDir>: The nameDir contains a file for each class named \"Klasse-<class>.txt\" containing  \n" +
			"   the names of all students of this class. For a detailed description of the structure of the\n" +
			"   look at method readNameFile above.\n" +
			"-t <extraction type>: Determines the way, the deliveries are stored in the task direcotry. The following \n" +
			"   values are possible:\n" +
			"   noDir: Delivered files are named with the name of the student, the extension is preserved. If more than\n" +
			"      one file is delivered, the file names get a counter (e.g. weiser1.java, weiser2.java,...). Zipped \n" +
			"      deliverieswill be unzipped.\n" +
			"   singleDir: All files of a single student are stored in a single directory named after the name of the\n" +
			"      student. A zipped delivery is unzipped, however the directory structure of the zip-file is not\n" +
			"      preserved. All files will be found directly in the student directory. The filenames within a zipped \n" +
			"      delivery are preserved, however double file names get a counter similar to the \"noDir\" extraction\n" +
			"      method.\n" +
			"   dirTree: Extract each delivered zip-File to a single direcotry. The directory structure as well as the filenames\n" +
			"      are fully preserved. If more than one file is delivered these files or directories are named with\n" +
			"      counters as described above.\n" +
			"   dirTreePrefix: Same as with dirTree, however there is an intermediate directory named after teh student name\n" +
			"      (e.g. weiser\\weiser1, weiser\\weiser2,...).\n" +
			"-u <unzipDir>: A special unzip directory can be given, if the default naming <workingDir>\\<class>\\<taskname>\n" +
			"   is not appropriate.\n" +
			"-e <ExtensionList>:\n" +
			"   Per Default all but a few binary files (class, bin,...) are processed. If you give an extension list here, then \n" +
			"   only files having such an extension are unpacked, other files are ignored\n" +
			"   However the special meaning of the zip-file types cannot be changed.\n"+
			"-7 <7zipDir>: The directory, where 7-zip is installed. 7-zip is used in the extraction process.\n" +
			" ");
		
	}
	
	public static void inputError(String message) {
		System.out.println("\nERROR in input parameters:\n   " + message);
		help();
		System.exit(1);
	}
	
	/**
	 * Implemente the parameters as described above in the help method.
	 * @param args The parameters according to the help method.
	 */
	public static void mainNew(String[] args) {
		Unzipper u = new Unzipper();
		boolean printOnly = false;
		String klasse = null;
		String subject = null;
		String workingDir = null;
		
		if (args == null || args.length == 0) {
			help();
			return;
		}
		
		int index = 0;
		while (index <args.length) {
			switch (args[index].toLowerCase()) {
			case "-h":
				help();
				return;
			case "-p":
				printOnly = true;
				break;
			case "-7": // 7-zip working directory.
				u.cd.dir7zip = args[++index];
				break;
			case "-w": // working directory
				workingDir = args[++index];
				break;
			case "-u": // special unzipdir directory
				u.cd.unzipDir = args[++index];
				break;
			case "-n": // nameDir - Directory mit Namenslisten für alle Klassen
				u.cd.nameDir = args[++index];
				break;
			case "-a": // task
				u.cd.unzipSubdir = args[++index];
				break;
			case "-c": // class
				klasse = args[++index];
				u.cd.klasse = klasse;
				break;
			case "-f": // Unterrichtsfach
				subject = args[++index].toUpperCase();
				break;
			case "-t": // Art des Entpackens
				String s = args[++index].toLowerCase();
				switch (s) {
				case "nodir":
					u.cd.fst = FileStructureType.noDir;
					break;
				case "singledir":
					u.cd.fst = FileStructureType.singleDir;
					break;
				case "dirtree":
					u.cd.fst = FileStructureType.dirTree;
					break;
				case "dirtreeprefix":
					u.cd.fst = FileStructureType.dirTreePrefix;
					break;
				default:
					inputError("Invalid value for option -t");
					break;
				}
				break;
			case "-e":
				u.cd.suffixList = args[++index].toLowerCase().split(" +");
				break;
			case "-nf":
				u.cd.nameFile = args[++index];
				break;
			default:
				inputError("Invalid option " + args[index]);
				break;
			}
			index ++;
		}
		
		if (workingDir == null) {
			inputError("Option -w missing!");
		}
		if (klasse == null) {
			inputError("Option -c missing!");
		}
		u.cd.unzipDirBase = workingDir + "\\Klasse-" + klasse;
		if (subject != null) {
			u.cd.unzipDirBase = u.cd.unzipDirBase + "\\" + subject;
		}
		if (u.cd.nameFile == null) {
			u.cd.nameFile = "Klasse-" + klasse + ".txt";
		}
		
		if (printOnly) {
			// print only parameters
			u.cd.update();
			u.init(u.cd);
			u.cd.print();
			return;
		} 
		
		u.execute();
	}
	
	static String[] createInput() {
		String is[] = {
			"-w", "D:\\Weiser\\SJ1617", // Basic working directory
			"-n", "C:\\Weiser\\workspace1\\MoodleUnzipper\\data3", //name directory, where all name-list-files are stored.
			//"-n", "C:\\Weiser\\workspace1\\MoodleUnzipper\\dataTest", //name directory, where all name-list-files are stored.
			"-7", "C:\\Programme\\7-Zip",  // 7-zip installation directory
			
			//"-c", "1BHIT", "-a", "E02", "-f", "SYT",   //Klasse und Aufgabe SYT, SEW-TREE-A
			//"-c", "1BHIT", "-f", "SEW", "-a", "Au41",    //Klasse und Aufgabe SEW
			"-c", "4XSYT", "-a", "B07a",    //Klasse und Aufgabe SEW
			//"-c", "2DHIT", "-a", "B08a", //"-t", "singleDir",  //Klasse und Aufgabe SEW
			//"-c", "2CHIT", "-a", "T05", "-t", "singleDir",  //Klasse und Aufgabe SEW (TREE-B)
			//"-c", "3CHIT", "-a", "T03a", "-t", "singleDir", //"-t", "dirTree",  //Klasse und Aufgabe SEW
			//"-c", "3DHIT", "-a", "T02a", /*"-f", "ITP",*/ "-t", "singleDir",  //Klasse und Aufgabe ITP

			//"-c", "1CHIT", "-a", "T04", //"-f", "SYT",   //Klasse und Aufgabe
			//"-c", "3BHIT", "-a", "A17", "-t", "singleDir", //Klasse und Aufgabe
			//"-c", "4CHIT", "-a", "L02b", "-t", "dirTree",  //Klasse und Aufgabe
			//"-c", "4BHIT", "-a", "L02", "-t", "dirTree",  //Klasse und Aufgabe
			//"-c", "34ABEL", "-a", "E05", "-t", "noDir",  //Klasse und Aufgabe
			//"-c", "56ABEL", "-a", "B01",    //Klasse und Aufgabe
			
			//"-t", "noDir",    //Typ: noDir, singleDir, dirTree, dirTreePrefix 
			//"-f", "SYT",   // Unterrichtsfach nur bei 1CHIT bei mir
			//"-e", "docx thmx doc xls xlsx pdf odt rtf", // eingeschränkte Extensionlist, durch blanks getrennt 
			"-e", "java", // meist nur bei noDir und singleDir sinnvoll
			                 // bei anderen -t-Werten nur auf oberster Ebene (Abgabefiles).
			                 // Nicht für SYT sinnvoll!!
			//"-e", "java txt log", 
			//"-e", "java txt pdf doc docx log png jpg csv", 
			//"-e", "pdf",
			"-e", "c h pdf txt doc rtf",
			//"-e", "pde",  // processing
			//"-p", // print only parameters - no unpacking!
			//"-h",  // Ausgabe der Hilfe
			
			//"-u", "D:\\Weiser\\uebung\\klasse2chit\\src",  // spezielles unzip-Dirfür 2CHIT
			//"-u", "D:\\weiser\\uebung\\klasse2bhit\\src",  /// special unzip-dir for 2bhit
			//"-u", "D:\\weiser\\uebung\\klasse3bhit\\src",  
			//"-u", "D:\\weiser\\uebung\\klasse3chit\\src",  
			//"-u", "C:\\Weiser\\SJ1314\\Klasse-5AHIT\\wsa06",  // 5ahit, Aufgabe 06
			
			// -nf: nameFile, Filename mitden Namen der Schüler der Klasse, Default ist Klasse-<Klasse>.txt
			//"-nf", "Klasse-3CHITb.txt",
		};
		return is;
	}
	
	public static void main(String[] args) {
		//mainOld(null);
		args = createInput();
		mainNew(args);
	}


}
