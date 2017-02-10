package omidtboe.qt

import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.nativeplatform.tasks.LinkExecutable

import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.TypeBuilder
import org.gradle.platform.base.ComponentType

import org.apache.commons.lang.StringUtils
import org.gradle.model.Defaults
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.language.cpp.CppSourceSet
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec

import omidtboe.qt.UiSourceSet
import omidtboe.qt.internal.DefaultUiSourceSet
import omidtboe.qt.QtSettings

class QtPlugin extends RuleSource {
	private boolean isValidModule(String version, String module) {
		def validModules = ['QtCore', 'QtDBus', 'QtGui', 'QtHelp', 'QtMultimedia', 'QtNetwork',
				    'QtOpenGL', 'QtSql', 'QtSvg', 'QtTest', 'QtXml', 'QtXmlPatterns']
		if (version == 'Qt5')
		{
			validModules += ['QtMultimediaWidgets', 'QtWidgets', 'QtConcurrent']
		}
		return module in validModules
	}

	private File getLibFile(String version, File libDir, String module) {
		def libFile = "lib${module}.so"
		if (version == 'Qt5')
		{
			libFile = libFile.replaceAll('Qt', 'Qt5')
		}
		return new File(libDir, libFile)
	}

	@Model
	void qtSettings(QtSettings qtSettings) {}

	@Defaults
	void setDefaultQtSettings(QtSettings settings) {
		settings.version "Qt5"
		settings.headerDir "/usr/include/qt5/"
		settings.libDir "/lib64/"
		settings.modules = ['QtCore', 'QtGui']
	}

	@Mutate
	void createQtMocTask(@Path("binaries") ModelMap<BinarySpec> binaries, final @Path("buildDir") File buildDir, QtSettings settings) {
		def moc = 'moc-qt4'
		if (settings.version.equalsIgnoreCase('qt5')) {
			moc = 'moc-qt5'
		}
		binaries.beforeEach { binary ->
			binary.inputs.withType(CppSourceSet) { sourceSet ->
				def taskName = "${binary.getNamingScheme().getTaskName('generateMoc')}${StringUtils.capitalize(sourceSet.parentName)}${StringUtils.capitalize(sourceSet.name)}"
				def destDir = new File(buildDir, "generated/${sourceSet.parentName}/moc")

				// Create moc task
				def mocHeaders = sourceSet.source.filter { x -> x.isFile() && x.getName().matches(".+?\\.h\\w*?") && x.text.contains('Q_OBJECT') }
				mocHeaders += sourceSet.exportedHeaders.filter { x -> x.isFile() && x.getName().matches(".+?\\.h\\w*?") && x.text.contains('Q_OBJECT') }
				if (!mocHeaders.isEmpty()) {
					binary.tasks.create(taskName, MocCompileTask) { task ->
						task.moc = moc
						// Add header files to moc task
						task.source = mocHeaders
						task.destinationDir = destDir
						task.description = "Generates Qt moc source files '${sourceSet.parentName}:${sourceSet.name}' for ${binary.displayName}"
					}

					// Add destination of generated moc_*.cpp to sourceset
					sourceSet.source.srcDir(destDir)

					// Set dependency so moc cpp files are generated first
					binary.tasks.withType(CppCompile) { compileTask ->
						compileTask.dependsOn(taskName)
					}
				}
			}
		}
	}

	@ComponentType
	void registerLanguage(TypeBuilder<UiSourceSet> builder) {
		builder.defaultImplementation(DefaultUiSourceSet);
	}

	@Mutate
	void createQtUicTasks(@Path("binaries") ModelMap<BinarySpec> binaries, final @Path("buildDir") File buildDir, QtSettings settings) {
		def uic = 'uic-qt4'
		if (settings.version.equalsIgnoreCase('qt5')) {
			uic = 'uic-qt5'
		}
		binaries.beforeEach { binary ->
			binary.inputs.withType(UiSourceSet) { uiSourceSet ->
				def taskName = "${binary.getNamingScheme().getTaskName('generateUi')}${StringUtils.capitalize(uiSourceSet.parentName)}${StringUtils.capitalize(uiSourceSet.name)}Headers"
				def destDir = new File(buildDir, "generated/${uiSourceSet.parentName}/uic")

				// Create uic task
				binary.tasks.create(taskName, UiCompileTask) { task ->
					task.uic = uic
					task.source = uiSourceSet.source
					task.destinationDir = destDir
					task.description = "Generates Qt ui header files '${uiSourceSet.parentName}:${uiSourceSet.name}' for ${binary.displayName}"
				}

				// Set exported header directory
				uiSourceSet.exportedHeaders.srcDir(destDir)

				// Add exported headers to cpp source sets
				binary.inputs.withType(CppSourceSet) { cppSourceSet ->
					cppSourceSet.lib(uiSourceSet)
				}

				// Set dependency so ui headers are generated first
				binary.tasks.withType(CppCompile) { compileTask ->
					compileTask.dependsOn(taskName)
				}
			}
		}
	}

	@Mutate
	void addQtDependencies(@Path("binaries") ModelMap<BinarySpec> binaries, QtSettings qtSettings) {
		def qtLibs = []
		def qtHeaders = [qtSettings.headerDir]
		qtSettings.modules.each {
			mod ->
			if (isValidModule(qtSettings.version, mod)) {
				qtLibs += getLibFile(qtSettings.version, qtSettings.libDir, mod)
				qtHeaders += new File(qtSettings.headerDir, mod)
			}
			else {
				println("Dropping invalid Qt module '${mod}'")
			}
		}

		binaries.beforeEach { binary ->
			binary.tasks.withType(CppCompile) { compileTask ->
				compileTask.includes(qtHeaders)
			}
		}

		binaries.withType(NativeExecutableBinarySpec) { binary ->
			binary.tasks.withType(LinkExecutable) { task ->
				task.lib(task.getProject().files(qtLibs))
			}
		}

		binaries.withType(SharedLibraryBinarySpec) { binary ->
			binary.tasks.withType(LinkSharedLibrary) { task ->
				task.lib(task.getProject().files(qtLibs))
			}
		}

		binaries.withType(NativeTestSuiteBinarySpec) { binary ->
			binary.tasks.withType(LinkExecutable) { task ->
				task.lib(task.getProject().files(qtLibs))
			}
		}
	}
}
