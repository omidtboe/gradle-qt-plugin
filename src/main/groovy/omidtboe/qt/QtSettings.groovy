package omidtboe.qt

import org.gradle.model.Managed

@Managed
interface QtSettings {
	void setVersion(String v)
	String getVersion()

	void setHeaderDir(File f)
	File getHeaderDir()

	void setLibDir(File f)
	File getLibDir()

	void setModules(List<String> m)
	List<String> getModules()
}

